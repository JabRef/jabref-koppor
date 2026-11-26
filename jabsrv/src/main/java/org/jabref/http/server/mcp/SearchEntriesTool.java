package org.jabref.http.server.mcp;

import java.util.EnumSet;
import java.util.List;

import org.jabref.http.SrvStateManager;
import org.jabref.logic.preferences.CliPreferences;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.search.SearchFlags;
import org.jabref.model.search.query.SearchQuery;
import org.jabref.model.search.query.SearchResults;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jspecify.annotations.NullMarked;

@NullMarked
class SearchEntriesTool implements McpTool {

    private static final int DEFAULT_MAXIMUM_RESULTS_COUNT = 25;

    private final SrvStateManager srvStateManager;
    private final CliPreferences preferences;

    SearchEntriesTool(SrvStateManager srvStateManager, CliPreferences preferences) {
        this.srvStateManager = srvStateManager;
        this.preferences = preferences;
    }

    @Override
    public String name() {
        return "search_entries";
    }

    @Override
    public String description() {
        return """
                Searches the fields of the entries of a library (not the full text of the linked files) with JabRef's search syntax \
                and returns citationKey, author, title, and year of the matching entries. \
                Examples for the query: `kubernetes` (any field), `author = kopp AND year >= 2020`, `title = "deployment model"`.""";
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
                      "description": "Query in JabRef's search syntax"
                    },
                    "maxResults": {
                      "type": "integer",
                      "minimum": 1,
                      "description": "Number of entries returned at most. Default: 25"
                    }
                  },
                  "required": ["library", "query"]
                }""";
    }

    @Override
    public String call(JsonObject arguments) throws McpToolException {
        BibDatabaseContext databaseContext = McpToolArguments.library(arguments, srvStateManager, preferences);
        String query = McpToolArguments.requiredString(arguments, "query");
        int maximumResultsCount = McpToolArguments.optionalInteger(arguments, "maxResults").orElse(DEFAULT_MAXIMUM_RESULTS_COUNT);

        SearchResults searchResults = srvStateManager.getSearchContext(databaseContext)
                                                     .search(new SearchQuery(query, EnumSet.noneOf(SearchFlags.class)));
        List<BibEntry> matchingEntries = databaseContext.getDatabase().getEntries().stream()
                                                        .filter(searchResults::isMatched)
                                                        .toList();

        JsonArray entries = new JsonArray();
        matchingEntries.stream().limit(maximumResultsCount).forEach(entry -> {
            JsonObject match = new JsonObject();
            entry.getCitationKey().ifPresent(citationKey -> match.addProperty("citationKey", citationKey));
            entry.getField(StandardField.AUTHOR).ifPresent(author -> match.addProperty("author", author));
            entry.getField(StandardField.TITLE).ifPresent(title -> match.addProperty("title", title));
            entry.getFieldOrAlias(StandardField.YEAR).ifPresent(year -> match.addProperty("year", year));
            entries.add(match);
        });

        JsonObject result = new JsonObject();
        result.addProperty("numberOfMatches", matchingEntries.size());
        result.add("entries", entries);
        return result.toString();
    }
}
