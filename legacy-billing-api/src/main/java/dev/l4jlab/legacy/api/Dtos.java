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

    /** A page of results plus the caller's own total — never the unfiltered total. */
    @Serdeable
    public record Page<T>(List<T> items, long totalCount) {
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
