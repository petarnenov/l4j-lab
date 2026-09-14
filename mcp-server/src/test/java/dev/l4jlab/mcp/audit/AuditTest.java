package dev.l4jlab.mcp.audit;

import dev.l4jlab.mcp.McpServerTestBase;
import dev.l4jlab.mcp.TestKeys;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.transaction.annotation.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T045, T056, FR-026: every invocation leaves a row, and the row says only what it should.
 *
 * <p>The argument summary is the part worth being strict about. It is built from a per-tool
 * allow-list, so a field nobody thought about is invisible by default — which is the failure
 * direction to prefer for a log that sits next to a billing system.
 */
class AuditTest extends McpServerTestBase {

    @BeforeEach
    void clearAudit() {
        stub().clear();
        server.getApplicationContext().getBean(AuditReader.class).clear();
    }

    @Test
    void aSuccessfulToolCallIsRecordedWithItsPrincipalAndDuration() {
        callTool("get_billing_run_status", """
            {"run_id":"run-a001"}""");

        Map<String, Object> row = onlyRow();
        assertThat(row).containsEntry("tool_name", "get_billing_run_status");
        assertThat(row).containsEntry("principal_user_id", "usr-900");
        assertThat(row).containsEntry("principal_firm_id", "firm-alpha");
        assertThat(row).containsEntry("principal_role", "FIRM_ADMIN");
        assertThat(row).containsEntry("outcome", "OK");
        assertThat((Integer) row.get("duration_ms")).isGreaterThanOrEqualTo(0);
    }

    @Test
    void theArgumentSummaryCarriesIdentifiersAndNothingElse() {
        String operationId = "op-" + UUID.randomUUID();
        callTool("post_fee_adjustment", """
            {"operation_id":"%s","account_id":"acc-0101","delta_bps":15,\
            "effective_date":"2026-10-01","reason":"client asked over the phone"}"""
            .formatted(operationId));

        String summary = (String) onlyRow().get("argument_summary");

        // Identifiers, so a row can be traced back to what it touched.
        assertThat(summary).contains(operationId).contains("acc-0101").contains("2026-10-01");
        // Not the free text a person typed, and not the change itself — the legacy reference id is
        // how the change is traced, and `reason` is a note that could say anything.
        assertThat(summary).doesNotContain("client asked over the phone");
        assertThat(summary).doesNotContain("delta_bps");
    }

    @Test
    void aToolFailureIsRecordedAsOneRatherThanAsSuccess() {
        stub().forceStatus(403);
        callTool("get_billing_run_status", """
            {"run_id":"run-a001"}""");

        assertThat(onlyRow()).containsEntry("outcome", "TOOL_ERROR");
    }

    @Test
    void aRequestRejectedAtTheProtocolLayerIsStillRecorded() {
        // T056. A refused request that leaves no trace is exactly the one an operator later needs.
        var thrown = org.junit.jupiter.api.Assertions.assertThrows(
            io.micronaut.http.client.exceptions.HttpClientResponseException.class,
            () -> http.toBlocking().exchange(HttpRequest.POST("/mcp", """
                {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}""")
                .contentType(MediaType.APPLICATION_JSON)
                .header("MCP-Protocol-Version", "1900-01-01")
                .header("Mcp-Method", "tools/list")
                .header("Authorization", "Bearer "
                    + TestKeys.valid(TestKeys.ADMIN_ALPHA, TestKeys.AUDIENCE_MCP))));
        assertThat(thrown.getStatus().getCode()).isEqualTo(400);

        Map<String, Object> row = onlyRow();
        assertThat(row).containsEntry("outcome", "PROTOCOL_ERROR");
        assertThat(row).containsEntry("error_code", "400");
    }

    @Test
    void anExecutedAdjustmentNamesWhoConfirmedItAndWhatTheRecordCalledIt() {
        String operationId = "op-" + UUID.randomUUID();
        String arguments = """
            {"operation_id":"%s","account_id":"acc-0101","delta_bps":15,\
            "effective_date":"2026-10-01"}""".formatted(operationId);

        String state = (String) callWithCapabilities(arguments, null, null).get("requestState");
        callWithCapabilities(arguments,
            Map.of("confirm_adjustment",
                Map.of("action", "accept", "content", Map.of("confirmed", true))),
            state);

        Map<String, Object> executed = rows().stream()
            .filter(r -> r.get("legacy_reference_id") != null)
            .findFirst().orElseThrow(() -> new AssertionError("no audited execution"));

        assertThat(executed).containsEntry("confirmed_by_user_id", "usr-900");
        assertThat(executed).containsEntry("legacy_reference_id", "adj-1");
    }

