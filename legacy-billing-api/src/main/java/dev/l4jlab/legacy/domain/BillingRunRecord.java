package dev.l4jlab.legacy.domain;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.core.annotation.Nullable;

import java.time.Instant;

/** A billing run as stored (data-model.md Part 1). The tool results are shaped from it, never as it. */
@MappedEntity(value = "billing_run", schema = "legacy_billing")
public record BillingRunRecord(
    @Id @GeneratedValue(GeneratedValue.Type.IDENTITY) @MappedProperty("run_id") String runId,
    @MappedProperty("firm_id") String firmId,
    @MappedProperty("executed_by_advisor_id") String executedByAdvisorId,
    String status,
    @Nullable String phase,
    @MappedProperty("accounts_processed") int accountsProcessed,
    @MappedProperty("accounts_total") int accountsTotal,
    @Nullable @MappedProperty("failure_reason") String failureReason,
    @MappedProperty("started_at") Instant startedAt,
    @Nullable @MappedProperty("finished_at") Instant finishedAt) {

    /** Terminal runs never change again; cancellation of one is acknowledged, not applied (FR-031). */
    public boolean isTerminal() {
        return "COMPLETED".equals(status) || "FAILED".equals(status) || "CANCELED".equals(status);
    }
}
