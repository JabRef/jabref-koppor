/// The MCP (Model Context Protocol) endpoint for external AI agents and its tools.
///
/// [org.jabref.http.server.mcp.McpResource] speaks the protocol; each tool is an [org.jabref.http.server.mcp.McpTool].
/// To add a tool, implement the interface and add it to the list in `McpResource`.
/// Keep the set small: each tool definition consumes context window of the calling model.
@NullMarked
package org.jabref.http.server.mcp;

import org.jspecify.annotations.NullMarked;
