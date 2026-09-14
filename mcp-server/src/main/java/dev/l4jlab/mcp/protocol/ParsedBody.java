package dev.l4jlab.mcp.protocol;

import io.micronaut.core.annotation.Order;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;

import java.util.Map;

/**
 * Parses the JSON-RPC body once, for every filter that needs it.
 *
 * <p>Four filters need the body, and an earlier arrangement had each of them bind {@code @Body}
 * independently. That subscribes the request body several times, which Micronaut answers with an
 * internal {@code AssertionError} in {@code BaseSharedBuffer} and a 500 that names nothing —
 * intermittently, depending on which filters actually run for a given request. One binding, one
 * parse, one attribute.
 *
 * <p>Runs first among the body-reading filters so everything after it can simply read the result.
 */
@ServerFilter("/mcp")
@Singleton
@Order(1)
public class ParsedBody {

    /** Where the parsed JSON-RPC message lives for the rest of the chain. */
    public static final String ATTRIBUTE = "l4jlab.parsedBody";

    private final JsonMapper json;

    public ParsedBody(JsonMapper json) {
        this.json = json;
    }

    @RequestFilter
    public void parse(HttpRequest<?> request, @Body String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return;
        }
        try {
            request.setAttribute(ATTRIBUTE,
                json.readValue(rawBody, Argument.mapOf(String.class, Object.class)));
        } catch (Exception e) {
            // Unparseable JSON is the module's to reject with -32700. Nothing here can enforce a
            // rule against a body it cannot read, and guessing would be worse than passing it on.
        }
    }

    /** @return the parsed message, or an empty map when the body was absent or unreadable */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> of(HttpRequest<?> request) {
        return request.getAttribute(ATTRIBUTE, Map.class)
            .map(map -> (Map<String, Object>) map)
            .orElseGet(Map::of);
    }

    /** @return the {@code params} object of the parsed message, or an empty map */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> paramsOf(HttpRequest<?> request) {
        Object params = of(request).get("params");
        return params instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
