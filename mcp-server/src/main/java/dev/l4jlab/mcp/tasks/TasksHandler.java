package dev.l4jlab.mcp.tasks;

import dev.l4jlab.mcp.legacy.LegacyBillingClient;
import dev.l4jlab.mcp.legacy.LegacyErrorTranslator;
import dev.l4jlab.mcp.protocol.RequestContext;
import dev.l4jlab.mcp.protocol.ToolFailure;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Tasks extension's own methods (FR-021, FR-031).
 *
 * <p>The module knows nothing of {@code tasks/get}, {@code tasks/cancel} or {@code tasks/update} —
 * the extension postdates it — so these are answered alongside {@code server/discover} in
 * {@link dev.l4jlab.mcp.protocol.McpRequestGate}. Retired by java-sdk#1013 (SEP-2663).
 *
 * <p>Status is refreshed from the legacy run on each poll rather than by a background worker. That
 * is not a shortcut: it means no replica owns a task, which is the property FR-007 needs. A worker
 * would have to run somewhere, and "somewhere" is exactly what a stateless design does not have.
 */
@Singleton
public class TasksHandler {

    private static final String TASKS_EXTENSION = "io.modelcontextprotocol/tasks";

    private final TaskStore tasks;
    private final LegacyBillingClient legacy;
    private final JsonMapper json;

    public TasksHandler(TaskStore tasks, LegacyBillingClient legacy, JsonMapper json) {
        this.tasks = tasks;
        this.legacy = legacy;
        this.json = json;
    }

    /**
     * The legacy run as it arrives. Every field is declared, including the ones this handler
     * ignores: Micronaut Serde refuses a payload with properties the record does not name, and the
     * symptom is an "unavailable" tool error that looks like a network problem.
     */
    @Serdeable
    record LegacyRun(String runId, String firmId, String executedByAdvisorId, String status,
                     @io.micronaut.core.annotation.Nullable String phase, int accountsProcessed,
                     int accountsTotal, @io.micronaut.core.annotation.Nullable String failureReason,
                     @io.micronaut.core.annotation.Nullable String startedAt,
                     @io.micronaut.core.annotation.Nullable String finishedAt) {
    }

    /** {@code tasks/get}: the current state, refreshed from the run it is tracking. */
    public Map<String, Object> get(RequestContext context, Map<String, Object> params) {
        requireExtension(context);
        TaskStore.Task task = tasks.requireOwn(taskIdOf(params), context.principal().userId());
        TaskStore.Task refreshed = task.isTerminal() ? task : refresh(context, task);

        Map<String, Object> result = wire(refreshed);
        if ("completed".equals(refreshed.status())) {
            // On a terminal success the result is what the original call would have returned
            // synchronously — which is the entire point of having waited.
            tasks.resultJson(refreshed.taskId()).ifPresent(stored -> result.put("result", parse(stored)));
        } else if ("failed".equals(refreshed.status())) {
            result.put("error", Map.of("code", -32603, "message",
                refreshed.statusMessage() == null ? "The run failed." : refreshed.statusMessage()));
        }
        return result;
    }

    /**
     * {@code tasks/cancel} (FR-031): asks the legacy API to cancel the run.
     *
     * <p>Cooperative, as the extension requires. A run that has already finished keeps its status and
     * the request still succeeds — refusing it would make a client that cancelled a moment too late
     * look like a client that did something wrong.
     *
     * <p>Accepted from any caller holding the handle, whether or not it declared the extension: the
     * extension governs what the server may <em>return</em>, not what it may accept, and a client on
     * the fallback path was given a handle and has no sixth tool to cancel with (FR-022).
     */
    public Map<String, Object> cancel(RequestContext context, Map<String, Object> params) {
        TaskStore.Task task = tasks.requireOwn(taskIdOf(params), context.principal().userId());
        if (task.isTerminal()) {
            return Map.of();
        }
        var outcome = legacy.post("/api/v1/billing-runs/" + task.legacyRunId() + "/cancel",
            Map.of(), context.inboundToken(), context.traceparent(), Argument.of(LegacyRun.class));
        if (!(outcome instanceof LegacyBillingClient.Outcome.Ok<?> ok)) {
            throw LegacyErrorTranslator.asToolFailure(outcome);
        }
        LegacyRun run = (LegacyRun) ok.value();
        tasks.update(task.taskId(), "CANCELED".equals(run.status()) ? "cancelled" : "working",
            "Cancellation requested.", null, null);
        return Map.of();
    }

