package dev.l4jlab.mcp.protocol;

import dev.l4jlab.mcp.McpServerTestBase;
import dev.l4jlab.mcp.TestKeys;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * T049–T054, User Story 4: the 2026-07-28 surface itself.
 *
 * <p>Needs no domain data and no legacy API — which is the point. If the protocol layer is wrong,
 * nothing built on it can be right, and this suite says so without a single billing run in sight.
 */
class ProtocolSurfaceTest extends McpServerTestBase {

    private static final String LIST_BODY = """
        {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}""";

    // ---------- T051, T052: discovery and the cacheable tool list ----------

    @Test
    void serverDiscoverAnswersWithVersionsCapabilitiesAndIdentity() {
        Map<String, Object> result = resultOf(mcp("server/discover", """
            {"jsonrpc":"2.0","id":"d1","method":"server/discover","params":{}}""",
            TestKeys.ADMIN_ALPHA));

        assertThat(result.get("supportedVersions")).isEqualTo(List.of("2026-07-28"));
        assertThat(result).containsKey("instructions");

        @SuppressWarnings("unchecked")
        Map<String, Object> capabilities = (Map<String, Object>) result.get("capabilities");
        assertThat(capabilities).containsKey("tools");
        @SuppressWarnings("unchecked")
        Map<String, Object> extensions = (Map<String, Object>) capabilities.get("extensions");
        assertThat(extensions).containsKey("io.modelcontextprotocol/tasks");

        assertThat(result).containsEntry("cacheScope", "public").containsKey("ttlMs");
        assertThat(serverInfoOf(result)).containsEntry("name", "mcp-billing-server");
    }

    @Test
    void toolsListIsCacheableDeterministicAndCarriesTheHints() {
        Map<String, Object> first = resultOf(mcp("tools/list", LIST_BODY, TestKeys.ADMIN_ALPHA));
        Map<String, Object> second = resultOf(mcp("tools/list", LIST_BODY, TestKeys.ADMIN_ALPHA));

        assertThat(first).containsEntry("resultType", "complete");
        assertThat(first).containsEntry("cacheScope", "public").containsKey("ttlMs");
        assertThat(serverInfoOf(first)).isNotEmpty();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) first.get("tools");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> again = (List<Map<String, Object>>) second.get("tools");

        // Deterministic order: a client caches this list, and an order that shuffles defeats both
        // its cache and the model's prompt cache (FR-009).
        assertThat(tools.stream().map(t -> t.get("name")))
            .containsExactlyElementsOf(again.stream().map(t -> t.get("name")).toList());

        // The fixed order contracts/README.md declares: reads before writes, and within reads the
        // order an agent walks — find a run, open it, open its failures (FR-009).
        assertThat(tools.stream().map(t -> t.get("name")))
            .containsExactly("search_billing_runs", "get_billing_run_status", "get_run_failures",
                "post_fee_adjustment", "start_billing_run");

        // FR-022: exactly five tools, no more.
        assertThat(tools).hasSize(5);

        Map<String, Object> status = byName(tools, "get_billing_run_status");
        assertThat(status).containsKey("inputSchema").containsKey("outputSchema");

