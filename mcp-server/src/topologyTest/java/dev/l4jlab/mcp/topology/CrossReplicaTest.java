package dev.l4jlab.mcp.topology;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
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

    /**
     * The property feature 007 exists to demonstrate: a cursor is signed and self-contained, so any
     * replica can continue a search another began. No shared table is consulted (research R-009).
     *
     * <p>Feature 010 H-005: this used to assume the search fitted in exactly two pages — it asserted
     * the second page was the last and that the two pages summed to the total. Every pass of this
     * suite starts more billing runs, so once enough had accumulated there was a third page and the
     * arithmetic stopped holding. The suite was green on a fresh volume and red on a used one, which
     * is a verdict about how often it had been run.
     *
     * <p>It now walks the whole search, taking each page from a different replica, and makes the
     * stronger claim the old arithmetic was reaching for: every page is continued elsewhere, no run
     * appears twice, and what comes back is the caller's whole total — however many pages that is.
     */
    @Test
    void aCursorFromOneReplicaContinuesTheSameSearchOnAnother() {
        String token = tokenFor("admin-alpha");
        URI[] replicas = {replicaA, replicaB, replicaC};

        Map<String, Object> page = structured(callTool(replicas[0], "search_billing_runs", """
            {"firm_id":"firm-alpha"}""", token));
        int total = ((Number) page.get("total_match_count")).intValue();
        assertThat(page).containsEntry("truncated", true);

        List<String> seen = new ArrayList<>(runIds(page));
        int hops = 0;
        while (Boolean.TRUE.equals(page.get("truncated"))) {
            String cursor = (String) page.get("next_cursor");
            assertThat(cursor).as("a truncated page must carry a cursor").isNotBlank();

            hops++;
            // A different replica every hop, so no page is ever continued by the one that minted it.
            page = structured(callTool(replicas[hops % replicas.length], "search_billing_runs", """
                {"firm_id":"firm-alpha","cursor":"%s"}""".formatted(cursor), token));

            List<String> ids = runIds(page);
            assertThat(ids).as("hop %s returned nothing", hops).isNotEmpty();
            assertThat(ids).as("hop %s repeated a run", hops).doesNotContainAnyElementsOf(seen);
            seen.addAll(ids);

            assertThat(hops).as("paging did not terminate").isLessThan(100);
        }

        assertThat(hops).as("the search must span more than one replica to prove anything")
            .isGreaterThanOrEqualTo(1);
        assertThat(seen).hasSize(total);
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
