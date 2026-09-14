package dev.l4jlab.mcp.topology;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T073, T090, T092: what each caller may see, decided by the legacy API and nowhere else.
 *
 * <p>Against the real system of record with its real seeded data, because the property being checked
 * is that the MCP server does <em>not</em> decide this. A stub would let the entitlement predicate
 * pass without ever running.
 */
class EntitlementTopologyTest extends TopologyFixture {

    @Test
    void anAdvisorSeesOnlyItsOwnRuns() {
        Map<String, Object> result = search("advisor-alpha-101", "firm-alpha");

        assertThat(advisorsIn(result)).containsExactly("adv-101");
        assertThat((Integer) result.get("total_match_count")).isPositive();
    }

    @Test
    void twoAdvisorsOfOneFirmSeeDisjointSets() {
        Set<String> first = runIds(search("advisor-alpha-101", "firm-alpha"));
        Set<String> second = runIds(search("advisor-alpha-102", "firm-alpha"));

        assertThat(first).isNotEmpty();
        assertThat(second).isNotEmpty();
        assertThat(first).doesNotContainAnyElementsOf(second);
    }

    /**
     * Feature 010 H-005: this used to read the first page and assert both advisors appeared on it.
     *
     * <p>A page holds 20 of however many runs exist, ordered by start time, and every pass of this
     * suite starts more runs — all under {@code adv-101}. After enough passes {@code adv-102} falls
     * off the first page and this failed, so the suite was green on a fresh volume and red on a used
     * one. That is a verdict about how often it had been run, not about the code.
     *
     * <p>The property is "a firm administrator reaches both advisors", so it is asked directly,
     * one advisor at a time. Page composition is not the subject and is no longer in the assertion.
     */
    @Test
    void aFirmAdminAndOpsBothSeeTheWholeFirm() {
        Map<String, Object> admin = search("admin-alpha", "firm-alpha");
        Map<String, Object> ops = search("ops-alpha", "firm-alpha");

        assertThat(runIds(searchAdvisor("admin-alpha", "firm-alpha", "adv-101"))).isNotEmpty();
        assertThat(runIds(searchAdvisor("admin-alpha", "firm-alpha", "adv-102"))).isNotEmpty();
        assertThat(ops.get("total_match_count")).isEqualTo(admin.get("total_match_count"));
    }

    @Test
    void theTotalACallerSeesIsItsOwnTotal() {
        int advisorTotal = (Integer) search("advisor-alpha-101", "firm-alpha")
            .get("total_match_count");
        int adminTotal = (Integer) search("admin-alpha", "firm-alpha").get("total_match_count");

        // Counted after the scope predicate, not before: otherwise an advisor would learn exactly
        // how much of its firm it cannot see.
        assertThat(advisorTotal).isLessThan(adminTotal);
    }

    @Test
    void noOneReachesAnotherFirmAndTheRefusalSaysNothingElse() {
        Map<String, Object> result = callTool(proxy, "search_billing_runs", """
            {"firm_id":"firm-beta"}""", tokenFor("admin-alpha"));

        assertThat(result).containsEntry("isError", true);
        assertThat(result).doesNotContainKey("structuredContent");
        assertThat(textOf(result)).isEqualTo("No access to that record.");
    }

    @Test
    void aForeignRunAndAnInventedOneAreIndistinguishable() {
        String token = tokenFor("admin-alpha");

        String foreign = textOf(callTool(proxy, "get_billing_run_status", """
            {"run_id":"run-b001"}""", token));
        String invented = textOf(callTool(proxy, "get_billing_run_status", """
            {"run_id":"run-not-a-real-id"}""", token));

        // Identical on purpose: a caller that could tell them apart could probe for real identifiers.
        assertThat(foreign).isEqualTo(invented);
    }

    @Test
    void aReadOnlyCallerMayReadButNotWrite() {
        assertThat(runIds(search("readonly-alpha", "firm-alpha"))).isNotEmpty();

        Map<String, Object> write = callTool(proxy, "start_billing_run", """
            {"firm_id":"firm-alpha","executed_by_advisor_id":"adv-101"}""",
            tokenFor("readonly-alpha"));

        assertThat(write).containsEntry("isError", true);
        assertThat(textOf(write)).isEqualTo("No access to that record.");
    }

    // ---------- T092: what the audit records about a write ----------

