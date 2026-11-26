package org.jabref.http.server.mcp;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

import org.jabref.http.SrvStateManager;
import org.jabref.logic.bibtex.BibEntryWriter;
import org.jabref.logic.bibtex.FieldWriter;
import org.jabref.logic.exporter.BibWriter;
import org.jabref.logic.preferences.CliPreferences;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.BibEntryTypesManager;

import com.google.gson.JsonObject;
import org.jspecify.annotations.NullMarked;

@NullMarked
class GetEntryTool implements McpTool {

    private final SrvStateManager srvStateManager;
    private final CliPreferences preferences;
    private final BibEntryTypesManager entryTypesManager;

    GetEntryTool(SrvStateManager srvStateManager, CliPreferences preferences, BibEntryTypesManager entryTypesManager) {
        this.srvStateManager = srvStateManager;
        this.preferences = preferences;
        this.entryTypesManager = entryTypesManager;
    }

    @Override
    public String name() {
        return "get_entry";
    }

    @Override
    public String description() {
        return "Returns the entry with the given citation key as BibTeX, including abstract, keywords, DOI, and the linked files.";
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
                    "citationKey": {
                      "type": "string",
                      "description": "Citation key of the entry"
                    }
                  },
                  "required": ["library", "citationKey"]
                }""";
    }

    @Override
    public String call(JsonObject arguments) throws McpToolException {
        BibDatabaseContext databaseContext = McpToolArguments.library(arguments, srvStateManager, preferences);
        String citationKey = McpToolArguments.requiredString(arguments, "citationKey");

        List<BibEntry> entries = databaseContext.getDatabase().getEntriesByCitationKey(citationKey);
        if (entries.isEmpty()) {
            throw new McpToolException("The library has no entry with the citation key \"" + citationKey + "\". Use search_entries to find the key.");
        }

        StringWriter bibtex = new StringWriter();
        BibEntryWriter entryWriter = new BibEntryWriter(new FieldWriter(preferences.getFieldPreferences()), entryTypesManager);
        try {
            entryWriter.write(entries.getFirst(), new BibWriter(bibtex, "\n"), databaseContext.getMode());
        } catch (IOException e) {
            throw new McpToolException("The entry could not be written as BibTeX.", e);
        }
        return bibtex.toString();
    }
}
