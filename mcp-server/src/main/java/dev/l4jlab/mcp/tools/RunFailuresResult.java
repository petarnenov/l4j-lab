package dev.l4jlab.mcp.tools;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.jsonschema.JsonSchema;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/**
 * The households a failed run could not process (FR-019).
 *
 * <p>Capped, with {@code total_count} reporting the full number. A model that sees fifty failures
 * and a total of three hundred knows to ask a different question; one that sees fifty and no total
 * concludes there were fifty.
 */
@JsonSchema(description = "Households a failed billing run could not process, with the cause of each.")
@Serdeable
public record RunFailuresResult(
    @JsonProperty("run_id") String runId,
    @JsonInclude(JsonInclude.Include.ALWAYS) List<RunFailure> failures,
    @JsonProperty("total_count") int totalCount,
    boolean truncated) {
}