    /**
     * {@code tasks/update}: accepts input responses and acknowledges with an empty result.
     *
     * <p>Implemented for completeness rather than need — no tool here moves a task to
     * {@code input_required}, because the only confirmation this server asks for happens inside a
     * single tool call via MRTR. Unknown keys are ignored, as the extension requires, so a client
     * that answers a request that has already been satisfied is not punished for it.
     */
    public Map<String, Object> update(RequestContext context, Map<String, Object> params) {
        requireExtension(context);
        tasks.requireOwn(taskIdOf(params), context.principal().userId());
        return Map.of();
    }

    /** Reads the run this task tracks and moves the task to match. */
    private TaskStore.Task refresh(RequestContext context, TaskStore.Task task) {
        var outcome = legacy.get("/api/v1/billing-runs/" + task.legacyRunId(), Map.of(),
            context.inboundToken(), context.traceparent(), Argument.of(LegacyRun.class));
        if (!(outcome instanceof LegacyBillingClient.Outcome.Ok<?> ok)) {
            // The task is not failed — the billing system is merely unreachable right now. Saying so
            // lets the client keep polling instead of abandoning a run that may be fine.
            throw LegacyErrorTranslator.asToolFailure(outcome);
        }
        LegacyRun run = (LegacyRun) ok.value();
        String status = switch (run.status()) {
            case "COMPLETED" -> "completed";
            case "FAILED" -> "failed";
            case "CANCELED" -> "cancelled";
            default -> "working";
        };
        String message = "failed".equals(status) && run.failureReason() != null
            ? run.failureReason()
            : "%s, %d/%d accounts".formatted(
                run.phase() == null ? run.status().toLowerCase(java.util.Locale.ROOT) : run.phase(),
                run.accountsProcessed(), run.accountsTotal());

        String resultJson = "completed".equals(status) ? writeJson(finalResult(run)) : null;
        tasks.update(task.taskId(), status, message, resultJson, null);
        return tasks.requireOwn(task.taskId(), context.principal().userId());
    }

    /** The shape {@code start_billing_run} would have returned had it been able to wait. */
    private static Map<String, Object> finalResult(LegacyRun run) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("run_id", run.runId());
        result.put("status", run.status());
        result.put("phase", run.phase());
        result.put("accounts_processed", run.accountsProcessed());
        result.put("accounts_total", run.accountsTotal());
        result.put("failure_reason", run.failureReason());
        return result;
    }

    private static Map<String, Object> wire(TaskStore.Task task) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", task.taskId());
        result.put("status", task.status());
        if (task.statusMessage() != null) {
            result.put("statusMessage", task.statusMessage());
        }
        result.put("createdAt", task.createdAt().toString());
        result.put("lastUpdatedAt", task.lastUpdatedAt().toString());
        result.put("ttlMs", task.ttlMs());
        if (task.pollIntervalMs() != null) {
            result.put("pollIntervalMs", task.pollIntervalMs());
        }
        return result;
    }

    private static String taskIdOf(Map<String, Object> params) {
        if (params.get("taskId") instanceof String taskId && !taskId.isBlank()) {
            return taskId;
        }
        throw new ToolFailure("taskId is required.");
    }

    /**
     * The server must not serve extension methods to a client that did not declare it; doing so
     * would let a client receive a shape it has told us it cannot read.
     */
    private static void requireExtension(RequestContext context) {
        if (!context.declaresExtension(TASKS_EXTENSION)) {
            throw new MissingCapability(List.of("extensions." + TASKS_EXTENSION));
        }
    }

    /** Becomes {@code -32021} with {@code data.requiredCapabilities}. */
    public static final class MissingCapability extends RuntimeException {

        private final transient List<String> required;

        public MissingCapability(List<String> required) {
            super("Missing required client capability");
            this.required = List.copyOf(required);
        }

        public List<String> required() {
            return required;
        }
    }

    private Map<String, Object> parse(String stored) {
        try {
            return json.readValue(stored, Argument.mapOf(String.class, Object.class));
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return new String(json.writeValueAsBytes(value), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
