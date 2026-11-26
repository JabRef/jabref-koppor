---
parent: Requirements
---
# HTTP Server

## HTTP server must support cross-library search via query endpoint
`req~jabsrv.query.search~1`

The HTTP server exposes `POST /libraries:query` accepting `{ "queries": ["…"] }`, where each query is a Search.g4 expression.
Each query is run independently against all open libraries and returns the matching entries.

Results are returned in the same order as the input queries, so a caller matching a list of references can align the n-th query with the n-th reference.
An entry that matches in more than one library produces one match per library.

Needs: impl, utest

## HTTP server must return library groups as pre-order list
`req~jabsrv.groups.list~1`

The HTTP server exposes `GET /libraries/{id}/groups` returning the groups of the library as a flat, depth-first pre-order list.

Each group carries its name and its breadcrumb path from the top-level group down to and including itself.
The root `AllEntriesGroup` is not part of the result.
Group names are unique within a library, so the name identifies the group.

Needs: impl, utest

## HTTP server must allow importing entries directly into group
`req~jabsrv.import.group~1`

`POST /libraries/{id}/entries` accepts an optional `group` query parameter naming a group the imported entries are additionally assigned to.
If no group with that name exists, it is created as a top-level group.
JabRef merges the entries into the library, so the regular duplicate handling applies.

This is currently available in GUI mode only (the import is dispatched through the GUI message handler).

TODO: if the named group exists but is not assignable (e.g. a search or automatic group), the assignment silently does nothing and no error is reported. Rejecting such requests is not yet implemented.

Needs: impl

## HTTP server must list the local PDF files of a single entry
`req~jabsrv.entry.files.list~1`

The HTTP server exposes `GET /libraries/{id}/entries/{citationKey}/files` returning the linked files of that entry as a list of `fileName`, `parentCitationKey` and `path`.
Only files of type PDF are returned; online links are skipped.
An unknown citation key yields `404`.

The files are served per entry (and not for the whole library at once), because in [JabMap](https://github.com/JabRef/jabmap), PDF nodes are attached to the BibEntry node they belong to.

Needs: impl, utest

## HTTP server must offer semantic search over the linked files of a library
`req~jabsrv.ai.search~1`

The HTTP server exposes `POST /libraries/{id}/ai/search` accepting `{ "query": "…", "maxResults": 10, "minScore": 0.3 }`.
`maxResults` and `minScore` are optional and default to the values of the AI preferences.
The response lists the most relevant chunks of the files linked to the entries of the library, best first, each with `citationKey`, `file`, `page`, `score`, and `text`.

Only ingested files are searched.
Linked files which are not ingested are listed in `pendingFiles` and their ingestion is started; the request does not wait for it.
Linked files which cannot be read are listed in `notFoundFiles`.

The endpoint answers `503` with the reason if the AI features are disabled, the embedding model is not loaded, or the server runs without the GUI.
Requests carrying an `Origin` header are answered with `403`: the response contains full text of the user's files, which no web page should be able to request.

This is step 1 of [ADR-0086](../decisions/0086-offer-jabref-to-external-ai-agents-via-http-skill-and-mcp.md).

Needs: impl, utest

## HTTP server must offer its libraries to AI agents via MCP tools
`req~jabsrv.mcp.tools~1`

The HTTP server exposes the [Model Context Protocol](https://modelcontextprotocol.io/specification/2026-07-28) endpoint `POST /mcp` (Streamable HTTP transport, revision `2026-07-28` only).
It answers `server/discover`, `tools/list`, and `tools/call` with a single JSON object; it opens no stream and keeps no session.

The tool set is small and fixed, because each tool definition consumes context window of the calling model:

- `list_libraries`: the opened libraries with id, path, and number of entries.
- `search_linked_files`: semantic search over the linked files of a library, with the same answer as `POST /libraries/{id}/ai/search`.
- `search_entries`: search in the fields of the entries of a library with JabRef's search syntax.
- `get_entry`: an entry as BibTeX.

A request of another protocol revision is answered with the error `UnsupportedProtocolVersion` listing the supported revision, so that clients can tell the endpoint apart from a server of an older revision.
A problem a model can correct (unknown library, missing argument, AI features disabled) is reported as tool result with `isError`, not as protocol error.
Requests carrying an `Origin` header of another machine are answered with `403`.

This is step 2 of [ADR-0086](../decisions/0086-offer-jabref-to-external-ai-agents-via-http-skill-and-mcp.md).

Needs: impl, utest

<!-- markdownlint-disable-file MD022 -->
