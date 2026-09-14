package dev.l4jlab.mcp.topology;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A search that matches nothing, against the real stack (US1, FR-001, FR-001a).
 *
 * <p>Finding F-006 was recorded as an entitlement problem. It was not. Any search whose result set
 * was empty answered HTTP 500 with {@code {"code":-32603,"message":"message must not be empty"}} — a
 * code absent from 007's error mapping table, a status contradicting its rule that a tool-level
 * outcome is settled at HTTP 200, and an internal validation complaint reaching a caller, which
 * SC-006 forbids. The legacy API omitted an empty {@code items}, this server dereferenced it, and
 * the resulting NPE became an error with no message for the SDK to reject.
 *
 * <p>Requires {@code make mcp-up}.
 */
class EmptyResultTopologyTest extends TopologyFixture {

    /** No seeded run starts this late, so the result set is empty for an ordinary reason. */
    private static final String EMPTY_RANGE = """
        {"firm_id":"firm-alpha","started_from":"2030-01-01"}""";

    @Test
    @DisplayName("a date range matching nothing is an empty result, not a crash")
    void aDateRangeMatchingNothingIsAnEmptyResult() {
        Map<String, Object> result =
            callTool(proxy, "search_billing_runs", EMPTY_RANGE, tokenFor("admin-alpha"));

        assertThat(result.get("isError")).isNotEqualTo(true);

        Map<String, Object> structured = structured(result);
        // Present, then empty. A page that arrives without its collection is the ambiguity that
        // started this: "nothing matched" and "malformed response" must not be the same bytes.
        assertThat(structured).containsKey("runs");
        assertThat((List<?>) structured.get("runs")).isEmpty();
        assertThat(structured).containsEntry("total_match_count", 0);
        assertThat(structured).containsEntry("truncated", false);
    }

    @Test
    @DisplayName("a filter the caller may not use, and one that does not exist, are the same answer")
    void anUnentitledAdvisorAndAnUnknownAdvisorAreIndistinguishable() {
        String advisor = tokenFor("advisor-alpha-101");

        Map<String, Object> unentitled = search(advisor, "adv-102");
        Map<String, Object> unknown = search(advisor, "adv-does-not-exist");

        // US1-5, and the one that carries the weight. Whatever the pair is answered with, it must be
        // the same answer — otherwise the difference is a probe, and the search becomes a directory
        // of advisors the caller may not act for.
        assertThat(unentitled.get("isError")).isEqualTo(unknown.get("isError"));
        assertThat(structured(unentitled)).isEqualTo(structured(unknown));
        assertThat(structured(unentitled)).containsEntry("total_match_count", 0);
    }

    @Test
    @DisplayName("a cross-firm search is still refused, and still says nothing about the firm")
    void aCrossFirmSearchIsStillRefused() {
        // US1-6: the path that was already correct must not move. A different firm is a refusal —
        // a tool failure carried by a successful response — and it is not the same shape as the
        // empty results above, which is exactly the distinction worth keeping.
        Map<String, Object> refused = callTool(proxy, "search_billing_runs", """
            {"firm_id":"firm-beta"}""", tokenFor("admin-alpha"));

        assertThat(refused).containsEntry("isError", true);
        assertThat(refused).doesNotContainKey("structuredContent");

        // SC-006: a sentence someone can act on, and nothing from the firm the caller cannot see.
        String text = textOf(refused);
        assertThat(text).isNotBlank();
        assertThat(text).doesNotContain("firm-beta");
        assertThat(text).doesNotContainIgnoringCase("exception");
        assertThat(text).doesNotContain("jdbc:");
    }

    private static Map<String, Object> search(String token, String advisorId) {
        return callTool(proxy, "search_billing_runs", """
            {"firm_id":"firm-alpha","advisor_id":"%s"}""".formatted(advisorId), token);
    }
}
