package dev.l4jlab.chain.persistence;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.model.DataType;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

/**
 * Maps the {@code node_execution} table. The two payload columns are JSONB, declared here as
 * {@link DataType#JSON} so Micronaut Data hands them to the serializer rather than treating them as
 * opaque strings.
 */
@MappedEntity("node_execution")
@Serdeable
public record NodeExecutionEntity(
        @Id UUID id,
        UUID runId,
        short position,
        String nodeName,
        @MappedProperty(type = DataType.JSON) Object inputPayload,
        @Nullable @MappedProperty(type = DataType.JSON) Object outputPayload,
        boolean succeeded,
        @Nullable String failureReason,
        Instant startedAt,
        int durationMs,
        @Nullable String modelRequestText,
        @Nullable String modelResponseText,
        @Nullable Integer inputTokens,
        @Nullable Integer outputTokens) {}