    @Test
    void anExecutedAdjustmentIsTraceableToItsLegacyReference() {
        String token = tokenFor("admin-alpha");

        // Raise the fee first, then lower it by the same amount, so this scenario nets to zero and
        // leaves the account as it found it. It used to take 10 bps off acc-0103 every pass; the
        // account reached zero, the next pass hit the table's CHECK constraint, and the run answered
        // "the billing system is unavailable" (feature 010, H-005 and the conflict it uncovered).
        // A test that changes the world it measures eventually measures its own history.
        apply(token, "acc-0103", 10);

        String operationId = "op-" + UUID.randomUUID();
        String arguments = """
            {"operation_id":"%s","account_id":"acc-0103","delta_bps":-10,\
            "effective_date":"2026-12-01"}""".formatted(operationId);

        String state = (String) callTool(proxy, "post_fee_adjustment", arguments, token, """
            {"elicitation":{}}""").get("requestState");

        Map<String, Object> executed = structured(send(proxy, mcp("tools/call",
            "post_fee_adjustment", """
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{\
                "name":"post_fee_adjustment","arguments":%s,\
                "inputResponses":{"confirm_adjustment":{"action":"accept","content":{"confirmed":true}}},\
                "requestState":"%s",\
                "_meta":{"io.modelcontextprotocol/clientCapabilities":{"elicitation":{}}}}}"""
                .formatted(arguments, state), token)));

        // FR-020: the caller is told what the system of record called this change, which is the only
        // identifier that lets an operator find it later.
        assertThat((String) executed.get("legacy_reference_id")).startsWith("adj-");
        assertThat(executed).containsEntry("confirmed_by_user_id", "usr-900");
        // A negative adjustment is ordinary: the fee goes down, by exactly the delta.
        //
        // Feature 010 H-005, third instance: this used to assert the new fee was *positive*, which
        // held only while acc-0103 still had room. Every pass of this suite takes 10 bps off it, and
        // the legacy API adds the delta without a floor, so after enough passes the fee reached zero
        // and this failed — a verdict about how often the suite had run rather than about the code.
        // `previous_fee_bps`, which this feature added for exactly this reason, makes the real claim
        // sayable: the fee moved by the delta, wherever it started.
        int previous = (Integer) executed.get("previous_fee_bps");
        assertThat((Integer) executed.get("new_fee_bps")).isEqualTo(previous - 10);
    }

    // ---------- helpers ----------

    /** Applies a confirmed adjustment, for a scenario that needs the account in a known state. */
    private static void apply(String token, String accountId, int deltaBps) {
        String operationId = "setup-" + UUID.randomUUID();
        String arguments = """
            {"operation_id":"%s","account_id":"%s","delta_bps":%d,\
            "effective_date":"2026-12-01"}""".formatted(operationId, accountId, deltaBps);

        String state = (String) callTool(proxy, "post_fee_adjustment", arguments, token, """
            {"elicitation":{}}""").get("requestState");

        structured(send(proxy, mcp("tools/call", "post_fee_adjustment", """
            {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{\
            "name":"post_fee_adjustment","arguments":%s,\
            "inputResponses":{"confirm_adjustment":{"action":"accept","content":{"confirmed":true}}},\
            "requestState":"%s",\
            "_meta":{"io.modelcontextprotocol/clientCapabilities":{"elicitation":{}}}}}"""
            .formatted(arguments, state), token)));
    }

    private static Map<String, Object> search(String principal, String firmId) {
        return structured(callTool(proxy, "search_billing_runs", """
            {"firm_id":"%s"}""".formatted(firmId), tokenFor(principal)));
    }

    /** Asks for one advisor's runs, so an assertion does not depend on what fits on a page. */
    private static Map<String, Object> searchAdvisor(String principal, String firmId,
                                                     String advisorId) {
        return structured(callTool(proxy, "search_billing_runs", """
            {"firm_id":"%s","advisor_id":"%s"}""".formatted(firmId, advisorId),
            tokenFor(principal)));
    }

    @SuppressWarnings("unchecked")
    private static Set<String> advisorsIn(Map<String, Object> structured) {
        return ((List<Map<String, Object>>) structured.get("runs")).stream()
            .map(run -> (String) run.get("executed_by_advisor_id"))
            .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
    }

    @SuppressWarnings("unchecked")
    private static Set<String> runIds(Map<String, Object> structured) {
        return ((List<Map<String, Object>>) structured.get("runs")).stream()
            .map(run -> (String) run.get("run_id"))
            .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
    }
}
