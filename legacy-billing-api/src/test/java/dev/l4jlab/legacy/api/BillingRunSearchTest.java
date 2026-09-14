package dev.l4jlab.legacy.api;

import dev.l4jlab.legacy.LegacyApiTestBase;
import dev.l4jlab.legacy.TestKeys;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * T067, FR-017 and FR-023: searching the system of record.
 *
 * <p>The filters are ordinary; the ordering and the counting are not. Both are asserted here because
 * the MCP server's pagination rests on them: a search whose order changed between pages would make
 * every cursor wrong, and a total taken before the scope predicate would leak the size of what the
 * caller cannot see.
 */
class BillingRunSearchTest extends LegacyApiTestBase {

    @SuppressWarnings("unchecked")
    private static final Argument<Dtos.Page<Dtos.BillingRun>> PAGE =
        (Argument<Dtos.Page<Dtos.BillingRun>>) (Argument<?>)
            Argument.of(Dtos.Page.class, Dtos.BillingRun.class);

    @Test
    void filtersByStatus() {
        Dtos.Page<Dtos.BillingRun> page = search("firmId=firm-alpha&status=FAILED",
            TestKeys.ADMIN_ALPHA);

        assertThat(page.items()).isNotEmpty();
        assertThat(page.items()).allSatisfy(run -> assertThat(run.status()).isEqualTo("FAILED"));
        assertThat(page.totalCount()).isEqualTo(page.items().size());
    }

    @Test
    void filtersByAdvisor() {
        Dtos.Page<Dtos.BillingRun> page = search("firmId=firm-alpha&advisorId=adv-102",
            TestKeys.ADMIN_ALPHA);

        assertThat(page.items()).isNotEmpty();
        assertThat(page.items())
            .allSatisfy(run -> assertThat(run.executedByAdvisorId()).isEqualTo("adv-102"));
    }

    @Test
    void filtersByStartDateRange() {
        Dtos.Page<Dtos.BillingRun> page = search(
            "firmId=firm-alpha&startedFrom=2026-08-05&startedTo=2026-08-09", TestKeys.ADMIN_ALPHA);

        assertThat(page.items()).isNotEmpty();
        assertThat(page.items()).allSatisfy(run -> {
            String day = run.startedAt().toString().substring(0, 10);
            // Inclusive at both ends: `startedTo` names a day, not an instant, and a caller asking
            // for "up to the 9th" means the whole of the 9th.
            assertThat(day).isBetween("2026-08-05", "2026-08-09");
        });
    }

    @Test
    void pagesWithAStableOrderSoACursorCanBeTrusted() {
        Dtos.Page<Dtos.BillingRun> first = search("firmId=firm-alpha&offset=0&limit=20",
            TestKeys.ADMIN_ALPHA);
        Dtos.Page<Dtos.BillingRun> second = search("firmId=firm-alpha&offset=20&limit=20",
            TestKeys.ADMIN_ALPHA);

        assertThat(first.items()).hasSize(20);
        assertThat(ids(second)).doesNotContainAnyElementsOf(ids(first));
        assertThat(first.items().size() + second.items().size())
            .isEqualTo((int) first.totalCount());

        // Re-reading the first page must give the same rows in the same order, or every cursor the
        // MCP server has minted is silently wrong.
        assertThat(ids(search("firmId=firm-alpha&offset=0&limit=20", TestKeys.ADMIN_ALPHA)))
            .isEqualTo(ids(first));
    }

    @Test
    void theLimitIsCappedRatherThanHonouredWithoutBound() {
        // An unbounded page against a system of record is a way to hurt it by accident.
        assertThat(search("firmId=firm-alpha&limit=500", TestKeys.ADMIN_ALPHA).items())
            .hasSizeLessThanOrEqualTo(20);
    }

    @Test
    void theTotalIsCountedAfterTheScopePredicate() {
        long advisorTotal = search("firmId=firm-alpha", TestKeys.ADVISOR_101).totalCount();
        long adminTotal = search("firmId=firm-alpha", TestKeys.ADMIN_ALPHA).totalCount();

        assertThat(advisorTotal).isPositive().isLessThan(adminTotal);
    }

    @Test
    void failuresOfARunThatDidNotFailAreAConflictRatherThanAnEmptyList() {
        String runId = search("firmId=firm-alpha&status=COMPLETED", TestKeys.ADMIN_ALPHA)
            .items().get(0).runId();

        HttpClientResponseException thrown = assertThrows(HttpClientResponseException.class,
            () -> http.toBlocking().exchange(
                as(HttpRequest.GET("/api/v1/billing-runs/" + runId + "/failures"),
                    TestKeys.ADMIN_ALPHA)));

        // An empty list would read as "it failed for no reason", which is a different claim.
        assertThat(thrown.getStatus().getCode()).isEqualTo(409);
    }

    @Test
    void failuresOfAFailedRunAreCappedAndReportTheFullTotal() {
        String runId = search("firmId=firm-alpha&status=FAILED", TestKeys.ADMIN_ALPHA)
            .items().get(0).runId();

        @SuppressWarnings("unchecked")
        Argument<Dtos.Page<Dtos.RunFailure>> failuresPage =
            (Argument<Dtos.Page<Dtos.RunFailure>>) (Argument<?>)
                Argument.of(Dtos.Page.class, Dtos.RunFailure.class);

        Dtos.Page<Dtos.RunFailure> page = http.toBlocking().retrieve(
            as(HttpRequest.GET("/api/v1/billing-runs/" + runId + "/failures?limit=2"),
                TestKeys.ADMIN_ALPHA), failuresPage);

        assertThat(page.items()).hasSize(2);
        assertThat(page.totalCount()).isGreaterThan(2);
        assertThat(page.items()).allSatisfy(failure -> {
            assertThat(failure.householdName()).isNotBlank();
            assertThat(failure.cause()).isNotBlank();
        });
    }

    /**
     * Feature 010, T009 (FR-001, research R-001). The bytes, not the deserialised object.
     *
     * <p>Serde omits an empty collection by default, so a page with nothing in it went out as
     * {@code {"totalCount":0}}. That makes "nothing matched" and "a response missing its items
     * field" the same bytes — and the MCP server, reading the absent field, turned an ordinary
     * query into HTTP 500 with an internal validation message (finding F-006).
     *
     * <p>Asserted against the raw response on purpose: {@code Page}'s compact constructor would
     * hand back an empty list either way, so a typed assertion here would pass while the wire was
     * still wrong.
     */
    @Test
    void aSearchMatchingNothingWritesAnEmptyItemsArray() {
        String raw = http.toBlocking().retrieve(
            as(HttpRequest.GET("/api/v1/billing-runs?firmId=firm-alpha&startedFrom=2030-01-01"),
                TestKeys.ADMIN_ALPHA),
            String.class);

        assertThat(raw).contains("\"items\"");
        assertThat(raw).contains("\"totalCount\":0");
    }

    private Dtos.Page<Dtos.BillingRun> search(String query, TestKeys.Fixture who) {
        return http.toBlocking()
            .retrieve(as(HttpRequest.GET("/api/v1/billing-runs?" + query), who), PAGE);
    }

    private static List<String> ids(Dtos.Page<Dtos.BillingRun> page) {
        return page.items().stream().map(Dtos.BillingRun::runId).toList();
    }
}
