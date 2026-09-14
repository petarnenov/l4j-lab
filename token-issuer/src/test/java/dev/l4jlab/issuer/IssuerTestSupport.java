package dev.l4jlab.issuer;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.text.ParseException;
import java.util.Map;

/** Shared helpers: parse a token and verify it against the published JWKS, as a client would. */
final class IssuerTestSupport {

    private IssuerTestSupport() {
    }

    static JWTClaimsSet verifyAgainstJwks(String token, Map<String, Object> jwks) throws Exception {
        SignedJWT jwt = SignedJWT.parse(token);
        RSAKey key = (RSAKey) JWKSet.parse(jwks).getKeyByKeyId(jwt.getHeader().getKeyID());
        if (key == null) {
            throw new AssertionError("The JWKS does not publish the key the token was signed with");
        }
        if (!jwt.verify(new RSASSAVerifier(key.toRSAPublicKey()))) {
            throw new AssertionError("Token does not verify against the published key");
        }
        return jwt.getJWTClaimsSet();
    }

    static JWTClaimsSet claimsWithoutVerifying(String token) throws ParseException {
        return SignedJWT.parse(token).getJWTClaimsSet();
    }
}
