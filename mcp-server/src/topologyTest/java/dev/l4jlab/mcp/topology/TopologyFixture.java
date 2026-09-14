package dev.l4jlab.mcp.topology;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The acceptance scenarios, against the real stack (SC-002).
 *
 * <p>Everything here talks to three MCP replicas behind a round-robin proxy, a legacy API that owns
 * the data, and an issuer that mints the tokens. Nothing is stubbed, because the properties these
 * tests exist to prove — a cursor from one replica read by another, a confirmation retry landing
 * elsewhere, an inbound token that never travels — do not exist in a single process.
 *
 * <p>Requires {@code make mcp-up}. It fails with that instruction rather than starting containers
 * behind your back: a test that quietly launches a six-container stack is a test that surprises
 * whoever runs it.
 */
public abstract class TopologyFixture {

    /** The one published port. Everything goes through the proxy unless a test needs otherwise. */
    protected static URI proxy;

    /** Named replicas, for the scenarios that must prove the hop between them. */
    protected static URI replicaA;
    protected static URI replicaB;
    protected static URI replicaC;

    protected static HttpClient http;

    @BeforeAll
    static void requireRunningStack() {
        proxy = URI.create(System.getenv().getOrDefault("MCP_PROXY_URL", "http://localhost:8877"));
        replicaA = named("MCP_REPLICA_A_URL", "http://localhost:8881");
        replicaB = named("MCP_REPLICA_B_URL", "http://localhost:8882");
        replicaC = named("MCP_REPLICA_C_URL", "http://localhost:8883");

        try {
            http = HttpClient.create(proxy.toURL());
            http.toBlocking().retrieve(HttpRequest.GET("/lb-health"), String.class);
        } catch (Exception e) {
            throw new IllegalStateException("""
                The MCP stack is not reachable at %s.

                Run `make mcp-up` first, or `make mcp-verify` to start it, run these scenarios and \
                stop it again. These tests deliberately do not start containers themselves."""
                .formatted(proxy), e);
        }
    }

    @AfterAll
    static void closeClient() {
        if (http != null) {
            http.close();
        }
    }

    private static URI named(String variable, String fallback) {
        return URI.create(System.getenv().getOrDefault(variable, fallback));
    }

    // ---------- tokens ----------

    /** Mints a token through the proxy, which routes {@code /dev/} to the issuer. */
    protected static String tokenFor(String fixturePrincipal) {
        return mint(fixturePrincipal, "mcp-billing-server", null);
    }

    protected static String mint(String fixturePrincipal, String audience, String flaw) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("principal", fixturePrincipal);
        request.put("audience", audience);
        if (flaw != null) {
            request.put("flaw", flaw);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> response = http.toBlocking()
            .retrieve(HttpRequest.POST("/dev/token", request), Map.class);
        return (String) response.get("token");
    }

    // ---------- requests ----------

    /** A conformant request for this revision: mirrored headers, per-request metadata, a token. */
    protected static MutableHttpRequest<String> mcp(String method, String toolName, String body,
                                                    String token) {
        MutableHttpRequest<String> request = HttpRequest.POST("/mcp", body)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Accept", "application/json, text/event-stream")
            .header("MCP-Protocol-Version", "2026-07-28")
            .header("Mcp-Method", method)
            .header("Authorization", "Bearer " + token);
        return toolName == null ? request : request.header("Mcp-Name", toolName);
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, Object> send(URI target, MutableHttpRequest<String> request) {
        try (HttpClient client = HttpClient.create(target.toURL())) {
            Map<String, Object> response = client.toBlocking().retrieve(request, Map.class);
            return (Map<String, Object>) response.get("result");
        } catch (HttpClientResponseException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot reach " + target, e);
        }
    }

    protected static Map<String, Object> viaProxy(MutableHttpRequest<String> request) {
        return send(proxy, request);
    }

    /** Calls a tool and returns the whole result, error or not. */
    protected static Map<String, Object> callTool(URI target, String tool, String argumentsJson,
                                                  String token) {
        return callTool(target, tool, argumentsJson, token, "{}");
    }

    protected static Map<String, Object> callTool(URI target, String tool, String argumentsJson,
                                                  String token, String capabilitiesJson) {
        String body = """
            {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"%s",\
            "arguments":%s,"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28",\
            "io.modelcontextprotocol/clientCapabilities":%s}}}"""
            .formatted(tool, argumentsJson, capabilitiesJson);
        return send(target, mcp("tools/call", tool, body, token));
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, Object> structured(Map<String, Object> result) {
        if (Boolean.TRUE.equals(result.get("isError"))) {
            throw new AssertionError("expected a successful tool result, got: " + textOf(result));
        }
        return (Map<String, Object>) result.get("structuredContent");
    }

    @SuppressWarnings("unchecked")
    protected static String textOf(Map<String, Object> result) {
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        return content == null || content.isEmpty() ? "" : (String) content.get(0).get("text");
    }

    /** What the legacy API actually received, for the assertions that must prove a negative. */
    protected static List<Map<String, Object>> legacyReceivedRequests() {
        URI legacy = named("MCP_LEGACY_TEST_URL", "http://localhost:8886");
        try (HttpClient client = HttpClient.create(legacy.toURL())) {
            // The element type must be named: Micronaut Serde refuses a raw list rather than
            // guessing, which is the right call and an easy one to trip over.
            return client.toBlocking().retrieve(HttpRequest.GET("/test/received-requests"),
                io.micronaut.core.type.Argument.listOf(
                    io.micronaut.core.type.Argument.mapOf(String.class, Object.class)));
        } catch (Exception e) {
            throw new IllegalStateException("""
                Cannot read the legacy API's capture log at %s. It is published only for these \
                scenarios; check that compose.mcp.yaml exposes it and that the service runs under \
                the `test-capture` environment.""".formatted(legacy), e);
        }
    }
}
