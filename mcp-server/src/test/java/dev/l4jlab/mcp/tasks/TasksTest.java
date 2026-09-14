package dev.l4jlab.mcp.tasks;

import dev.l4jlab.mcp.McpServerTestBase;
import dev.l4jlab.mcp.TestKeys;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T102–T105, User Story 3: the Tasks extension and the fallback beside it.
 *
 * <p>The timing is driven by hand rather than waited out — the real 30-to-90-second band is asserted
 * against an injected clock in the legacy API's own suite (T099). What is checked here is the shape
 * of the handle, the polling, and that a client which never opted in is still able to follow the run.
 */
class TasksTest extends McpServerTestBase {

    private static final Map<String, Object> WITH_TASKS =
        Map.of("extensions", Map.of("io.modelcontextprotocol/tasks", Map.of()));
    private static final Map<String, Object> WITHOUT_TASKS = Map.of();

    @BeforeEach
    void resetStub() {
        stub().clear();
    }

    // ---------- T102: the handle ----------

    @Test
    void aClientThatDeclaredTheExtensionGetsATaskHandleImmediately() {
        long before = System.nanoTime();
        Map<String, Object> result = startRun(WITH_TASKS);
        long elapsedMs = (System.nanoTime() - before) / 1_000_000;

        assertThat(result).containsEntry("resultType", "task");
        assertThat((String) result.get("taskId")).startsWith("tsk_");
        assertThat(result).containsEntry("status", "working");
        assertThat(result).containsKey("createdAt").containsKey("lastUpdatedAt");
        assertThat(result).containsKey("ttlMs").containsKey("pollIntervalMs");

        // SC-003. Generous for a unit test, but it would catch a tool that waited for the run.
        assertThat(elapsedMs).isLessThan(1000);
    }

    // ---------- T103: polling to a terminal state ----------

    @Test
    void pollingReportsProgressAndThenTheFinalResult() {
        String taskId = (String) startRun(WITH_TASKS).get("taskId");

        Map<String, Object> working = tasksGet(taskId, WITH_TASKS);
        assertThat(working).containsEntry("status", "working");
        assertThat((String) working.get("statusMessage")).contains("accounts");
        assertThat(working).doesNotContainKey("result");

        stub().setRunState("COMPLETED", null);

        Map<String, Object> completed = tasksGet(taskId, WITH_TASKS);
        assertThat(completed).containsEntry("status", "completed");

        @SuppressWarnings("unchecked")
        Map<String, Object> finalResult = (Map<String, Object>) completed.get("result");
        // What start_billing_run would have returned had it been able to wait.
        assertThat(finalResult).containsEntry("run_id", "run-started");
        assertThat(finalResult).containsEntry("status", "COMPLETED");
        assertThat(finalResult).containsEntry("accounts_processed", 24);
    }

    @Test
    void aTerminalTaskStaysTerminal() {
        String taskId = (String) startRun(WITH_TASKS).get("taskId");
        stub().setRunState("COMPLETED", null);
        assertThat(tasksGet(taskId, WITH_TASKS)).containsEntry("status", "completed");

        // Even if the underlying run were somehow reported as running again, the task does not go
        // backwards: a poller that saw "completed" has already stopped.
        stub().setRunState("RUNNING", "POSTING");
        assertThat(tasksGet(taskId, WITH_TASKS)).containsEntry("status", "completed");
    }

    @Test
    void aFailedRunSurfacesAsAFailedTaskWithItsReason() {
        String taskId = (String) startRun(WITH_TASKS).get("taskId");
        stub().setRunState("FAILED", null);

        Map<String, Object> failed = tasksGet(taskId, WITH_TASKS);
        assertThat(failed).containsEntry("status", "failed");
        assertThat(failed).containsKey("error");
    }

    // ---------- T104: cancellation, and who may poll ----------

    @Test
    void cancellingALiveRunDrivesItToCanceled() {
        String taskId = (String) startRun(WITH_TASKS).get("taskId");

        assertThat(tasksCancel(taskId, WITH_TASKS)).containsEntry("resultType", "complete");
        assertThat(tasksGet(taskId, WITH_TASKS)).containsEntry("status", "cancelled");
    }

    @Test
    void cancellingAFinishedRunIsAcknowledgedAndChangesNothing() {
        String taskId = (String) startRun(WITH_TASKS).get("taskId");
        stub().setRunState("COMPLETED", null);
        tasksGet(taskId, WITH_TASKS);

        // Cooperative, as the extension requires: a client that cancelled a moment too late did
        // nothing wrong, and telling it otherwise would be noise.
        assertThat(tasksCancel(taskId, WITH_TASKS)).containsEntry("resultType", "complete");
        assertThat(tasksGet(taskId, WITH_TASKS)).containsEntry("status", "completed");
    }

    @Test
    void aClientOnTheFallbackPathCanStillCancel() {
        Map<String, Object> handle = structured(startRun(WITHOUT_TASKS));
        String taskId = (String) handle.get("task_id");

        // It holds the handle and has no sixth tool to cancel with (FR-022, FR-031). Refusing here
        // would leave it unable to stop a run it started.
        assertThat(tasksCancel(taskId, WITHOUT_TASKS)).containsEntry("resultType", "complete");
    }

