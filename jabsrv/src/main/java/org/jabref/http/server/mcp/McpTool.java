package org.jabref.http.server.mcp;

import com.google.gson.JsonObject;
import org.jspecify.annotations.NullMarked;

/// A tool offered to external AI agents at the MCP endpoint ([McpResource]).
///
/// The tool set is deliberately small: each tool definition is sent to the model and consumes context window.
@NullMarked
public interface McpTool {

    /// @return the identifier the agent calls the tool with (ASCII letters, digits, `_`, `-`, `.`)
    String name();

    /// @return what the tool does and when to use it; read by the model
    String description();

    /// @return the JSON Schema of the arguments
    String inputSchema();

    /// @return the answer for the model, as text
    /// @throws McpToolException if the tool cannot answer; the message is shown to the model, so that it can correct its call
    String call(JsonObject arguments) throws McpToolException;
}
