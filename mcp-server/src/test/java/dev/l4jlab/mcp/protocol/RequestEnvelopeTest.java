package dev.l4jlab.mcp.protocol;

import dev.l4jlab.mcp.McpServerTestBase;
import dev.l4jlab.mcp.TestKeys;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T035, FR-002: every request carries its own protocol metadata, and the server infers none of it
 * from anything that came before.
 *
 * <p>The same client is used for every request in this class, on purpose. If the server were
 * remembering anything per connection, a second request that omits what the first supplied would
 * quietly succeed — and that is the failure this suite exists to catch.
 */
class RequestEnvelopeTest extends McpServerTestBase {

    @Test
    void capabilitiesDeclaredOnOneRequestDoNotCarryToTheNext() {
        String token = TestKeys.valid(TestKeys.ADMIN_ALPHA, TestKeys.AUDIENCE_MCP);

        // First: declare elicitation, and get the confirmation the tool needs it for.
        Map<String, Object> declared = toolCall(token, """
            {"elicitation":{}}""");
        assertThat(declared).containsEntry("resultType", "input_required");

        // Second, same connection, capabilities omitted. A server that remembered the first request
        // would send an elicitation the client has not said it can handle.
        Map<String, Object> omitted = toolCall(token, "{}");
        assertThat(omitted).containsEntry("isError", true);
        assertThat(textOf(omitted)).contains("did not declare elicitation support");
    }

    @Test
    void theProtocolVersionIsReadFromEachRequestRatherThanRemembered() {
        String token = TestKeys.valid(TestKeys.ADMIN_ALPHA, TestKeys.AUDIENCE_MCP);
        String body = """
            {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}""";

        // A good request first, so any per-connection memory would have something to remember.
        http.toBlocking().retrieve(request(body, "tools/list", null, token, "2026-07-28"),
            Map.class);

        var thrown = org.junit.jupiter.api.Assertions.assertThrows(
            io.micronaut.http.client.exceptions.HttpClientResponseException.class,
            () -> http.toBlocking().exchange(
                request(body, "tools/list", null, token, "2025-11-25"), Map.class));

        assertThat(thrown.getStatus().getCode()).isEqualTo(400);
    }

    @Test
    void aRequestWithNoProtocolVersionHeaderIsRejectedEvenAfterAGoodOne() {
        String token = TestKeys.valid(TestKeys.ADMIN_ALPHA, TestKeys.AUDIENCE_MCP);
        String body = """
            {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}""";

        http.toBlocking().retrieve(request(body, "tools/list", null, token, "2026-07-28"),
            Map.class);

        var thrown = org.junit.jupiter.api.Assertions.assertThrows(
            io.micronaut.http.client.exceptions.HttpClientResponseException.class,
            () -> http.toBlocking().exchange(
                request(body, "tools/list", null, token, null), Map.class));

        assertThat(thrown.getStatus().getCode()).isEqualTo(400);
    }

    @Test
    void clientInfoIsOptionalAndItsAbsenceChangesNothing() {
        String token = TestKeys.valid(TestKeys.ADMIN_ALPHA, TestKeys.AUDIENCE_MCP);

        // The specification marks clientInfo as SHOULD, not MUST. A server that required it would
        // refuse conformant clients.
        Map<String, Object> result = http.toBlocking().retrieve(request("""
            {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{"_meta":{\
            "io.modelcontextprotocol/protocolVersion":"2026-07-28",\
            "io.modelcontextprotocol/clientCapabilities":{}}}}""",
            "tools/list", null, token, "2026-07-28"), Map.class);

        assertThat(result).containsKey("result");
    }

    // ---------- helpers ----------

    @SuppressWarnings("unchecked")
    private Map<String, Object> toolCall(String token, String capabilities) {
        String body = """
            {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{\
            "name":"post_fee_adjustment",\
            "arguments":{"operation_id":"op-%s","account_id":"acc-0101","delta_bps":15,\
            "effective_date":"2026-10-01"},\
            "_meta":{"io.modelcontextprotocol/clientCapabilities":%s}}}"""
            .formatted(java.util.UUID.randomUUID(), capabilities);
        Map<String, Object> response = http.toBlocking().retrieve(
            request(body, "tools/call", "post_fee_adjustment", token, "2026-07-28"), Map.class);
        return (Map<String, Object>) response.get("result");
    }

    private static io.micronaut.http.MutableHttpRequest<String> request(
        String body, String method, String name, String token, String version) {

        var request = HttpRequest.POST("/mcp", body)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Accept", "application/json, text/event-stream")
            .header("Mcp-Method", method)
            .header("Authorization", "Bearer " + token);
        if (version != null) {
            request = request.header("MCP-Protocol-Version", version);
        }
        return name == null ? request : request.header("Mcp-Name", name);
    }

    @SuppressWarnings("unchecked")
    private static String textOf(Map<String, Object> result) {
        var content = (java.util.List<Map<String, Object>>) result.get("content");
        return content == null || content.isEmpty() ? "" : (String) content.get(0).get("text");
    }
}
