package dev.l4jlab.issuer;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * T021, FR-025 and research.md R-013: "development only" is a constraint, not a comment.
 *
 * <p>Starts the issuer under an environment that is neither development nor {@code test-capture}
 * and asserts the beans do not exist. A committed key pair is only defensible if reaching it is
 * impossible outside the one environment that needs it.
 */
class EnvironmentGatingTest {

    @Test
    void outsideDevelopmentTheEndpointsDoNotExist() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class,
            Map.of("micronaut.server.port", -1), "production")) {

            HttpClient http = server.getApplicationContext()
                .createBean(HttpClient.class, server.getURL());

            assertThat(statusOf(http, HttpRequest.GET("/.well-known/jwks.json"))).isEqualTo(404);
            assertThat(statusOf(http, HttpRequest.POST("/dev/token",
                new TokenController.TokenRequest("admin-alpha", TokenMinter.AUDIENCE_MCP, null))))
                .isEqualTo(404);
            assertThat(statusOf(http, HttpRequest.POST("/dev/exchange",
                new ExchangeController.ExchangeRequest("irrelevant", TokenMinter.AUDIENCE_LEGACY))))
                .isEqualTo(404);
        }
    }

    @Test
    void theDeliberatelyBrokenLeversAreGatedTogetherWithEverythingElse() {
        try (EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class,
            Map.of("micronaut.server.port", -1), "production")) {

            HttpClient http = server.getApplicationContext()
                .createBean(HttpClient.class, server.getURL());

            // No profile that is not development can mint a token designed to be rejected.
            assertThat(statusOf(http, HttpRequest.POST("/dev/token",
                new TokenController.TokenRequest("admin-alpha", TokenMinter.AUDIENCE_MCP, "bad-signature"))))
                .isEqualTo(404);
        }
    }

    private static int statusOf(HttpClient http, HttpRequest<?> request) {
        HttpClientResponseException thrown = assertThrows(HttpClientResponseException.class,
            () -> http.toBlocking().exchange(request));
        return thrown.getStatus().getCode();
    }
}
