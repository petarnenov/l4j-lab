package dev.l4jlab.mcp.topology;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * T057, T058: the protocol surface as a client actually meets it — through the proxy.
 *
 * <p>The deterministic suite proves the same rules against one in-process server. This proves they
 * survive the hop: that nginx passes every method through so the application's own 405 is what a
 * client sees, that all three replicas answer identically, and that a rejection is still a
 * well-formed JSON-RPC error after it has been through a reverse proxy.
 */
class ProtocolConformanceTopologyTest extends TopologyFixture {

    private static final String LIST_BODY = """
        {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}""";

    @Test
    void everyReplicaServesTheSameToolsInTheSameOrder() {
        String token = tokenFor("admin-alpha");

        List<String> onA = toolNames(send(replicaA, mcp("tools/list", null, LIST_BODY, token)));
        List<String> onB = toolNames(send(replicaB, mcp("tools/list", null, LIST_BODY, token)));
        List<String> onC = toolNames(send(replicaC, mcp("tools/list", null, LIST_BODY, token)));

        // FR-022 and FR-009 together: exactly five, and the same order everywhere. A client caching
        // this list must not get a different answer depending on which replica answered.
        assertThat(onA).containsExactly("search_billing_runs", "get_billing_run_status",
            "get_run_failures", "post_fee_adjustment", "start_billing_run");
        assertThat(onB).isEqualTo(onA);
        assertThat(onC).isEqualTo(onA);
    }

