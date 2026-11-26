package org.jabref.http.server.mcp;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.jabref.http.server.ServerTest;
import org.jabref.http.server.TestBibFile;
import org.jabref.logic.ai.rag.logic.SemanticSearch;
import org.jabref.model.ai.pipeline.SemanticSearchMatch;
import org.jabref.model.ai.pipeline.SemanticSearchResult;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpResourceTest extends ServerTest {

    private static final String META = """
            "_meta": {
              "io.modelcontextprotocol/protocolVersion": "2026-07-28",
              "io.modelcontextprotocol/clientInfo": {"name": "TestClient", "version": "1.0.0"},
              "io.modelcontextprotocol/clientCapabilities": {}
            }""";

    /// Stands in for the embeddings-backed search of the GUI
    private static final SemanticSearch SEMANTIC_SEARCH = (_, query, _, _) ->
            new SemanticSearchResult(
                    List.of(new SemanticSearchMatch("Cocoa2026", "cocoa.pdf", 7, 0.75, "Passage about " + query)),
                    List.of("pending.pdf"),
                    List.of());

    @Override
    protected Application configure() {
        ResourceConfig resourceConfig = new ResourceConfig(McpResource.class);
        addGuiBridgeToResourceConfig(resourceConfig);
        addPreferencesToResourceConfig(resourceConfig);
        addEntryTypesManagerToResourceConfig(resourceConfig);
        addGlobalExceptionMapperToResourceConfig(resourceConfig);
        resourceConfig.register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(SEMANTIC_SEARCH).to(SemanticSearch.class);
            }
        });
        return resourceConfig.getApplication();
    }

    @Test
    void discoverTellsVersionAndCapabilities() {
        // [utest->req~jabsrv.mcp.tools~1]
        JsonObject result = call("server/discover", "{" + META + "}");

        assertEquals("complete", result.get("resultType").getAsString());
        assertEquals("[\"2026-07-28\"]", result.get("supportedVersions").toString());
        assertEquals("{\"tools\":{}}", result.get("capabilities").toString());
        assertEquals("JabRef", result.getAsJsonObject("_meta").getAsJsonObject("io.modelcontextprotocol/serverInfo").get("name").getAsString());
        assertEquals("public", result.get("cacheScope").getAsString());
    }

    @Test
    void toolsListOffersTheFixedToolSet() {
        // [utest->req~jabsrv.mcp.tools~1]
        JsonObject result = call("tools/list", "{" + META + "}");

        List<String> names = result.getAsJsonArray("tools").asList().stream()
                                   .map(tool -> tool.getAsJsonObject().get("name").getAsString())
                                   .toList();
        assertEquals(List.of("list_libraries", "search_linked_files", "search_entries", "get_entry"), names);
        assertEquals("object", result.getAsJsonArray("tools").get(0).getAsJsonObject().getAsJsonObject("inputSchema").get("type").getAsString());
    }

    @Test
    void listLibrariesReturnsTheOpenedLibraries() {
        JsonObject result = callTool("list_libraries", "{}");

        assertFalse(result.get("isError").getAsBoolean());
        assertEquals(
                "[{\"id\":\"%s\",\"path\":\"%s\",\"entries\":3}]".formatted(TestBibFile.GENERAL_SERVER_TEST.id, TestBibFile.GENERAL_SERVER_TEST.path.toString().replace("\\", "\\\\")),
                text(result));
    }

    @Test
    void searchLinkedFilesReturnsPassagesAndFilesNotSearched() {
        // [utest->req~jabsrv.mcp.tools~1]
        JsonObject result = callTool("search_linked_files", """
                {"library": "%s", "query": "chocolate", "maxResults": 3, "minScore": 0.25}""".formatted(TestBibFile.GENERAL_SERVER_TEST.id), true);

        assertFalse(result.get("isError").getAsBoolean());
        assertEquals(
                "{\"matches\":[{\"citationKey\":\"Cocoa2026\",\"file\":\"cocoa.pdf\",\"page\":7,\"score\":0.75,\"text\":\"Passage about chocolate\"}],\"pendingFiles\":[\"pending.pdf\"],\"notFoundFiles\":[]}",
                text(result));
    }

    @Test
    void searchEntriesReturnsMatchingEntries() {
        JsonObject result = callTool("search_entries", """
                {"library": "%s", "query": "title = \\"Test DOI Entry\\""}""".formatted(TestBibFile.GENERAL_SERVER_TEST.id));

        assertEquals(
                "{\"numberOfMatches\":1,\"entries\":[{\"citationKey\":\"doi2023entry\",\"author\":\"Test Author\",\"title\":\"Test DOI Entry\",\"year\":\"2023\"}]}",
                text(result));
    }

    @Test
    void getEntryReturnsBibtex() {
        JsonObject result = callTool("get_entry", """
                {"library": "%s", "citationKey": "Author2023test"}""".formatted(TestBibFile.GENERAL_SERVER_TEST.id));

        assertEquals("""
                @Misc{Author2023test,
                  author = {Demo Author},
                  title  = {Demo Title},
                  year   = {2023},
                }
                """, text(result));
    }

    @Test
    void toolReportsWrongArgumentsAsToolError() {
        JsonObject result = callTool("get_entry", """
                {"library": "unknown", "citationKey": "Author2023test"}""");

        assertTrue(result.get("isError").getAsBoolean());
        assertEquals("No opened library has the id \"unknown\". Call list_libraries for the ids.", text(result));
    }

    @Test
    void unknownToolIsInvalidParams() {
        Response response = request("tools/call", "unknown_tool")
                .post(Entity.json(message("tools/call", "{\"name\": \"unknown_tool\", \"arguments\": {}, " + META + "}")));

        assertEquals(400, response.getStatus());
        assertEquals(-32602, errorCode(response));
    }

    @Test
    void unknownMethodIsNotFound() {
        Response response = request("initialize", "")
                .post(Entity.json(message("initialize", "{" + META + "}")));

        assertEquals(404, response.getStatus());
        assertEquals(-32601, errorCode(response));
    }

    @Test
    void otherProtocolVersionIsUnsupported() {
        // [utest->req~jabsrv.mcp.tools~1]
        Response response = target("/mcp").request(MediaType.APPLICATION_JSON)
                                          .header("MCP-Protocol-Version", "2025-11-25")
                                          .header("Mcp-Method", "tools/list")
                                          .post(Entity.json(message("tools/list", "{}")));

        assertEquals(400, response.getStatus());
        JsonObject error = JsonParser.parseString(response.readEntity(String.class)).getAsJsonObject().getAsJsonObject("error");
        assertEquals(-32022, error.get("code").getAsInt());
        assertEquals("{\"supported\":[\"2026-07-28\"],\"requested\":\"2025-11-25\"}", error.get("data").toString());
    }

    @Test
    void missingRequestMetadataIsInvalidParams() {
        Response response = request("tools/list", "").post(Entity.json(message("tools/list", "{}")));

        assertEquals(400, response.getStatus());
        assertEquals(-32602, errorCode(response));
    }

    @Test
    void headerNotMatchingBodyIsHeaderMismatch() {
        Response response = request("tools/list", "").post(Entity.json(message("server/discover", "{" + META + "}")));

        assertEquals(400, response.getStatus());
        assertEquals(-32020, errorCode(response));
    }

    @Test
    void missingToolNameHeaderIsHeaderMismatch() {
        Response response = request("tools/call", "")
                .post(Entity.json(message("tools/call", "{\"name\": \"list_libraries\", \"arguments\": {}, " + META + "}")));

        assertEquals(400, response.getStatus());
        assertEquals(-32020, errorCode(response));
    }

    @Test
    void notificationIsAccepted() {
        Response response = request("notifications/cancelled", "")
                .post(Entity.json("{\"jsonrpc\": \"2.0\", \"method\": \"notifications/cancelled\"}"));

        assertEquals(202, response.getStatus());
    }

    @Test
    void getIsNotAllowed() {
        assertEquals(405, target("/mcp").request().get().getStatus());
    }

    private Invocation.Builder request(String method, String toolName) {
        Invocation.Builder builder = target("/mcp").request(MediaType.APPLICATION_JSON)
                                                   .header("MCP-Protocol-Version", McpResource.PROTOCOL_VERSION)
                                                   .header("Mcp-Method", method);
        if (!toolName.isEmpty()) {
            builder = builder.header("Mcp-Name", toolName);
        }
        return builder;
    }

    private static String message(String method, String params) {
        return "{\"jsonrpc\": \"2.0\", \"id\": 1, \"method\": \"" + method + "\", \"params\": " + params + "}";
    }

    private JsonObject call(String method, String params) {
        String response = request(method, "").post(Entity.json(message(method, params)), String.class);
        return JsonParser.parseString(response).getAsJsonObject().getAsJsonObject("result");
    }

    private JsonObject callTool(String toolName, String arguments) {
        return callTool(toolName, arguments, false);
    }

    /// @param encodeName whether the tool name is sent in the Base64 form of the `Mcp-Name` header
    private JsonObject callTool(String toolName, String arguments, boolean encodeName) {
        String nameHeader = encodeName
                            ? "=?base64?" + Base64.getEncoder().encodeToString(toolName.getBytes(StandardCharsets.UTF_8)) + "?="
                            : toolName;
        String params = "{\"name\": \"" + toolName + "\", \"arguments\": " + arguments + ", " + META + "}";
        String response = request("tools/call", nameHeader).post(Entity.json(message("tools/call", params)), String.class);
        return JsonParser.parseString(response).getAsJsonObject().getAsJsonObject("result");
    }

    private static String text(JsonObject toolResult) {
        return toolResult.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString();
    }

    private static int errorCode(Response response) {
        return JsonParser.parseString(response.readEntity(String.class)).getAsJsonObject().getAsJsonObject("error").get("code").getAsInt();
    }
}
