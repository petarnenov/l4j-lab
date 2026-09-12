package dev.l4jlab.chain.core;

import io.micronaut.core.annotation.Nullable;

import java.time.Instant;

/**
 * What the runner observed for one node. Becomes one node_execution row. The four model fields are
 * null for positions 1 through 3, which is the shape Principle V asks a trace to have.
 */
public record NodeRecord(
        int position,
        String nodeName,
        Object inputPayload,
        @Nullable Object outputPayload,
        boolean succeeded,
        @Nullable String failureReason,
        Instant startedAt,
        long durationMs,
        @Nullable String modelRequestText,
        @Nullable String modelResponseText,
        @Nullable Integer inputTokens,
        @Nullable Integer outputTokens) {}
