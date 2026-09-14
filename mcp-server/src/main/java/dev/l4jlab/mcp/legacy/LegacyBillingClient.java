package dev.l4jlab.mcp.legacy;

import dev.l4jlab.mcp.security.TokenExchangeClient;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Singleton;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/**
 * Calls the legacy API on behalf of the user (FR-016, FR-027).
 *
 * <p>Two things travel with every call and nothing else does: the <em>exchanged</em> token, and the
 * {@code traceparent} the client put in {@code _meta}. The inbound token is not among them.
 *
 * <p>Every call is bounded by {@code mcp.legacy-timeout-ms}. An unbounded call to a system of record
 * that has stopped answering turns a tool error into a hang, and a hung tool call is worse for a
 * model than a refused one.
 */
@Singleton
public class LegacyBillingClient {

    private final URI base;
    private final TokenExchangeClient exchange;
    private final Duration timeout;

    public LegacyBillingClient(@Value("${mcp.legacy-url}") String legacyUrl,
                               @Value("${mcp.legacy-timeout-ms:5000}") long timeoutMs,
                               TokenExchangeClient exchange) {
        this.base = URI.create(legacyUrl);
        this.exchange = exchange;
        this.timeout = Duration.ofMillis(timeoutMs);
    }

    /** What a legacy call produced, or why it did not. Never an exception across the boundary. */
    public sealed interface Outcome<T> {

        record Ok<T>(T value) implements Outcome<T> {
        }

        /** The legacy API refused. Becomes a short "no access" tool error (FR-014). */
        record Forbidden<T>() implements Outcome<T> {
        }

        record NotFound<T>() implements Outcome<T> {
        }

        /** A conflict the caller can act on, such as asking for failures of a run that did not fail. */
        record Conflict<T>(String detail) implements Outcome<T> {
        }

        /** The legacy API broke or could not be reached. Becomes "do not retry". */
        record Unavailable<T>() implements Outcome<T> {
        }
    }

    public <T> Outcome<T> get(String path, Map<String, ?> query, String inboundToken,
                              @Nullable String traceparent, Argument<T> type) {
        return call(HttpRequest.GET(withQuery(path, query)), inboundToken, traceparent, type);
    }

    public <T> Outcome<T> post(String path, Object body, String inboundToken,
                               @Nullable String traceparent, Argument<T> type) {
        return call(HttpRequest.POST(path, body), inboundToken, traceparent, type);
    }

    private <T> Outcome<T> call(MutableHttpRequest<?> request, String inboundToken,
                                @Nullable String traceparent, Argument<T> type) {
        String legacyToken;
        try {
            legacyToken = exchange.exchangeForLegacy(inboundToken);
        } catch (TokenExchangeClient.TokenExchangeFailed e) {
            return new Outcome.Unavailable<>();
        }
        request.header("Authorization", "Bearer " + legacyToken);
        if (traceparent != null) {
            // FR-027: propagated byte-for-byte, so the legacy API's spans join the client's trace.
            request.header("traceparent", traceparent);
        }
        try (HttpClient client = HttpClient.create(base.toURL())) {
            client.toBlocking();
            T value = client.toBlocking().retrieve(request, type);
            return new Outcome.Ok<>(value);
        } catch (HttpClientResponseException e) {
            return switch (e.getStatus().getCode()) {
                case 403 -> new Outcome.Forbidden<>();
                case 404 -> new Outcome.NotFound<>();
                case 409 -> new Outcome.Conflict<>(e.getMessage());
                default -> new Outcome.Unavailable<>();
            };
        } catch (Exception e) {
            return new Outcome.Unavailable<>();
        }
    }

    private static String withQuery(String path, Map<String, ?> query) {
        StringBuilder sb = new StringBuilder(path);
        char sep = '?';
        for (Map.Entry<String, ?> e : query.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            sb.append(sep).append(e.getKey()).append('=')
                .append(java.net.URLEncoder.encode(String.valueOf(e.getValue()),
                    java.nio.charset.StandardCharsets.UTF_8));
            sep = '&';
        }
        return sb.toString();
    }
}
