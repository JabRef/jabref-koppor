package org.jabref.http.server.mcp;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.jabref.http.SrvStateManager;
import org.jabref.logic.ai.rag.logic.SemanticSearch;
import org.jabref.logic.preferences.CliPreferences;
import org.jabref.logic.util.BuildInfo;
import org.jabref.logic.util.strings.StringUtil;
import org.jabref.model.entry.BibEntryTypesManager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// The MCP endpoint of JabRef: offers a small, fixed set of tools to external AI agents via the
/// [Model Context Protocol](https://modelcontextprotocol.io/specification/2026-07-28), Streamable HTTP transport.
///
/// Only protocol revision `2026-07-28` is spoken. That revision has no handshake and no sessions: each request is a
/// self-contained HTTP POST, which is why this class needs no state. Requests are answered with a single JSON object;
/// no SSE stream is opened. `GET` and `DELETE` are answered with `405` by the JAX-RS runtime, as the revision asks for.
///
/// Written by hand instead of with the MCP Java SDK, see ADR-0086.
// [impl->req~jabsrv.mcp.tools~1]
@NullMarked
@Path("mcp")
public class McpResource {

    public static final String PROTOCOL_VERSION = "2026-07-28";

    private static final Logger LOGGER = LoggerFactory.getLogger(McpResource.class);

    private static final String META = "_meta";
    private static final String META_PROTOCOL_VERSION = "io.modelcontextprotocol/protocolVersion";
    private static final String META_CLIENT_CAPABILITIES = "io.modelcontextprotocol/clientCapabilities";
    private static final String META_SERVER_INFO = "io.modelcontextprotocol/serverInfo";

    private static final int PARSE_ERROR = -32700;
    private static final int INVALID_REQUEST = -32600;
    private static final int METHOD_NOT_FOUND = -32601;
    private static final int INVALID_PARAMS = -32602;
    private static final int HEADER_MISMATCH = -32020;
    private static final int UNSUPPORTED_PROTOCOL_VERSION = -32022;

    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");

    private static final String BASE64_PREFIX = "=?base64?";
    private static final String BASE64_SUFFIX = "?=";

    /// The tool set changes only with a new JabRef version
    private static final int TIME_TO_LIVE_IN_MILLISECONDS = 3_600_000;

    private static final String INSTRUCTIONS = """
            JabRef is a reference manager. This server gives access to the libraries (.bib files) opened in the running JabRef. \
            Start with list_libraries to learn the library ids. \
            search_linked_files searches the full text of the PDFs linked to the entries by meaning; \
            search_entries searches the fields of the entries (author, title, year, ...); \
            get_entry returns an entry as BibTeX.""";

    @Inject
    private SrvStateManager srvStateManager;

    @Inject
    private CliPreferences preferences;

    @Inject
    private BibEntryTypesManager entryTypesManager;

