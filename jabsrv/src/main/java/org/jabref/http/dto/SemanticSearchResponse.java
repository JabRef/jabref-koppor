package org.jabref.http.dto;

import java.util.List;

import org.jabref.model.ai.pipeline.SemanticSearchResult;

import org.jspecify.annotations.NullMarked;

/// Response body of `POST /libraries/{id}/ai/search`.
///
/// @param matches       the most relevant chunks, best first
/// @param pendingFiles  linked files which are not ingested yet and therefore were not searched; their ingestion was started
/// @param notFoundFiles linked files which could not be read and therefore were not searched
@NullMarked
public record SemanticSearchResponse(List<SemanticSearchMatchDTO> matches, List<String> pendingFiles, List<String> notFoundFiles) {
    public static SemanticSearchResponse of(SemanticSearchResult result) {
        return new SemanticSearchResponse(
                result.matches().stream().map(SemanticSearchMatchDTO::of).toList(),
                result.pendingFiles(),
                result.notFoundFiles());
    }
}
