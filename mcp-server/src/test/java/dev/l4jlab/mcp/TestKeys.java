package dev.l4jlab.mcp;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Mints tokens for the tests with the same committed key the issuer signs with.
 *
 * <p>Mirrors the issuer's {@code TokenMinter} rather than calling it: one Micronaut application
 * cannot live on another's classpath (two {@code application.yml} files is a startup error). The
 * claims are the identity model of FR-013, so a token minted here is indistinguishable from one the
 * issuer would produce, and the audience check it has to pass is the real one.
 */
public final class TestKeys {

    public static final String KEY_ID = "l4jlab-dev";

    /**
     * Generated once per JVM, never written down.
     *
     * <p>These tests mint their own tokens because a Micronaut application cannot sit on
     * another's classpath, so the real issuer cannot be started in-process. What they must not
     * do is read a key from a file: a real RSA private key committed to a public repository
     * cannot be unpublished, and this one is no more necessary than the issuer's was.
     *
     * <p>The verification still happens for real — {@code jwks()} publishes the public half and
     * the filter under test checks signature, expiry and audience against it.
     */
    private static final KeyPair SIGNING = generate();

    /** A second pair, never published, so a bad signature is genuinely unverifiable. */
    private static final KeyPair WRONG = generate();
    public static final String AUDIENCE_LEGACY = "legacy-billing-api";
    public static final String AUDIENCE_MCP = "mcp-billing-server";

    /** The fixture principals, matching contracts/token-issuer.md and the seeded data. */
    public record Fixture(String userId, String firmId, String role, List<String> advisorIds) {
    }

    public static final Fixture ADVISOR_101 = new Fixture("usr-101", "firm-alpha", "ADVISOR", List.of("adv-101"));
    public static final Fixture ADVISOR_102 = new Fixture("usr-102", "firm-alpha", "ADVISOR", List.of("adv-102"));
    public static final Fixture ADMIN_ALPHA = new Fixture("usr-900", "firm-alpha", "FIRM_ADMIN", List.of("adv-101", "adv-102"));
    public static final Fixture OPS_ALPHA = new Fixture("usr-901", "firm-alpha", "OPS", List.of("adv-101", "adv-102"));
    public static final Fixture READONLY_ALPHA = new Fixture("usr-902", "firm-alpha", "READ_ONLY", List.of("adv-101", "adv-102"));
    public static final Fixture ADMIN_BETA = new Fixture("usr-800", "firm-beta", "FIRM_ADMIN", List.of("adv-201"));

    private TestKeys() {
    }

    public static JWKSet jwks() {
        try {
            return new JWKSet(new RSAKey.Builder((RSAPublicKey) SIGNING.getPublic())
                .keyID(KEY_ID).build());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot publish the test key", e);
        }
    }

    public static String valid(Fixture principal, String audience) {
        return sign(claims(principal, audience, Instant.now(), 3600), signingKey());
    }

    public static String expired(Fixture principal, String audience) {
        Instant past = Instant.now().minusSeconds(3600);
        return sign(claims(principal, audience, past, 60), signingKey());
    }

    public static String badlySigned(Fixture principal, String audience) {
        return sign(claims(principal, audience, Instant.now(), 3600),
            (RSAPrivateKey) WRONG.getPrivate());
    }

    private static JWTClaimsSet claims(Fixture p, String audience, Instant issuedAt, long lifetime) {
        return new JWTClaimsSet.Builder()
            .issuer("l4jlab-dev-issuer")
            .subject(p.userId())
            .audience(audience)
            .issueTime(Date.from(issuedAt))
            .expirationTime(Date.from(issuedAt.plusSeconds(lifetime)))
            .claim("firm_id", p.firmId())
            .claim("role", p.role())
            .claim("advisor_ids", p.advisorIds())
            .build();
    }

    private static String sign(JWTClaimsSet claims, RSAPrivateKey key) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT).keyID(KEY_ID).build(), claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static RSAPrivateKey signingKey() {
        return (RSAPrivateKey) SIGNING.getPrivate();
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot generate a test key", e);
        }
    }
}
