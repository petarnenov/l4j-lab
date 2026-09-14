package dev.l4jlab.mcp.topology;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private static final String TOOL = "post_fee_adjustment";
    private static final String ACCOUNT = "acc-0101";

    @Test
    @DisplayName("the contract's own worked example applies the change, run as written")
    void theContractsWorkedExampleAppliesTheChange() {
        // SC-002 asks that someone who has not read the server can implement this from the contract
        // alone. That needs a person, and is recorded as unverified. What can be checked mechanically
        // is the half that failed last time: the contract's example is *executed*, not paraphrased.
        // The JSON below comes out of `contracts/mcp-protocol.md`; only the two placeholders are
        // filled in. An edit there that stops working fails the build.
        String token = tokenFor("admin-alpha");
        String operationId = "contract-example-" + System.nanoTime();

        String proposeBody = executableBlock("confirmation-first-call")
            .replace("<OPERATION_ID>", operationId);
        Map<String, Object> proposed = send(proxy, mcp("tools/call", TOOL, proposeBody, token));

        assertThat(proposed)
            .as("the contract says the first call answers input_required")
            .containsEntry("resultType", "input_required");
        String requestState = (String) proposed.get("requestState");
        assertThat(requestState).as("the contract says the retry echoes requestState").isNotBlank();

        String retryBody = executableBlock("confirmation-retry")
            .replace("<OPERATION_ID>", operationId)
            .replace("<REQUEST_STATE>", requestState);
        Map<String, Object> applied = send(proxy, mcp("tools/call", TOOL, retryBody, token));

        assertThat(applied.get("isError")).as("a confirmed change is not an error").isNotEqualTo(true);
        assertThat(structured(applied))
            .as("the adjustment the legacy API recorded")
            .containsKey("legacy_reference_id");
    }

    @Test
    @DisplayName("a retry assembled by hand from the contract's prose applies the change")
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

    /**
     * Lifts a fenced JSON block out of the contract, by the marker comment above it.
     *
     * <p>Reading the document rather than restating it is the point: a test that keeps its own copy
     * of the example proves the server works, not that the contract does. Finding F-005 was a
     * contract that read convincingly and was wrong, and nothing executed it.
     */
    private static String executableBlock(String marker) {
        Path contract = Path.of(System.getProperty("contract.path",
            "../specs/007-mcp-billing-server/contracts/mcp-protocol.md"));
        String text;
        try {
            text = Files.readString(contract);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + contract.toAbsolutePath(), e);
        }
        String needle = "<!-- executable: " + marker + " -->";
        int at = text.indexOf(needle);
        assertThat(at).as("the contract must carry the marker %s", needle).isNotNegative();

        int open = text.indexOf("```json", at);
        int start = text.indexOf('\n', open) + 1;
        int close = text.indexOf("```", start);
        assertThat(close).as("the marker %s must be followed by a json fence", needle).isPositive();
        return text.substring(start, close).trim();
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
