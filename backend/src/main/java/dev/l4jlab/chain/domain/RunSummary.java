package dev.l4jlab.chain.domain;

import dev.l4jlab.chain.model.ProviderMode;
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
}
