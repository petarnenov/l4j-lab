package dev.l4jlab.mcp.security;

import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;

import java.net.URI;

/**
 * FR-016: exchanges the token this server received for one issued for the legacy API's audience.
 *
 * <p>The inbound token is presented to the issuer and then dropped. It is never attached to a legacy
 * request, never logged, and never embedded in the minted token. SC-005 asserts that from the other
 * end by reading the legacy API's capture log; this class is what makes the assertion hold.
 */
@Singleton
public class TokenExchangeClient {

    private final URI exchangeUri;
    private final URI issuerBase;

    public TokenExchangeClient(@Value("${mcp.issuer-url}") String issuerUrl) {
        this.issuerBase = URI.create(issuerUrl);
        this.exchangeUri = URI.create(issuerUrl + "/dev/exchange");
    }

    @Serdeable
    public record ExchangeRequest(String subjectToken, String targetAudience) {
    }

    @Serdeable
    public record ExchangeResponse(String token, long expiresInSeconds) {
    }

    /**
     * @param inboundToken the token this server received; presented once, never forwarded
     * @return a token for {@code legacy-billing-api} carrying the same identity
     */
    public String exchangeForLegacy(String inboundToken) {
        try (HttpClient client = HttpClient.create(issuerBase.toURL())) {
            return client.toBlocking().retrieve(
                HttpRequest.POST(exchangeUri.getPath(),
                    new ExchangeRequest(inboundToken, "legacy-billing-api")),
                ExchangeResponse.class).token();
        } catch (Exception e) {
            // Deliberately opaque: an exchange failure must not tell a caller anything about the
            // issuer, and it becomes a tool error upstream rather than a protocol error.
            throw new TokenExchangeFailed();
        }
    }

    /** Signals that the caller could not be represented to the legacy API. */
    public static final class TokenExchangeFailed extends RuntimeException {

        public TokenExchangeFailed() {
            super("Could not obtain credentials for the billing system");
        }
    }
}
