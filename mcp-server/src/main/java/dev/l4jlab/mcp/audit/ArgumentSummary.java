package dev.l4jlab.mcp.audit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reduces a tool's arguments to identifiers (FR-026).
 *
 * <p>An <em>allow-list</em> per tool, deliberately not a redaction pass over a serialized payload. A
 * deny-list leaks the first time someone adds a field and forgets to name it; an allow-list makes a
 * new field invisible by default, which is the failure direction to prefer for an audit log that
 * lives next to a billing system.
 */
public final class ArgumentSummary {

    private static final Map<String, List<String>> ALLOWED = Map.of(
        "search_billing_runs", List.of("firm_id", "status", "advisor_id", "page_size"),
        "get_billing_run_status", List.of("run_id"),
        "get_run_failures", List.of("run_id", "limit"),
        // Note what is absent: `reason` is free text a user wrote, and `delta_bps` is the change
        // itself. The legacy reference id on the audit row is how the change is traced.
        "post_fee_adjustment", List.of("operation_id", "account_id", "effective_date"),
        "start_billing_run", List.of("firm_id", "executed_by_advisor_id"));

    private ArgumentSummary() {
    }

    public static Map<String, Object> of(String toolName, Map<String, Object> arguments) {
        Map<String, Object> summary = new LinkedHashMap<>();
        for (String key : ALLOWED.getOrDefault(toolName, List.of())) {
            Object value = arguments.get(key);
            if (value != null) {
                summary.put(key, value);
            }
        }
        return summary;
    }
}
