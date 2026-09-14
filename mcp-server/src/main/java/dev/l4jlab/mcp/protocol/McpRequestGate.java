package dev.l4jlab.mcp.protocol;

import dev.l4jlab.mcp.tools.ToolArgumentConstraints;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.core.annotation.Order;
import io.micronaut.http.annotation.ServerFilter;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Three more of the 2026-07-28 deltas, on the request side (research.md R-014).
 *
 * <p>All three want the same thing — the parsed body, before the module sees it — so they share one
 * filter rather than parsing it three times:
 *
 * <ul>
 *   <li><b>Header routing</b> (FR-008): {@code MCP-Protocol-Version}, {@code Mcp-Method}, and on
 *       {@code tools/call} {@code Mcp-Name}, each required and each checked against the body.
 *       Mismatch is {@code -32020} with HTTP 400. The module's own extractor belongs to an earlier
 *       revision and knows none of these.</li>
 *   <li><b>Version negotiation</b> (FR-004): an unsupported version is {@code -32022} carrying the
 *       versions this server does support, so a client can retry rather than guess.</li>
 *   <li><b>{@code server/discover}</b> (FR-005): answered here, because the module has no notion of
 *       the method. Issue java-sdk#1072 is the same gap seen from the other side.</li>
 * </ul>
 *
 * <p>The {@code initialize} handshake is refused earlier still, by {@link LegacyHandshakeGate},
 * because a legacy client has no credentials to offer and would otherwise learn only "401".
 *
 * <p>Retired by java-sdk#1011 (SEP-2575).
 */
@ServerFilter("/mcp")
@Singleton
// Runs *after* InboundTokenFilter, which is a change from the obvious arrangement and worth the
// note. A malformed request is malformed whoever sent it, so refusing it first is tempting — but
// then the refusal has no principal to attribute it to, and FR-026 wants protocol rejections in the
// audit log as much as tool failures. This server has no anonymous surface on /mcp, so
// authenticating first costs nothing and makes every rejection but a bad token attributable.
@Order(20)
public class McpRequestGate {

    /** FR-003: every result carries it, including the ones this filter answers without the module. */
    private final Map<String, Object> serverInfo;

    public McpRequestGate(ServerInfo serverInfo) {
        this.serverInfo = serverInfo.asMap();
    }

    private static final String BASE64_PREFIX = "=?base64?";
    private static final String BASE64_SUFFIX = "?=";


    /**
     * The body is bound as a filter argument rather than read from the request: a Micronaut server
     * filter runs before the route, so the request is still streaming and {@code getBody} yields
     * nothing. Binding makes Micronaut buffer it for us.
     */
    @RequestFilter
    public Optional<MutableHttpResponse<?>> gate(HttpRequest<?> request) {
        Map<String, Object> body = ParsedBody.of(request);
        if (body.isEmpty()) {
            // Unparseable JSON is the module's to reject; this filter only enforces the additions
            // this revision made, and cannot enforce them against a body it cannot read.
            return Optional.empty();
        }
        Object id = body.get("id");
        String method = body.get("method") instanceof String m ? m : null;


        Optional<MutableHttpResponse<?>> headerProblem = checkHeaders(request, body, id, method);
        if (headerProblem.isPresent()) {
            return headerProblem;
        }
        Optional<MutableHttpResponse<?>> versionProblem = checkVersion(request, id);
        if (versionProblem.isPresent()) {
            return versionProblem;
        }
        Optional<MutableHttpResponse<?>> argumentProblem = checkArguments(body, id, method);
        if (argumentProblem.isPresent()) {
            return argumentProblem;
        }
        // The revision's own additions to tools/call params. The SDK's CallToolRequest predates
        // them, so they are carried to the tool through the transport context instead.
        return Optional.empty();
    }

