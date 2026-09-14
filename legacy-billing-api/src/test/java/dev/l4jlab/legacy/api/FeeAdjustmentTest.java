package dev.l4jlab.legacy.api;

import dev.l4jlab.legacy.LegacyApiTestBase;
import dev.l4jlab.legacy.TestKeys;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * T084, FR-020's legacy half: applying a fee adjustment to the system of record.
 *
 * <p>Idempotency is deliberately absent here. The operation id is an MCP-level concern, and this
 * service stays a system of record: it applies what it is told, once per call, and records that it
 * did. Putting the idempotency here as well would create a second place for the rule to drift.
 */
class FeeAdjustmentTest extends LegacyApiTestBase {

    private static final LocalDate EFFECTIVE = LocalDate.of(2026, 10, 1);

    @Test
    void theAdjustmentAndTheNewBalanceAgree() {
        int before = feeOf("acc-0101");

        Dtos.FeeAdjustmentResult applied = apply("acc-0101", 15, "quarterly review",
            TestKeys.ADMIN_ALPHA);

        assertThat(applied.legacyReferenceId()).startsWith("adj-");
        assertThat(applied.newFeeBps()).isEqualTo(before + 15);
        // One transaction: the recorded adjustment and the account's fee cannot disagree, because
        // there is no moment at which only one of them has happened.
        assertThat(feeOf("acc-0101")).isEqualTo(applied.newFeeBps());
    }

    /**
     * Feature 010: a fee cannot go below zero, and saying so is this service's job.
     *
     * <p>The rule was enforced only by the table's {@code CHECK (current_fee_bps >= 0)}. The
     * adjustment row was inserted, the UPDATE then failed, and the constraint violation left here as
     * HTTP 500 — which the MCP server reports as <em>"the billing system is unavailable. Do not
     * retry; report this and stop."</em> The system was available and the request was simply not
     * applicable. A caller following that instruction would escalate a mistyped delta as an outage.
     *
     * <p>It is a `409`, which the MCP server already renders as "that request does not apply to this
     * record in its current state". The same shape as finding F-006, met in a different place.
     */
    @Test
    void anAdjustmentBelowZeroIsRefusedRatherThanCrashing() {
        int before = feeOf("acc-0101");

        HttpClientResponseException refused = assertThrows(HttpClientResponseException.class,
            () -> apply("acc-0101", -(before + 1), "too far", TestKeys.ADMIN_ALPHA));

        assertThat(refused.getStatus().getCode()).isEqualTo(409);
        // The reason travels in the body, which is where a caller reads it.
        assertThat(refused.getResponse().getBody(String.class).orElse(""))
            .contains("below zero");
        // And nothing was written: the row and the balance move together or not at all.
        assertThat(feeOf("acc-0101")).isEqualTo(before);
    }

    @Test
    void aNegativeAdjustmentLowersTheFee() {
        int before = feeOf("acc-0102");
        assertThat(apply("acc-0102", -10, null, TestKeys.ADMIN_ALPHA).newFeeBps())
            .isEqualTo(before - 10);
    }

    @Test
    void anAdjustmentWithNoReasonIsOrdinary() {
        // The note is optional, and a null must not be mistaken for a missing argument.
        assertThat(apply("acc-0103", 5, null, TestKeys.ADMIN_ALPHA).legacyReferenceId())
            .isNotBlank();
    }

    @Test
    void aZeroAdjustmentIsRefused() {
        assertThat(statusOfApplying("acc-0101", 0, TestKeys.ADMIN_ALPHA)).isEqualTo(400);
    }

    @Test
    void anAccountOfAnotherFirmIsRefusedWithoutSayingWhetherItExists() {
        assertThat(statusOfApplying("acc-0801", 15, TestKeys.ADMIN_ALPHA)).isEqualTo(403);
    }

    @Test
    void anInventedAccountIsRefusedTheSameWay() {
        // Identical to the cross-firm case on purpose: a caller able to tell them apart could probe
        // for real account identifiers.
        assertThat(statusOfApplying("acc-does-not-exist", 15, TestKeys.ADMIN_ALPHA)).isEqualTo(403);
    }

    @Test
    void aReadOnlyCallerCannotWrite() {
        assertThat(statusOfApplying("acc-0101", 15, TestKeys.READONLY_ALPHA)).isEqualTo(403);
    }

    @Test
    void theAdjustmentRecordsWhoPostedIt() {
        int before = feeOf("acc-0201");
        Dtos.FeeAdjustmentResult applied = apply("acc-0201", 20, null, TestKeys.OPS_ALPHA);

        // The caller comes from the token, never from the request body: a client cannot post an
        // adjustment in someone else's name.
        assertThat(applied.newFeeBps()).isEqualTo(before + 20);
        assertThat(applied.accountId()).isEqualTo("acc-0201");
    }

    // ---------- helpers ----------

    private Dtos.FeeAdjustmentResult apply(String accountId, int deltaBps, String reason,
                                           TestKeys.Fixture who) {
        return http.toBlocking().retrieve(
            as(HttpRequest.POST("/api/v1/fee-adjustments",
                new Dtos.FeeAdjustmentRequest(accountId, deltaBps, EFFECTIVE, reason)), who),
            Dtos.FeeAdjustmentResult.class);
    }

    private int statusOfApplying(String accountId, int deltaBps, TestKeys.Fixture who) {
        HttpClientResponseException thrown = assertThrows(HttpClientResponseException.class,
            () -> http.toBlocking().exchange(
                as(HttpRequest.POST("/api/v1/fee-adjustments",
                    new Dtos.FeeAdjustmentRequest(accountId, deltaBps, EFFECTIVE, null)), who)));
        return thrown.getStatus().getCode();
    }

    /** Reads the fee back through a second adjustment of zero would be circular; read it directly. */
    private int feeOf(String accountId) {
        return legacy.getApplicationContext()
            .getBean(dev.l4jlab.legacy.domain.AccountRepository.class)
            .findById(accountId)
            .orElseThrow(() -> new AssertionError("no such seeded account: " + accountId))
            .currentFeeBps();
    }
}
