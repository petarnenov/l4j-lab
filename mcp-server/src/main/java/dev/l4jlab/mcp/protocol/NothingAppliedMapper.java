package dev.l4jlab.mcp.protocol;

import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.mcp.server.exceptions.McpErrorExceptionMapper;
import io.modelcontextprotocol.spec.McpError;
import jakarta.inject.Singleton;

/**
 * Carries a {@link NothingApplied} through the module's exception handling so
 * {@link JsonRpcResponseSerializer} can turn it into the successful result it is.
 *
 * <p>The same arrangement {@link InputRequiredMapper} uses, for the same reason: the module maps every
 * thrown exception to a JSON-RPC error, and a tool method has no way to return a result that is
 * neither a value nor a failure.
 */
@Singleton
public class NothingAppliedMapper implements McpErrorExceptionMapper<NothingApplied> {

    @Override
    public boolean canMap(Class<? extends Throwable> clazz) {
        return NothingApplied.class.isAssignableFrom(clazz);
    }

    @Override
    public McpError map(NothingApplied exception) {
        // HTTP 200, as for a tool failure: the module answers any JSON-RPC error with 500, and this is
        // not an error at all.
        ServerRequestContext.currentRequest()
            .ifPresent(request -> request.setAttribute(ToolFailureMapper.ATTRIBUTE, Boolean.TRUE));
        return McpError.builder(NothingApplied.CODE).message(exception.getMessage()).build();
    }
}
