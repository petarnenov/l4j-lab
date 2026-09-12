package dev.l4jlab.chain.web.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/**
 * One indicator as the screen shows it. The value is a plain string, not a number, so the figure
 * the learner reads is byte-identical to the figure that was computed and stored. Round-tripping
 * through a JSON number would be the one place SC-005 could silently break.
 */
@Serdeable
public record IndicatorView(
        String name,
        @Nullable String value,
        @Nullable String notApplicableReason,
        List<String> derivedFrom) {}
