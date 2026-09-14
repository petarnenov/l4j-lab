package dev.l4jlab.issuer;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.Date;
import java.util.List;

/**
 * {@code POST /dev/exchange} — FR-016's token exchange.
 *
 * <p>Validates the subject token against the MCP server's own audience, then mints a new token for
 * the legacy API's audience carrying the same identity. The subject token is neither returned nor
 * embedded: that is what makes "the MCP server never forwards the token it received" true rather
 * than merely intended, and SC-005 asserts it from the other end.
 *
 * <p>Shaped after RFC 8693 so replacing this with a real authorization server is mechanical. The
 * discovery flow it would need is out of scope for this iteration.
 */
@DevOnly
@Controller("/dev")
public class ExchangeController {

    private static final long EXCHANGED_LIFETIME_SECONDS = 300;

    private final KeyProvider keys;
    private final TokenMinter minter;

    public ExchangeController(KeyProvider keys, TokenMinter minter) {
        this.keys = keys;
        this.minter = minter;
    }

    @Serdeable
    public record ExchangeRequest(@NotBlank String subjectToken, @NotBlank String targetAudience) {
    }

    @Serdeable
    public record ExchangeResponse(String token, long expiresInSeconds) {
    }

    @Post("/exchange")
    public HttpResponse<?> exchange(@Valid @Body ExchangeRequest request) {
        Principals.Fixture principal;
        try {
            principal = verifyAndDerive(request.subjectToken());
        } catch (IllegalArgumentException e) {
            // Deliberately terse: the caller learns the exchange failed, not how the check works.
            return HttpResponse.unauthorized().body(new TokenController.ErrorResponse(e.getMessage()));
        }
        String token = minter.mint(principal, request.targetAudience(), EXCHANGED_LIFETIME_SECONDS);
        return HttpResponse.ok(new ExchangeResponse(token, EXCHANGED_LIFETIME_SECONDS));
    }

    /** Signature, expiry, and audience — the three FR-015 names, applied to the subject token. */
    private Principals.Fixture verifyAndDerive(String subjectToken) {
        try {
            SignedJWT jwt = SignedJWT.parse(subjectToken);
            if (!jwt.verify(new RSASSAVerifier(keys.verificationKey()))) {
                throw new IllegalArgumentException("Subject token signature is not valid");
            }
            var claims = jwt.getJWTClaimsSet();
            if (claims.getExpirationTime() == null || claims.getExpirationTime().before(new Date())) {
                throw new IllegalArgumentException("Subject token has expired");
            }
            if (!claims.getAudience().contains(TokenMinter.AUDIENCE_MCP)) {
                throw new IllegalArgumentException(
                    "Subject token was not issued for " + TokenMinter.AUDIENCE_MCP);
            }
            @SuppressWarnings("unchecked")
            List<String> advisorIds = (List<String>) claims.getClaim("advisor_ids");
            return new Principals.Fixture(
                claims.getSubject(),
                claims.getStringClaim("firm_id"),
                claims.getStringClaim("role"),
                List.copyOf(advisorIds));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Subject token could not be read");
        }
    }
}
