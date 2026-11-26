package org.jabref.http.server;

import java.util.List;

import org.jabref.http.dto.SemanticSearchRequest;
import org.jabref.http.server.resources.AiSearchResource;
import org.jabref.logic.ai.rag.logic.SemanticSearch;
import org.jabref.logic.ai.rag.logic.SemanticSearchNotAvailableException;
import org.jabref.model.ai.pipeline.SemanticSearchMatch;
import org.jabref.model.ai.pipeline.SemanticSearchResult;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiSearchResourceTest extends ServerTest {

    private static final String NOT_AVAILABLE_QUERY = "not available";

    /// Stands in for the embeddings-backed search of the GUI: answers with one chunk echoing the parameters it was called with.
    private static final SemanticSearch SEMANTIC_SEARCH = (databaseContext, query, maximumResultsCount, minimumScore) -> {
        if (NOT_AVAILABLE_QUERY.equals(query)) {
            throw new SemanticSearchNotAvailableException("The AI features are disabled.");
        }
        return new SemanticSearchResult(
                List.of(
                        new SemanticSearchMatch("Cocoa2026", "cocoa.pdf", 7, 0.75, query + " in " + databaseContext.getEntries().size() + " entries, at most " + maximumResultsCount + " results"),
                        new SemanticSearchMatch(null, "notes.md", null, minimumScore, "Chunk of a file without pages of an entry without citation key")),
                List.of("pending.pdf"),
                List.of("missing.pdf"));
    };

    @Override
    protected Application configure() {
        ResourceConfig resourceConfig = new ResourceConfig(AiSearchResource.class);
        addGuiBridgeToResourceConfig(resourceConfig);
        addGsonToResourceConfig(resourceConfig);
        addPreferencesToResourceConfig(resourceConfig);
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
    void searchReturnsMatchesAndFilesNotSearched() {
        // [utest->req~jabsrv.ai.search~1]
        String body = """
                {"query": "chocolate", "maxResults": 3, "minScore": 0.25}""";
        String result = target("/libraries/" + TestBibFile.GENERAL_SERVER_TEST.id + "/ai/search")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(body), String.class);
        String expected = """
                {
                  "matches": [
                    {
                      "citationKey": "Cocoa2026",
                      "file": "cocoa.pdf",
                      "page": 7,
                      "score": 0.75,
                      "text": "chocolate in 3 entries, at most 3 results"
                    },
                    {
                      "citationKey": "{NONE}",
                      "file": "notes.md",
                      "score": 0.25,
                      "text": "Chunk of a file without pages of an entry without citation key"
                    }
                  ],
                  "pendingFiles": [
                    "pending.pdf"
                  ],
                  "notFoundFiles": [
                    "missing.pdf"
                  ]
                }""";
        assertEquals(expected, result);
    }

    @Test
    void searchWithoutQueryIsRejected() {
        Response response = target("/libraries/" + TestBibFile.GENERAL_SERVER_TEST.id + "/ai/search")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json("{}"));
        assertEquals(400, response.getStatus());
    }

    /// Calls the resource directly, because the HTTP client of the test framework drops the restricted header `Origin`.
    @Test
    void searchFromWebPageIsRejected() {
        // [utest->req~jabsrv.ai.search~1]
        SemanticSearchRequest request = new SemanticSearchRequest("chocolate", 3, 0.25);
        assertThrows(ForbiddenException.class,
                () -> new AiSearchResource().search(TestBibFile.GENERAL_SERVER_TEST.id, "https://example.org", request));
    }

    @Test
    void searchInUnknownLibraryIsNotFound() {
        Response response = target("/libraries/unknown/ai/search")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json("""
                        {"query": "chocolate", "maxResults": 3, "minScore": 0.25}"""));
        assertEquals(404, response.getStatus());
    }

    @Test
    void searchTellsWhySemanticSearchIsNotAvailable() {
        Response response = target("/libraries/" + TestBibFile.GENERAL_SERVER_TEST.id + "/ai/search")
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json("""
                        {"query": "not available", "maxResults": 3, "minScore": 0.25}"""));
        assertEquals(503, response.getStatus());
        assertEquals("The AI features are disabled.", response.readEntity(String.class));
    }
}
