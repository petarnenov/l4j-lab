package dev.l4jlab.mcp;

import com.nimbusds.jose.jwk.JWKSet;
import dev.l4jlab.mcp.security.JwksSource;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import jakarta.inject.Singleton;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.HashMap;
import java.util.Map;

/**
 * The MCP server on a real PostgreSQL, with its own migrations applied.
 *
 * <p>The legacy API is not started here: these tests are about the protocol surface, and the legacy
 * client is stubbed per test where a tool needs it. That split is Principle IV's, not an accident —
 * everything that needs the real topology lives in {@code topologyTest}.
 */
public abstract class McpServerTestBase {

    /** The one substitution: where the keys come from, never how they are checked. */
    @Singleton
    @Replaces(JwksSource.class)
    public static class LocalJwksSource implements JwksSource {

        @Override
        public JWKSet keys() {
            return TestKeys.jwks();
        }
    }

    private static PostgreSQLContainer<?> postgres;
    protected static EmbeddedServer server;
    protected static HttpClient http;

    @BeforeAll
    static void start() {
        postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg17")
            .withDatabaseName("l4j").withUsername("l4j").withPassword("l4j");
        postgres.start();

        // A port chosen before startup, because the server must know its own URL: the stub legacy
        // API and issuer live on it, on paths the MCP endpoint does not use.
        int port = freePort();
        String self = "http://localhost:" + port;

        Map<String, Object> props = new HashMap<>();
        props.put("micronaut.server.port", port);
        props.put("mcp.legacy-url", self);
        props.put("mcp.issuer-url", self);
        props.put("datasources.default.url", postgres.getJdbcUrl());
        props.put("datasources.default.username", postgres.getUsername());
        props.put("datasources.default.password", postgres.getPassword());
        server = ApplicationContext.run(EmbeddedServer.class, props, "test");
        http = server.getApplicationContext().createBean(HttpClient.class, server.getURL());
    }

    /** A conformant MCP request as this revision requires, authenticated as the given principal. */
    protected static MutableHttpRequest<String> mcp(String method, String body,
                                                    TestKeys.Fixture who) {
        return HttpRequest.POST("/mcp", body)
            .contentType(io.micronaut.http.MediaType.APPLICATION_JSON)
            .header("Accept", "application/json, text/event-stream")
            .header("MCP-Protocol-Version", "2026-07-28")
            .header("Mcp-Method", method)
            .header("Authorization", "Bearer " + TestKeys.valid(who, TestKeys.AUDIENCE_MCP));
    }

    private static int freePort() {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("No free port for the test server", e);
        }
    }

    /** The stand-in legacy API and issuer, so a test can assert on what actually travelled. */
    protected static StubLegacyApi stub() {
        return server.getApplicationContext().getBean(StubLegacyApi.class);
    }

    @AfterAll
    static void stop() {
        if (http != null) {
            http.close();
        }
        if (server != null) {
            server.close();
        }
        if (postgres != null) {
            postgres.stop();
        }
    }
}
