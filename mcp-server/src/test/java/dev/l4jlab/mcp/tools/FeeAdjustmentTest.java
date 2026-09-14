package dev.l4jlab.mcp.tools;

import dev.l4jlab.mcp.McpServerTestBase;
import dev.l4jlab.mcp.TestKeys;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T086–T089, User Story 2: the write path.
 *
 * <p>The sequence the specification cares about is three calls, and each one has to behave
 * differently: propose, execute, replay. The assertion that matters most is the stub's count of
 * adjustments, because a tool that returns the right answer while applying a fee twice is worse than
 * one that fails.
 */
class FeeAdjustmentTest extends McpServerTestBase {

    private static final Map<String, Object> ELICITATION_CAPABLE =
        Map.of("elicitation", Map.of());

    @BeforeEach
    void resetStub() {
        stub().clear();
    }

    @Test
    void theFirstCallProposesTheChangeAndTouchesNothing() {
        String operationId = newOperationId();
        Map<String, Object> result = call(operationId, 15, null, null);

        assertThat(result).containsEntry("resultType", "input_required");
        assertThat(result).containsKey("requestState");

        @SuppressWarnings("unchecked")
        Map<String, Object> requests = (Map<String, Object>) result.get("inputRequests");
        assertThat(requests).containsKey("confirm_adjustment");

        @SuppressWarnings("unchecked")
        Map<String, Object> elicitation = (Map<String, Object>) requests.get("confirm_adjustment");
        assertThat(elicitation).containsEntry("method", "elicitation/create");

        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) elicitation.get("params");
        String message = (String) params.get("message");
        // A confirmation the user cannot check is not a confirmation.
        assertThat(message).contains("acc-0101").contains("+15 bps").contains("2026-10-01");
        assertThat(message).contains("Nothing has been changed yet");

