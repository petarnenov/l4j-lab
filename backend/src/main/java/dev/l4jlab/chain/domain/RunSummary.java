package dev.l4jlab.chain.domain;

import dev.l4jlab.chain.model.ModelProperties;
import dev.l4jlab.chain.model.ProviderMode;
import dev.langchain4j.model.output.TokenUsage;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Boundary 4 to out. Produced by Summarize, the only node that calls the model (FR-007).
 *
 * <p>The credential is never part of this record, or of the request and response text stored
 * alongside it. It lives only in the header supplier the model factory builds.
 */
@Serdeable
public record RunSummary(
        @NotBlank @Size(max = MAX_TEXT_LENGTH) String text,
        @NotBlank String modelId,
        ProviderMode providerMode,
        @Nullable Integer inputTokens,
        @Nullable Integer outputTokens) {

    /** Storage cap. A longer response is truncated with the truncation marked, never dropped. */
    public static final int MAX_TEXT_LENGTH = 4000;

    public static final String TRUNCATION_MARKER = "\n\n[truncated: the model returned more than "
            + MAX_TEXT_LENGTH + " characters]";

    /**
     * Builds the summarizing step's stored output from the model's reply (feature 005, research R-005).
     * The reply is kept whole in the step's response text; this is the capped form shown as the summary.
     */
    public static RunSummary from(String text, @Nullable TokenUsage usage, ModelProperties properties) {
        return new RunSummary(
                truncate(text),
                properties.getModelId(),
                properties.providerMode(),
                usage == null ? null : usage.inputTokenCount(),
                usage == null ? null : usage.outputTokenCount());
    }

    private static String truncate(String text) {
        if (text.length() <= MAX_TEXT_LENGTH) {
            return text;
        }
        int keep = MAX_TEXT_LENGTH - TRUNCATION_MARKER.length();
        return text.substring(0, keep) + TRUNCATION_MARKER;
    }
}
