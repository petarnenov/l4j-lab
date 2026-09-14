package dev.l4jlab.mcp.tools;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.jsonschema.JsonSchema;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/**
 * One page of billing runs (FR-017).
 *
 * <p>{@code total_match_count} is the caller's own total, not the firm's: the legacy API applies the
 * entitlement predicate before counting, so this number never tells a caller how much it cannot see.
 *
 * <p>{@code runs} is annotated {@code ALWAYS} because Serde omits an empty collection by default,
 * and an empty page that arrives without its {@code runs} field is indistinguishable from a
 * malformed one — the same confusion that made F-006 an HTTP 500 one level down. The global
 * {@code jackson.serialization-inclusion} setting does not reach here: the MCP integration
 * serialises {@code structuredContent} through its own path, so the rule has to be on the type.
 *
 * <p>{@code truncated} and {@code refine_hint} travel together. A hint that is always present is
 * noise a model learns to skip, so it appears only when narrowing would actually help.
 */
@JsonSchema(description = "One page of billing runs matching a search, with a cursor for the next.")
@Serdeable
public record BillingRunSearchResult(
    @JsonInclude(JsonInclude.Include.ALWAYS) List<BillingRunSummary> runs,
    @JsonProperty("total_match_count") int totalMatchCount,
    boolean truncated,
    @Nullable @JsonProperty("next_cursor") String nextCursor,
    @Nullable @JsonProperty("refine_hint") String refineHint) {
}