    @Test
    void theToolListIsCacheableAndNamesTheServerThatAnswered() {
        Map<String, Object> result = viaProxy(
            mcp("tools/list", null, LIST_BODY, tokenFor("admin-alpha")));

        assertThat(result).containsEntry("resultType", "complete");
        assertThat(result).containsEntry("cacheScope", "public").containsKey("ttlMs");

        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) result.get("_meta");
        assertThat(meta).isNotNull();
        assertThat(meta).containsKey("io.modelcontextprotocol/serverInfo");
    }

    @Test
    void discoveryThroughTheProxyNamesTheVersionAndTheTasksExtension() {
        Map<String, Object> result = viaProxy(mcp("server/discover", null, """
            {"jsonrpc":"2.0","id":"d1","method":"server/discover","params":{}}""",
            tokenFor("admin-alpha")));

        assertThat(result.get("supportedVersions")).isEqualTo(List.of("2026-07-28"));

        @SuppressWarnings("unchecked")
        Map<String, Object> capabilities = (Map<String, Object>) result.get("capabilities");
        @SuppressWarnings("unchecked")
        Map<String, Object> extensions = (Map<String, Object>) capabilities.get("extensions");
        assertThat(extensions).containsKey("io.modelcontextprotocol/tasks");
    }

    @Test
    void theProxyPassesEveryMethodThroughSoThe405IsTheApplicationsOwn() {
        // If nginx answered these itself the conformance test would be testing nginx. The proxy
        // config passes all methods on /mcp for exactly this reason.
        assertThat(statusOfRaw(HttpRequest.GET("/mcp"))).isEqualTo(405);
        assertThat(statusOfRaw(HttpRequest.DELETE("/mcp"))).isEqualTo(405);
    }

    @Test
    void aMismatchedHeaderIsRejectedWithHeaderMismatchAfterTheHop() {
        HttpClientResponseException thrown = assertThrows(HttpClientResponseException.class,
            () -> http.toBlocking().exchange(HttpRequest.POST("/mcp", LIST_BODY)
                .contentType(MediaType.APPLICATION_JSON)
                .header("MCP-Protocol-Version", "2026-07-28")
                // An intermediary routing on this header would send the request somewhere the body
                // never asked for. That is why it is a rejection and not a preference.
                .header("Mcp-Method", "tools/call")
                .header("Authorization", "Bearer " + tokenFor("admin-alpha")), Map.class));

        assertThat(thrown.getStatus().getCode()).isEqualTo(400);
        assertThat(errorCodeOf(thrown)).isEqualTo(-32020);
    }

    @Test
    void anUnsupportedVersionStillNamesWhatIsSupportedAfterTheHop() {
        HttpClientResponseException thrown = assertThrows(HttpClientResponseException.class,
            () -> http.toBlocking().exchange(HttpRequest.POST("/mcp", LIST_BODY)
                .contentType(MediaType.APPLICATION_JSON)
                .header("MCP-Protocol-Version", "2025-11-25")
                .header("Mcp-Method", "tools/list")
                .header("Authorization", "Bearer " + tokenFor("admin-alpha")), Map.class));

        assertThat(errorCodeOf(thrown)).isEqualTo(-32022);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) errorOf(thrown).get("data");
        assertThat(data.get("supported")).isEqualTo(List.of("2026-07-28"));
    }

    @Test
    void noReplicaEverIssuesASessionIdentifier() {
        for (URI replica : List.of(replicaA, replicaB, replicaC)) {
            try (HttpClient client = HttpClient.create(replica.toURL())) {
                var response = client.toBlocking().exchange(
                    mcp("tools/list", null, LIST_BODY, tokenFor("admin-alpha")), Map.class);
                // FR-006. The header belongs to an earlier revision, and a client that saw one
                // echoed would reasonably keep sending it.
                assertThat(response.getHeaders().getFirst("Mcp-Session-Id"))
                    .as("replica %s must not mint a session id", replica).isEmpty();
            } catch (Exception e) {
                throw new IllegalStateException("Cannot reach " + replica, e);
            }
        }
    }

    // ---------- T058: the three ways a token can be wrong ----------

    @Test
    void aTokenForAnotherAudienceIsRefused() {
        // Precisely the token the legacy API expects. If this passed, the MCP server would be
        // accepting credentials meant for a different service.
        assertThat(statusOfCall(mint("admin-alpha", "legacy-billing-api", null))).isEqualTo(401);
    }

    @Test
    void anExpiredTokenIsRefused() {
        assertThat(statusOfCall(mint("admin-alpha", "mcp-billing-server", "expired")))
            .isEqualTo(401);
    }

    @Test
    void aBadlySignedTokenIsRefused() {
        assertThat(statusOfCall(mint("admin-alpha", "mcp-billing-server", "bad-signature")))
            .isEqualTo(401);
    }

    @Test
    void theLegacyApiRefusesAnMcpTokenPresentedDirectlyToIt() {
        // The other half of FR-015, and the reason the exchange has to exist.
        URI legacy = URI.create(System.getenv()
            .getOrDefault("MCP_LEGACY_TEST_URL", "http://localhost:8886"));
        try (HttpClient client = HttpClient.create(legacy.toURL())) {
            HttpClientResponseException thrown = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(
                    HttpRequest.GET("/api/v1/billing-runs?firmId=firm-alpha")
                        .header("Authorization", "Bearer " + tokenFor("admin-alpha"))));
            assertThat(thrown.getStatus().getCode()).isEqualTo(401);
        } catch (HttpClientResponseException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot reach the legacy API at " + legacy, e);
        }
    }

    // ---------- helpers ----------

    @SuppressWarnings("unchecked")
    private static List<String> toolNames(Map<String, Object> result) {
        return ((List<Map<String, Object>>) result.get("tools")).stream()
            .map(tool -> (String) tool.get("name")).toList();
    }

    private int statusOfRaw(io.micronaut.http.MutableHttpRequest<?> request) {
        HttpClientResponseException thrown = assertThrows(HttpClientResponseException.class,
            () -> http.toBlocking().exchange(request));
        return thrown.getStatus().getCode();
    }

    private int statusOfCall(String token) {
        HttpClientResponseException thrown = assertThrows(HttpClientResponseException.class,
            () -> http.toBlocking().exchange(mcp("tools/list", null, LIST_BODY, token), Map.class));
        return thrown.getStatus().getCode();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> errorOf(HttpClientResponseException e) {
        Map<String, Object> body = (Map<String, Object>) e.getResponse().getBody(Map.class)
            .orElseThrow(() -> new AssertionError("no JSON-RPC body on the error response"));
        return (Map<String, Object>) body.get("error");
    }

    private static int errorCodeOf(HttpClientResponseException e) {
        return ((Number) errorOf(e).get("code")).intValue();
    }
}
