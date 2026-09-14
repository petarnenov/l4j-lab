package dev.l4jlab.mcp.tools;

import dev.l4jlab.mcp.legacy.LegacyBillingClient;
import dev.l4jlab.mcp.legacy.LegacyErrorTranslator;
import dev.l4jlab.mcp.ops.OperationRecords;
import dev.l4jlab.mcp.protocol.InputRequired;
import dev.l4jlab.mcp.protocol.RequestContext;
import dev.l4jlab.mcp.protocol.RequestStateCodec;
import dev.l4jlab.mcp.protocol.ToolFailure;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.mcp.annotations.Tool;
import io.micronaut.mcp.annotations.ToolArg;
import io.micronaut.serde.annotation.Serdeable;
import io.modelcontextprotocol.common.McpTransportContext;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adjusting the fee on one account (FR-020).
 *
 * <p>The only destructive tool this server exposes, and the only one that needs three mechanisms at
 * once, each answering a question the others do not:
 *
 * <ul>
 *   <li><b>Confirmation</b> (Multi Round-Trip Requests): the first call does not act. It describes
 *       the exact change and asks the user to approve it. A model proposing a fee change is not the
 *       same as a person agreeing to one.</li>
 *   <li><b>Sealed request state</b>: proves the confirmation that comes back belongs to <em>this</em>
 *       proposal, by <em>this</em> caller, recently. Without it a confirmation of +15 bps could
 *       execute +150.</li>
 *   <li><b>The operation record</b>: makes execution at-most-once across all three replicas. The
 *       specification is explicit that request state bounds replay but does not make it one-shot, so
 *       this is not redundant with the seal — it is the part the seal cannot do.</li>
 * </ul>
 */
@Singleton
public class FeeAdjustmentTool {

    private final LegacyBillingClient legacy;
    private final RequestStateCodec requestState;
    private final OperationRecords operations;
    private final JsonMapper json;

    public FeeAdjustmentTool(LegacyBillingClient legacy, RequestStateCodec requestState,
                             OperationRecords operations, JsonMapper json) {
        this.legacy = legacy;
        this.requestState = requestState;
        this.operations = operations;
        this.json = json;
    }

    @Serdeable
    record LegacyAdjustmentRequest(String accountId, int deltaBps, String effectiveDate,
                                   @Nullable String reason) {
    }

    @Serdeable
    record LegacyAdjustmentResult(String legacyReferenceId, String accountId, int deltaBps,
                                  String effectiveDate, int newFeeBps) {
    }

    @Tool(
        name = "post_fee_adjustment",
        title = "Post a fee adjustment",
        description = """
            Change the fee on one account by a signed amount in basis points, effective on a given \
            date. This writes to the billing system of record. The first call does not execute: it \
            returns a confirmation request describing the exact change. Repeat the call with the \
            confirmation and the returned request state to execute. Supply a stable operation_id; \
            repeating a call with the same operation_id returns the original result and does not \
            adjust the fee a second time.""",
        annotations = @Tool.ToolAnnotations(
            // Writes, and changes an existing account's fee — that is a destructive update.
            // Idempotent *because* of operation_id, not in spite of the write: without the
            // operation record the hint would be a lie (FR-011).
            readOnlyHint = false,
            destructiveHint = true,
            idempotentHint = true,
            openWorldHint = true))
    public FeeAdjustmentResult postFeeAdjustment(
        @ToolArg(name = "operation_id",
            description = "Client-supplied idempotency key. Reuse it for retries of the same change; "
                + "use a new one for a different change.") String operationId,
        @ToolArg(name = "account_id", description = "Account whose fee changes.") String accountId,
        @ToolArg(name = "delta_bps",
            description = "Signed change in basis points. Must not be zero.") int deltaBps,
        @ToolArg(name = "effective_date",
            description = "Date the adjustment takes effect, as YYYY-MM-DD.") String effectiveDate,
        @ToolArg(name = "reason",
            description = "Optional short note recorded with the adjustment.") @Nullable String reason,
        McpTransportContext transport) {

        RequestContext context = required(transport);
        String user = context.principal().userId();
        String digest = digestOf(operationId, accountId, deltaBps, effectiveDate);

        // Asked first, before anything else: a repeat must not re-propose a change that already
        // happened, let alone re-execute it.
        var alreadyDone = operations.findMatching(user, operationId, digest);
        if (alreadyDone.isPresent()) {
            return replayed(alreadyDone.get());
        }

        if (deltaBps == 0) {
            throw new ToolFailure("delta_bps must not be zero: that would change nothing.");
        }

        if (!context.hasInputResponses()) {
            return propose(context, user, operationId, accountId, deltaBps, effectiveDate, digest);
        }

        // Coming back with an answer. The seal is what makes it this proposal's answer.
        if (context.requestState() == null) {
            throw new ToolFailure("The confirmation did not carry the request state it was issued "
                + "with. Call post_fee_adjustment again to propose the change afresh.");
        }
        requestState.open(context.requestState(), user, digest);

        if (!confirmed(context)) {
            // Declining is an outcome, not an error. Nothing was applied, and saying so plainly is
            // more useful to a model than isError.
            throw new ToolFailure("The change was not confirmed, so nothing was applied.");
        }
        return execute(context, user, operationId, accountId, deltaBps, effectiveDate, reason, digest);
    }

