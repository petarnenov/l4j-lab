package dev.l4jlab.mcp.tools;

import dev.l4jlab.mcp.McpServerTestBase;
import dev.l4jlab.mcp.TestKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T069–T072: the three read-only tools, and the properties a caller depends on.
 *
 * <p>Against the stub legacy API rather than the real one, per Principle IV — but the stub returns
 * the real status codes and records the real headers, so the error translation and the
 * never-forward-the-token property are both genuinely exercised.
 */
class ReadToolsTest extends McpServerTestBase {

    @BeforeEach
    void resetStub() {
        stub().clear();
    }

    // ---------- T069: search, paging, and the refine hint ----------

    @Test
    void searchReturnsOnePageWithTheCallersOwnTotalAndACursor() {
        Map<String, Object> result = callStructured("search_billing_runs",
            Map.of("firm_id", "firm-alpha"));

        assertThat((List<?>) result.get("runs")).hasSize(20);
        assertThat(result).containsEntry("total_match_count", 26);
        assertThat(result).containsEntry("truncated", true);
        assertThat(result.get("next_cursor")).isNotNull();
        // The hint appears only when narrowing would help; always-on hints are noise.
        assertThat((String) result.get("refine_hint")).contains("Narrow by status");
    }

    /**
     * Feature 010, T010 (FR-001, US1-1). The defect behind finding F-006, reproduced in-process.
     *
     * <p>The stub serialises its page the same way the real legacy API did, so an empty one arrives
     * as {@code {"totalCount":0}} with no {@code items} at all. This server used to dereference that
     * absent field; the NPE became a JSON-RPC error with an empty message, which the SDK then
     * rejected, and a date range with nothing in it answered HTTP 500.
     *
     * <p>Two things are asserted, and the first is the one that was missing: {@code runs} is
     * <em>present</em> and empty. A search that matched nothing is a search that matched nothing —
     * not a response a client has to guess about.
     */
    @Test
    void aSearchMatchingNothingIsAnEmptyResultRatherThanACrash() {
        Map<String, Object> result = callStructured("search_billing_runs",
            Map.of("firm_id", "firm-alpha", "started_from", "2030-01-01"));

        assertThat(result).containsKey("runs");
        assertThat((List<?>) result.get("runs")).isEmpty();
        assertThat(result).containsEntry("total_match_count", 0);
        assertThat(result).containsEntry("truncated", false);
    }

    @Test
    void aPageSizeAboveTheCapIsClampedRatherThanRefused() {
        Map<String, Object> result = callStructured("search_billing_runs",
            Map.of("firm_id", "firm-alpha", "page_size", 100));

        // A model that asked for 100 wanted as many as it could get. An error teaches it nothing.
        assertThat((List<?>) result.get("runs")).hasSize(20);
    }

    @Test
    void theCursorContinuesTheSameResultSet() {
        Map<String, Object> first = callStructured("search_billing_runs",
            Map.of("firm_id", "firm-alpha"));
        Map<String, Object> second = callStructured("search_billing_runs",
            Map.of("firm_id", "firm-alpha", "cursor", first.get("next_cursor")));

        List<String> firstIds = idsOf(first);
        List<String> secondIds = idsOf(second);

        assertThat(secondIds).hasSize(6);
        assertThat(secondIds).doesNotContainAnyElementsOf(firstIds);
        assertThat(second).containsEntry("truncated", false);
        assertThat(second.get("next_cursor")).isNull();
    }

    @Test
    void aCursorFromADifferentSearchIsRefusedAsAToolError() {
        Map<String, Object> first = callStructured("search_billing_runs",
            Map.of("firm_id", "firm-alpha"));

        Map<String, Object> result = call("search_billing_runs",
            Map.of("firm_id", "firm-alpha", "status", "FAILED",
                "cursor", first.get("next_cursor")));

        assertThat(result).containsEntry("isError", true);
        assertThat(textOf(result)).contains("Run the search again");
    }

    // ---------- T070: status, and the next-step hint ----------

    @Test
    void aRunningRunCarriesItsPhaseAndNoNextStepHint() {
        Map<String, Object> result = callStructured("get_billing_run_status",
            Map.of("run_id", "run-a001"));

        assertThat(result).containsEntry("status", "RUNNING");
        assertThat(result).containsEntry("phase", "FEE_CALC");
        assertThat(result).containsEntry("accounts_processed", 6);
        assertThat(result).containsEntry("accounts_total", 12);
        assertThat(result.get("next_step_hint")).isNull();
    }

    @Test
    void aFailedRunPointsAtGetRunFailures() {
        Map<String, Object> result = callStructured("get_billing_run_status",
            Map.of("run_id", "run-a019-failed"));

        assertThat(result).containsEntry("status", "FAILED");
        assertThat(result.get("phase")).isNull();
        assertThat((String) result.get("failure_reason")).isNotBlank();
        assertThat((String) result.get("next_step_hint")).contains("get_run_failures");
    }

