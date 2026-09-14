package dev.l4jlab.mcp.protocol;

import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.mcp.server.exceptions.McpErrorExceptionMapper;
import io.modelcontextprotocol.spec.McpError;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The last boundary: what a caller receives when something throws where nobody expected it
 * (feature 010, FR-002).
 *
 * <p>Without this, an unexpected exception falls through to the module's generic handling and becomes
 * a JSON-RPC error with an <em>empty</em> message — which the SDK then rejects, so the caller learns
 * only that the server's validator complained. That is how a {@code NullPointerException} on an empty
 * search reached callers as {@code -32603 "message must not be empty"}: a defect wearing a second
 * defect's clothes.
 *
 * <p>The exception's own message never travels. Feature 007's SC-006 requires that no stack trace, SQL
 * fragment or hostname reach a caller, and an unexpected exception is precisely where one would — it
 * is the path nobody wrote a message for. So the caller gets one fixed sentence, and the operator gets
 * everything, in the log.
 *
 * <p>It is raised as a tool failure rather than a protocol failure because that is what it is: the
 * protocol worked, and a tool could not finish. The code is therefore one the error table in
 * {@code specs/007-mcp-billing-server/contracts/mcp-protocol.md} already lists.
 */
@Singleton
public class UnexpectedFailureMapper implements McpErrorExceptionMapper<RuntimeException> {

    private static final Logger LOG = LoggerFactory.getLogger(UnexpectedFailureMapper.class);

    /**
     * One sentence, fixed. It says what happened in terms of the caller's request and it says whether
     * to try again — the same distinction {@link dev.l4jlab.mcp.legacy.LegacyErrorTranslator} draws
     * for every failure this server does expect, because a model that retries a broken thing makes an
     * outage worse.
     */
    static final String MESSAGE =
        "This tool could not complete the request. Do not retry; report it and stop.";

    /**
     * Everything this server throws deliberately. Each is a signal the serializer turns into a result:
     * a failure with an actionable sentence, an elicitation, a task handle, a decision not to act.
     *
     * <p>They are listed rather than detected because the first version of this class detected — it
     * returned true for every {@code RuntimeException} except {@code ToolFailure}, and swallowed all of
     * them. Worse, whether it did depended on the order the framework happened to resolve the mappers
     * in, so the suite was green once and red the next run. A catch-all at a boundary catches the
     * control flow too, and intermittently is the worst way to find that out.
     */
    private static final Set<Class<? extends RuntimeException>> SIGNALS = Set.of(
        ToolFailure.class, InputRequired.class, TaskCreated.class, NothingApplied.class,
        McpError.class);

    @Override
    public boolean canMap(Class<? extends Throwable> clazz) {
        return RuntimeException.class.isAssignableFrom(clazz)
            && SIGNALS.stream().noneMatch(signal -> signal.isAssignableFrom(clazz));
    }

    @Override
    public McpError map(RuntimeException exception) {
        LOG.error("Unexpected failure in a tool: {}", logDetail(exception));
        ServerRequestContext.currentRequest()
            .ifPresent(request -> request.setAttribute(ToolFailureMapper.ATTRIBUTE, Boolean.TRUE));
        return errorFor(exception);
    }

    /** The answer, with nothing of the exception in it. */
    static McpError errorFor(RuntimeException exception) {
        return McpError.builder(ToolFailure.CODE).message(MESSAGE).build();
    }

    /**
     * Everything the answer left out, for the log. Structured, because feature 007 requires logs to be
     * — and because making the caller's view safe must not make the operator's view empty.
     */
    static Map<String, Object> logDetail(RuntimeException exception) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("exception", exception.getClass().getName());
        detail.put("detail", String.valueOf(exception.getMessage()));
        StackTraceElement[] frames = exception.getStackTrace();
        if (frames.length > 0) {
            detail.put("at", frames[0].toString());
        }
        return detail;
    }
}
