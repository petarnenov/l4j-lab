package dev.l4jlab.mcp.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

/** One household a failed run could not process, and why (FR-019). */
@Serdeable
public record RunFailure(
    @JsonProperty("household_id") String householdId,
    @JsonProperty("household_name") String householdName,
    String cause) {
}
