package dev.l4jlab.mcp.security;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.core.annotation.Order;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.inject.Singleton;

import java.util.Date;
import java.util.Optional;

/**
 * FR-015: this server accepts only tokens minted for <em>its own</em> audience.
 *
 * <p>The audience check is what makes FR-016's exchange necessary. Without it, forwarding the inbound
 * token to the legacy API would simply work, and "never forwards the token it received" would be a
 * convention nobody had to keep. With it, the only token that reaches the legacy API is one this
 * server asked the issuer to mint.
 *
 * <p>All three failures — wrong audience, expired, bad signature — are an indistinguishable 401. A
 * caller learns the token was refused, never which check refused it.
 */
@ServerFilter("/mcp")
@Singleton
// First on /mcp. Everything here requires a caller, and knowing who is asking makes every later
// rejection attributable in the audit log (FR-026).
@Order(10)
// The first JWKS fetch is a blocking HTTP call, and filters run on the Netty event loop, where
// Micronaut refuses to block. Invisible in the deterministic suite, where the keys come from a
// local file — found only by running the real stack (research.md R-017).
@ExecuteOn(TaskExecutors.BLOCKING)
public class InboundTokenFilter {

    /** Where the derived principal is left for the tools to read. */
    public static final String PRINCIPAL_ATTRIBUTE = "l4jlab.principal";

    /** Where the raw inbound token is left, so the exchange can present it. Never forwarded. */
    public static final String INBOUND_TOKEN_ATTRIBUTE = "l4jlab.inboundToken";

    private static final String OWN_AUDIENCE = "mcp-billing-server";

    private final JwksSource jwks;

    public InboundTokenFilter(JwksSource jwks) {
        this.jwks = jwks;
    }

    @RequestFilter
    public Optional<MutableHttpResponse<?>> validate(HttpRequest<?> request) {
        Optional<String> header = request.getHeaders().getFirst("Authorization");
        if (header.isEmpty() || !header.get().startsWith("Bearer ")) {
            return Optional.of(HttpResponse.unauthorized());
        }
        String token = header.get().substring("Bearer ".length());
        try {
            request.setAttribute(PRINCIPAL_ATTRIBUTE, derive(token));
            request.setAttribute(INBOUND_TOKEN_ATTRIBUTE, token);
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of(HttpResponse.unauthorized());
        }
    }

    private Principal derive(String token) throws Exception {
        SignedJWT jwt = SignedJWT.parse(token);
        RSAKey key = (RSAKey) jwks.keys().getKeyByKeyId(jwt.getHeader().getKeyID());
        if (key == null || !jwt.verify(new RSASSAVerifier(key.toRSAPublicKey()))) {
            throw new IllegalArgumentException("signature");
        }
        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        if (claims.getExpirationTime() == null || claims.getExpirationTime().before(new Date())) {
            throw new IllegalArgumentException("expiry");
        }
        if (!claims.getAudience().contains(OWN_AUDIENCE)) {
            throw new IllegalArgumentException("audience");
        }
        return new Principal(claims.getSubject(), claims.getStringClaim("firm_id"),
            claims.getStringClaim("role"), claims.getStringListClaim("advisor_ids"));
    }
}
