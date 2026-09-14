package dev.l4jlab.mcp.topology;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T118, SC-006: nothing a caller is shown gives away how the system is built.
 *
 * <p>Drives every failure the server can produce and scans what comes back. The list is deliberately
 * about the <em>shape</em> of a leak rather than any particular string: a stack frame, a SQL
 * keyword, an internal hostname, a driver's name. Those are what a hand-written message accidentally
 * includes when someone reaches for {@code e.getMessage()}.
 *
 * <p>This is a blunt instrument and that is the point. The precise assertions live with each tool;
 * this one catches the case where a new failure path is added and nobody thinks about the text.
 */
class ErrorMessageLeakTest extends TopologyFixture {

    /**
     * Substrings that should never reach a caller. Internal hostnames come from this stack's own
     * compose file — a message naming {@code legacy-billing-api} tells an outsider the topology.
     */
    private static final List<String> FORBIDDEN = List.of(
        "Exception", "\tat ", "java.lang", "io.micronaut", "org.postgresql",
        "SELECT", "INSERT", "UPDATE", "jdbc:", "legacy-billing-api", "token-issuer",
        "postgres", "mcp-a", "mcp-b", "mcp-c", "Caused by");

    @Test
    void noToolErrorGivesAwayHowTheSystemIsBuilt() {
        String token = tokenFor("admin-alpha");
        List<String> messages = new ArrayList<>();

        // A run belonging to another firm.
        messages.add(textOf(callTool(proxy, "get_billing_run_status", """
            {"run_id":"run-b001"}""", token)));
        // A run that does not exist. Indistinguishable from the above, by design.
        messages.add(textOf(callTool(proxy, "get_billing_run_status", """
            {"run_id":"run-does-not-exist"}""", token)));
        // Failures of a run that did not fail.
        messages.add(textOf(callTool(proxy, "get_run_failures", """
            {"run_id":"run-a001"}""", token)));
        // A search of another firm.
        messages.add(textOf(callTool(proxy, "search_billing_runs", """
            {"firm_id":"firm-beta"}""", token)));
        // A forged cursor.
        messages.add(textOf(callTool(proxy, "search_billing_runs", """
            {"firm_id":"firm-alpha","cursor":"not-a-real-cursor"}""", token)));
        // An adjustment of zero.
        messages.add(textOf(callTool(proxy, "post_fee_adjustment", """
            {"operation_id":"op-%s","account_id":"acc-0101","delta_bps":0,\
            "effective_date":"2026-10-01"}""".formatted(UUID.randomUUID()), token,
            """
                {"elicitation":{}}""")));
        // A client that cannot be asked to confirm.
        messages.add(textOf(callTool(proxy, "post_fee_adjustment", """
            {"operation_id":"op-%s","account_id":"acc-0101","delta_bps":15,\
            "effective_date":"2026-10-01"}""".formatted(UUID.randomUUID()), token)));
        // A task that does not exist.
        messages.add(textOf(send(proxy, mcp("tasks/get", null, """
            {"jsonrpc":"2.0","id":1,"method":"tasks/get","params":{"taskId":"tsk_nope",\
            "_meta":{"io.modelcontextprotocol/clientCapabilities":\
            {"extensions":{"io.modelcontextprotocol/tasks":{}}}}}}""", token))));

        assertThat(messages).isNotEmpty();
        assertThat(messages).allSatisfy(message -> {
            assertThat(message).as("every failure must say something").isNotBlank();
            for (String forbidden : FORBIDDEN) {
                assertThat(message)
                    .as("a tool error must not contain %s; got: %s", forbidden, message)
                    .doesNotContain(forbidden);
            }
        });
    }

    @Test
    void noProtocolErrorGivesAwayHowTheSystemIsBuiltEither() {
        List<String> messages = new ArrayList<>();
        String token = tokenFor("admin-alpha");

        messages.add(protocolErrorMessage(HttpRequest.POST("/mcp", """
            {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}""")
            .contentType(MediaType.APPLICATION_JSON)
            .header("MCP-Protocol-Version", "2026-07-28")
            .header("Mcp-Method", "tools/call")
            .header("Authorization", "Bearer " + token)));

        messages.add(protocolErrorMessage(HttpRequest.POST("/mcp", """
            {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}""")
            .contentType(MediaType.APPLICATION_JSON)
            .header("MCP-Protocol-Version", "1999-01-01")
            .header("Mcp-Method", "tools/list")
            .header("Authorization", "Bearer " + token)));

        messages.add(protocolErrorMessage(HttpRequest.POST("/mcp", """
            {"jsonrpc":"2.0","id":1,"method":"nonsense/method","params":{}}""")
            .contentType(MediaType.APPLICATION_JSON)
            .header("MCP-Protocol-Version", "2026-07-28")
            .header("Mcp-Method", "nonsense/method")
            .header("Authorization", "Bearer " + token)));

        assertThat(messages).allSatisfy(message -> {
            for (String forbidden : FORBIDDEN) {
                assertThat(message)
                    .as("a protocol error must not contain %s; got: %s", forbidden, message)
                    .doesNotContain(forbidden);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private String protocolErrorMessage(io.micronaut.http.MutableHttpRequest<String> request) {
        try {
            http.toBlocking().exchange(request, Map.class);
            return "";
        } catch (HttpClientResponseException e) {
            Map<String, Object> body = (Map<String, Object>) e.getResponse().getBody(Map.class)
                .orElse(Map.of());
            Map<String, Object> error = (Map<String, Object>) body.get("error");
            return error == null ? "" : String.valueOf(error.get("message"));
        }
    }
}
