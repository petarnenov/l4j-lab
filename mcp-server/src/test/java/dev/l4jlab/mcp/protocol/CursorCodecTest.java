package dev.l4jlab.mcp.protocol;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T068, research.md R-009: a cursor is a name, not a capability.
 *
 * <p>Each case closes a different hole, and all of them fail the same way on purpose — telling a
 * caller <em>which</em> check refused a forged cursor tells them what to fix.
 */
class CursorCodecTest {

    private static final String KEY = "a-test-key-that-is-not-a-secret";
    private static final String PREDICATE = "firm-alpha/FAILED//";
    private static final String OTHER_PREDICATE = "firm-alpha/COMPLETED//";

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-13T10:00:00Z"), ZoneOffset.UTC);
    private final CursorCodec codec = new CursorCodec(KEY, clock);

    @Test
    void roundTripsTheOffsetForTheSameSearchAndCaller() {
        String cursor = codec.mint(PREDICATE, 20, "usr-900");
        assertThat(codec.open(cursor, PREDICATE, "usr-900").offset()).isEqualTo(20);
    }

    @Test
    void isOpaqueRatherThanAnEncodedOffset() {
        String cursor = codec.mint(PREDICATE, 20, "usr-900");
        // Not something the specification demands, but a handle that reads as "20" invites a client
        // to construct "40" rather than ask for it.
        assertThat(cursor).doesNotContain("usr-900").doesNotContain("firm-alpha");
    }

    @Test
    void refusesATamperedCursor() {
        String cursor = codec.mint(PREDICATE, 20, "usr-900");
        String tampered = cursor.substring(0, cursor.length() - 4) + "AAAA";
        assertThatThrownBy(() -> codec.open(tampered, PREDICATE, "usr-900"))
            .isInstanceOf(ToolFailure.class)
            .hasMessageContaining("Run the search again");
    }

    @Test
    void refusesACursorMintedForAnotherCaller() {
        String cursor = codec.mint(PREDICATE, 20, "usr-900");
        assertThatThrownBy(() -> codec.open(cursor, PREDICATE, "usr-101"))
            .isInstanceOf(ToolFailure.class);
    }

    @Test
    void refusesACursorMintedForAnotherSearch() {
        String cursor = codec.mint(PREDICATE, 20, "usr-900");
        // Without the predicate digest this would page into results the caller never searched for.
        assertThatThrownBy(() -> codec.open(cursor, OTHER_PREDICATE, "usr-900"))
            .isInstanceOf(ToolFailure.class);
    }

    @Test
    void refusesAnExpiredCursor() {
        String cursor = codec.mint(PREDICATE, 20, "usr-900");
        CursorCodec later = new CursorCodec(KEY, Clock.offset(clock, Duration.ofHours(1)));
        assertThatThrownBy(() -> later.open(cursor, PREDICATE, "usr-900"))
            .isInstanceOf(ToolFailure.class);
    }

    @Test
    void aCursorFromOneReplicaOpensOnAnother() {
        // The property FR-007 rests on: every replica shares the key, so no shared table is needed
        // and any replica can continue any page.
        CursorCodec replicaA = new CursorCodec(KEY, clock);
        CursorCodec replicaB = new CursorCodec(KEY, clock);
        String cursor = replicaA.mint(PREDICATE, 40, "usr-900");
        assertThat(replicaB.open(cursor, PREDICATE, "usr-900").offset()).isEqualTo(40);
    }

    @Test
    void aReplicaWithADifferentKeyCannotOpenIt() {
        CursorCodec misconfigured = new CursorCodec("a-different-key", clock);
        String cursor = codec.mint(PREDICATE, 40, "usr-900");
        // The failure mode of forgetting to share MCP_CURSOR_HMAC_KEY, made visible.
        assertThatThrownBy(() -> misconfigured.open(cursor, PREDICATE, "usr-900"))
            .isInstanceOf(ToolFailure.class);
    }
}
