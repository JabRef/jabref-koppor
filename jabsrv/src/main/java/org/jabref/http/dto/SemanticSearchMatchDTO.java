package org.jabref.http.dto;

import java.util.Optional;

import org.jabref.model.ai.pipeline.SemanticSearchMatch;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// A chunk of a linked file matching a semantic search.
///
/// Entries without a citation key are reported with [LibraryQueryMatch#UNSET_CITATION_KEY].
/// `page` is absent for files without pages.
@NullMarked
public record SemanticSearchMatchDTO(String citationKey, String file, @Nullable Integer page, double score, String text) {
    public static SemanticSearchMatchDTO of(SemanticSearchMatch match) {
        String citationKey = Optional.ofNullable(match.citationKey()).orElse(LibraryQueryMatch.UNSET_CITATION_KEY);
        return new SemanticSearchMatchDTO(citationKey, match.file(), match.pageNumber(), match.score(), match.text());
    }
}