    @Test
    void theTraceparentTheClientSentIsRecorded() {
        String traceparent = "00-0af7651916cd43dd8448eb211c80319c-00f067aa0ba902b7-01";
        http.toBlocking().retrieve(toolRequest("get_billing_run_status", """
            {"run_id":"run-a001"}""", null, null).header("traceparent", traceparent), Map.class);

        assertThat(onlyRow()).containsEntry("traceparent", traceparent);
    }

    // ---------- helpers ----------

    private void callTool(String tool, String arguments) {
        http.toBlocking().retrieve(toolRequest(tool, arguments, null, null), Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callWithCapabilities(String arguments,
                                                     Map<String, Object> inputResponses,
                                                     String requestState) {
        Map<String, Object> response = http.toBlocking().retrieve(
            toolRequest("post_fee_adjustment", arguments, inputResponses, requestState), Map.class);
        return (Map<String, Object>) response.get("result");
    }

    private io.micronaut.http.MutableHttpRequest<String> toolRequest(
        String tool, String arguments, Map<String, Object> inputResponses, String requestState) {

        StringBuilder params = new StringBuilder("\"name\":\"").append(tool)
            .append("\",\"arguments\":").append(arguments)
            .append(",\"_meta\":{\"io.modelcontextprotocol/clientCapabilities\":{\"elicitation\":{}}}");
        if (inputResponses != null) {
            params.append(",\"inputResponses\":").append(writeJson(inputResponses));
        }
        if (requestState != null) {
            params.append(",\"requestState\":\"").append(requestState).append('"');
        }
        String body = """
            {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{%s}}""".formatted(params);

        return HttpRequest.POST("/mcp", body)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Accept", "application/json, text/event-stream")
            .header("MCP-Protocol-Version", "2026-07-28")
            .header("Mcp-Method", "tools/call")
            .header("Mcp-Name", tool)
            .header("Authorization", "Bearer "
                + TestKeys.valid(TestKeys.ADMIN_ALPHA, TestKeys.AUDIENCE_MCP));
    }

    private String writeJson(Object value) {
        try {
            return new String(server.getApplicationContext()
                .getBean(io.micronaut.json.JsonMapper.class).writeValueAsBytes(value));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> onlyRow() {
        List<Map<String, Object>> rows = rows();
        assertThat(rows).as("exactly one audited request").hasSize(1);
        return rows.get(0);
    }

    @Transactional
    List<Map<String, Object>> rows() {
        return server.getApplicationContext().getBean(AuditReader.class).all();
    }

    /** Reads the audit table. A bean so the read runs inside a transaction, as the writes do. */
    @jakarta.inject.Singleton
    @io.micronaut.context.annotation.Requires(env = "test")
    public static class AuditReader {

        private final JdbcOperations jdbc;

        public AuditReader(JdbcOperations jdbc) {
            this.jdbc = jdbc;
        }

        @Transactional
        public void clear() {
            jdbc.prepareStatement("DELETE FROM mcp_ops.audit_record",
                java.sql.PreparedStatement::executeUpdate);
        }

        @Transactional
        public List<Map<String, Object>> all() {
            return jdbc.prepareStatement("""
                SELECT tool_name, principal_user_id, principal_firm_id, principal_role,
                       argument_summary::text, outcome, error_code, duration_ms, traceparent,
                       confirmed_by_user_id, legacy_reference_id
                  FROM mcp_ops.audit_record
                 ORDER BY occurred_at
                """, statement -> {
                var rs = statement.executeQuery();
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("tool_name", rs.getString(1));
                    row.put("principal_user_id", rs.getString(2));
                    row.put("principal_firm_id", rs.getString(3));
                    row.put("principal_role", rs.getString(4));
                    row.put("argument_summary", rs.getString(5));
                    row.put("outcome", rs.getString(6));
                    row.put("error_code", rs.getString(7));
                    row.put("duration_ms", rs.getInt(8));
                    row.put("traceparent", rs.getString(9));
                    row.put("confirmed_by_user_id", rs.getString(10));
                    row.put("legacy_reference_id", rs.getString(11));
                    rows.add(row);
                }
                return rows;
            });
        }
    }
}
