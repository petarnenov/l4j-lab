package dev.l4jlab.legacy.security;

import com.nimbusds.jose.jwk.JWKSet;

/**
 * Where the verification keys come from.
 *
 * <p>A seam, not an abstraction for its own sake: in a container the keys are fetched from the
 * issuer over HTTP, and in a test they are read from the same committed PEM the issuer signs with.
 * What is never replaced is the verification itself — signature, expiry and audience are checked
 * identically either way, so a test cannot pass because the check was softened.
 */
public interface JwksSource {

    JWKSet keys();
}
