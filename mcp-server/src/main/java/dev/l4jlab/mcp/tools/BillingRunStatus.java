package dev.l4jlab.mcp.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.jsonschema.JsonSchema;
import io.micronaut.serde.annotation.Serdeable;

/**
 * The result of {@code get_billing_run_status} (FR-018).
 *
 * <p>This record <em>is</em> the tool's declared {@code outputSchema}, so everything about it
 * reaches the model. Hence the enum types and the snake_case {@code @JsonProperty} names: they make
 * the generated schema carry the same constraints and names as the committed contract, rather than
 * a widened approximation of it.
 *
 * <p>{@code @JsonSchema} carries only {@code description}. Its {@code title} and {@code uri} members
 * both feed the generated file name, which the server module also uses to look the schema up; any
 * divergence drops {@code outputSchema} from the tool definition <em>silently</em> rather than
 * failing. Verified 2026-09-13. The contract test is what would catch a regression.
 */
@JsonSchema(description =
    "The current state of one billing run, shaped for a caller deciding what to do next.")
@Serdeable
public record BillingRunStatus(
    @JsonProperty("run_id") String runId,
    RunStatus status,
    @Nullable RunPhase phase,
    @JsonProperty("accounts_processed") int accountsProcessed,
    @JsonProperty("accounts_total") int accountsTotal,
    @Nullable @JsonProperty("failure_reason") String failureReason,
    @Nullable @JsonProperty("next_step_hint") String nextStepHint) {
}
