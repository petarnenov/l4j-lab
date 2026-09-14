package dev.l4jlab.mcp.protocol;

import io.micronaut.core.annotation.Order;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * The MCP endpoint accepts POST and nothing else (FR-006).
 *
 * <p>First on the chain, and the only thing here that does not need a caller: whether {@code GET} is
 * allowed has nothing to do with who is asking, and answering 401 to a {@code GET} would tell a
 * legacy client to go looking for credentials it does not need.
 *
 * <p>Earlier revisions used {@code GET} for a standalone event stream and {@code DELETE} to end a
 * session. Both are gone, and the proxy passes every method through so the application's own 405 is
 * what a client sees rather than nginx's.
 */
@ServerFilter("/mcp")
@Singleton
@Order(5)
public class HttpMethodGate {

    @RequestFilter
    public Optional<MutableHttpResponse<?>> reject(HttpRequest<?> request) {
        return request.getMethod() == HttpMethod.POST
            ? Optional.empty()
            : Optional.of(HttpResponse.status(HttpStatus.METHOD_NOT_ALLOWED));
    }
}
