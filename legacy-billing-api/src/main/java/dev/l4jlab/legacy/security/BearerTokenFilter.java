package dev.l4jlab.legacy.security;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.inject.Singleton;

import java.util.Date;
import java.util.Optional;

/**
 * Validates the bearer token against this API's <em>own</em> audience (FR-015).
 *
 * <p>Three rejections, all 401 and all deliberately indistinguishable from outside: a wrong
 * audience, an expired token, a bad signature. The audience check is the one that matters
 * architecturally — it is what stops a token minted for the MCP server from being replayed here, and
 * so what makes FR-016's exchange necessary rather than decorative.
 */
@ServerFilter("/api/**")
@Singleton
// The first JWKS fetch is a blocking HTTP call, and filters run on the Netty event loop, where
// Micronaut refuses to block. The deterministic suite substitutes a local JwksSource and so never
// sees it; only the running stack does (research.md R-017).
@ExecuteOn(TaskExecutors.BLOCKING)
public class BearerTokenFilter {

    /** The attribute the controllers read the derived scope from. */
    public static final String SCOPE_ATTRIBUTE = "l4jlab.callerScope";

    private static final String OWN_AUDIENCE = "legacy-billing-api";

    private final JwksSource jwks;

    public BearerTokenFilter(JwksSource jwks) {
        this.jwks = jwks;
    }

    @RequestFilter
    public Optional<MutableHttpResponse<?>> validate(HttpRequest<?> request) {
        Optional<String> header = request.getHeaders().getFirst("Authorization");
        if (header.isEmpty() || !header.get().startsWith("Bearer ")) {
            return Optional.of(HttpResponse.unauthorized());
        }
        try {
            CallerScope scope = verify(header.get().substring("Bearer ".length()));
            request.setAttribute(SCOPE_ATTRIBUTE, scope);
            return Optional.empty();
        } catch (Exception e) {
            // No detail: a caller learns the token was refused, never which of the three checks failed.
            return Optional.of(HttpResponse.unauthorized());
        }
    }

    private CallerScope verify(String token) throws Exception {
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
        return new CallerScope(
            claims.getSubject(),
            claims.getStringClaim("firm_id"),
            claims.getStringClaim("role"),
            claims.getStringListClaim("advisor_ids"));
    }


}
