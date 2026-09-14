package dev.l4jlab.legacy.api;

import com.fasterxml.jackson.annotation.JsonInclude;
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
     * <p>{@code items} is never null and is never omitted. Serde's default left an empty one out, so
     * a page with nothing in it went out as {@code {"totalCount":0}} — which makes "nothing matched"
     * and "malformed response" the same bytes. The MCP server dereferenced the absent field, and an
     * ordinary query — a date range with no runs in it — answered HTTP 500 (feature 010, R-001).
     *
     * <p>The rule is on the component rather than in {@code application.yml}: a global
     * {@code jackson.serialization-inclusion: ALWAYS} was tried first and does not cover an empty
     * collection here, which is precisely the case that mattered. A setting that looks like it fixes
     * something and does not is worse than no setting, so the guarantee lives where it is checkable.
     *
     * <p>An empty collection is a fact. It is serialised as one.
     */
    @Serdeable
    public record Page<T>(@JsonInclude(JsonInclude.Include.ALWAYS) List<T> items, long totalCount) {
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
