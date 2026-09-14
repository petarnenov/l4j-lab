package dev.l4jlab.issuer;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /dev/token} — mint a token for a named fixture principal
 * (contracts/token-issuer.md).
 *
 * <p>The three {@code flaw} values are the levers FR-015's rejection tests need. They live behind
 * {@link DevOnly} with everything else here, so a profile that is not development cannot reach them.
 */
@DevOnly
@Controller("/dev")
public class TokenController {

    private static final long DEFAULT_LIFETIME_SECONDS = 3600;

    private final TokenMinter minter;

    public TokenController(TokenMinter minter) {
        this.minter = minter;
    }

    /**
     * @param principal one of the fixture names
     * @param audience {@code mcp-billing-server} or {@code legacy-billing-api}
     * @param flaw {@code null} for a valid token, or {@code expired}, {@code bad-signature},
     *     {@code wrong-audience} to produce one a verifier must reject
     */
    @Serdeable
    public record TokenRequest(@NotBlank String principal, @NotBlank String audience, String flaw) {
    }

    @Serdeable
    public record TokenResponse(String token, long expiresInSeconds) {
    }

    @Post("/token")
    public HttpResponse<?> mint(@Valid @Body TokenRequest request) {
        if (!Principals.exists(request.principal())) {
            return HttpResponse.badRequest(
                new ErrorResponse("Unknown fixture principal: " + request.principal()));
        }
        Principals.Fixture principal = Principals.byName(request.principal());
        String flaw = request.flaw() == null ? "" : request.flaw();

        String token = switch (flaw) {
            case "expired" -> minter.mintExpired(principal, request.audience());
            case "bad-signature" -> minter.mintBadlySigned(principal, request.audience());
            // A deliberately mismatched audience: the caller asked for one, we mint the other.
            case "wrong-audience" -> minter.mint(principal, otherAudience(request.audience()),
                DEFAULT_LIFETIME_SECONDS);
            case "" -> minter.mint(principal, request.audience(), DEFAULT_LIFETIME_SECONDS);
            default -> null;
        };
        if (token == null) {
            return HttpResponse.badRequest(new ErrorResponse("Unknown flaw: " + flaw));
        }
        return HttpResponse.ok(new TokenResponse(token, DEFAULT_LIFETIME_SECONDS));
    }

    private static String otherAudience(String audience) {
        return TokenMinter.AUDIENCE_MCP.equals(audience)
            ? TokenMinter.AUDIENCE_LEGACY
            : TokenMinter.AUDIENCE_MCP;
    }

    @Serdeable
    public record ErrorResponse(String message) {
    }
}
