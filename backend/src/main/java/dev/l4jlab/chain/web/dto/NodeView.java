package dev.l4jlab.chain.web.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * One node's record as the detail view shows it (US2). The four model fields are populated for
 * position 4 only, and none of them, ever, carries the credential (FR-018).
 */
@Serdeable
public record NodeView(
        int position,
        String nodeName,
        boolean succeeded,
        @Nullable String failureReason,
        @Nullable
                @Schema(
                        type = "object",
                        additionalProperties = Schema.AdditionalPropertiesValue.TRUE,
                        description = "The boundary structure this node received, verbatim")
                Object inputPayload,
        @Nullable
                @Schema(
                        type = "object",
                        additionalProperties = Schema.AdditionalPropertiesValue.TRUE,
                        description = "The boundary structure this node produced, verbatim")
                Object outputPayload,
        Instant startedAt,
        int durationMs,
        @Nullable String modelRequestText,
        @Nullable String modelResponseText,
        @Nullable Integer inputTokens,
        @Nullable Integer outputTokens) {}
