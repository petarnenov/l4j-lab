package dev.l4jlab.mcp.tools;

import dev.l4jlab.mcp.McpServerTestBase;
import dev.l4jlab.mcp.TestKeys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature 010, T026 (FR-013). A declared constraint is a validated constraint.
 *
 * <p>Finding F-001 was that the served declarations had lost the enumerations, formats, bounds and
 * defaults the committed contracts carried. Restoring them raises a second question immediately: does
 * the server actually honour what it now advertises? If it does not, the repair swaps a lie about
 * what is declared for a lie about what is enforced — and the second is worse, because a model that
 * reads {@code enum} and trusts it has no way to find out.
 *
 * <p>Each case below is answered as a <b>tool</b> error at HTTP 200 naming the field, which is what
 * the error table in {@code contracts/mcp-protocol.md} says an argument failing {@code inputSchema}
 * is. The request was well-formed; the call was not, and the caller can fix it from the sentence.
 */
class ArgumentValidationTest extends McpServerTestBase {

    @Test
    @DisplayName("a status outside the declared enumeration is refused, naming the field")
    void aStatusOutsideTheEnumerationIsRefused() {
        Map<String, Object> result = call("search_billing_runs",
            Map.of("firm_id", "firm-alpha", "status", "ALMOST_DONE"));

        assertThat(result).containsEntry("isError", true);
        assertThat(textOf(result)).contains("status");
        assertThat(textOf(result)).contains("PENDING");
    }

    @Test
    @DisplayName("a date that is not a date is refused rather than passed on")
    void aMalformedDateIsRefused() {
        Map<String, Object> result = call("search_billing_runs",
            Map.of("firm_id", "firm-alpha", "started_from", "last Tuesday"));

        assertThat(result).containsEntry("isError", true);
        assertThat(textOf(result)).contains("started_from");
        assertThat(textOf(result)).contains("YYYY-MM-DD");
    }

    @Test
    @DisplayName("a page size above the declared maximum is refused, not silently clamped")
    void aPageSizeAboveTheDeclaredMaximumIsRefused() {
        // The tool clamps internally, which is right for a value it chose to accept. But the
        // declaration says maximum 20, and quietly accepting 100 makes the declaration untrue.
        Map<String, Object> result = call("search_billing_runs",
            Map.of("firm_id", "firm-alpha", "page_size", 100));

        assertThat(result).containsEntry("isError", true);
        assertThat(textOf(result)).contains("page_size");
        assertThat(textOf(result)).contains("20");
    }

    @Test
    @DisplayName("an operation id shorter than the declared minimum is refused")
    void anOperationIdBelowTheDeclaredMinimumIsRefused() {
        Map<String, Object> result = call("post_fee_adjustment",
            Map.of("operation_id", "short", "account_id", "acc-0101", "delta_bps", 15,
                "effective_date", "2026-10-01"));

        assertThat(result).containsEntry("isError", true);
        assertThat(textOf(result)).contains("operation_id");
        assertThat(textOf(result)).contains("8");
    }

    @Test
    @DisplayName("a value inside every declared bound is not refused")
    void aValidCallIsNotRefused() {
        // The other half of the check: a validator that refuses everything would pass the four tests
        // above and be useless.
        Map<String, Object> result = call("search_billing_runs",
            Map.of("firm_id", "firm-alpha", "status", "COMPLETED", "started_from", "2026-08-01",
                "page_size", 5));

        assertThat(result.get("isError")).isNotEqualTo(true);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String tool, Map<String, Object> arguments) {
        Map<String, Object> response = http.toBlocking().retrieve(
            mcp("tools/call", toolCallBody(tool, arguments), TestKeys.ADMIN_ALPHA)
                .header("Mcp-Name", tool),
            Map.class);
        return (Map<String, Object>) response.get("result");
    }

    private static String toolCallBody(String tool, Map<String, Object> arguments) {
        StringBuilder args = new StringBuilder("{");
        String separator = "";
        for (Map.Entry<String, Object> e : arguments.entrySet()) {
            args.append(separator).append('"').append(e.getKey()).append("\":");
            Object value = e.getValue();
            if (value instanceof Number || value instanceof Boolean) {
                args.append(value);
            } else {
                args.append('"').append(value).append('"');
            }
            separator = ",";
        }
        args.append('}');
        return """
            {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"%s","arguments":%s}}"""
            .formatted(tool, args);
    }

    @SuppressWarnings("unchecked")
    private static String textOf(Map<String, Object> result) {
        java.util.List<Map<String, Object>> content =
            (java.util.List<Map<String, Object>>) result.get("content");
        assertThat(content).as("every tool result carries a text rendering").isNotEmpty();
        return (String) content.get(0).get("text");
    }
}
