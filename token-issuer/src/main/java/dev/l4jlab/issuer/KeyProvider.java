package dev.l4jlab.issuer;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import jakarta.inject.Singleton;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * The signing key, generated at startup and never written down.
 *
 * <p>Nothing needs it to persist. The MCP server and the legacy API verify against the <em>public</em>
 * key, which they fetch from {@code /.well-known/jwks.json}, and neither has ever seen the private
 * one. A single issuer container holds it in memory for as long as it runs, and that is the whole
 * lifetime a development token needs.
 *
 * <p>This replaces a committed key pair. The reason to prefer generation is not that the committed
 * key was valuable — it protected nothing — but that a real RSA private key in a public repository
 * trips secret scanning, cannot be unpublished, and invites reuse somewhere it would matter.
 * Generating costs about a tenth of a second at startup and nothing else: a fresh clone still runs
 * with one command, because there is no step to run first.
 *
 * <p><b>The cost, stated plainly.</b> Restarting the issuer invalidates every token minted before it.
 * For a development stack that is fine, and arguably more honest than a key that outlives the process
 * — a token from a previous run of a system that no longer exists should not still work.
 */
@Singleton
public class KeyProvider {

    /**
     * Published in the JWKS and set in every token header so a verifier can select the right key.
     * Stable across restarts on purpose: the key changes, the name for "the current key" does not,
     * so a verifier looking it up by id finds whatever is current rather than nothing.
     */
    public static final String KEY_ID = "l4jlab-dev";

    private static final int KEY_SIZE = 2048;

    private final RSAPrivateKey signingKey;
    private final RSAPublicKey verificationKey;
    private final RSAPrivateKey wrongKey;
    private final JWKSet jwkSet;

    public KeyProvider() {
        KeyPair signing = generate();
        this.signingKey = (RSAPrivateKey) signing.getPrivate();
        this.verificationKey = (RSAPublicKey) signing.getPublic();
        // A second pair, never published, so the bad-signature tests have something to sign with
        // that no verifier will accept.
        this.wrongKey = (RSAPrivateKey) generate().getPrivate();
        this.jwkSet = new JWKSet(new RSAKey.Builder(verificationKey).keyID(KEY_ID).build());
    }

    public RSAPrivateKey signingKey() {
        return signingKey;
    }

    public RSAPublicKey verificationKey() {
        return verificationKey;
    }

    /** Only the bad-signature lever uses this, and only under {@link DevOnly}. */
    public RSAPrivateKey wrongKey() {
        return wrongKey;
    }

    public JWKSet jwkSet() {
        return jwkSet;
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(KEY_SIZE);
            return generator.generateKeyPair();
        } catch (Exception e) {
            // Nothing this service does is possible without a key, so failing at startup with a
            // clear message beats failing on the first token request.
            throw new IllegalStateException("Cannot generate the development signing key", e);
        }
    }
}
