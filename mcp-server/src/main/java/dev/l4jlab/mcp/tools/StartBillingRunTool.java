package dev.l4jlab.mcp.tools;

import dev.l4jlab.mcp.legacy.LegacyBillingClient;
import dev.l4jlab.mcp.legacy.LegacyErrorTranslator;
import dev.l4jlab.mcp.protocol.RequestContext;
import dev.l4jlab.mcp.protocol.TaskCreated;
import dev.l4jlab.mcp.protocol.ToolFailure;
import dev.l4jlab.mcp.tasks.TaskStore;
import io.micronaut.core.type.Argument;
import io.micronaut.mcp.annotations.Tool;
import io.micronaut.mcp.annotations.ToolArg;
import io.micronaut.serde.annotation.Serdeable;
import io.modelcontextprotocol.common.McpTransportContext;
import jakarta.inject.Singleton;

/**
 * Starting a billing run (FR-021).
 *
 * <p>The run takes 30 to 90 seconds, which is far longer than a tool call should hold a connection
 * open. So the call returns a handle and the client polls — the Tasks extension's whole purpose.
 *
 * <p>Two shapes come out of this tool, and the difference is the client's, not the server's:
 *
 * <ul>
 *   <li>A client that declared {@code io.modelcontextprotocol/tasks} gets a
 *       {@code CreateTaskResult} and polls {@code tasks/get}.</li>
 *   <li>A client that did not gets an ordinary result carrying the same handle plus the name of the
 *       tool to poll. The specification forbids sending a task result to a client that did not opt
 *       in, and a run that cannot be followed is worse than one expressed in core protocol only.</li>
 * </ul>
 *
 * <p>Both return immediately. That is the property SC-003 measures, and it holds either way.
 */
@Singleton
public class StartBillingRunTool {

    private final LegacyBillingClient legacy;
    private final TaskStore tasks;

    public StartBillingRunTool(LegacyBillingClient legacy, TaskStore tasks) {
        this.legacy = legacy;
        this.tasks = tasks;
    }

    @Serdeable
    record StartRunRequest(String firmId, String executedByAdvisorId) {
    }

    @Serdeable
    record StartedRun(String runId, String status, String phase, int accountsProcessed,
                      int accountsTotal) {
    }

    @Tool(
        name = "start_billing_run",
        title = "Start a billing run",
        description = """
            Start a billing run for a firm. The run takes 30 to 90 seconds and advances through four \
            phases. This call returns immediately with a handle rather than waiting. Poll the handle \
            until the run reaches a terminal status. Starting a run writes to the billing system of \
            record.""",
        annotations = @Tool.ToolAnnotations(
            // Writes, but only adds a run; it destroys nothing. Not idempotent: two calls start two
            // runs, and claiming otherwise would invite a client to retry into a duplicate.
            readOnlyHint = false,
            destructiveHint = false,
            idempotentHint = false,
            openWorldHint = true))
    public StartedRunHandle startBillingRun(
        @ToolArg(name = "firm_id", description = "Firm to bill.") String firmId,
        @ToolArg(name = "executed_by_advisor_id",
            description = "Advisor the run is executed for. Must be an advisor the caller may act for.")
        String executedByAdvisorId,
        McpTransportContext transport) {

        RequestContext context = required(transport);

        var outcome = legacy.post("/api/v1/billing-runs",
            new StartRunRequest(firmId, executedByAdvisorId),
            context.inboundToken(), context.traceparent(), Argument.of(StartedRun.class));
        if (!(outcome instanceof LegacyBillingClient.Outcome.Ok<?> ok)) {
            throw LegacyErrorTranslator.asToolFailure(outcome);
        }
        StartedRun run = (StartedRun) ok.value();

        // Committed before the handle is returned: the extension requires it, and it is what lets an
        // immediate poll on a different replica succeed.
        TaskStore.Task task = tasks.create(context.principal().userId(), "start_billing_run",
            run.runId(), "%s, %d/%d accounts".formatted(
                run.phase() == null ? "starting" : run.phase(),
                run.accountsProcessed(), run.accountsTotal()));

        if (context.declaresExtension("io.modelcontextprotocol/tasks")) {
            throw new TaskCreated(task.taskId(), task.status(), task.statusMessage(),
                task.createdAt().toString(), task.lastUpdatedAt().toString(),
                task.ttlMs(), task.pollIntervalMs());
        }
        // The documented fallback: the same handle, expressed in core protocol only.
        return new StartedRunHandle(task.taskId(), run.runId(), "get_billing_run_status",
            "The run has started. Poll get_billing_run_status with this run_id until it reaches a "
                + "terminal status.");
    }

    private static RequestContext required(McpTransportContext transport) {
        RequestContext context = RequestContext.from(transport);
        if (context == null) {
            throw new ToolFailure("No authenticated caller.");
        }
        return context;
    }
}
