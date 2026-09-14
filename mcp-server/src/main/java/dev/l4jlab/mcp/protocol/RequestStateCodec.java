package dev.l4jlab.mcp.protocol;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;

/**
 * The opaque {@code requestState} of a Multi Round-Trip Request (FR-020, research.md R-002).
 *
 * <p>The specification is explicit that {@code requestState} is attacker-controlled: it passes
 * through the client, which may alter it. Where it influences authorization or business logic —
 * and here it decides whether a fee change executes — it MUST be integrity-protected and invalid
 * state MUST be refused. AES-GCM, so tampering is detected on open rather than trusted.
 *
 * <p>Sealed inside are the three things the specification names for bounding replay: the
 * authenticated principal, a short expiry, and a digest identifying the originating request. Each
 * closes a hole the others do not:
 *
 * <ul>
 *   <li>the <b>principal</b>, so one caller's confirmation cannot execute another's proposal;</li>
 *   <li>the <b>expiry</b>, so an abandoned confirmation does not stay live indefinitely;</li>
 *   <li>the <b>digest</b>, so a confirmation for one change cannot execute a different one — the
 *       failure that would let a user approve +15 bps and have +150 applied.</li>
 * </ul>
 *
 * <p>What this does <em>not</em> give is single use. The specification says so plainly: these
 * measures bound the replay window, they do not make state one-shot. At-most-once execution comes
 * from the shared operation record, and neither mechanism substitutes for the other.
 */
@Singleton
public class RequestStateCodec {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final String SEPARATOR = "|";
    private static final long LIFETIME_SECONDS = 300;

    private final SecretKeySpec key;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RequestStateCodec(@Value("${mcp.request-state-key}") String key, Clock clock) {
        // A fixed-length key derived from the configured value, so operators are not required to
        // supply exactly 32 bytes to start the server.
        this.key = new SecretKeySpec(sha256(key), "AES");
        this.clock = clock;
    }

    /** What the server proposed, recovered when the client comes back to confirm it. */
    public record Proposal(String principalUserId, String operationId, String requestDigest) {
    }

    public String seal(String principalUserId, String operationId, String requestDigest) {
        long expiresAt = Instant.now(clock).plusSeconds(LIFETIME_SECONDS).getEpochSecond();
        String payload = String.join(SEPARATOR, principalUserId, operationId, requestDigest,
            String.valueOf(expiresAt));
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] sealed = cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + sealed.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(sealed, 0, combined, iv.length, sealed.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot seal request state", e);
        }
    }

    /**
     * @throws ToolFailure when the state is unreadable, tampered with, expired, presented by a
     *     different principal, or does not match the request being retried — all with one message,
     *     because naming the failed check tells a forger what to fix
     */
    public Proposal open(String requestState, String principalUserId, String requestDigest) {
        String payload;
        try {
            byte[] combined = Base64.getUrlDecoder().decode(requestState);
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            payload = new String(
                cipher.doFinal(combined, IV_LENGTH, combined.length - IV_LENGTH),
                StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw stale();
        }
        String[] parts = payload.split("\\" + SEPARATOR);
        if (parts.length != 4) {
            throw stale();
        }
        if (!parts[0].equals(principalUserId) || !parts[2].equals(requestDigest)) {
            throw stale();
        }
        if (Instant.now(clock).getEpochSecond() > Long.parseLong(parts[3])) {
            throw stale();
        }
        return new Proposal(parts[0], parts[1], parts[2]);
    }

    /** Actionable, and honest about the remedy: propose the change again. */
    private static ToolFailure stale() {
        return new ToolFailure("That confirmation is no longer valid. "
            + "Call post_fee_adjustment again to propose the change afresh.");
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot derive the request-state key", e);
        }
    }
}
