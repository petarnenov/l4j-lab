package dev.l4jlab.mcp.topology;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T062, T063, T074, T078, T091, T100: the properties that do not exist in one process.
 *
 * <p>Every scenario here would pass against a single instance while the code was quietly stateful,
 * which is worse than failing. That is the whole reason the stack runs three replicas behind a
 * round-robin proxy with no stickiness.
 */
class CrossReplicaTest extends TopologyFixture {

    private static final String ELICITATION = """
        {"elicitation":{}}""";

    // ---------- US1-5, US1-6: a cursor minted by one replica, read by another ----------

    @Test
    void aCursorFromOneReplicaContinuesTheSameSearchOnAnother() {
        String token = tokenFor("admin-alpha");

        Map<String, Object> first = structured(callTool(replicaA, "search_billing_runs", """
            {"firm_id":"firm-alpha"}""", token));

        assertThat(first).containsEntry("truncated", true);
        assertThat((String) first.get("next_cursor")).isNotBlank();

        Map<String, Object> second = structured(callTool(replicaB, "search_billing_runs", """
            {"firm_id":"firm-alpha","cursor":"%s"}""".formatted(first.get("next_cursor")), token));

        List<String> firstIds = runIds(first);
        List<String> secondIds = runIds(second);

        // No shared table was consulted: the cursor is signed and self-contained, and every replica
        // shares the key (research.md R-009).
        assertThat(secondIds).isNotEmpty().doesNotContainAnyElementsOf(firstIds);
        assertThat(second).containsEntry("truncated", false);
        assertThat(firstIds.size() + secondIds.size())
            .isEqualTo(((Number) first.get("total_match_count")).intValue());
    }

    @Test
    void aThirdReplicaAlsoAcceptsIt() {
        String token = tokenFor("admin-alpha");
        Map<String, Object> first = structured(callTool(replicaA, "search_billing_runs", """
            {"firm_id":"firm-alpha"}""", token));

        Map<String, Object> onC = structured(callTool(replicaC, "search_billing_runs", """
            {"firm_id":"firm-alpha","cursor":"%s"}""".formatted(first.get("next_cursor")), token));

        assertThat(runIds(onC)).isNotEmpty().doesNotContainAnyElementsOf(runIds(first));
    }

    // ---------- US2-4: a confirmation retry landing on a different replica ----------

    @Test
    void proposeOnOneReplicaConfirmOnAnotherReplayOnAThirdAndItExecutesExactlyOnce() {
        String token = tokenFor("admin-alpha");
        String operationId = "op-" + UUID.randomUUID();
        String arguments = """
            {"operation_id":"%s","account_id":"acc-0102","delta_bps":25,\
            "effective_date":"2026-11-01"}""".formatted(operationId);

        int writesBefore = feeAdjustmentWrites();

        Map<String, Object> proposal = callTool(replicaA, "post_fee_adjustment", arguments, token,
            ELICITATION);
        assertThat(proposal).containsEntry("resultType", "input_required");
        String requestState = (String) proposal.get("requestState");
        assertThat(requestState).isNotBlank();
        // The first turn touches nothing, whichever replica answered it.
        assertThat(feeAdjustmentWrites()).isEqualTo(writesBefore);

        String confirmed = """
            {"operation_id":"%s","account_id":"acc-0102","delta_bps":25,\
            "effective_date":"2026-11-01"}""".formatted(operationId);

        Map<String, Object> executed = structured(confirm(replicaB, confirmed, requestState, token));
        assertThat(executed).containsEntry("replayed", false);
        assertThat((String) executed.get("legacy_reference_id")).isNotBlank();

        Map<String, Object> replayed = structured(confirm(replicaC, confirmed, requestState, token));
        assertThat(replayed).containsEntry("replayed", true);
        assertThat(replayed.get("legacy_reference_id"))
            .isEqualTo(executed.get("legacy_reference_id"));

        // The assertion that matters: three replicas, one write. A tool that returns the right
        // answer while applying a fee twice is worse than one that fails.
        assertThat(feeAdjustmentWrites()).isEqualTo(writesBefore + 1);
    }

    @Test
    void aConfirmationFromAnotherCallerIsRefusedWhicheverReplicaSeesIt() {
        String admin = tokenFor("admin-alpha");
        String advisor = tokenFor("advisor-alpha-101");
        String operationId = "op-" + UUID.randomUUID();
        String arguments = """
            {"operation_id":"%s","account_id":"acc-0102","delta_bps":25,\
            "effective_date":"2026-11-01"}""".formatted(operationId);

        int writesBefore = feeAdjustmentWrites();
        String requestState = (String) callTool(replicaA, "post_fee_adjustment", arguments, admin,
            ELICITATION).get("requestState");

        Map<String, Object> stolen = confirm(replicaB, arguments, requestState, advisor);
        assertThat(stolen).containsEntry("isError", true);
        assertThat(feeAdjustmentWrites()).isEqualTo(writesBefore);
    }

    // ---------- SC-005: the inbound token never travels ----------

    @Test
    void theTokenThisServerReceivedIsNeverSeenByTheLegacyApi() {
        String token = tokenFor("admin-alpha");
        structured(callTool(proxy, "search_billing_runs", """
            {"firm_id":"firm-alpha"}""", token));

        List<Map<String, Object>> received = legacyReceivedRequests();
        assertThat(received).isNotEmpty();

        // A negative that can only be proved from the other end: every call carried *a* token, and
        // none of them carried this one. That is what makes FR-016's exchange load-bearing rather
        // than decorative.
        assertThat(received).allSatisfy(request -> {
            String authorization = (String) request.get("authorization");
            if (authorization != null) {
                assertThat(authorization).doesNotContain(token);
            }
        });
        assertThat(received).anySatisfy(request ->
            assertThat((String) request.get("authorization")).startsWith("Bearer "));
    }

    // ---------- FR-027: the trace context reaches the system of record ----------

    @Test
    void theTraceparentTheClientSentArrivesUnchanged() {
        String token = tokenFor("admin-alpha");
        String traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

        send(proxy, mcp("tools/call", "search_billing_runs", """
            {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"search_billing_runs",\
            "arguments":{"firm_id":"firm-alpha"},"_meta":{}}}""", token)
            .header("traceparent", traceparent));

        assertThat(legacyReceivedRequests())
            .as("the traceparent must reach the system of record, not stop at the MCP server")
            .anySatisfy(request -> assertThat(request.get("traceparent")).isEqualTo(traceparent));
    }

    // ---------- helpers ----------

    private static Map<String, Object> confirm(java.net.URI target, String arguments,
                                               String requestState, String token) {
        String body = """
            {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"post_fee_adjustment",\
            "arguments":%s,\
            "inputResponses":{"confirm_adjustment":{"action":"accept","content":{"confirmed":true}}},\
            "requestState":"%s",\
            "_meta":{"io.modelcontextprotocol/clientCapabilities":%s}}}"""
            .formatted(arguments, requestState, ELICITATION);
        return send(target, mcp("tools/call", "post_fee_adjustment", body, token));
    }

    @SuppressWarnings("unchecked")
    private static List<String> runIds(Map<String, Object> structured) {
        return ((List<Map<String, Object>>) structured.get("runs")).stream()
            .map(run -> (String) run.get("run_id")).toList();
    }

    private static int feeAdjustmentWrites() {
        return (int) legacyReceivedRequests().stream()
            .filter(request -> String.valueOf(request.get("path")).contains("fee-adjustments"))
            .count();
    }
}
