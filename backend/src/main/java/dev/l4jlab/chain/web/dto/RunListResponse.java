package dev.l4jlab.chain.web.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The history list, newest first (FR-014, US3). Carries no node payloads. */
@Serdeable
public record RunListResponse(List<RunListEntry> runs, @Nullable String nextCursor) {

    @Serdeable
    public record RunListEntry(
            UUID runId,
            String companyId,
            @Nullable String companyName,
            String period,
            String status,
            @Nullable String summaryPreview,
            Instant startedAt,
            @Nullable Instant endedAt) {}

    /** The first 160 characters of the summary, or null. */
    public static final int PREVIEW_LENGTH = 160;
}
