package dev.l4jlab.mcp.protocol;

import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.mcp.server.exceptions.McpErrorExceptionMapper;
import io.modelcontextprotocol.spec.McpError;
import jakarta.inject.Singleton;

import java.util.Map;

/** Carries a {@link TaskCreated} to the serializer, which turns it into a {@code task} result. */
@Singleton
public class TaskCreatedMapper implements McpErrorExceptionMapper<TaskCreated> {

    static final String TASK = "task";

    @Override
    public boolean canMap(Class<? extends Throwable> clazz) {
        return TaskCreated.class.isAssignableFrom(clazz);
    }

    @Override
    public McpError map(TaskCreated exception) {
        // A task handle is a successful response; without this the module would answer 500.
        ServerRequestContext.currentRequest()
            .ifPresent(request -> request.setAttribute(ToolFailureMapper.ATTRIBUTE, Boolean.TRUE));
        return McpError.builder(TaskCreated.CODE)
            .message(exception.getMessage())
            .data(Map.of(TASK, exception.task()))
            .build();
    }
}
