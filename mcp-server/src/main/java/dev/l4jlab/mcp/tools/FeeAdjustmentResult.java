package dev.l4jlab.mcp.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.jsonschema.JsonSchema;
import io.micronaut.serde.annotation.Serdeable;

/**
 * An applied fee adjustment (FR-020).
 *
 * <p>{@code previous_fee_bps} is here because every caller needed it and every caller had to work it
 * out (FR-017, finding F-002). A result saying only "the new fee is 105" cannot be reported without
 * the number it replaced, so each client subtracted the delta itself — the same arithmetic, written
 * three times, in three places that could each get it wrong. It is derived rather than fetched: the
 * system of record applies the delta to the previous fee, so {@code new_fee_bps - delta_bps} is the
 * previous fee exactly, with no second call to a system that might have moved underneath.
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
    @JsonProperty("previous_fee_bps") int previousFeeBps,
    @JsonProperty("new_fee_bps") int newFeeBps,
    @JsonProperty("confirmed_by_user_id") String confirmedByUserId,
    boolean replayed) {
}
