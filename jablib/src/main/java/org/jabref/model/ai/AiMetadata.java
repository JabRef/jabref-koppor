package org.jabref.model.ai;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jabref.model.ai.llm.AiProvider;

/// Metadata about the AI model used to produce a particular result.
///
/// This record is passed to all exporter implementations so that outputs can include
/// provenance information (which provider and model generated the content, and when).
public record AiMetadata(AiProvider aiProvider, String model, Instant timestamp) {
    @JsonCreator
    public AiMetadata(@JsonProperty("aiProvider") AiProvider aiProvider, @JsonProperty("model") String model, @JsonProperty("timestamp") Instant timestamp) {
        this.aiProvider = aiProvider;
        this.model = model;
        this.timestamp = timestamp;
    }
}
