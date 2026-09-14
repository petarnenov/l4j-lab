package dev.l4jlab.mcp.protocol;

import dev.l4jlab.mcp.security.InboundTokenFilter;
import dev.l4jlab.mcp.security.Principal;
import dev.l4jlab.mcp.tasks.TasksHandler;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Order;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.json.JsonMapper;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.inject.Singleton;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The methods this revision added that the module has no route for (research.md R-014).
 *
 * <p>Split from {@link McpRequestGate} on purpose, and the ordering is the reason. The gate refuses
 * malformed requests before anyone asks who sent them — a missing {@code Mcp-Method} header is wrong
 * whoever you are. These methods are different: they act on a caller's own data, so they run
 * <em>after</em> {@link InboundTokenFilter} and have a principal to work with.
 *
 * <ul>
 *   <li><b>{@code server/discover}</b> (FR-005), which servers MUST implement. Issue java-sdk#1072
 *       is this same gap seen from the other side.</li>
 *   <li><b>{@code tasks/get}, {@code tasks/cancel}, {@code tasks/update}</b> (FR-021, FR-031).</li>
 * </ul>
 *
 * <p>Retired by java-sdk#1011 and #1013.
 */
@ServerFilter("/mcp")
@Singleton
@Order(30)
// Filters run on the Netty event loop, and these methods call the legacy API with a blocking
// client — which Micronaut refuses outright, and rightly: blocking an event loop starves every
// other request on it. tools/call does not hit this because the module dispatches tools on its own
// blocking executor. Found 2026-09-13, the hard way.
@ExecuteOn(TaskExecutors.BLOCKING)
public class McpMethodHandler {

    private static final String META = "_meta";
    private static final String SERVER_INFO_KEY = "io.modelcontextprotocol/serverInfo";

    private final JsonMapper json;
    private final TasksHandler tasks;
    private final Map<String, Object> serverInfo;
    private final long toolsTtlMs;

    public McpMethodHandler(JsonMapper json, TasksHandler tasks,
                            @Value("${micronaut.mcp.server.info.name:mcp-billing-server}") String name,
                            @Value("${micronaut.mcp.server.info.version:0.1.0}") String version,
                            @Value("${mcp.tools-ttl-ms:300000}") long toolsTtlMs) {
        this.json = json;
        this.tasks = tasks;
        this.serverInfo = Map.of("name", name, "version", version);
        this.toolsTtlMs = toolsTtlMs;
    }

    @RequestFilter
    public Optional<MutableHttpResponse<?>> handle(HttpRequest<?> request) {
        Map<String, Object> body = ParsedBody.of(request);
        String method = body.get("method") instanceof String m ? m : "";
        Object id = body.get("id");

        if ("server/discover".equals(method)) {
            return Optional.of(HttpResponse.ok(envelope(id, discoverResult())));
        }
        if (!method.startsWith("tasks/")) {
            return Optional.empty();
        }
        return Optional.of(handleTasks(request, method, id, ParsedBody.paramsOf(request)));
    }

    private MutableHttpResponse<?> handleTasks(HttpRequest<?> request, String method, Object id,
                                               Map<String, Object> params) {
        Principal principal = request
            .getAttribute(InboundTokenFilter.PRINCIPAL_ATTRIBUTE, Principal.class).orElse(null);
        String inboundToken = request
            .getAttribute(InboundTokenFilter.INBOUND_TOKEN_ATTRIBUTE, String.class).orElse(null);
        if (principal == null || inboundToken == null) {
            return HttpResponse.status(HttpStatus.UNAUTHORIZED);
        }
        RequestContext context = new RequestContext(principal, inboundToken,
            header(request, "traceparent"), header(request, "MCP-Protocol-Version"),
            capabilitiesOf(params), Map.of(), null);
        try {
            Map<String, Object> result = switch (method) {
                case "tasks/get" -> tasks.get(context, params);
                case "tasks/cancel" -> tasks.cancel(context, params);
                case "tasks/update" -> tasks.update(context, params);
                default -> null;
            };
            if (result == null) {
                return error(HttpStatus.NOT_FOUND, id, ProtocolVersions.METHOD_NOT_FOUND,
                    "Unknown method: " + method, null);
            }
            return HttpResponse.ok(envelope(id, complete(result)));
        } catch (TasksHandler.MissingCapability e) {
            return error(HttpStatus.BAD_REQUEST, id,
                ProtocolVersions.MISSING_REQUIRED_CLIENT_CAPABILITY, e.getMessage(),
                Map.of("requiredCapabilities", e.required()));
        } catch (ToolFailure e) {
            // A well-formed question about a task that does not exist is a tool failure, not a
            // protocol one: the request was fine, the answer is "no such thing" (FR-010).
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("resultType", "complete");
            result.put("content", List.of(Map.of("type", "text", "text", e.getMessage())));
            result.put("isError", true);
            attachServerInfo(result);
            return HttpResponse.ok(envelope(id, result));
        }
    }

    /** FR-005: supported versions, capabilities, identity, and its own caching hints. */
    private Map<String, Object> discoverResult() {
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("tools", Map.of("listChanged", false));
        capabilities.put("extensions", Map.of("io.modelcontextprotocol/tasks", Map.of()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resultType", "complete");
        result.put("supportedVersions", ProtocolVersions.SUPPORTED);
        result.put("capabilities", capabilities);
        result.put("instructions", """
            Billing runs and fee adjustments for wealth-management firms. Search runs before opening \
            one. Fee adjustments require confirmation and a stable operation_id.""");
        result.put("ttlMs", toolsTtlMs);
        result.put("cacheScope", "public");
        attachServerInfo(result);
        return result;
    }

    private Map<String, Object> complete(Map<String, Object> result) {
        Map<String, Object> out = new LinkedHashMap<>(result);
        out.put("resultType", "complete");
        attachServerInfo(out);
        return out;
    }

    private void attachServerInfo(Map<String, Object> result) {
        result.put(META, Map.of(SERVER_INFO_KEY, serverInfo));
    }

    private static Map<String, Object> envelope(Object id, Map<String, Object> result) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("jsonrpc", "2.0");
        message.put("id", id);
        message.put("result", result);
        return message;
    }

    private static MutableHttpResponse<?> error(HttpStatus status, Object id, int code,
                                                String message, Map<String, Object> data) {
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


    /** Capabilities travel per request, not in a handshake — the point of the stateless core. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> capabilitiesOf(Map<String, Object> params) {
        if (params.get("_meta") instanceof Map<?, ?> meta
            && meta.get("io.modelcontextprotocol/clientCapabilities") instanceof Map<?, ?> caps) {
            return (Map<String, Object>) caps;
        }
        return Map.of();
    }

    private static String header(HttpRequest<?> request, String name) {
        return request.getHeaders().getFirst(name).orElse(null);
    }

}