    @Test
    void anotherCallersTaskIsNotVisible() {
        String taskId = (String) startRun(WITH_TASKS).get("taskId");

        Map<String, Object> result = tasksGetAs(TestKeys.ADVISOR_101, taskId, WITH_TASKS);
        assertThat(result).containsEntry("isError", true);
        // The same message an unknown id gets: learning that a task exists but is not yours is
        // already learning something.
        assertThat(textOf(result)).contains("No such task");
    }

    @Test
    void anUnknownTaskSaysSoInAWayTheModelCanActOn() {
        Map<String, Object> result = tasksGet("tsk_does_not_exist", WITH_TASKS);
        assertThat(result).containsEntry("isError", true);
        assertThat(textOf(result)).contains("Start the operation again");
    }

    @Test
    void aClientThatNeverDeclaredTheExtensionCannotPollWithIt() {
        String taskId = (String) startRun(WITH_TASKS).get("taskId");

        Map<String, Object> error = tasksGetExpectingError(taskId, WITHOUT_TASKS);
        assertThat(error.get("code")).isEqualTo(-32021);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) error.get("data");
        assertThat((List<Object>) data.get("requiredCapabilities"))
            .contains((Object) "extensions.io.modelcontextprotocol/tasks");
    }

    // ---------- T105: the documented fallback ----------

    @Test
    void aClientWithoutTheExtensionStillGetsAHandleAndIsToldWhatToPoll() {
        Map<String, Object> result = startRun(WITHOUT_TASKS);

        // Never a task result: the specification forbids sending one to a client that did not opt in.
        assertThat(result).containsEntry("resultType", "complete");

        Map<String, Object> handle = structured(result);
        assertThat((String) handle.get("task_id")).startsWith("tsk_");
        assertThat(handle).containsEntry("run_id", "run-started");
        assertThat(handle).containsEntry("poll_with", "get_billing_run_status");
        assertThat((String) handle.get("next_step_hint")).contains("Poll get_billing_run_status");
    }

    // ---------- helpers ----------

    @SuppressWarnings("unchecked")
    private Map<String, Object> startRun(Map<String, Object> capabilities) {
        String body = """
            {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{\
            "name":"start_billing_run",\
            "arguments":{"firm_id":"firm-alpha","executed_by_advisor_id":"adv-101"},\
            "_meta":{"io.modelcontextprotocol/clientCapabilities":%s}}}"""
            .formatted(writeJson(capabilities));
        Map<String, Object> response = http.toBlocking().retrieve(
            post(body, "tools/call", "start_billing_run", TestKeys.ADMIN_ALPHA), Map.class);
        return (Map<String, Object>) response.get("result");
    }

    private Map<String, Object> tasksGet(String taskId, Map<String, Object> capabilities) {
        return tasksGetAs(TestKeys.ADMIN_ALPHA, taskId, capabilities);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> tasksGetAs(TestKeys.Fixture who, String taskId,
                                           Map<String, Object> capabilities) {
        Map<String, Object> response = http.toBlocking()
            .retrieve(post(taskBody("tasks/get", taskId, capabilities), "tasks/get", null, who),
                Map.class);
        return (Map<String, Object>) response.get("result");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> tasksGetExpectingError(String taskId,
                                                       Map<String, Object> capabilities) {
        var thrown = org.junit.jupiter.api.Assertions.assertThrows(
            io.micronaut.http.client.exceptions.HttpClientResponseException.class,
            () -> http.toBlocking().exchange(
                post(taskBody("tasks/get", taskId, capabilities), "tasks/get", null,
                    TestKeys.ADMIN_ALPHA), Map.class));
        Map<String, Object> body = (Map<String, Object>) thrown.getResponse().getBody(Map.class)
            .orElseThrow();
        return (Map<String, Object>) body.get("error");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> tasksCancel(String taskId, Map<String, Object> capabilities) {
        Map<String, Object> response = http.toBlocking()
            .retrieve(post(taskBody("tasks/cancel", taskId, capabilities), "tasks/cancel", null,
                TestKeys.ADMIN_ALPHA), Map.class);
        return (Map<String, Object>) response.get("result");
    }

    private String taskBody(String method, String taskId, Map<String, Object> capabilities) {
        return """
            {"jsonrpc":"2.0","id":1,"method":"%s","params":{"taskId":"%s",\
            "_meta":{"io.modelcontextprotocol/clientCapabilities":%s}}}"""
            .formatted(method, taskId, writeJson(capabilities));
    }

    private io.micronaut.http.MutableHttpRequest<String> post(String body, String method,
                                                              String name, TestKeys.Fixture who) {
        var request = HttpRequest.POST("/mcp", body)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Accept", "application/json, text/event-stream")
            .header("MCP-Protocol-Version", "2026-07-28")
            .header("Mcp-Method", method)
            .header("Authorization", "Bearer " + TestKeys.valid(who, TestKeys.AUDIENCE_MCP));
        return name == null ? request : request.header("Mcp-Name", name);
    }

    private String writeJson(Object value) {
        try {
            return new String(server.getApplicationContext()
                .getBean(io.micronaut.json.JsonMapper.class).writeValueAsBytes(value));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> structured(Map<String, Object> result) {
        assertThat(result.get("isError"))
            .as("expected a successful tool result, got: " + result).isNotEqualTo(true);
        return (Map<String, Object>) result.get("structuredContent");
    }

    @SuppressWarnings("unchecked")
    private static String textOf(Map<String, Object> result) {
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        assertThat(content).isNotEmpty();
        return (String) content.get(0).get("text");
    }
}