    /**
     * FR-013. A declared constraint is a validated constraint.
     *
     * <p>{@link ToolArgumentConstraints} is merged into what {@code tools/list} serves; enforcing it
     * here is the other half of the same fact. Restoring {@code enum} and bounds to the declaration
     * without checking them would swap a lie about what is declared for a lie about what is enforced,
     * and a model that reads the declaration would be misled either way.
     *
     * <p>Answered as a <b>tool</b> error at HTTP 200 naming the field, which is what the error table
     * in {@code contracts/mcp-protocol.md} says an argument failing {@code inputSchema} is. The
     * request was well-formed; the call was not.
     */
    @SuppressWarnings("unchecked")
    private Optional<MutableHttpResponse<?>> checkArguments(Map<String, Object> body, Object id,
                                                            String method) {
        if (!"tools/call".equals(method) || !(body.get("params") instanceof Map<?, ?> params)) {
            return Optional.empty();
        }
        if (!(params.get("name") instanceof String tool)
            || !(params.get("arguments") instanceof Map<?, ?> arguments)) {
            return Optional.empty();
        }
        String violation =
            ToolArgumentConstraints.firstViolation(tool, (Map<String, Object>) arguments);
        return violation == null ? Optional.empty() : Optional.of(toolError(id, violation));
    }

    /** The tool-error result shape, built here because a filter answers before the serializer runs. */
    private MutableHttpResponse<?> toolError(Object id, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", List.of(Map.of("type", "text", "text", message)));
        result.put("isError", true);
        result.put("resultType", "complete");
        result.put("_meta", Map.of("io.modelcontextprotocol/serverInfo", serverInfo));

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("jsonrpc", "2.0");
        envelope.put("id", id);
        envelope.put("result", result);
        return HttpResponse.ok(envelope);
    }

    /** FR-008. A header that disagrees with the body is a security problem, not a formatting one:
     * an intermediary routing on the header would send the request somewhere the body did not ask
     * for. Hence rejection rather than preferring one source. */
    private Optional<MutableHttpResponse<?>> checkHeaders(HttpRequest<?> request,
                                                          Map<String, Object> body,
                                                          Object id, String method) {
        String headerMethod = header(request, "Mcp-Method");
        if (headerMethod == null) {
            return Optional.of(headerMismatch(id, "Mcp-Method header is required."));
        }
        if (!headerMethod.equals(method)) {
            return Optional.of(headerMismatch(id,
                "Mcp-Method header does not match the request method."));
        }
        if ("tools/call".equals(method)) {
            String headerName = decodeSentinel(header(request, "Mcp-Name"));
            if (headerName == null) {
                return Optional.of(headerMismatch(id,
                    "Mcp-Name header is required on tools/call."));
            }
            Object params = body.get("params");
            String bodyName = params instanceof Map<?, ?> p && p.get("name") instanceof String n
                ? n : null;
            if (!headerName.equals(bodyName)) {
                return Optional.of(headerMismatch(id,
                    "Mcp-Name header does not match the tool named in the request."));
            }
        }
        return Optional.empty();
    }

    private Optional<MutableHttpResponse<?>> checkVersion(HttpRequest<?> request, Object id) {
        String declared = header(request, "MCP-Protocol-Version");
        if (declared == null) {
            return Optional.of(headerMismatch(id, "MCP-Protocol-Version header is required."));
        }
        if (!ProtocolVersions.SUPPORTED.contains(declared)) {
            return Optional.of(error(HttpStatus.BAD_REQUEST, id,
                ProtocolVersions.UNSUPPORTED_PROTOCOL_VERSION, "Unsupported protocol version",
                Map.of("supported", ProtocolVersions.SUPPORTED, "requested", declared)));
        }
        return Optional.empty();
    }

    /**
     * Decodes the {@code =?base64?...?=} sentinel the specification defines for header values that
     * cannot be plain ASCII. The server MUST decode before comparing, or a tool with a non-ASCII
     * name could never be called.
     */
    private static String decodeSentinel(String value) {
        if (value == null || !value.startsWith(BASE64_PREFIX) || !value.endsWith(BASE64_SUFFIX)) {
            return value;
        }
        String encoded = value.substring(BASE64_PREFIX.length(),
            value.length() - BASE64_SUFFIX.length());
        try {
            return new String(java.util.Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }


    private static String header(HttpRequest<?> request, String name) {
        return request.getHeaders().getFirst(name).orElse(null);
    }

    private MutableHttpResponse<?> headerMismatch(Object id, String message) {
        return error(HttpStatus.BAD_REQUEST, id, ProtocolVersions.HEADER_MISMATCH, message, null);
    }

    private MutableHttpResponse<?> error(HttpStatus status, Object id, int code, String message,
                                         Map<String, Object> data) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        if (data != null) {
            error.put("data", data);
        }
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("jsonrpc", "2.0");
        envelope.put("id", id);
        envelope.put("error", error);
        return HttpResponse.status(status).body(envelope);
    }

}
