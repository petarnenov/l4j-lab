package dev.l4jlab.mcp.protocol;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature 010, T006 (FR-002). What a caller receives when something throws where nobody expected it.
 *
 * <p>Today an unexpected exception becomes a JSON-RPC error with an <em>empty</em> message, which the
 * SDK then rejects — so the caller learns that the server's validator complained, and nothing else.
 * That is how a {@code NullPointerException} on an empty search reached callers as
 * {@code -32603 "message must not be empty"}.
 *
 * <p>A boundary that loses the message is one bad exception away from leaking one instead. These tests
 * are about the shape of the answer, not about any particular bug: whatever throws, the caller gets a
 * sentence they can act on and the operator gets the detail.
 */
class UnexpectedFailureBoundaryTest {

    @Test
    void anUnexpectedFailureBecomesASentenceSomeoneCanActOn() {
        var error = UnexpectedFailureMapper.errorFor(new NullPointerException(
            "Cannot invoke \"java.util.List.stream()\" because the return value of "
                + "\"dev.l4jlab.mcp.tools.BillingRunTools$LegacyRunPage.items()\" is null"));

        assertThat(error.getJsonRpcError().message()).isNotBlank();
        assertThat(error.getJsonRpcError().message()).doesNotContain("NullPointerException");
        assertThat(error.getJsonRpcError().message()).doesNotContain("dev.l4jlab");
        assertThat(error.getJsonRpcError().message()).doesNotContain("java.util.List");
    }

    @Test
    void theCodeIsOneTheErrorTableLists() {
        // specs/007-mcp-billing-server/contracts/mcp-protocol.md lists -32020, -32021, -32022,
        // -32602, -32601 and -32700. An unexpected failure is not a protocol failure at all: it is a
        // tool that could not finish, and the table already has a shape for that.
        var error = UnexpectedFailureMapper.errorFor(new IllegalStateException("anything"));

        assertThat(error.getJsonRpcError().code()).isEqualTo(ToolFailure.CODE);
    }

    @Test
    void theMessageNamesWhatFailedWithoutNamingHow() {
        var error = UnexpectedFailureMapper.errorFor(new RuntimeException("connection reset by peer"));

        assertThat(error.getJsonRpcError().message()).doesNotContain("connection reset");
        assertThat(error.getJsonRpcError().message().length()).isLessThan(200);
        // Actionable for a model: it says whether to try again, which is the distinction
        // LegacyErrorTranslator already draws for every failure this server expects.
        assertThat(error.getJsonRpcError().message().toLowerCase()).contains("retry");
    }

    @Test
    void anExceptionWithNoMessageStillProducesOne() {
        // The case that started this: the SDK rejects an empty message, so the caller sees the
        // validator's complaint instead of anything about their request.
        var error = UnexpectedFailureMapper.errorFor(new NullPointerException());

        assertThat(error.getJsonRpcError().message()).isNotBlank();
    }

    @Test
    void whatActuallyHappenedIsNotDiscarded() {
        // T008: making the caller's view safe must not make the operator's view empty. The detail
        // goes to the log, structured, where feature 007's SC-006 already requires it to stay.
        Map<String, Object> logged = UnexpectedFailureMapper.logDetail(
            new IllegalStateException("the detail an operator needs"));

        assertThat(logged).containsEntry("exception", "java.lang.IllegalStateException");
        assertThat(String.valueOf(logged.get("detail"))).contains("the detail an operator needs");
    }
}
