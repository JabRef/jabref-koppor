package org.jabref.http.dto;

import java.util.Optional;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// Request body for `POST /libraries/{id}/ai/search`.
///
/// `maxResults` and `minScore` (0 to 1) are optional; if absent, the values of JabRef's AI preferences are used.
/// A missing `query` is normalized to the empty string, which the resource rejects.
@NullMarked
public record SemanticSearchRequest(String query, @Nullable Integer maxResults, @Nullable Double minScore) {
    public SemanticSearchRequest(@Nullable String query, @Nullable Integer maxResults, @Nullable Double minScore) {
        this.query = Optional.ofNullable(query).orElse("");
        this.maxResults = maxResults;
        this.minScore = minScore;
    }
}
