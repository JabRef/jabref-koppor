package org.jabref.http.server.mcp;

import org.jspecify.annotations.NullMarked;

/// Signals that an [McpTool] cannot answer, for instance because an argument is missing or a library is not opened.
/// The message is handed to the model and should tell how to correct the call.
@NullMarked
public class McpToolException extends Exception {
    public McpToolException(String message) {
        super(message);
    }

    public McpToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
