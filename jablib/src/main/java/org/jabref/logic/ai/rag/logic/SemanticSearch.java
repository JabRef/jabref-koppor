package org.jabref.logic.ai.rag.logic;

import org.jabref.model.ai.pipeline.SemanticSearchResult;
import org.jabref.model.database.BibDatabaseContext;

import org.jspecify.annotations.NullMarked;

/// Semantic search over the files linked to the entries of a library, offered to callers outside of JabRef's AI chat
/// (HTTP server, MCP server).
@NullMarked
public interface SemanticSearch {

    /// For contexts without AI services, such as the stand-alone HTTP server:
    /// the embeddings store is owned by the running JabRef GUI.
    SemanticSearch NONE = (_, _, _, _) -> {
        throw new SemanticSearchNotAvailableException("Semantic search is available only in a running JabRef (GUI).");
    };

    /// @param maximumResultsCount the number of chunks to return at most
    /// @param minimumScore        chunks with a lower relevance (0 to 1) are left out
    SemanticSearchResult search(BibDatabaseContext databaseContext, String query, int maximumResultsCount, double minimumScore) throws SemanticSearchNotAvailableException;
}
