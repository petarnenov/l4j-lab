package dev.l4jlab.issuer;

import com.nimbusds.jwt.JWTClaimsSet;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T016: a minted token carries the identity model of FR-013 and verifies against the published
 * JWKS — which is the only thing the two verifying services are given.
 */
@MicronautTest(environments = {"test-capture"})
class TokenMintingTest {

    @Inject
    @Client("/")
    HttpClient http;

    @Test
    void mintsATokenCarryingTheIdentityModelAndVerifiableAgainstTheJwks() throws Exception {
        BlockingHttpClient client = http.toBlocking();

        @SuppressWarnings("unchecked")
        Map<String, Object> jwks = client.retrieve("/.well-known/jwks.json", Map.class);

        var response = client.retrieve(
            io.micronaut.http.HttpRequest.POST("/dev/token",
                new TokenController.TokenRequest("advisor-alpha-101", TokenMinter.AUDIENCE_MCP, null)),
            TokenController.TokenResponse.class);

        JWTClaimsSet claims = IssuerTestSupport.verifyAgainstJwks(response.token(), jwks);

        assertThat(claims.getSubject()).isEqualTo("usr-101");
        assertThat(claims.getStringClaim("firm_id")).isEqualTo("firm-alpha");
        assertThat(claims.getStringClaim("role")).isEqualTo("ADVISOR");
        assertThat(claims.getStringListClaim("advisor_ids")).containsExactly("adv-101");
        assertThat(claims.getAudience()).containsExactly(TokenMinter.AUDIENCE_MCP);
        assertThat(claims.getExpirationTime()).isAfter(new Date());
    }

    @Test
    void firmAdminCarriesEveryAdvisorOfItsFirm() throws Exception {
        var response = http.toBlocking().retrieve(
            io.micronaut.http.HttpRequest.POST("/dev/token",
                new TokenController.TokenRequest("admin-alpha", TokenMinter.AUDIENCE_MCP, null)),
            TokenController.TokenResponse.class);

        JWTClaimsSet claims = IssuerTestSupport.claimsWithoutVerifying(response.token());
        assertThat(claims.getStringClaim("role")).isEqualTo("FIRM_ADMIN");
        assertThat(claims.getStringListClaim("advisor_ids"))
            .containsExactlyInAnyOrderElementsOf(List.of("adv-101", "adv-102"));
    }

    @Test
    void anUnknownFixtureIsRefusedRatherThanInvented() {
        var thrown = org.junit.jupiter.api.Assertions.assertThrows(
            io.micronaut.http.client.exceptions.HttpClientResponseException.class,
            () -> http.toBlocking().exchange(
                io.micronaut.http.HttpRequest.POST("/dev/token",
                    new TokenController.TokenRequest("nobody", TokenMinter.AUDIENCE_MCP, null))));
        assertThat(thrown.getStatus().getCode()).isEqualTo(400);
    }
}
