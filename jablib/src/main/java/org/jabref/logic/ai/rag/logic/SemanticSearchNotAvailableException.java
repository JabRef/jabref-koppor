package org.jabref.logic.ai.rag.logic;

import org.jspecify.annotations.NullMarked;

/// Signals that a [SemanticSearch] cannot answer at the moment, for instance because the AI features are disabled.
/// The message tells the caller what to change.
@NullMarked
public class SemanticSearchNotAvailableException extends Exception {
    public SemanticSearchNotAvailableException(String message) {
        super(message);
    }
}
