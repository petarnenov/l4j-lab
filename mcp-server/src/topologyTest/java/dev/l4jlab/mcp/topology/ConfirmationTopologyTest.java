package dev.l4jlab.mcp.topology;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The confirmation retry, assembled only from what the contract says (US2-1, FR-005).
 *
 * <p>This test exists because a client built from `contracts/mcp-protocol.md` did the opposite of
 * what it asked. The contract named `params.inputResponses.confirm_adjustment` and stopped; the
 * elicitation's `requestedSchema` is `{ confirmed: boolean }`; so a careful reader assembled
 * `{"confirmed": true}` — and the server, finding no `content`, read it as a refusal and applied
 * nothing. Finding F-005.
 *
 * <p>So the retry below is built the way the corrected contract reads, and nothing else. It is
 * deliberately not built from the server's code: a test that consults the implementation to learn
 * the shape cannot notice when the document and the implementation disagree, which is the only
 * thing this test is for.
 *
 * <p>Requires {@code make mcp-up}. It goes through the proxy on purpose — the first call and the
 * retry may land on different replicas, and `requestState` is what carries the operation across.
 */
class ConfirmationTopologyTest extends TopologyFixture {

    private static final String ACCOUNT = "acc-0101";

    @Test
    @DisplayName("a retry built only from the contract applies the change")
    void aRetryBuiltOnlyFromTheContractAppliesTheChange() {
        String token = tokenFor("admin-alpha");
        String operationId = "topology-confirm-" + System.nanoTime();

        Map<String, Object> first = firstCall(token, operationId);
        assertThat(first).containsEntry("resultType", "input_required");
        String requestState = (String) first.get("requestState");
        assertThat(requestState).as("the contract says the retry echoes requestState").isNotBlank();

        Map<String, Object> applied = retry(token, operationId, requestState, """
            {"action":"accept","content":{"confirmed":true}}""");

        assertThat(applied.get("isError")).as("a confirmed change is not an error").isNotEqualTo(true);
        assertThat(structured(applied))
            .as("the adjustment the legacy API recorded")
            .containsKey("legacy_reference_id");
    }

    @Test
    @DisplayName("a decline is a result, not an error")
    void aDeclineIsAResultNotAnError() {
        String token = tokenFor("admin-alpha");
        String operationId = "topology-decline-" + System.nanoTime();

        String requestState = (String) firstCall(token, operationId).get("requestState");
        Map<String, Object> declined = retry(token, operationId, requestState, """
            {"action":"decline","content":{"confirmed":false}}""");

        // Nothing was applied, so there is nothing to describe. That is an outcome, not a failure.
        assertThat(declined).doesNotContainKey("isError");
        assertThat(declined).containsEntry("resultType", "complete");
        assertThat(declined).doesNotContainKey("structuredContent");
        assertThat(textOf(declined)).contains("nothing was applied");
    }

    @Test
    @DisplayName("the shape the old contract implied is answered as unreadable, not as a refusal")
    void theShapeTheOldContractImpliedIsAnsweredAsUnreadable() {
        String token = tokenFor("admin-alpha");
        String operationId = "topology-flat-" + System.nanoTime();

        String requestState = (String) firstCall(token, operationId).get("requestState");
        // The flat form: what the incomplete contract led a client to send.
        Map<String, Object> unreadable = retry(token, operationId, requestState, """
            {"confirmed":true}""");

        assertThat(unreadable).containsEntry("isError", true);
        assertThat(textOf(unreadable))
            .as("the answer must name the shape it expected, or the client cannot correct itself")
            .contains("could not be read")
            .contains("content.confirmed");
    }

    private static String arguments(String operationId) {
        return """
            {"operation_id":"%s","account_id":"%s","delta_bps":15,"effective_date":"2026-10-01"}"""
            .formatted(operationId, ACCOUNT);
    }

    private static Map<String, Object> firstCall(String token, String operationId) {
        return callTool(proxy, "post_fee_adjustment", arguments(operationId), token,
            """
                {"elicitation":{}}""");
    }

    /** The retry exactly as the contract describes it: same arguments, echoed state, a new id. */
    private static Map<String, Object> retry(String token, String operationId, String requestState,
                                             String answerJson) {
        String body = """
            {"jsonrpc":"2.0","id":"retry-%s","method":"tools/call","params":{\
            "name":"post_fee_adjustment","arguments":%s,\
            "inputResponses":{"confirm_adjustment":%s},"requestState":"%s",\
            "_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28",\
            "io.modelcontextprotocol/clientCapabilities":{"elicitation":{}}}}}"""
            .formatted(operationId, arguments(operationId), answerJson, requestState);
        return send(proxy, mcp("tools/call", "post_fee_adjustment", body, token));
    }
}