    @Inject
    private SemanticSearch semanticSearch;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response handle(@HeaderParam("Origin") @Nullable String origin,
                           @HeaderParam("MCP-Protocol-Version") @Nullable String protocolVersionHeader,
                           @HeaderParam("Mcp-Method") @Nullable String methodHeader,
                           @HeaderParam("Mcp-Name") @Nullable String nameHeader,
                           String body) {
        if (StringUtil.isNotBlank(origin) && !isLocalOrigin(origin)) {
            // A web page of another site must not reach the libraries of the user (DNS rebinding)
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        JsonObject request;
        try {
            JsonElement parsed = JsonParser.parseString(body);
            if (!parsed.isJsonObject()) {
                return errorWithoutId(INVALID_REQUEST, "The body has to be a single JSON-RPC message");
            }
            request = parsed.getAsJsonObject();
        } catch (JsonParseException e) {
            LOGGER.debug("Could not parse MCP request", e);
            return errorWithoutId(PARSE_ERROR, "Parse error");
        }

        Optional<String> method = string(request, "method");
        if (method.isEmpty()) {
            return errorWithoutId(INVALID_REQUEST, "The field \"method\" is required");
        }
        if (!request.has("id")) {
            // Notification. This revision defines none sent by clients over HTTP; accepting it is harmless.
            return Response.accepted().build();
        }
        JsonElement id = request.get("id");
        JsonObject params = object(request, "params").orElseGet(JsonObject::new);

        if (StringUtil.isBlank(protocolVersionHeader)) {
            return error(Response.Status.BAD_REQUEST, id, HEADER_MISMATCH, "Header mismatch: the header MCP-Protocol-Version is required");
        }
        if (!PROTOCOL_VERSION.equals(protocolVersionHeader)) {
            return unsupportedProtocolVersion(id, protocolVersionHeader);
        }
        JsonObject meta = object(params, META).orElseGet(JsonObject::new);
        Optional<String> protocolVersion = string(meta, META_PROTOCOL_VERSION);
        if (protocolVersion.isEmpty() || !meta.has(META_CLIENT_CAPABILITIES)) {
            return error(Response.Status.BAD_REQUEST, id, INVALID_PARAMS,
                    "Invalid params: _meta has to contain " + META_PROTOCOL_VERSION + " and " + META_CLIENT_CAPABILITIES);
        }
        if (!protocolVersionHeader.equals(protocolVersion.get())) {
            return error(Response.Status.BAD_REQUEST, id, HEADER_MISMATCH,
                    "Header mismatch: the header MCP-Protocol-Version does not match " + META_PROTOCOL_VERSION + " of the body");
        }
        if (!method.get().equals(methodHeader)) {
            return error(Response.Status.BAD_REQUEST, id, HEADER_MISMATCH, "Header mismatch: the header Mcp-Method is missing or does not match the method of the body");
        }

        return switch (method.get()) {
            case "server/discover" ->
                    result(id, discover());
            case "tools/list" ->
                    result(id, listTools());
            case "tools/call" ->
                    callTool(id, params, nameHeader);
            default ->
                    error(Response.Status.NOT_FOUND, id, METHOD_NOT_FOUND, "Method not found: " + method.get());
        };
    }

    private List<McpTool> tools() {
        return List.of(
                new ListLibrariesTool(srvStateManager),
                new SearchLinkedFilesTool(srvStateManager, preferences, semanticSearch),
                new SearchEntriesTool(srvStateManager, preferences),
                new GetEntryTool(srvStateManager, preferences, entryTypesManager));
    }

    private JsonObject discover() {
        JsonArray supportedVersions = new JsonArray();
        supportedVersions.add(PROTOCOL_VERSION);
        JsonObject capabilities = new JsonObject();
        capabilities.add("tools", new JsonObject());

        JsonObject result = new JsonObject();
        result.add("supportedVersions", supportedVersions);
        result.add("capabilities", capabilities);
        result.addProperty("instructions", INSTRUCTIONS);
        addCachingHints(result);
        return result;
    }

    private JsonObject listTools() {
        JsonArray tools = new JsonArray();
        for (McpTool tool : tools()) {
            JsonObject description = new JsonObject();
            description.addProperty("name", tool.name());
            description.addProperty("description", tool.description());
            description.add("inputSchema", JsonParser.parseString(tool.inputSchema()));
            tools.add(description);
        }
        JsonObject result = new JsonObject();
        result.add("tools", tools);
        addCachingHints(result);
        return result;
    }

    private Response callTool(JsonElement id, JsonObject params, @Nullable String nameHeader) {
        Optional<String> name = string(params, "name");
        if (name.isEmpty()) {
            return error(Response.Status.BAD_REQUEST, id, INVALID_PARAMS, "Invalid params: the field \"name\" is required");
        }
        if (!name.get().equals(decodeHeaderValue(nameHeader))) {
            return error(Response.Status.BAD_REQUEST, id, HEADER_MISMATCH, "Header mismatch: the header Mcp-Name is missing or does not match the name of the body");
        }
        Optional<McpTool> tool = tools().stream().filter(candidate -> candidate.name().equals(name.get())).findFirst();
        if (tool.isEmpty()) {
            return error(Response.Status.BAD_REQUEST, id, INVALID_PARAMS, "Unknown tool: " + name.get());
        }

        JsonObject arguments = object(params, "arguments").orElseGet(JsonObject::new);
        String text;
        boolean isError;
        try {
            text = tool.get().call(arguments);
            isError = false;
        } catch (McpToolException e) {
            // Reported as result (not as protocol error), so that the model can correct its call
            LOGGER.debug("MCP tool {} could not answer", name.get(), e);
            text = e.getMessage();
            isError = true;
        }

        JsonObject textContent = new JsonObject();
        textContent.addProperty("type", "text");
        textContent.addProperty("text", text);
        JsonArray content = new JsonArray();
        content.add(textContent);

        JsonObject result = new JsonObject();
        result.add("content", content);
        result.addProperty("isError", isError);
        return result(id, result);
    }

    private static void addCachingHints(JsonObject result) {
        result.addProperty("ttlMs", TIME_TO_LIVE_IN_MILLISECONDS);
        result.addProperty("cacheScope", "public");
    }

    private static Response result(JsonElement id, JsonObject result) {
        JsonObject serverInfo = new JsonObject();
        serverInfo.addProperty("name", "JabRef");
        serverInfo.addProperty("version", new BuildInfo().version.toString());
        JsonObject meta = new JsonObject();
        meta.add(META_SERVER_INFO, serverInfo);

        result.addProperty("resultType", "complete");
        result.add(META, meta);

        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        response.add("result", result);
        return Response.ok(response.toString(), MediaType.APPLICATION_JSON).build();
    }

    private static Response unsupportedProtocolVersion(JsonElement id, String requested) {
        JsonArray supported = new JsonArray();
        supported.add(PROTOCOL_VERSION);
        JsonObject data = new JsonObject();
        data.add("supported", supported);
        data.addProperty("requested", requested);
        return error(Response.Status.BAD_REQUEST, id, UNSUPPORTED_PROTOCOL_VERSION, "Unsupported protocol version", data);
    }

    private static Response error(Response.Status status, JsonElement id, int code, String message) {
        return error(status, id, code, message, new JsonObject());
    }

    private static Response error(Response.Status status, JsonElement id, int code, String message, JsonObject data) {
        JsonObject response = errorResponse(code, message, data);
        response.add("id", id);
        return Response.status(status).entity(response.toString()).type(MediaType.APPLICATION_JSON).build();
    }

    /// For requests whose id could not be read
    private static Response errorWithoutId(int code, String message) {
        JsonObject response = errorResponse(code, message, new JsonObject());
        return Response.status(Response.Status.BAD_REQUEST).entity(response.toString()).type(MediaType.APPLICATION_JSON).build();
    }

    private static JsonObject errorResponse(int code, String message, JsonObject data) {
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        if (!data.isEmpty()) {
            error.add("data", data);
        }
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("error", error);
        return response;
    }

    /// Header values which are not plain ASCII are sent as `=?base64?…?=`.
    private static String decodeHeaderValue(@Nullable String value) {
        if (StringUtil.isBlank(value)) {
            return "";
        }
        if (value.startsWith(BASE64_PREFIX) && value.endsWith(BASE64_SUFFIX) && value.length() >= BASE64_PREFIX.length() + BASE64_SUFFIX.length()) {
            String encoded = value.substring(BASE64_PREFIX.length(), value.length() - BASE64_SUFFIX.length());
            try {
                return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                LOGGER.debug("Header value is not valid Base64", e);
                return "";
            }
        }
        return value;
    }

    /// Browser-based MCP clients served from the same machine are let in; all other web pages are not.
    private static boolean isLocalOrigin(String origin) {
        try {
            return Optional.ofNullable(new URI(origin).getHost())
                           .map(host -> host.toLowerCase(Locale.ROOT))
                           .filter(LOCAL_HOSTS::contains)
                           .isPresent();
        } catch (URISyntaxException e) {
            LOGGER.debug("Origin {} is not a local one", origin, e);
            return false;
        }
    }

    private static Optional<String> string(JsonObject object, String name) {
        return Optional.ofNullable(object.get(name))
                       .filter(JsonElement::isJsonPrimitive)
                       .map(JsonElement::getAsJsonPrimitive)
                       .filter(JsonPrimitive::isString)
                       .map(JsonPrimitive::getAsString);
    }

    private static Optional<JsonObject> object(JsonObject object, String name) {
        return Optional.ofNullable(object.get(name))
                       .filter(JsonElement::isJsonObject)
                       .map(JsonElement::getAsJsonObject);
    }
}
