package dev.l4jlab.mcp.topology;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T106–T109: a long-running operation followed to the end, across replicas.
 *
 * <p>The stack runs with {@code LEGACY_RUN_DURATION_MS} compressed, because the real 30-to-90-second
 * band is a property of the domain simulation and is asserted against an injected clock in the
 * legacy API's own suite. Waiting it out here would buy no additional confidence and cost minutes.
 */
class TasksTopologyTest extends TopologyFixture {

    private static final String WITH_TASKS = """
        {"extensions":{"io.modelcontextprotocol/tasks":{}}}""";
    private static final String WITHOUT_TASKS = "{}";

    private static final String START_ARGUMENTS = """
        {"firm_id":"firm-alpha","executed_by_advisor_id":"adv-101"}""";

    /** Generous, but it would still catch a tool that waited for the run instead of handing back. */
    private static final Duration HANDLE_BUDGET = Duration.ofSeconds(1);

    @Test
    void theHandleComesBackImmediatelyAndPollingReachesTheFinalStatus() {
        String token = tokenFor("ops-alpha");

        Instant before = Instant.now();
        Map<String, Object> created = callTool(proxy, "start_billing_run", START_ARGUMENTS, token,
            WITH_TASKS);
        Duration elapsed = Duration.between(before, Instant.now());

        assertThat(created).containsEntry("resultType", "task");
        assertThat(created).containsEntry("status", "working");
        String taskId = (String) created.get("taskId");
        assertThat(taskId).startsWith("tsk_");

        // SC-003, measured where the specification measures it: at the client.
        assertThat(elapsed).isLessThan(HANDLE_BUDGET);

        Map<String, Object> terminal = pollToTerminal(proxy, taskId, token);
        assertThat(terminal).containsEntry("status", "completed");

        @SuppressWarnings("unchecked")
        Map<String, Object> finalResult = (Map<String, Object>) terminal.get("result");
        assertThat(finalResult).containsEntry("status", "COMPLETED");
        assertThat(finalResult.get("run_id")).isNotNull();

        // The final status must agree with the system of record, not merely with the task row.
        Map<String, Object> fromLegacy = structured(callTool(proxy, "get_billing_run_status", """
            {"run_id":"%s"}""".formatted(finalResult.get("run_id")), token));
        assertThat(fromLegacy).containsEntry("status", "COMPLETED");
        assertThat(fromLegacy.get("accounts_processed")).isEqualTo(finalResult.get("accounts_processed"));
    }

    @Test
    void everyReplicaAnswersThePollIdentically() {
        String token = tokenFor("ops-alpha");
        String taskId = (String) callTool(replicaA, "start_billing_run", START_ARGUMENTS, token,
            WITH_TASKS).get("taskId");

        // No replica owns a task: the row is shared and the status is refreshed from the run on
        // every poll, so a round-robin proxy cannot produce a contradiction (FR-007).
        for (URI replica : List.of(replicaA, replicaB, replicaC)) {
            Map<String, Object> task = tasksGet(replica, taskId, token, WITH_TASKS);
            assertThat(task.get("taskId")).as("replica %s", replica).isEqualTo(taskId);
            assertThat(task.get("status")).isIn("working", "completed");
        }

        Map<String, Object> onB = pollToTerminal(replicaB, taskId, token);
        assertThat(onB).containsEntry("status", "completed");

        // Terminal on B means terminal on C: the state is in the database, not in a replica.
        assertThat(tasksGet(replicaC, taskId, token, WITH_TASKS))
            .containsEntry("status", "completed");
    }

    @Test
    void cancellingOnOneReplicaStopsARunStartedOnAnother() {
        String token = tokenFor("ops-alpha");
        Map<String, Object> created = callTool(replicaA, "start_billing_run", START_ARGUMENTS,
            token, WITH_TASKS);
        String taskId = (String) created.get("taskId");

        // Cancelled from a different replica than the one that started it (FR-031).
        send(replicaC, mcp("tasks/cancel", null, """
            {"jsonrpc":"2.0","id":1,"method":"tasks/cancel","params":{"taskId":"%s",\
            "_meta":{"io.modelcontextprotocol/clientCapabilities":%s}}}"""
            .formatted(taskId, WITH_TASKS), token));

        Map<String, Object> task = tasksGet(replicaB, taskId, token, WITH_TASKS);
        assertThat(task).containsEntry("status", "cancelled");
    }

    @Test
    void aClientWithoutTheExtensionFollowsTheRunThroughTheFallback() {
        String token = tokenFor("ops-alpha");

        Map<String, Object> result = callTool(proxy, "start_billing_run", START_ARGUMENTS, token,
            WITHOUT_TASKS);

        // Never a task result for a client that did not opt in, and never a dead end either.
        assertThat(result).containsEntry("resultType", "complete");
        Map<String, Object> handle = structured(result);
        assertThat(handle).containsEntry("poll_with", "get_billing_run_status");
        String runId = (String) handle.get("run_id");

        String status = null;
        Instant deadline = Instant.now().plusSeconds(30);
        while (Instant.now().isBefore(deadline)) {
            status = (String) structured(callTool(proxy, "get_billing_run_status", """
                {"run_id":"%s"}""".formatted(runId), token)).get("status");
            if (List.of("COMPLETED", "FAILED", "CANCELED").contains(status)) {
                break;
            }
            sleep(500);
        }
        assertThat(status).isEqualTo("COMPLETED");
    }

    // ---------- helpers ----------

    private static Map<String, Object> tasksGet(URI target, String taskId, String token,
                                                String capabilities) {
        return send(target, mcp("tasks/get", null, """
            {"jsonrpc":"2.0","id":1,"method":"tasks/get","params":{"taskId":"%s",\
            "_meta":{"io.modelcontextprotocol/clientCapabilities":%s}}}"""
            .formatted(taskId, capabilities), token));
    }

    private static Map<String, Object> pollToTerminal(URI target, String taskId, String token) {
        Instant deadline = Instant.now().plusSeconds(30);
        Map<String, Object> task = null;
        while (Instant.now().isBefore(deadline)) {
            task = tasksGet(target, taskId, token, WITH_TASKS);
            if (List.of("completed", "failed", "cancelled").contains(task.get("status"))) {
                return task;
            }
            // The server suggests a polling interval; a client that ignores it is a client that
            // hammers the system of record.
            sleep(((Number) task.getOrDefault("pollIntervalMs", 500)).longValue());
        }
        throw new AssertionError("task did not reach a terminal state in 30s: " + task);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
