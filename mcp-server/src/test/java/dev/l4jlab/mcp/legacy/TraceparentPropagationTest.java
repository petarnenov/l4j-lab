package dev.l4jlab.mcp.legacy;

import dev.l4jlab.mcp.McpServerTestBase;
import dev.l4jlab.mcp.TestKeys;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T033, FR-027: trace context is accepted from {@code _meta} and propagated to the legacy call.
 *
 * <p>Both halves matter, and only the second is easy to get wrong. A server that reads a
 * {@code traceparent} and then drops it produces a trace that stops at the MCP boundary — precisely
 * where an operator needs it to continue.
 */
class TraceparentPropagationTest extends McpServerTestBase {

    private static final String TRACEPARENT =
        "00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01";

    @BeforeEach
    void clearStub() {
        stub().clear();
    }

    @Test
    void theTraceparentIsAttachedToTheLegacyCallByteForByte() {
        call(TRACEPARENT);

        List<String> propagated = stub().received().stream()
            .filter(received -> received.path().startsWith("/api/"))
            .map(received -> received.traceparent())
            .toList();

        assertThat(propagated).isNotEmpty();
        // Byte-for-byte: a re-encoded or regenerated value joins no trace.
        assertThat(propagated).allSatisfy(value -> assertThat(value).isEqualTo(TRACEPARENT));
    }

    @Test
    void noHeaderIsSentWhenTheClientDidNotSupplyOne() {
        call(null);

        assertThat(stub().received().stream()
            .filter(received -> received.path().startsWith("/api/"))
            .map(received -> received.traceparent()))
            // Inventing one would create a root span unrelated to the caller's trace: worse than
            // none, because it looks like a trace and joins nothing.
            .allSatisfy(value -> assertThat(value).isNull());
    }

    private void call(String traceparent) {
        var request = HttpRequest.POST("/mcp", """
            {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{\
            "name":"get_billing_run_status","arguments":{"run_id":"run-a001"},"_meta":{}}}""")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Accept", "application/json, text/event-stream")
            .header("MCP-Protocol-Version", "2026-07-28")
            .header("Mcp-Method", "tools/call")
            .header("Mcp-Name", "get_billing_run_status")
            .header("Authorization", "Bearer "
                + TestKeys.valid(TestKeys.ADMIN_ALPHA, TestKeys.AUDIENCE_MCP));
        if (traceparent != null) {
            request = request.header("traceparent", traceparent);
        }
        http.toBlocking().retrieve(request, Map.class);
    }
}
