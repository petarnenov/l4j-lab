package dev.l4jlab.issuer;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.Date;

/**
 * Turns a fixture principal into a signed JWT for a named audience.
 *
 * <p>The claims are the identity model of FR-013 verbatim: {@code sub}, {@code firm_id},
 * {@code role}, {@code advisor_ids}. Both services derive their view of the caller from exactly
 * these, which is what lets the legacy API decide entitlements without the MCP server duplicating
 * them (FR-014).
 */
@Singleton
public class TokenMinter {

    /** The MCP server's own audience. A token for anything else must be refused by it (FR-015). */
    public static final String AUDIENCE_MCP = "mcp-billing-server";

    /** The legacy API's audience. The MCP server exchanges for this; it never forwards (FR-016). */
    public static final String AUDIENCE_LEGACY = "legacy-billing-api";

    private static final String ISSUER = "l4jlab-dev-issuer";

    private final KeyProvider keys;

    public TokenMinter(KeyProvider keys) {
        this.keys = keys;
    }

    public String mint(Principals.Fixture principal, String audience, long lifetimeSeconds) {
        return sign(claims(principal, audience, Instant.now(), lifetimeSeconds), false);
    }

    /** Lever: a token that expired an hour ago, for the expiry half of FR-015. */
    public String mintExpired(Principals.Fixture principal, String audience) {
        Instant anHourAgo = Instant.now().minusSeconds(3600);
        return sign(claims(principal, audience, anHourAgo, 60), false);
    }

    /** Lever: correctly shaped, signed by a key no verifier trusts. */
    public String mintBadlySigned(Principals.Fixture principal, String audience) {
        return sign(claims(principal, audience, Instant.now(), 3600), true);
    }

    private JWTClaimsSet claims(Principals.Fixture principal, String audience, Instant issuedAt,
                                long lifetimeSeconds) {
        return new JWTClaimsSet.Builder()
            .issuer(ISSUER)
            .subject(principal.userId())
            .audience(audience)
            .issueTime(Date.from(issuedAt))
            .expirationTime(Date.from(issuedAt.plusSeconds(lifetimeSeconds)))
            .claim("firm_id", principal.firmId())
            .claim("role", principal.role())
            .claim("advisor_ids", principal.advisorIds())
            .build();
    }

    private String sign(JWTClaimsSet claims, boolean useWrongKey) {
        try {
            SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .type(JOSEObjectType.JWT)
                    .keyID(KeyProvider.KEY_ID)
                    .build(),
                claims);
            jwt.sign(new RSASSASigner(useWrongKey ? keys.wrongKey() : keys.signingKey()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot sign the development token", e);
        }
    }
}
