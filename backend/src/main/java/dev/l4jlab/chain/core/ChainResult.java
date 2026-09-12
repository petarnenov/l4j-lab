package dev.l4jlab.chain.core;

import dev.l4jlab.chain.domain.IndicatorSet;
import dev.l4jlab.chain.domain.RunSummary;
import io.micronaut.core.annotation.Nullable;

import java.util.List;

/** Everything one run produced, including the records of nodes that ran before a failure. */
public record ChainResult(
        RunStatus status,
        @Nullable String failedNode,
        @Nullable String failureReason,
        @Nullable IndicatorSet indicators,
        @Nullable RunSummary summary,
        List<NodeRecord> nodeRecords) {}
