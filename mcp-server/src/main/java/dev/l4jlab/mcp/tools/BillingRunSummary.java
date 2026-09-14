package dev.l4jlab.mcp.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

/**
 * One run in a search result.
 *
 * <p>Narrower than the status result on purpose: a search is for choosing which run to open, and
 * phase counts and failure reasons are noise until one has been chosen.
 */
@Serdeable
public record BillingRunSummary(
    @JsonProperty("run_id") String runId,
    @JsonProperty("firm_id") String firmId,
    @JsonProperty("executed_by_advisor_id") String executedByAdvisorId,
    RunStatus status,
    @JsonProperty("started_at") String startedAt) {
}
