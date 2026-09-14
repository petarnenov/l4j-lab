package dev.l4jlab.mcp.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.jsonschema.JsonSchema;
import io.micronaut.serde.annotation.Serdeable;

/**
 * An applied fee adjustment (FR-020).
 *
 * <p>{@code replayed} is not decoration. A caller that repeats an operation id gets this same result
 * either way, and the flag is the only thing telling it whether the second call did anything — which
 * is exactly what a model needs to know before deciding whether to report "done" or "already done".
 */
@JsonSchema(description = "A fee adjustment that has been applied to the billing system of record.")
@Serdeable
public record FeeAdjustmentResult(
    @JsonProperty("operation_id") String operationId,
    @JsonProperty("account_id") String accountId,
    @JsonProperty("delta_bps") int deltaBps,
    @JsonProperty("effective_date") String effectiveDate,
    @JsonProperty("legacy_reference_id") String legacyReferenceId,
    @JsonProperty("new_fee_bps") int newFeeBps,
    @JsonProperty("confirmed_by_user_id") String confirmedByUserId,
    boolean replayed) {
}
