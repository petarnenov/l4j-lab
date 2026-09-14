package dev.l4jlab.legacy.security;

import dev.l4jlab.legacy.TestKeys;
import dev.l4jlab.legacy.LegacyApiTestBase;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * T023, FR-015: this API accepts only tokens minted for its own audience, and refuses the three
 * ways a token can be wrong.
 *
 * <p>The audience case is the one that carries architectural weight: it is what makes the MCP
 * server's token exchange necessary. Without it, forwarding the inbound token would simply work,
 * and FR-016 would be a convention nobody had to keep.
 */
class AudienceValidationTest extends LegacyApiTestBase {

    private static final String ANY_PATH = "/api/v1/billing-runs?firmId=firm-alpha";

    @Test
    void acceptsATokenMintedForItsOwnAudience() {
        var response = http.toBlocking().exchange(
            as(HttpRequest.GET(ANY_PATH), TestKeys.ADMIN_ALPHA));
        assertThat(response.getStatus().getCode()).isEqualTo(200);
    }

    @Test
    void refusesATokenMintedForTheMcpServer() {
        // Exactly the token the MCP server receives. If this passed, forwarding would be invisible.
        String mcpToken = TestKeys.valid(TestKeys.ADMIN_ALPHA, TestKeys.AUDIENCE_MCP);
        assertThat(statusOf(mcpToken)).isEqualTo(401);
    }

    @Test
    void refusesAnExpiredToken() {
        assertThat(statusOf(TestKeys.expired(TestKeys.ADMIN_ALPHA, TestKeys.AUDIENCE_LEGACY))).isEqualTo(401);
    }

    @Test
    void refusesABadlySignedToken() {
        assertThat(statusOf(TestKeys.badlySigned(TestKeys.ADMIN_ALPHA, TestKeys.AUDIENCE_LEGACY)))
            .isEqualTo(401);
    }

    @Test
    void refusesARequestWithNoTokenAtAll() {
        HttpClientResponseException thrown = assertThrows(HttpClientResponseException.class,
            () -> http.toBlocking().exchange(HttpRequest.GET(ANY_PATH)));
        assertThat(thrown.getStatus().getCode()).isEqualTo(401);
    }

    private int statusOf(String token) {
        HttpClientResponseException thrown = assertThrows(HttpClientResponseException.class,
            () -> http.toBlocking().exchange(authorized(HttpRequest.GET(ANY_PATH), token)));
        return thrown.getStatus().getCode();
    }
}
