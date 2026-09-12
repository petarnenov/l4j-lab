package dev.l4jlab.chain.persistence;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

/** Maps the {@code chain_run} table. Immutable record; the service writes a new copy on transition. */
@MappedEntity("chain_run")
@Serdeable
public record ChainRunEntity(
        @Id UUID id,
        String companyId,
        String period,
        String status,
        @Nullable String currentNode,
        @Nullable String failedNode,
        @Nullable String failureReason,
        String providerMode,
        String modelId,
        @Nullable String summaryText,
        Instant startedAt,
        @Nullable Instant endedAt) {}
