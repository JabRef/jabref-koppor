package org.jabref.model.ai.pipeline;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// A chunk of a linked file which is similar to the query of a semantic search.
///
/// @param citationKey the citation key of the entry the file is linked to; `null` if the entry has none
/// @param file        the link of the file as stored in the entry
/// @param pageNumber  the page the chunk was taken from; `null` if the file has no pages
/// @param score       the relevance of the chunk, from 0 (unrelated) to 1 (identical meaning)
@NullMarked
public record SemanticSearchMatch(
        @Nullable String citationKey,
        String file,
        @Nullable Integer pageNumber,
        double score,
        String text
) {
}
