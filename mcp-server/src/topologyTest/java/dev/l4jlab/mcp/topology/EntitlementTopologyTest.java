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

    @Test
    void aFirmAdminAndOpsBothSeeTheWholeFirm() {
        Map<String, Object> admin = search("admin-alpha", "firm-alpha");
        Map<String, Object> ops = search("ops-alpha", "firm-alpha");

        assertThat(advisorsIn(admin)).contains("adv-101", "adv-102");
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
        // A negative adjustment is ordinary: the fee goes down.
        assertThat((Integer) executed.get("new_fee_bps")).isPositive();
    }

    // ---------- helpers ----------

    private static Map<String, Object> search(String principal, String firmId) {
        return structured(callTool(proxy, "search_billing_runs", """
            {"firm_id":"%s"}""".formatted(firmId), tokenFor(principal)));
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
