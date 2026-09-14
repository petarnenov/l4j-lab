package dev.l4jlab.legacy.api;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** The wire shapes of the legacy API (contracts/legacy-billing-api.md). */
public final class Dtos {

    private Dtos() {
    }

    /**
     * A page of results plus the caller's own total — never the unfiltered total.
     *
     * <p>{@code items} is never null, and {@code jackson.serialization-inclusion: ALWAYS} in
     * application.yml keeps an empty one in the JSON. Serde's default omitted it, so a page with
     * nothing in it went out as {@code {"totalCount":0}} — which makes "nothing matched" and
     * "malformed response" the same bytes. The MCP server dereferenced the absent field, and an
     * ordinary query — a date range with no runs in it — answered HTTP 500 (feature 010, R-001).
     *
     * <p>An empty collection is a fact. It is serialised as one.
     */
    @Serdeable
    public record Page<T>(List<T> items, long totalCount) {
        public Page {
            items = items == null ? List.of() : items;
        }
    }

    @Serdeable
    public record BillingRun(String runId, String firmId, String executedByAdvisorId, String status,
                             @Nullable String phase, int accountsProcessed, int accountsTotal,
                             @Nullable String failureReason, Instant startedAt,
                             @Nullable Instant finishedAt) {
    }

    @Serdeable
    public record RunFailure(String householdId, String householdName, String cause) {
    }

    @Serdeable
    public record StartRunRequest(String firmId, String executedByAdvisorId) {
    }

    @Serdeable
    public record FeeAdjustmentRequest(String accountId, int deltaBps, LocalDate effectiveDate,
                                       @Nullable String reason) {
    }

    @Serdeable
    public record FeeAdjustmentResult(String legacyReferenceId, String accountId, int deltaBps,
                                      LocalDate effectiveDate, int newFeeBps) {
    }
}
