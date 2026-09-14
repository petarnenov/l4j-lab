package dev.l4jlab.mcp.tools;

import dev.l4jlab.mcp.legacy.LegacyBillingClient;
import dev.l4jlab.mcp.legacy.LegacyErrorTranslator;
import dev.l4jlab.mcp.protocol.CursorCodec;
import dev.l4jlab.mcp.protocol.RequestContext;
import io.micronaut.core.type.Argument;
import io.micronaut.mcp.annotations.Tool;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.mcp.annotations.ToolArg;
import io.modelcontextprotocol.common.McpTransportContext;
import dev.l4jlab.mcp.protocol.ToolFailure;
import jakarta.inject.Singleton;

import io.micronaut.core.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The read-only billing run tools (FR-018).
 *
 * <p>The four hints on each {@code @Tool} are declarations, not code, because
 * {@code micronaut-mcp-annotations} carries them — which is the whole reason this feature builds on
 * the Micronaut integration (research.md R-014).
 *
 * <p>Entitlement is decided nowhere in this class. The legacy API decides it from the exchanged
 * token, and a 403 becomes a short "no access" (FR-014). Duplicating the rule here would create a
 * second place for it to drift.
 */
@Singleton
public class BillingRunTools {

    /** The cap FR-017 sets. A larger page is clamped, never refused: a model that asked for 100
     * wanted as many as it could get, and an error teaches it nothing it can act on. */
    private static final int MAX_PAGE_SIZE = 20;

    /** The cap on per-household failures. Same reasoning. */
    private static final int MAX_FAILURE_LIMIT = 50;

    private final LegacyBillingClient legacy;
    private final CursorCodec cursors;

    public BillingRunTools(LegacyBillingClient legacy, CursorCodec cursors) {
        this.legacy = legacy;
        this.cursors = cursors;
    }

    /** The legacy run as it arrives; mapped to the purpose-built result before anyone sees it. */
    @Serdeable
    record LegacyRun(String runId, String firmId, String executedByAdvisorId, String status,
                     String phase, int accountsProcessed, int accountsTotal, String failureReason,
                     String startedAt) {
    }

    /**
     * A page from the legacy API: its items, and the caller's own total.
     *
     * <p>Two concrete records rather than one generic {@code Page<T>}: Micronaut Serde deserializes
     * a generic record's element type unreliably here, and the failure is a 500 with nothing useful
     * in it. Two small records cost less than that debugging session costs twice.
     */
    @Serdeable
    record LegacyRunPage(List<LegacyRun> items, int totalCount) {
    }

    @Serdeable
    record LegacyFailurePage(List<LegacyFailure> items, int totalCount) {
    }

    @Serdeable
    record LegacyFailure(String householdId, String householdName, String cause) {
    }

    @Tool(
        name = "get_billing_run_status",
        title = "Get billing run status",
        description = """
            Return the current status of one billing run: its status, the phase it is in, how many \
            accounts it has processed out of the total, and the failure reason if it failed. When \
            the run has failed, the result names get_run_failures as the next step.""",
        annotations = @Tool.ToolAnnotations(
            // Reads only, repeating it changes nothing, and the data lives in a system this server
            // does not control. FR-011 asks for honesty, and this is it.
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true,
            openWorldHint = true))
    public BillingRunStatus getBillingRunStatus(
        @ToolArg(name = "run_id", description = "The run to look up.") String runId,
        McpTransportContext transport) {

        RequestContext context = required(transport);
        var outcome = legacy.get("/api/v1/billing-runs/" + runId, Map.of(),
            context.inboundToken(), context.traceparent(), Argument.of(LegacyRun.class));

        if (!(outcome instanceof LegacyBillingClient.Outcome.Ok<LegacyRun> ok)) {
            throw LegacyErrorTranslator.asToolFailure(outcome);
        }
        LegacyRun run = ok.value();
        return new BillingRunStatus(
            run.runId(),
            RunStatus.valueOf(run.status()),
            run.phase() == null ? null : RunPhase.valueOf(run.phase()),
            run.accountsProcessed(),
            run.accountsTotal(),
            run.failureReason(),
            // The next-step hint exists only when there is a next step. A hint that is always
            // present is noise the model learns to ignore.
            "FAILED".equals(run.status())
                ? "This run failed. Call get_run_failures with the same run_id to see which "
                  + "households could not be processed and why."
                : null);
    }

