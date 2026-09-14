package dev.l4jlab.mcp.protocol;

import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.mcp.server.exceptions.McpErrorExceptionMapper;
import io.modelcontextprotocol.spec.McpError;
import jakarta.inject.Singleton;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Carries an {@link InputRequired} through the module's exception handling with its payload intact.
 *
 * <p>The {@code data} member is used as a courier: the module only knows how to produce a JSON-RPC
 * error, so the elicitation and the sealed request state ride there until
 * {@link JsonRpcResponseSerializer} unpacks them into a proper interim result. Neither the code nor
 * the {@code data} shape ever reaches a client.
 */
@Singleton
public class InputRequiredMapper implements McpErrorExceptionMapper<InputRequired> {

    static final String INPUT_REQUESTS = "inputRequests";
    static final String REQUEST_STATE = "requestState";

    @Override
    public boolean canMap(Class<? extends Throwable> clazz) {
        return InputRequired.class.isAssignableFrom(clazz);
    }

    @Override
    public McpError map(InputRequired exception) {
        // Same marker as a tool failure: an interim result is a successful HTTP response, and the
        // module would otherwise answer 500.
        ServerRequestContext.currentRequest()
            .ifPresent(request -> request.setAttribute(ToolFailureMapper.ATTRIBUTE, Boolean.TRUE));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put(INPUT_REQUESTS, exception.inputRequests());
        data.put(REQUEST_STATE, exception.requestState());
        return McpError.builder(InputRequired.CODE).message(exception.getMessage()).data(data).build();
    }
}
