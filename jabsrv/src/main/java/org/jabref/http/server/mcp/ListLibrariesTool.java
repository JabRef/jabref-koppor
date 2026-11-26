package org.jabref.http.server.mcp;

import org.jabref.http.SrvStateManager;
import org.jabref.model.database.BibDatabaseContext;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jspecify.annotations.NullMarked;

@NullMarked
class ListLibrariesTool implements McpTool {

    private final SrvStateManager srvStateManager;

    ListLibrariesTool(SrvStateManager srvStateManager) {
        this.srvStateManager = srvStateManager;
    }

    @Override
    public String name() {
        return "list_libraries";
    }

    @Override
    public String description() {
        return "Lists the libraries (.bib files) opened in JabRef with id, file path, and number of entries. The other tools take the id as argument \"library\".";
    }

    @Override
    public String inputSchema() {
        return """
                {
                  "type": "object",
                  "additionalProperties": false
                }""";
    }

    @Override
    public String call(JsonObject arguments) {
        JsonArray libraries = new JsonArray();
        for (BibDatabaseContext databaseContext : srvStateManager.getOpenDatabases()) {
            databaseContext.getLibraryId().ifPresent(id -> {
                JsonObject library = new JsonObject();
                library.addProperty("id", id);
                databaseContext.getDatabasePath().ifPresent(path -> library.addProperty("path", path.toString()));
                library.addProperty("entries", databaseContext.getEntries().size());
                libraries.add(library);
            });
        }
        return libraries.toString();
    }
}
