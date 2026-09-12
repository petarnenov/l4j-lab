package dev.l4jlab.chain.web;

import dev.l4jlab.chain.support.PostgresTest;
import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FR-019 in feature 004. The container health check and the load balancer's startup order both ask
 * {@code GET /health/readiness} whether an instance can take traffic, so this test asks the same way:
 * over HTTP, against a running server, with the database reachable.
 */
class ReadinessEndpointTest extends PostgresTest {

    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeAll
    static void startServer() {
        Map<String, Object> properties = new HashMap<>(databaseProperties());
        // A random port, so this never collides with a backend a developer is running on 8080.
        properties.put("micronaut.server.port", -1);
        server = ApplicationContext.run(EmbeddedServer.class, properties);
        client = server.getApplicationContext().createBean(HttpClient.class, server.getURL());
    }

    @AfterAll
    static void stopServer() {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.close();
        }
    }

    @Test
    void reportsReadyWhenTheDatabaseIsReachable() {
        HttpResponse<String> response =
                client.toBlocking().exchange("/health/readiness", String.class);

        assertThat(response.code()).isEqualTo(HttpStatus.OK.getCode());
    }
}
