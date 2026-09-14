package dev.l4jlab.mcp.audit;

import dev.l4jlab.mcp.protocol.ToolFailureMapper;
import dev.l4jlab.mcp.security.InboundTokenFilter;
import dev.l4jlab.mcp.security.Principal;
import io.micronaut.core.annotation.Order;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.inject.Singleton;

import java.util.Map;

/**
 * One audit row per request (FR-026).
 *
 * <p>A filter rather than an interceptor around the tool methods, for two reasons. The module's
 * {@code @Tool} annotation is not around-advice, so there is nothing to intercept; and more
 * importantly a request rejected at the protocol layer never reaches a tool, yet FR-026 wants it
 * recorded too. A refused request that leaves no trace is exactly the one an operator later needs.
 *
 * <p>This is the analogue of Principle V's agent trace. No agent runs here, so there is nothing for
 * LangChain4j's observation mechanisms to observe — but an invocation that cannot be inspected
 * afterwards is still unfinished work, and the same bar applies: structured, no secrets, no full
 * payloads.
 */
@ServerFilter("/mcp")
@Singleton
// Between the token filter and the protocol gate, deliberately. A request filter that returns a
// response short-circuits everything after it, so an audit filter ordered last would never see a
// rejected request — and a rejected request is the one an operator most often goes looking for.
@Order(15)
// The audit row is a database write, and a filter runs on the event loop otherwise.
@ExecuteOn(TaskExecutors.BLOCKING)
public class AuditFilter {

    private static final String STARTED_AT = "l4jlab.auditStartedAt";
    private static final String TOOL_NAME = "l4jlab.auditToolName";
    private static final String ARGUMENTS = "l4jlab.auditArguments";

    private final AuditWriter audit;

    public AuditFilter(AuditWriter audit) {
        this.audit = audit;
    }


    @RequestFilter
    public void start(HttpRequest<?> request) {
        request.setAttribute(STARTED_AT, System.nanoTime());
        Map<String, Object> params = dev.l4jlab.mcp.protocol.ParsedBody.paramsOf(request);
        if (params.get("name") instanceof String name) {
            request.setAttribute(TOOL_NAME, name);
        }
        if (params.get("arguments") instanceof Map<?, ?> arguments) {
            request.setAttribute(ARGUMENTS, arguments);
        }
    }

    @ResponseFilter
    public void finish(HttpRequest<?> request, MutableHttpResponse<?> response) {
        Principal principal = request
            .getAttribute(InboundTokenFilter.PRINCIPAL_ATTRIBUTE, Principal.class).orElse(null);
        if (principal == null) {
            // Nothing useful to record: the request never got far enough to have a caller, and a row
            // of nulls would only dilute the log.
            return;
        }
        long startedAt = request.getAttribute(STARTED_AT, Long.class).orElse(System.nanoTime());
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;

        String toolName = request.getAttribute(TOOL_NAME, String.class)
            .orElseGet(() -> request.getHeaders().getFirst("Mcp-Method").orElse("unknown"));

        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) request
            .getAttribute(ARGUMENTS, Map.class).orElse(Map.of());

        boolean toolFailure = request.getAttribute(ToolFailureMapper.ATTRIBUTE, Boolean.class)
            .orElse(false);
        int status = response.getStatus().getCode();

        AuditWriter.Outcome outcome;
        String errorCode = null;
        if (toolFailure) {
            outcome = AuditWriter.Outcome.TOOL_ERROR;
        } else if (status >= 400) {
            outcome = AuditWriter.Outcome.PROTOCOL_ERROR;
            errorCode = String.valueOf(status);
        } else {
            outcome = AuditWriter.Outcome.OK;
        }

        audit.record(principal, toolName, arguments, outcome, errorCode, durationMs,
            request.getHeaders().getFirst("traceparent").orElse(null),
            // Set by the tool itself when a change was confirmed; absent everywhere else.
            request.getAttribute("l4jlab.confirmedBy", String.class).orElse(null),
            request.getAttribute("l4jlab.legacyReferenceId", String.class).orElse(null));
    }
}
