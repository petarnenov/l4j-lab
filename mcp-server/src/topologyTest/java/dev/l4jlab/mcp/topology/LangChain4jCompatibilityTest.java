package dev.l4jlab.mcp.topology;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * T059, FR-030: a real client built for an earlier revision meets this server.
 *
 * <p>LangChain4j has no MCP server, so this is its entire contribution to the feature (research.md
 * R-004). Its {@code DefaultMcpClient} defaults to {@code 2025-11-25} and always opens with an
 * {@code initialize} handshake — the version is configurable, the handshake is not — which makes it
 * a genuine legacy client rather than an imitation of one.
 *
 * <p><b>What this found.</b> The server answers {@code initialize} with a JSON-RPC error naming the
 * version it does speak, before authentication, precisely so a client with no credentials still
 * learns something actionable. LangChain4j's transport then <em>discards the response body</em> and
 * reports only {@code "Unexpected status code: 400"}. So FR-001's diagnostic is correct and this
 * particular client throws it away.
 *
 * <p>The test therefore asserts the two things separately and honestly: the server's part, checked
 * on the wire, and the client's part, checked as deterministic prompt failure rather than a hang or
 * an accidental success. Asserting that the diagnostic reaches a LangChain4j user would be asserting
 * something untrue.
 */
class LangChain4jCompatibilityTest extends TopologyFixture {

    private static final Duration FAILURE_BUDGET = Duration.ofSeconds(15);

    @Test
    void theServerAnswersTheHandshakeWithTheVersionItSpeaks() {
        HttpClientResponseException thrown = assertThrows(HttpClientResponseException.class,
            () -> http.toBlocking().exchange(HttpRequest.POST("/mcp", """
                {"jsonrpc":"2.0","id":1,"method":"initialize",\
                "params":{"protocolVersion":"2025-11-25"}}""")
                .contentType(MediaType.APPLICATION_JSON), Map.class));

        assertThat(thrown.getStatus().getCode()).isEqualTo(400);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) thrown.getResponse().getBody(Map.class)
            .orElseThrow(() -> new AssertionError("the refusal must carry a JSON-RPC body"));
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) body.get("error");

        assertThat((String) error.get("message")).contains("2026-07-28");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) error.get("data");
        // A legacy client has no way to fall forward, so the versions must be machine-readable and
        // not only prose.
        assertThat(data.get("supported")).isEqualTo(List.of("2026-07-28"));
    }

    @Test
    void theDiagnosticIsAnsweredWithoutACredential() {
        // Deliberate: which protocol versions a server speaks is not privileged, and answering 401
        // would send a legacy client's user hunting for credentials that would not help.
        assertThat(assertThrows(HttpClientResponseException.class,
            () -> http.toBlocking().exchange(HttpRequest.POST("/mcp", """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""")
                .contentType(MediaType.APPLICATION_JSON), Map.class))
            .getStatus().getCode()).isEqualTo(400);
    }

    @Test
    void aRealLangChain4jClientFailsPromptlyRatherThanHangingOrSucceeding() {
        Instant before = Instant.now();

        assertThatThrownBy(() -> {
            try (McpClient client = new DefaultMcpClient.Builder()
                .transport(transportTo(proxy + "/mcp"))
                .clientName("langchain4j-compatibility-probe")
                .build()) {
                client.listTools();
            }
        }).satisfies(thrown -> {
            // The failure a LangChain4j user actually sees. The server's diagnostic is in the body
            // this client drops, which is worth knowing and not worth pretending otherwise.
            assertThat(describe(thrown)).contains("400");
            assertThat(Duration.between(before, Instant.now()))
                .as("a legacy client must fail, not hang").isLessThan(FAILURE_BUDGET);
        });
    }

    @Test
    void everyReplicaRefusesItTheSameWay() {
        for (java.net.URI replica : List.of(replicaA, replicaB, replicaC)) {
            assertThatThrownBy(() -> {
                try (McpClient client = new DefaultMcpClient.Builder()
                    .transport(transportTo(replica + "/mcp"))
                    .clientName("langchain4j-compatibility-probe")
                    .build()) {
                    client.listTools();
                }
            }).as("replica %s", replica).satisfies(thrown ->
                assertThat(describe(thrown)).contains("400"));
        }
    }

    private static McpTransport transportTo(String url) {
        return new StreamableHttpMcpTransport.Builder()
            .url(url)
            .timeout(Duration.ofSeconds(10))
            .logRequests(false)
            .logResponses(false)
            .build();
    }

    /** Walks the cause chain: the client wraps its failure several layers deep. */
    private static String describe(Throwable thrown) {
        StringBuilder text = new StringBuilder();
        for (Throwable current = thrown; current != null; current = current.getCause()) {
            text.append(current).append(" | ");
            if (current.getCause() == current) {
                break;
            }
        }
        return text.toString();
    }
}
