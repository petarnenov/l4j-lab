package dev.l4jlab.mcp.protocol;

import io.micronaut.core.annotation.Order;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import jakarta.inject.Singleton;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Answers the handshake this revision removed, with a diagnostic rather than a refusal (FR-001).
 *
 * <p>Before authentication, deliberately, and that placement is the whole point. A client built for
 * an earlier revision opens with {@code initialize} and has <em>no way to fall forward</em>: if it is
 * told only "401", it learns nothing it can act on, and its user sees a credentials problem that
 * does not exist. Which protocol versions a server speaks is not privileged information — the
 * specification itself says a modern-only server should name its versions in any error it returns to
 * an {@code initialize}, precisely because this message may be the only diagnostic its user ever
 * gets.
 *
 * <p>Nothing else is answered here. Every other method goes through the token filter first.
 */
@ServerFilter("/mcp")
@Singleton
@Order(6)
public class LegacyHandshakeGate {

    @RequestFilter
    public Optional<MutableHttpResponse<?>> refuseHandshake(HttpRequest<?> request) {
        Map<String, Object> body = ParsedBody.of(request);
        if (!"initialize".equals(body.get("method"))) {
            return Optional.empty();
        }
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", ProtocolVersions.METHOD_NOT_FOUND);
        error.put("message", "This server implements MCP " + ProtocolVersions.TARGET
            + ", which has no initialize handshake. Send requests directly, carrying the protocol "
            + "version in _meta and in the MCP-Protocol-Version header.");
        error.put("data", Map.of("supported", ProtocolVersions.SUPPORTED));

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("jsonrpc", "2.0");
        envelope.put("id", body.get("id"));
        envelope.put("error", error);
        return Optional.of(HttpResponse.status(HttpStatus.BAD_REQUEST).body(envelope));
    }

}
