package dev.l4jlab.issuer;

import com.nimbusds.jwt.JWTClaimsSet;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * T019, FR-016: the exchange refuses anything not issued for the MCP server's own audience, carries
 * the identity forward unchanged, and — the point of the whole arrangement — does not hand the
 * subject token back or embed it.
 */
@MicronautTest(environments = {"test-capture"})
class TokenExchangeTest {

    @Inject
    @Client("/")
    HttpClient http;

    private String mint(String principal, String audience, String flaw) {
        return http.toBlocking().retrieve(
            HttpRequest.POST("/dev/token", new TokenController.TokenRequest(principal, audience, flaw)),
            TokenController.TokenResponse.class).token();
    }

    @Test
    void carriesTheIdentityForwardAndNeverEmbedsTheSubjectToken() throws Exception {
        String subject = mint("admin-alpha", TokenMinter.AUDIENCE_MCP, null);

        var exchanged = http.toBlocking().retrieve(
            HttpRequest.POST("/dev/exchange",
                new ExchangeController.ExchangeRequest(subject, TokenMinter.AUDIENCE_LEGACY)),
            ExchangeController.ExchangeResponse.class);

        JWTClaimsSet claims = IssuerTestSupport.claimsWithoutVerifying(exchanged.token());
        assertThat(claims.getAudience()).containsExactly(TokenMinter.AUDIENCE_LEGACY);
        assertThat(claims.getSubject()).isEqualTo("usr-900");
        assertThat(claims.getStringClaim("firm_id")).isEqualTo("firm-alpha");
        assertThat(claims.getStringClaim("role")).isEqualTo("FIRM_ADMIN");
        assertThat(claims.getStringListClaim("advisor_ids")).containsExactlyInAnyOrder("adv-101", "adv-102");

        // FR-016 stated as an assertion rather than an intention.
        assertThat(exchanged.token()).isNotEqualTo(subject);
        assertThat(exchanged.token()).doesNotContain(subject);
    }

    @Test
    void refusesASubjectTokenMintedForAnotherAudience() {
        String wrongAudience = mint("admin-alpha", TokenMinter.AUDIENCE_LEGACY, null);
        assertThat(statusOfExchanging(wrongAudience)).isEqualTo(401);
    }

    @Test
    void refusesAnExpiredSubjectToken() {
        assertThat(statusOfExchanging(mint("admin-alpha", TokenMinter.AUDIENCE_MCP, "expired")))
            .isEqualTo(401);
    }

    @Test
    void refusesABadlySignedSubjectToken() {
        assertThat(statusOfExchanging(mint("admin-alpha", TokenMinter.AUDIENCE_MCP, "bad-signature")))
            .isEqualTo(401);
    }

    private int statusOfExchanging(String subjectToken) {
        HttpClientResponseException thrown = assertThrows(HttpClientResponseException.class,
            () -> http.toBlocking().exchange(HttpRequest.POST("/dev/exchange",
                new ExchangeController.ExchangeRequest(subjectToken, TokenMinter.AUDIENCE_LEGACY))));
        return thrown.getStatus().getCode();
    }
}
