package org.jabref.http.server.mcp;

import org.jabref.http.SrvStateManager;
import org.jabref.http.dto.SemanticSearchResponse;
import org.jabref.logic.ai.rag.logic.SemanticSearch;
import org.jabref.logic.ai.rag.logic.SemanticSearchNotAvailableException;
import org.jabref.logic.preferences.CliPreferences;
import org.jabref.model.database.BibDatabaseContext;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.jspecify.annotations.NullMarked;

/// The MCP counterpart of `POST /libraries/{id}/ai/search`: both delegate to [SemanticSearch] and answer with the same JSON.
// [impl->adr~external-ai-agent-access~1]
@NullMarked
class SearchLinkedFilesTool implements McpTool {

    private static final Gson GSON = new Gson();

    private final SrvStateManager srvStateManager;
    private final CliPreferences preferences;
    private final SemanticSearch semanticSearch;

    SearchLinkedFilesTool(SrvStateManager srvStateManager, CliPreferences preferences, SemanticSearch semanticSearch) {
        this.srvStateManager = srvStateManager;
        this.preferences = preferences;
        this.semanticSearch = semanticSearch;
    }

    @Override
    public String name() {
        return "search_linked_files";
    }

    @Override
    public String description() {
        return """
                Semantic search in the full text of the files (PDFs) linked to the entries of a library: \
                returns the passages most similar in meaning to the query, each with citationKey, file, page, score (0 to 1), and text. \
                Use it to find which papers say something about a topic or to find a passage to cite. \
                Files listed in pendingFiles (not ingested yet; ingestion was started, repeat the search later) and in notFoundFiles were not searched.""";
    }

    @Override
    public String inputSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "library": {
                      "type": "string",
                      "description": "Id of the library, from list_libraries"
                    },
                    "query": {
                      "type": "string",
                      "description": "Question or statement in natural language; full sentences work better than keywords"
                    },
                    "maxResults": {
                      "type": "integer",
                      "minimum": 1,
                      "description": "Number of passages returned at most. Default: JabRef's AI preferences"
                    },
                    "minScore": {
                      "type": "number",
                      "minimum": 0,
                      "maximum": 1,
                      "description": "Passages with a lower score are left out. Default: JabRef's AI preferences"
                    }
                  },
                  "required": ["library", "query"]
                }""";
    }

    @Override
    public String call(JsonObject arguments) throws McpToolException {
        BibDatabaseContext databaseContext = McpToolArguments.library(arguments, srvStateManager, preferences);
        String query = McpToolArguments.requiredString(arguments, "query");
        int maximumResultsCount = McpToolArguments.optionalInteger(arguments, "maxResults")
                                                  .orElseGet(() -> preferences.getAiPreferences().getRagMaxResultsCount());
        double minimumScore = McpToolArguments.optionalNumber(arguments, "minScore")
                                              .orElseGet(() -> preferences.getAiPreferences().getRagMinScore());
        try {
            return GSON.toJson(SemanticSearchResponse.of(semanticSearch.search(databaseContext, query, maximumResultsCount, minimumScore)));
        } catch (SemanticSearchNotAvailableException e) {
            throw new McpToolException(e.getMessage(), e);
        }
    }
}
