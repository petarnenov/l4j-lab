package dev.l4jlab.mcp.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.jsonschema.JsonSchema;
import io.micronaut.serde.annotation.Serdeable;

/**
 * The fallback shape of {@code start_billing_run} (FR-021), for a client that did not declare the
 * Tasks extension.
 *
 * <p>It carries a handle and says, in a field rather than in prose, which tool to poll with it. A
 * client that cannot receive a task result can still follow the run to completion; the only thing it
 * loses is the extension's vocabulary.
 */
@JsonSchema(description =
    "A started billing run, with the handle and the tool to poll it with.")
@Serdeable
public record StartedRunHandle(
    @JsonProperty("task_id") String taskId,
    @JsonProperty("run_id") String runId,
    @JsonProperty("poll_with") String pollWith,
    @JsonProperty("next_step_hint") String nextStepHint) {
}
