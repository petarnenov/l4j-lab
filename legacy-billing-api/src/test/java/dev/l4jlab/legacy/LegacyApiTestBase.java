package dev.l4jlab.legacy;

import com.nimbusds.jose.jwk.JWKSet;
import dev.l4jlab.legacy.security.JwksSource;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.HashMap;
import java.util.Map;

/**
 * A real PostgreSQL with the real migrations and the real seed data, and this API pointed at it.
 *
 * <p>Only one thing is substituted: {@link JwksSource}, because a Micronaut application cannot sit
 * on another Micronaut application's classpath. The keys it returns are the same committed keys the
 * issuer signs with, so signature, expiry and audience are all verified for real.
 */
public abstract class LegacyApiTestBase {

    private static PostgreSQLContainer<?> postgres;
    protected static EmbeddedServer legacy;
    protected static HttpClient http;

    /** The one substitution, and the reason it does not weaken anything. */
    @Singleton
    @Replaces(JwksSource.class)
    public static class LocalJwksSource implements JwksSource {

        @Override
        public JWKSet keys() {
            return TestKeys.jwks();
        }
    }

    @BeforeAll
    static void startEverything() {
        postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg17")
            .withDatabaseName("l4j").withUsername("l4j").withPassword("l4j");
        postgres.start();

        Map<String, Object> props = new HashMap<>();
        props.put("micronaut.server.port", -1);
        props.put("datasources.default.url", postgres.getJdbcUrl());
        props.put("datasources.default.username", postgres.getUsername());
        props.put("datasources.default.password", postgres.getPassword());
        legacy = ApplicationContext.run(EmbeddedServer.class, props, "test", "test-capture");
        http = legacy.getApplicationContext().createBean(HttpClient.class, legacy.getURL());
    }

    @AfterAll
    static void stopEverything() {
        if (http != null) {
            http.close();
        }
        if (legacy != null) {
            legacy.close();
        }
        if (postgres != null) {
            postgres.stop();
        }
    }

    protected static <T> MutableHttpRequest<T> authorized(MutableHttpRequest<T> request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    protected static <T> MutableHttpRequest<T> as(MutableHttpRequest<T> request, TestKeys.Fixture who) {
        return authorized(request, TestKeys.valid(who, TestKeys.AUDIENCE_LEGACY));
    }

    /** Unused import guard for HttpRequest, kept because subclasses build requests with it. */
    protected static HttpRequest<?> get(String uri) {
        return HttpRequest.GET(uri);
    }
}