    /** The first call: describe the exact change and ask, without touching anything. */
    private FeeAdjustmentResult propose(RequestContext context, String user, String operationId,
                                        String accountId, int deltaBps, String effectiveDate,
                                        String digest) {
        if (!context.declaresElicitation()) {
            // The specification forbids sending an elicitation to a client that did not declare it,
            // and this tool cannot act without one.
            throw new ToolFailure("This tool needs to ask the user to confirm the change, and this "
                + "client did not declare elicitation support.");
        }
        String message = "Confirm: change account %s fee by %s%d bps, effective %s.%s".formatted(
            accountId, deltaBps > 0 ? "+" : "", deltaBps, effectiveDate,
            " Nothing has been changed yet.");
        throw new InputRequired(InputRequired.confirmation(message),
            requestState.seal(user, operationId, digest));
    }

    /** The second call: apply it once, and record that it happened in the same breath. */
    private FeeAdjustmentResult execute(RequestContext context, String user, String operationId,
                                        String accountId, int deltaBps, String effectiveDate,
                                        String reason, String digest) {
        var outcome = legacy.post("/api/v1/fee-adjustments",
            new LegacyAdjustmentRequest(accountId, deltaBps, effectiveDate, reason),
            context.inboundToken(), context.traceparent(),
            Argument.of(LegacyAdjustmentResult.class));

        if (!(outcome instanceof LegacyBillingClient.Outcome.Ok<?> ok)) {
            // Recorded as FAILED so a retry with the same operation id does not quietly try again
            // against a system of record that just refused.
            String failure = LegacyErrorTranslator.message(outcome);
            operations.record(user, operationId, digest, "FAILED", null, writeJson(
                Map.of("message", failure)));
            throw LegacyErrorTranslator.asToolFailure(outcome);
        }
        LegacyAdjustmentResult applied = (LegacyAdjustmentResult) ok.value();

        FeeAdjustmentResult result = new FeeAdjustmentResult(operationId, accountId, deltaBps,
            effectiveDate, applied.legacyReferenceId(), applied.newFeeBps(), user, false);

        // FR-020: the audit row must name who confirmed and what the system of record called it.
        // Left on the request so AuditFilter can pick them up without this tool knowing about audit.
        io.micronaut.http.context.ServerRequestContext.currentRequest().ifPresent(request -> {
            request.setAttribute("l4jlab.confirmedBy", user);
            request.setAttribute("l4jlab.legacyReferenceId", applied.legacyReferenceId());
        });
        operations.record(user, operationId, digest, "SUCCEEDED", applied.legacyReferenceId(),
            writeJson(asMap(result)));
        return result;
    }

    /** A repeat: the original answer, with {@code replayed} telling the caller nothing happened. */
    private FeeAdjustmentResult replayed(OperationRecords.Completed completed) {
        if ("FAILED".equals(completed.outcome())) {
            throw new ToolFailure("That operation already failed against the billing system. "
                + "Do not retry it; use a new operation_id if the change is still wanted.");
        }
        try {
            Map<String, Object> stored = json.readValue(completed.resultJson(),
                Argument.mapOf(String.class, Object.class));
            return new FeeAdjustmentResult(
                (String) stored.get("operation_id"),
                (String) stored.get("account_id"),
                ((Number) stored.get("delta_bps")).intValue(),
                (String) stored.get("effective_date"),
                (String) stored.get("legacy_reference_id"),
                ((Number) stored.get("new_fee_bps")).intValue(),
                (String) stored.get("confirmed_by_user_id"),
                true);
        } catch (Exception e) {
            throw new ToolFailure("That operation already completed, but its result could not be "
                + "read back. Do not retry; check the billing system directly.");
        }
    }

    /** True only for an explicit {@code confirmed: true}; anything else is a refusal. */
    private static boolean confirmed(RequestContext context) {
        Object response = context.inputResponses().get("confirm_adjustment");
        if (!(response instanceof Map<?, ?> map)) {
            return false;
        }
        if (map.get("content") instanceof Map<?, ?> content) {
            return Boolean.TRUE.equals(content.get("confirmed"));
        }
        return false;
    }

    /**
     * Binds the operation id to the change it was issued for, so the same id used for different
     * arguments is caught rather than answered with the first change's result.
     */
    private static String digestOf(String operationId, String accountId, int deltaBps,
                                   String effectiveDate) {
        String material = String.join("/", operationId, accountId, String.valueOf(deltaBps),
            effectiveDate);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(material.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot digest the adjustment", e);
        }
    }

    private static Map<String, Object> asMap(FeeAdjustmentResult result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("operation_id", result.operationId());
        map.put("account_id", result.accountId());
        map.put("delta_bps", result.deltaBps());
        map.put("effective_date", result.effectiveDate());
        map.put("legacy_reference_id", result.legacyReferenceId());
        map.put("new_fee_bps", result.newFeeBps());
        map.put("confirmed_by_user_id", result.confirmedByUserId());
        return map;
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return new String(json.writeValueAsBytes(value), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static RequestContext required(McpTransportContext transport) {
        RequestContext context = RequestContext.from(transport);
        if (context == null) {
            throw new ToolFailure("No authenticated caller.");
        }
        return context;
    }
}
