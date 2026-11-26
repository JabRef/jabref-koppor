package org.jabref.http.server.mcp;

import java.io.IOException;
import java.util.Optional;

import org.jabref.http.SrvStateManager;
import org.jabref.http.server.services.ServerUtils;
import org.jabref.logic.preferences.CliPreferences;
import org.jabref.logic.util.strings.StringUtil;
import org.jabref.model.database.BibDatabaseContext;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import jakarta.ws.rs.NotFoundException;
import org.jspecify.annotations.NullMarked;

/// Reads the arguments of a tool call. Agents send what the model generated, so each value is checked.
@NullMarked
final class McpToolArguments {

    private McpToolArguments() {
    }

    static String requiredString(JsonObject arguments, String name) throws McpToolException {
        return optionalString(arguments, name)
                .filter(StringUtil::isNotBlank)
                .orElseThrow(() -> new McpToolException("The argument \"" + name + "\" is required and has to be a non-empty string."));
    }

    static Optional<String> optionalString(JsonObject arguments, String name) {
        return primitive(arguments, name)
                .filter(JsonPrimitive::isString)
                .map(JsonPrimitive::getAsString);
    }

    static Optional<Integer> optionalInteger(JsonObject arguments, String name) {
        return primitive(arguments, name)
                .filter(JsonPrimitive::isNumber)
                .map(JsonPrimitive::getAsInt);
    }

    static Optional<Double> optionalNumber(JsonObject arguments, String name) {
        return primitive(arguments, name)
                .filter(JsonPrimitive::isNumber)
                .map(JsonPrimitive::getAsDouble);
    }

    /// Resolves the argument `library` to an opened library.
    static BibDatabaseContext library(JsonObject arguments, SrvStateManager srvStateManager, CliPreferences preferences) throws McpToolException {
        String libraryId = requiredString(arguments, "library");
        try {
            return ServerUtils.getBibDatabaseContext(libraryId, srvStateManager, preferences.getImportFormatPreferences());
        } catch (NotFoundException | IOException e) {
            throw new McpToolException("No opened library has the id \"" + libraryId + "\". Call list_libraries for the ids.", e);
        }
    }

    private static Optional<JsonPrimitive> primitive(JsonObject arguments, String name) {
        return Optional.ofNullable(arguments.get(name))
                       .filter(JsonElement::isJsonPrimitive)
                       .map(JsonElement::getAsJsonPrimitive);
    }
}
