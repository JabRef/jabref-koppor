package org.jabref.model.ai.pipeline;

import java.util.List;

import org.jspecify.annotations.NullMarked;

/// The outcome of a semantic search over the linked files of a library.
///
/// Only ingested files can match. The other linked files are reported, so that a caller can tell
/// "nothing relevant" apart from "not searched".
///
/// @param matches       the most relevant chunks, best first
/// @param pendingFiles  links of the files which are not ingested yet; their ingestion was started
/// @param notFoundFiles links of the files which could not be read
@NullMarked
public record SemanticSearchResult(
        List<SemanticSearchMatch> matches,
        List<String> pendingFiles,
        List<String> notFoundFiles
) {
}