        @SuppressWarnings("unchecked")
        Map<String, Object> hints = (Map<String, Object>) status.get("annotations");
        assertThat(hints)
            .containsEntry("readOnlyHint", true)
            .containsEntry("destructiveHint", false)
            .containsEntry("idempotentHint", true)
            .containsEntry("openWorldHint", true);
    }

    // ---------- T049: header routing ----------

    @Test
    void aMissingMcpMethodHeaderIsRejected() {
        var response = failureOf(HttpRequest.POST("/mcp", LIST_BODY)
            .contentType(MediaType.APPLICATION_JSON)
            .header("MCP-Protocol-Version", "2026-07-28")
            .header("Authorization", bearer(TestKeys.ADMIN_ALPHA)));

        assertThat(response.getStatus().getCode()).isEqualTo(400);
        assertThat(errorCodeOf(response)).isEqualTo(-32020);
    }

    @Test
    void anMcpMethodHeaderThatDisagreesWithTheBodyIsRejected() {
        var response = failureOf(HttpRequest.POST("/mcp", LIST_BODY)
            .contentType(MediaType.APPLICATION_JSON)
            .header("MCP-Protocol-Version", "2026-07-28")
            // An intermediary routing on this header would send the request somewhere the body
            // never asked for. That is why it is a rejection, not a preference.
            .header("Mcp-Method", "tools/call")
            .header("Authorization", bearer(TestKeys.ADMIN_ALPHA)));

        assertThat(errorCodeOf(response)).isEqualTo(-32020);
    }

    @Test
    void aMissingMcpNameHeaderOnAToolCallIsRejected() {
        String callBody = """
            {"jsonrpc":"2.0","id":2,"method":"tools/call",
             "params":{"name":"get_billing_run_status","arguments":{"run_id":"run-a001"}}}""";

        var response = failureOf(HttpRequest.POST("/mcp", callBody)
            .contentType(MediaType.APPLICATION_JSON)
            .header("MCP-Protocol-Version", "2026-07-28")
            .header("Mcp-Method", "tools/call")
            .header("Authorization", bearer(TestKeys.ADMIN_ALPHA)));

        assertThat(errorCodeOf(response)).isEqualTo(-32020);
    }

    // ---------- T050: version negotiation ----------

    @Test
    void anUnsupportedProtocolVersionNamesWhatIsSupported() {
        var response = failureOf(HttpRequest.POST("/mcp", LIST_BODY)
            .contentType(MediaType.APPLICATION_JSON)
            .header("MCP-Protocol-Version", "1900-01-01")
            .header("Mcp-Method", "tools/list")
            .header("Authorization", bearer(TestKeys.ADMIN_ALPHA)));

        assertThat(response.getStatus().getCode()).isEqualTo(400);
        assertThat(errorCodeOf(response)).isEqualTo(-32022);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) errorOf(response).get("data");
        // A client can retry from this. An error that only says "no" cannot be recovered from.
        assertThat(data.get("supported")).isEqualTo(List.of("2026-07-28"));
        assertThat(data).containsEntry("requested", "1900-01-01");
    }

    // ---------- T053, T054: no handshake, no session ----------

    @Test
    void initializeIsRefusedWithADiagnosticRatherThanSilence() {
        var response = failureOf(HttpRequest.POST("/mcp", """
            {"jsonrpc":"2.0","id":9,"method":"initialize","params":{}}""")
            .contentType(MediaType.APPLICATION_JSON)
            .header("MCP-Protocol-Version", "2025-11-25")
            .header("Mcp-Method", "initialize")
            .header("Authorization", bearer(TestKeys.ADMIN_ALPHA)));

        Map<String, Object> error = errorOf(response);
        assertThat(error.get("code")).isEqualTo(-32601);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) error.get("data");
        assertThat(data.get("supported")).isEqualTo(List.of("2026-07-28"));
    }

    @Test
    void getAndDeleteOnTheEndpointAreNotAllowed() {
        assertThat(failureOf(HttpRequest.GET("/mcp")).getStatus().getCode()).isEqualTo(405);
        assertThat(failureOf(HttpRequest.DELETE("/mcp")).getStatus().getCode()).isEqualTo(405);
    }

    @Test
    void noSessionIdentifierIsEverIssued() {
        var response = http.toBlocking().exchange(
            mcp("tools/list", LIST_BODY, TestKeys.ADMIN_ALPHA), Map.class);

        // FR-006. The header belongs to an earlier revision; a client that sent one must not be
        // encouraged to keep doing so.
        assertThat(response.getHeaders().getFirst("Mcp-Session-Id")).isEmpty();
    }

    @Test
    void anMcpSessionIdOnTheRequestIsIgnoredRatherThanEchoed() {
        var response = http.toBlocking().exchange(
            mcp("tools/list", LIST_BODY, TestKeys.ADMIN_ALPHA)
                .header("Mcp-Session-Id", "left-over-from-2025"),
            Map.class);

        assertThat(response.getStatus().getCode()).isEqualTo(200);
        assertThat(response.getHeaders().getFirst("Mcp-Session-Id")).isEmpty();
    }

    // ---------- helpers ----------

    private static String bearer(TestKeys.Fixture who) {
        return "Bearer " + TestKeys.valid(who, TestKeys.AUDIENCE_MCP);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resultOf(io.micronaut.http.MutableHttpRequest<String> request) {
        Map<String, Object> body = http.toBlocking().retrieve(request, Map.class);
        return (Map<String, Object>) body.get("result");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> serverInfoOf(Map<String, Object> result) {
        Map<String, Object> meta = (Map<String, Object>) result.get("_meta");
        assertThat(meta).as("_meta must carry serverInfo on every result (FR-003)").isNotNull();
        return (Map<String, Object>) meta.get("io.modelcontextprotocol/serverInfo");
    }

    private static Map<String, Object> byName(List<Map<String, Object>> tools, String name) {
        return tools.stream().filter(t -> name.equals(t.get("name"))).findFirst()
            .orElseThrow(() -> new AssertionError("No tool named " + name));
    }

    private HttpClientResponseException failureOf(HttpRequest<?> request) {
        return assertThrows(HttpClientResponseException.class,
            () -> http.toBlocking().exchange(request, Map.class));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> errorOf(HttpClientResponseException e) {
        Map<String, Object> body = (Map<String, Object>) e.getResponse().getBody(Map.class)
            .orElseThrow(() -> new AssertionError("No JSON-RPC body on the error response"));
        return (Map<String, Object>) body.get("error");
    }

    private static int errorCodeOf(HttpClientResponseException e) {
        return ((Number) errorOf(e).get("code")).intValue();
    }
}
