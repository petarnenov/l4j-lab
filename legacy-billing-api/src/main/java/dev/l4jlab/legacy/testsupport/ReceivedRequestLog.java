package dev.l4jlab.legacy.testsupport;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Test-only capture of what actually arrived (T028).
 *
 * <p>Two assertions read it. SC-005: the token the MCP server received must appear nowhere here,
 * which is the only way to prove a negative about forwarding. FR-027: the {@code traceparent} a
 * client put in {@code _meta} must arrive unchanged.
 *
 * <p>In memory, bounded, gated to {@code test-capture}, and never written to stdout — a log of
 * bearer tokens is exactly the thing that must not leak into a file someone later ships.
 */
@Requires(env = "test-capture")
@Singleton
@ServerFilter("/api/**")
public class ReceivedRequestLog {

    private static final int MAX_ENTRIES = 500;

    private final Queue<Entry> entries = new ConcurrentLinkedQueue<>();

    /** One inbound request, reduced to the two headers the assertions care about. */
    @Serdeable
    public record Entry(String method, String path, String authorization, String traceparent) {
    }

    @RequestFilter
    public void record(HttpRequest<?> request) {
        while (entries.size() >= MAX_ENTRIES) {
            entries.poll();
        }
        entries.add(new Entry(
            request.getMethodName(),
            request.getPath(),
            request.getHeaders().getFirst("Authorization").orElse(null),
            request.getHeaders().getFirst("traceparent").orElse(null)));
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public void clear() {
        entries.clear();
    }

    @Requires(env = "test-capture")
    @Controller("/test")
    public static class Endpoint {

        private final ReceivedRequestLog log;

        public Endpoint(ReceivedRequestLog log) {
            this.log = log;
        }

        @Get("/received-requests")
        public List<Entry> received() {
            return log.entries();
        }
    }
}
