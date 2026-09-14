package dev.l4jlab.mcp.security;

import com.nimbusds.jose.jwk.JWKSet;

/**
 * Where the verification keys come from: the issuer over HTTP in a container, a local key in a test.
 * What is never substituted is the verification — signature, expiry and audience are checked the
 * same way either way.
 */
public interface JwksSource {

    JWKSet keys();
}
