package dev.l4jlab.chain.web.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The full record of one run. Polled while the run is not terminal, then left alone.
 *
 * <p>{@code nodes} holds only the nodes that have started, in ascending position, so a running
 * chain shows its progress. A failed run still returns the records of the nodes that completed
 * (FR-015).
 */
@Serdeable
public record RunDetailResponse(
        UUID runId,
        String companyId,
        @Nullable String companyName,
        String period,
        String status,
        @Nullable String currentNode,
        @Nullable String failedNode,
        @Nullable String failureReason,
        String providerMode,
        String modelId,
        @Nullable String summary,
        @Nullable List<IndicatorView> indicators,
        Instant startedAt,
        @Nullable Instant endedAt,
        List<NodeView> nodes) {}