    // ---------- T071: failures, capped, with the full total ----------

    @Test
    void failuresAreCappedButTheTotalIsHonest() {
        Map<String, Object> result = callStructured("get_run_failures",
            Map.of("run_id", "run-a019-failed", "limit", 3));

        assertThat((List<?>) result.get("failures")).hasSize(3);
        // A model that sees 3 of 7 asks a different question; one that sees 3 and no total
        // concludes there were 3.
        assertThat(result).containsEntry("total_count", 7);
        assertThat(result).containsEntry("truncated", true);
    }

    // ---------- T072: error translation ----------

    @Test
    void aLegacyRefusalBecomesAShortNoAccessErrorCarryingNoData() {
        stub().forceStatus(403);
        Map<String, Object> result = call("get_billing_run_status", Map.of("run_id", "run-a001"));

        assertThat(result).containsEntry("isError", true);
        assertThat(result).doesNotContainKey("structuredContent");
        assertThat(textOf(result)).isEqualTo("No access to that record.");
    }

    @Test
    void aBrokenLegacyApiTellsTheModelNotToRetry() {
        stub().forceStatus(500);
        Map<String, Object> result = call("get_billing_run_status", Map.of("run_id", "run-a001"));

        assertThat(result).containsEntry("isError", true);
        // A model that retries a broken system of record makes the outage worse.
        assertThat(textOf(result)).contains("Do not retry");
    }

    @Test
    void noErrorMessageLeaksAStackTraceSqlOrAHostname() {
        for (int status : new int[] {403, 404, 409, 500}) {
            stub().forceStatus(status);
            String text = textOf(call("get_billing_run_status", Map.of("run_id", "run-a001")));
            assertThat(text)
                .doesNotContain("Exception").doesNotContain("\tat ")
                .doesNotContain("SELECT").doesNotContain("localhost")
                .doesNotContain("legacy-billing-api");
        }
    }

    // ---------- FR-016: the inbound token never travels ----------

    @Test
    void theInboundTokenNeverReachesTheLegacyApi() {
        String inbound = TestKeys.valid(TestKeys.ADMIN_ALPHA, TestKeys.AUDIENCE_MCP);
        callWithToken("get_billing_run_status", Map.of("run_id", "run-a001"), inbound);

        List<StubLegacyApiCapture> legacyCalls = stub().received().stream()
            .filter(r -> r.path().startsWith("/api/"))
            .map(r -> new StubLegacyApiCapture(r.path(), r.authorization()))
            .toList();

        assertThat(legacyCalls).isNotEmpty();
        assertThat(legacyCalls).allSatisfy(captured -> {
            assertThat(captured.authorization()).isNotNull();
            // SC-005 stated as an assertion: the exchanged token travels, the inbound one does not.
            assertThat(captured.authorization()).doesNotContain(inbound);
        });
    }

    private record StubLegacyApiCapture(String path, String authorization) {
    }

    // ---------- helpers ----------

    private Map<String, Object> call(String tool, Map<String, Object> arguments) {
        return callWithToken(tool, arguments,
            TestKeys.valid(TestKeys.ADMIN_ALPHA, TestKeys.AUDIENCE_MCP));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callWithToken(String tool, Map<String, Object> arguments,
                                              String token) {
        String body = toolCallBody(tool, arguments);
        Map<String, Object> response = http.toBlocking().retrieve(
            io.micronaut.http.HttpRequest.POST("/mcp", body)
                .contentType(io.micronaut.http.MediaType.APPLICATION_JSON)
                .header("Accept", "application/json, text/event-stream")
                .header("MCP-Protocol-Version", "2026-07-28")
                .header("Mcp-Method", "tools/call")
                .header("Mcp-Name", tool)
                .header("Authorization", "Bearer " + token),
            Map.class);
        return (Map<String, Object>) response.get("result");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callStructured(String tool, Map<String, Object> arguments) {
        Map<String, Object> result = call(tool, arguments);
        assertThat(result.get("isError"))
            .as("expected a successful tool result, got: " + result)
            .isNotEqualTo(true);
        return (Map<String, Object>) result.get("structuredContent");
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
    private static List<String> idsOf(Map<String, Object> structured) {
        return ((List<Map<String, Object>>) structured.get("runs")).stream()
            .map(run -> (String) run.get("run_id")).toList();
    }

    @SuppressWarnings("unchecked")
    private static String textOf(Map<String, Object> result) {
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        assertThat(content).as("every tool result carries a text rendering (FR-012)").isNotEmpty();
        return (String) content.get(0).get("text");
    }
}
