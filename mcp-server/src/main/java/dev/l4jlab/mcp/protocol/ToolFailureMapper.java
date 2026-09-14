package dev.l4jlab.mcp.protocol;

import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.mcp.server.exceptions.McpErrorExceptionMapper;
import io.modelcontextprotocol.spec.McpError;
import jakarta.inject.Singleton;

/**
 * Carries a {@link ToolFailure} through the module's exception handling with its message intact and
 * a code {@link McpResultFilter} recognises.
 *
 * <p>Without this the module would fall through to {@code INTERNAL_ERROR}, losing the actionable
 * sentence the tool wrote — and an actionable sentence is the entire value of a tool error.
 */
@Singleton
public class ToolFailureMapper implements McpErrorExceptionMapper<ToolFailure> {

    /**
     * Marks the request so {@link ToolErrorStatusFilter} can restore HTTP 200.
     *
     * <p>The module answers a JSON-RPC error with HTTP 500, which is right for a protocol failure and
     * wrong for a tool failure: FR-010 requires a tool error to be an ordinary successful response
     * carrying {@code isError}. A client that sees 500 may not even read the body.
     */
    public static final String ATTRIBUTE = "l4jlab.toolFailure";

    @Override
    public boolean canMap(Class<? extends Throwable> clazz) {
        return ToolFailure.class.isAssignableFrom(clazz);
    }

    @Override
    public McpError map(ToolFailure exception) {
        ServerRequestContext.currentRequest()
            .ifPresent(request -> request.setAttribute(ATTRIBUTE, Boolean.TRUE));
        return McpError.builder(ToolFailure.CODE).message(exception.getMessage()).build();
    }
}
