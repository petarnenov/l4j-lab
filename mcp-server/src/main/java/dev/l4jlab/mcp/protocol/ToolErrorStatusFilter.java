package dev.l4jlab.mcp.protocol;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import jakarta.inject.Singleton;

/**
 * Restores HTTP 200 for a tool execution failure (FR-010).
 *
 * <p>{@code micronaut-mcp-server-java-sdk} answers any JSON-RPC error with HTTP 500. That is right
 * for a protocol failure and wrong for a tool failure: a tool error is an ordinary successful
 * response whose result carries {@code isError}, and a client that sees 500 may never read the body
 * at all.
 *
 * <p>The two are told apart by an attribute {@link ToolFailureMapper} sets, not by inspecting the
 * body — which a response filter cannot see, since the module streams it (research.md R-016). So
 * only a failure this server deliberately raised as a tool failure is rewritten; a genuine protocol
 * error keeps its status.
 */
@ServerFilter("/mcp")
@Singleton
public class ToolErrorStatusFilter {

    @ResponseFilter
    public void restoreOkForToolFailures(HttpRequest<?> request, MutableHttpResponse<?> response) {
        if (request.getAttribute(ToolFailureMapper.ATTRIBUTE, Boolean.class).orElse(false)) {
            response.status(HttpStatus.OK);
        }
    }
}