        // The whole point of the first turn: the system of record was not touched.
        assertThat(stub().adjustments()).isEmpty();
    }

    @Test
    void aninterimResultIsNotCacheable() {
        Map<String, Object> result = call(newOperationId(), 15, null, null);
        // The specification forbids caching a result produced by the MRTR mechanism: it depends on
        // inputs that are not part of the cache key.
        assertThat(result).doesNotContainKey("ttlMs").doesNotContainKey("cacheScope");
    }

    @Test
    void theConfirmedCallExecutesExactlyOnceAndTheThirdReplaysIt() {
        String operationId = newOperationId();

        Map<String, Object> proposal = call(operationId, 15, null, null);
        String state = (String) proposal.get("requestState");

        Map<String, Object> executed = structured(call(operationId, 15, confirm(true), state));
        assertThat(executed).containsEntry("legacy_reference_id", "adj-1");
        assertThat(executed).containsEntry("new_fee_bps", 115);
        assertThat(executed).containsEntry("confirmed_by_user_id", "usr-900");
        assertThat(executed).containsEntry("replayed", false);
        assertThat(stub().adjustments()).hasSize(1);

        // Third call, same operation id. The result is the original one, and nothing happens again.
        Map<String, Object> replayed = structured(call(operationId, 15, confirm(true), state));
        assertThat(replayed).containsEntry("legacy_reference_id", "adj-1");
        assertThat(replayed).containsEntry("replayed", true);
        assertThat(stub().adjustments()).hasSize(1);
    }

    @Test
    void aRepeatWithoutConfirmationStillReplaysRatherThanReProposing() {
        String operationId = newOperationId();
        String state = (String) call(operationId, 15, null, null).get("requestState");
        structured(call(operationId, 15, confirm(true), state));

        // A model that forgets it already confirmed must not be asked to confirm a second time:
        // the change is already made, and re-proposing invites a duplicate.
        Map<String, Object> again = call(operationId, 15, null, null);
        assertThat(again).containsEntry("resultType", "complete");
        assertThat(structured(again)).containsEntry("replayed", true);
        assertThat(stub().adjustments()).hasSize(1);
    }

    @Test
    void theSameOperationIdForADifferentChangeIsRefused() {
        String operationId = newOperationId();
        String state = (String) call(operationId, 15, null, null).get("requestState");
        structured(call(operationId, 15, confirm(true), state));

        // Returning the first change's result here would be the worst outcome available: the caller
        // would believe a +150 change happened that never did.
        Map<String, Object> result = call(operationId, 150, confirm(true), state);
        assertThat(result).containsEntry("isError", true);
        assertThat(textOf(result)).contains("already used for a different change");
        assertThat(stub().adjustments()).hasSize(1);
    }

    @Test
    void aConfirmationForADifferentChangeIsRefused() {
        String state = (String) call(newOperationId(), 15, null, null).get("requestState");

        // The seal binds the request state to the exact change proposed. Without it, a user
        // approving +15 bps could have +150 applied.
        Map<String, Object> result = call(newOperationId(), 150, confirm(true), state);
        assertThat(result).containsEntry("isError", true);
        assertThat(textOf(result)).contains("no longer valid");
        assertThat(stub().adjustments()).isEmpty();
    }

    @Test
    void aConfirmationFromAnotherCallerIsRefused() {
        String operationId = newOperationId();
        String state = (String) call(operationId, 15, null, null).get("requestState");

        Map<String, Object> result = callAs(TestKeys.ADVISOR_101, operationId, 15,
            confirm(true), state);
        assertThat(result).containsEntry("isError", true);
        assertThat(stub().adjustments()).isEmpty();
    }

    /**
     * Feature 010, T018 (FR-008). This assertion changed: it used to require {@code isError: true}.
     *
     * <p>{@code contracts/mcp-protocol.md} has always said a declined confirmation is answered
     * <em>"with a tool result saying the change was not applied — not an error"</em>, and the code
     * disagreed with it. The distinction matters to a model: {@code isError} says something went wrong
     * and invites a retry, which is the one thing nobody wants attempted with a fee adjustment.
     */
    @Test
    void decliningAppliesNothingAndSaysSo() {
        String operationId = newOperationId();
        String state = (String) call(operationId, 15, null, null).get("requestState");

        Map<String, Object> result = call(operationId, 15, confirm(false), state);
        assertThat(result).doesNotContainKey("isError");
        assertThat(result).containsEntry("resultType", "complete");
        assertThat(textOf(result)).contains("not confirmed");
        assertThat(stub().adjustments()).isEmpty();
    }

    /**
     * Feature 010, T017 (FR-007). Absent and unreadable are different answers.
     *
     * <p>A client following the published contract sent the flat shape — {@code {confirmed: true}} —
     * and the server read anything it could not interpret as a refusal. So the client asked for a
     * change and was silently understood to have declined it, with no diagnostic and a response that
     * looked like success.
     */
    @Test
    void anAnswerThatCannotBeReadIsSaidSoRatherThanTakenAsARefusal() {
        String operationId = newOperationId();
        String state = (String) call(operationId, 15, null, null).get("requestState");

        Map<String, Object> flat = call(operationId, 15,
            Map.of("confirm_adjustment", Map.of("confirmed", true)), state);

        assertThat(flat).containsEntry("isError", true);
        assertThat(textOf(flat)).contains("could not be read");
        assertThat(textOf(flat)).contains("content.confirmed");
        assertThat(stub().adjustments()).isEmpty();
    }

    /**
     * Feature 010, T019 (FR-007). The other side of the distinction T017 draws.
     *
     * <p>An answer that is simply <em>not there</em> is still a refusal: the caller carried the
     * elicitation envelope and left the decision out, which is a decision. Only an answer that is
     * present and uninterpretable is an error. Collapsing the two is what let a client ask for a
     * change and be recorded as having declined it.
     */
    @Test
    void anAbsentAnswerIsStillARefusal() {
        String operationId = newOperationId();
        String state = (String) call(operationId, 15, null, null).get("requestState");

        Map<String, Object> absent = call(operationId, 15,
            Map.of("confirm_adjustment", Map.of("action", "decline")), state);

        assertThat(absent).doesNotContainKey("isError");
        assertThat(textOf(absent)).contains("not confirmed");
        assertThat(stub().adjustments()).isEmpty();
    }

    @Test
    void aZeroAdjustmentIsRefusedBeforeAnyoneIsAsked() {
        Map<String, Object> result = call(newOperationId(), 0, null, null);
        assertThat(result).containsEntry("isError", true);
        assertThat(textOf(result)).contains("must not be zero");
    }

    @Test
    void aClientThatCannotBeAskedIsToldSoRatherThanHavingTheChangeApplied() {
        Map<String, Object> result = callWithCapabilities(TestKeys.ADMIN_ALPHA, newOperationId(),
            15, null, null, Map.of());

        assertThat(result).containsEntry("isError", true);
        assertThat(textOf(result)).contains("did not declare elicitation support");
        assertThat(stub().adjustments()).isEmpty();
    }

    @Test
    void aFailedExecutionIsNotSilentlyRetriedUnderTheSameOperationId() {
        String operationId = newOperationId();
        String state = (String) call(operationId, 15, null, null).get("requestState");

        stub().forceStatus(500);
        Map<String, Object> failed = call(operationId, 15, confirm(true), state);
        assertThat(failed).containsEntry("isError", true);

        stub().forceStatus(0);
        Map<String, Object> retried = call(operationId, 15, confirm(true), state);
        // The failure is remembered: a retry under the same id must not quietly try again against a
        // system of record that just refused.
        assertThat(retried).containsEntry("isError", true);
        assertThat(textOf(retried)).contains("already failed");
        assertThat(stub().adjustments()).isEmpty();
    }

    // ---------- helpers ----------

    private static String newOperationId() {
        return "op-" + UUID.randomUUID();
    }

    private static Map<String, Object> confirm(boolean confirmed) {
        return Map.of("confirm_adjustment",
            Map.of("action", "accept", "content", Map.of("confirmed", confirmed)));
    }

    private Map<String, Object> call(String operationId, int deltaBps,
                                     Map<String, Object> inputResponses, String requestState) {
        return callAs(TestKeys.ADMIN_ALPHA, operationId, deltaBps, inputResponses, requestState);
    }

    private Map<String, Object> callAs(TestKeys.Fixture who, String operationId, int deltaBps,
                                       Map<String, Object> inputResponses, String requestState) {
        return callWithCapabilities(who, operationId, deltaBps, inputResponses, requestState,
            ELICITATION_CAPABLE);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callWithCapabilities(TestKeys.Fixture who, String operationId,
                                                     int deltaBps,
                                                     Map<String, Object> inputResponses,
                                                     String requestState,
                                                     Map<String, Object> capabilities) {
        String body = body(operationId, deltaBps, inputResponses, requestState, capabilities);
        Map<String, Object> response = http.toBlocking().retrieve(
            HttpRequest.POST("/mcp", body)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Accept", "application/json, text/event-stream")
                .header("MCP-Protocol-Version", "2026-07-28")
                .header("Mcp-Method", "tools/call")
                .header("Mcp-Name", "post_fee_adjustment")
                .header("Authorization", "Bearer " + TestKeys.valid(who, TestKeys.AUDIENCE_MCP)),
            Map.class);
        return (Map<String, Object>) response.get("result");
    }

    private String body(String operationId, int deltaBps, Map<String, Object> inputResponses,
                        String requestState, Map<String, Object> capabilities) {
        StringBuilder params = new StringBuilder();
        params.append("""
            "name":"post_fee_adjustment","arguments":{"operation_id":"%s","account_id":"acc-0101",\
            "delta_bps":%d,"effective_date":"2026-10-01"}"""
            .formatted(operationId, deltaBps));
        params.append(",\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\",")
            .append("\"io.modelcontextprotocol/clientCapabilities\":")
            .append(writeJson(capabilities)).append('}');
        if (inputResponses != null) {
            params.append(",\"inputResponses\":").append(writeJson(inputResponses));
        }
        if (requestState != null) {
            params.append(",\"requestState\":\"").append(requestState).append('"');
        }
        return """
            {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{%s}}""".formatted(params);
    }

    private String writeJson(Object value) {
        try {
            return new String(server.getApplicationContext()
                .getBean(io.micronaut.json.JsonMapper.class).writeValueAsBytes(value));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> structured(Map<String, Object> result) {
        assertThat(result.get("isError"))
            .as("expected a successful tool result, got: " + result).isNotEqualTo(true);
        return (Map<String, Object>) result.get("structuredContent");
    }

    @SuppressWarnings("unchecked")
    private static String textOf(Map<String, Object> result) {
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        assertThat(content).isNotEmpty();
        return (String) content.get(0).get("text");
    }
}
