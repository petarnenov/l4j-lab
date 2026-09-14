package dev.l4jlab.mcp.protocol;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;

/**
 * Server-minted opaque pagination cursors (FR-017, research.md R-009).
 *
 * <p>Signed and self-contained rather than stored: every replica shares one HMAC key, so a cursor
 * minted by replica A is accepted by replica B with no shared table to consult. That is the model
 * this revision prefers — state that must survive a request travels <em>in</em> the request.
 *
 * <p>Three things are bound into the signature, each closing a different hole:
 *
 * <ul>
 *   <li>the <b>predicate digest</b>, so a cursor cannot be replayed against a different search and
 *       page into results the caller never asked for;</li>
 *   <li>the <b>principal</b>, so one caller's cursor is useless to another — the specification is
 *       explicit that a handle is a name, not a capability;</li>
 *   <li>an <b>expiry</b>, which bounds both.</li>
 * </ul>
 *
 * <p>The entitlement check is not bound in and must never be: it is re-run against the legacy API on
 * every page, because what a caller may see can change between two pages.
 */
@Singleton
public class CursorCodec {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String SEPARATOR = "|";
    private static final long LIFETIME_SECONDS = 900;

    private final byte[] key;
    private final Clock clock;

    public CursorCodec(@Value("${mcp.cursor-hmac-key}") String key, Clock clock) {
        this.key = key.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    /** Where the next page starts, and what search it belongs to. */
    public record Cursor(String predicateDigest, int offset) {
    }

    public String mint(String predicate, int offset, String principalUserId) {
        String digest = digestOf(predicate);
        long expiresAt = Instant.now(clock).plusSeconds(LIFETIME_SECONDS).getEpochSecond();
        String payload = String.join(SEPARATOR, digest, String.valueOf(offset), principalUserId,
            String.valueOf(expiresAt));
        String signature = sign(payload);
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString((payload + SEPARATOR + signature).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @throws ToolFailure when the cursor is unreadable, tampered with, expired, minted for a
     *     different caller, or minted for a different search — all with the same message, because
     *     distinguishing them would tell a caller which part of a forged cursor to fix
     */
    public Cursor open(String cursor, String predicate, String principalUserId) {
        String decoded;
        try {
            decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw stale();
        }
        String[] parts = decoded.split("\\" + SEPARATOR);
        if (parts.length != 5) {
            throw stale();
        }
        String payload = String.join(SEPARATOR, parts[0], parts[1], parts[2], parts[3]);
        if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.UTF_8),
            parts[4].getBytes(StandardCharsets.UTF_8))) {
            throw stale();
        }
        if (!parts[2].equals(principalUserId) || !parts[0].equals(digestOf(predicate))) {
            throw stale();
        }
        if (Instant.now(clock).getEpochSecond() > Long.parseLong(parts[3])) {
            throw stale();
        }
        return new Cursor(parts[0], Integer.parseInt(parts[1]));
    }

    /** Actionable, and honest about the remedy: start the search again. */
    private static ToolFailure stale() {
        return new ToolFailure(
            "That cursor is no longer usable. Run the search again without a cursor.");
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot sign a cursor", e);
        }
    }

    private static String digestOf(String predicate) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(predicate.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash).substring(0, 16);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot digest a search predicate", e);
        }
    }
}
