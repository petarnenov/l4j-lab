package dev.l4jlab.legacy.security;

import dev.l4jlab.legacy.LegacyApiTestBase;
import dev.l4jlab.legacy.TestKeys;
import dev.l4jlab.legacy.api.Dtos;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * T025, FR-013 and FR-014: the legacy API decides what a caller may see, from the token's claims and
 * from nothing the request says.
 *
 * <p>This is the test that makes the MCP server's job small. If entitlement were decided anywhere
 * else, or in two places, the acceptance scenarios could pass while the boundary leaked.
 */
class EntitlementTest extends LegacyApiTestBase {

    @SuppressWarnings("unchecked")
    private static final Argument<Dtos.Page<Dtos.BillingRun>> PAGE =
        (Argument<Dtos.Page<Dtos.BillingRun>>) (Argument<?>)
            Argument.of(Dtos.Page.class, Dtos.BillingRun.class);

    private List<Dtos.BillingRun> runsSeenBy(TestKeys.Fixture who, String firmId) {
        return http.toBlocking()
            .retrieve(as(HttpRequest.GET("/api/v1/billing-runs?firmId=" + firmId), who), PAGE)
            .items();
    }

    @Test
    void anAdvisorSeesOnlyItsOwnRuns() {
        assertThat(runsSeenBy(TestKeys.ADVISOR_101, "firm-alpha"))
            .isNotEmpty()
            .allSatisfy(run -> assertThat(run.executedByAdvisorId()).isEqualTo("adv-101"));

        assertThat(runsSeenBy(TestKeys.ADVISOR_102, "firm-alpha"))
            .isNotEmpty()
            .allSatisfy(run -> assertThat(run.executedByAdvisorId()).isEqualTo("adv-102"));
    }

    @Test
    void aFirmAdminSeesEveryAdvisorOfItsFirm() {
        assertThat(runsSeenBy(TestKeys.ADMIN_ALPHA, "firm-alpha"))
            .extracting(Dtos.BillingRun::executedByAdvisorId)
            .contains("adv-101", "adv-102");
    }

    @Test
    void opsSeesTheWholeFirmJustAsAnAdminDoes() {
        assertThat(runsSeenBy(TestKeys.OPS_ALPHA, "firm-alpha"))
            .extracting(Dtos.BillingRun::executedByAdvisorId)
            .contains("adv-101", "adv-102");
    }

    @Test
    void theCallersTotalIsTheCallersOwnTotal() {
        long advisorTotal = http.toBlocking().retrieve(
            as(HttpRequest.GET("/api/v1/billing-runs?firmId=firm-alpha"), TestKeys.ADVISOR_101), PAGE)
            .totalCount();
        long adminTotal = http.toBlocking().retrieve(
            as(HttpRequest.GET("/api/v1/billing-runs?firmId=firm-alpha"), TestKeys.ADMIN_ALPHA), PAGE)
            .totalCount();

        // If the count were taken before the scope predicate, these would be equal and the advisor
        // would learn how much it cannot see.
        assertThat(advisorTotal).isLessThan(adminTotal);
    }

    @Test
    void noOneReachesAnotherFirm() {
        assertThat(statusOf(as(HttpRequest.GET("/api/v1/billing-runs?firmId=firm-beta"),
            TestKeys.ADMIN_ALPHA))).isEqualTo(403);
        assertThat(statusOf(as(HttpRequest.GET("/api/v1/billing-runs?firmId=firm-alpha"),
            TestKeys.ADMIN_BETA))).isEqualTo(403);
    }

    @Test
    void aForeignRunIdAndAnInventedOneAreIndistinguishable() {
        int foreign = statusOf(as(HttpRequest.GET("/api/v1/billing-runs/run-b001"), TestKeys.ADMIN_ALPHA));
        int invented = statusOf(as(HttpRequest.GET("/api/v1/billing-runs/run-does-not-exist"),
            TestKeys.ADMIN_ALPHA));

        // Both 403. A caller cannot probe for real identifiers by comparing status codes.
        assertThat(foreign).isEqualTo(403);
        assertThat(invented).isEqualTo(403);
    }

    @Test
    void readOnlyMayReadButNotWrite() {
        assertThat(runsSeenBy(TestKeys.READONLY_ALPHA, "firm-alpha")).isNotEmpty();

        assertThat(statusOf(as(HttpRequest.POST("/api/v1/fee-adjustments",
            new Dtos.FeeAdjustmentRequest("acc-0101", 15, LocalDate.of(2026, 10, 1), "test")),
            TestKeys.READONLY_ALPHA))).isEqualTo(403);

        assertThat(statusOf(as(HttpRequest.POST("/api/v1/billing-runs",
            new Dtos.StartRunRequest("firm-alpha", "adv-101")), TestKeys.READONLY_ALPHA)))
            .isEqualTo(403);
    }

    private int statusOf(HttpRequest<?> request) {
        HttpClientResponseException thrown = assertThrows(HttpClientResponseException.class,
            () -> http.toBlocking().exchange(request));
        return thrown.getStatus().getCode();
    }
}