    @Tool(
        name = "search_billing_runs",
        title = "Search billing runs",
        description = """
            Search billing runs for a firm by status, executing advisor, and start-date range. \
            Returns one page of at most 20 runs. When more matches exist, the result carries an \
            opaque cursor; pass it back as the cursor argument to get the next page. Results are \
            limited to what the caller is entitled to see.""",
        annotations = @Tool.ToolAnnotations(
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true,
            openWorldHint = true))
    public BillingRunSearchResult searchBillingRuns(
        @ToolArg(name = "firm_id", description = "Firm whose runs to search.") String firmId,
        @ToolArg(name = "status", description = "Optional. Only runs in this status.")
        @Nullable String status,
        @ToolArg(name = "advisor_id", description = "Optional. Only runs executed by this advisor.")
        @Nullable String advisorId,
        @ToolArg(name = "started_from", description = "Optional. Only runs started on or after this date.")
        @Nullable String startedFrom,
        @ToolArg(name = "started_to", description = "Optional. Only runs started on or before this date.")
        @Nullable String startedTo,
        @ToolArg(name = "page_size", description = "Runs per page. Values above 20 are clamped to 20.")
        @Nullable Integer pageSize,
        @ToolArg(name = "cursor", description = "Opaque cursor from a previous result. Do not construct or modify it.")
        @Nullable String cursor,
        McpTransportContext transport) {

        RequestContext context = required(transport);
        int size = pageSize == null ? MAX_PAGE_SIZE : Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);

        // The predicate every page of this search must agree on. A cursor carries its digest, so it
        // cannot be replayed against a different search (research.md R-009).
        String predicate = String.join("\u0000", firmId, nullToEmpty(status), nullToEmpty(advisorId),
            nullToEmpty(startedFrom), nullToEmpty(startedTo));
        int offset = cursor == null
            ? 0
            : cursors.open(cursor, predicate, context.principal().userId()).offset();

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("firmId", firmId);
        query.put("status", status);
        query.put("advisorId", advisorId);
        query.put("startedFrom", startedFrom);
        query.put("startedTo", startedTo);
        query.put("offset", offset);
        query.put("limit", size);

        var outcome = legacy.get("/api/v1/billing-runs", query, context.inboundToken(),
            context.traceparent(),
            Argument.of(LegacyRunPage.class));
        if (!(outcome instanceof LegacyBillingClient.Outcome.Ok<?> ok)) {
            throw LegacyErrorTranslator.asToolFailure(outcome);
        }
        LegacyRunPage page = (LegacyRunPage) ok.value();

        // Never dereferenced directly: the system of record is outside this server's control — every
        // one of these tools declares openWorldHint: true — and it omitted `items` from an empty page,
        // so an ordinary query with no matches crashed the tool (feature 010, research R-001). A client
        // of a system it does not own does not assume a field is present because a record says it is.
        List<BillingRunSummary> runs = orEmpty(page.items()).stream()
            .map(r -> new BillingRunSummary(r.runId(), r.firmId(), r.executedByAdvisorId(),
                RunStatus.valueOf(r.status()), r.startedAt()))
            .toList();
        int nextOffset = offset + runs.size();
        boolean truncated = nextOffset < page.totalCount();

        return new BillingRunSearchResult(
            runs,
            page.totalCount(),
            truncated,
            truncated ? cursors.mint(predicate, nextOffset, context.principal().userId()) : null,
            truncated
                ? "More runs match than fit on one page. Narrow by status, advisor_id, or a "
                  + "start-date range, or page on with the cursor."
                : null);
    }

    @Tool(
        name = "get_run_failures",
        title = "Get billing run failures",
        description = """
            For a failed billing run, list the households the run could not process and why. The \
            list is capped; total_count says how many failures exist in all.""",
        annotations = @Tool.ToolAnnotations(
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true,
            openWorldHint = true))
    public RunFailuresResult getRunFailures(
        @ToolArg(name = "run_id", description = "The failed run to inspect.") String runId,
        @ToolArg(name = "limit", description = "Maximum households to return. Values above 50 are clamped to 50.")
        @Nullable Integer limit,
        McpTransportContext transport) {

        RequestContext context = required(transport);
        int capped = limit == null ? MAX_FAILURE_LIMIT
            : Math.min(Math.max(limit, 1), MAX_FAILURE_LIMIT);

        var outcome = legacy.get("/api/v1/billing-runs/" + runId + "/failures",
            Map.of("limit", capped), context.inboundToken(), context.traceparent(),
            Argument.of(LegacyFailurePage.class));
        if (!(outcome instanceof LegacyBillingClient.Outcome.Ok<?> ok)) {
            throw LegacyErrorTranslator.asToolFailure(outcome);
        }
        LegacyFailurePage page = (LegacyFailurePage) ok.value();

        List<RunFailure> failures = orEmpty(page.items()).stream()
            .map(f -> new RunFailure(f.householdId(), f.householdName(), f.cause()))
            .toList();
        return new RunFailuresResult(runId, failures, page.totalCount(),
            failures.size() < page.totalCount());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static RequestContext required(McpTransportContext transport) {
        RequestContext context = RequestContext.from(transport);
        if (context == null) {
            throw new ToolFailure("No authenticated caller.");
        }
        return context;
    }

    /**
     * A collection read from the system of record, never null.
     *
     * <p>Feature 010, FR-001. The legacy API omitted an empty {@code items}, this class dereferenced
     * it, and a date range matching nothing answered HTTP 500. That serialiser is fixed too — but a
     * client of a system it does not control should not have needed the fix to be safe.
     */
    private static <T> List<T> orEmpty(List<T> maybe) {
        return maybe == null ? List.of() : maybe;
    }
}
