package dev.l4jlab.mcp.protocol;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Four of the 2026-07-28 deltas, applied where the bytes are actually produced (research.md R-014).
 *
 * <p>Two earlier seams were tried and rejected on evidence, both recorded because the next person
 * will reach for them too:
 *
 * <ul>
 *   <li>An HTTP {@code @ResponseFilter} never sees the body — the module streams it, so
 *       {@code response.body()} is {@code null}.</li>
 *   <li>Replacing the {@code McpJsonMapper} bean has no effect on responses: instrumenting it showed
 *       it is never called on the response path. The module hands the {@code JSONRPCResponse} to
 *       Micronaut Serde, which writes it.</li>
 * </ul>
 *
 * <p>So: a Serde serializer for the response type. What it adds, none of which the module's revision
 * knows about:
 *
 * <ul>
 *   <li><b>{@code resultType}</b> on every result — mandatory now, absent from the SDK's result
 *       types. Retired by java-sdk#1011.</li>
 *   <li><b>{@code _meta.io.modelcontextprotocol/serverInfo}</b> (FR-003): how a stateless client
 *       learns who answered, with no handshake to have learned it from.</li>
 *   <li><b>{@code ttlMs} and {@code cacheScope}</b> on cacheable list results (FR-009). Retired by
 *       java-sdk#1009.</li>
 *   <li><b>Tool failures</b>: a {@link ToolFailure} arrives carrying {@link ToolFailure#CODE} and
 *       leaves as a result with {@code isError: true}. FR-010 says a protocol failure and a tool
 *       failure must never be confused, and the module has no other path from a tool method to an
 *       {@code isError} result.</li>
 * </ul>
 */
@Singleton
public class JsonRpcResponseSerializer implements Serializer<McpSchema.JSONRPCResponse> {

    private static final String META = "_meta";
    private static final String SERVER_INFO_KEY = "io.modelcontextprotocol/serverInfo";
    private static final String RESULT_TYPE = "resultType";
    private static final String COMPLETE = "complete";

    /**
     * The order {@code contracts/README.md} declares, enforced here (FR-009).
     *
     * <p>Bean discovery order is stable enough in practice but is not a promise, and "deterministic"
     * is not the same as "the order the contract says". Reads before writes, and within reads the
     * order an agent would walk: find a run, open it, then open its failures.
     */
    private static final List<String> TOOL_ORDER = List.of(
        "search_billing_runs",
        "get_billing_run_status",
        "get_run_failures",
        "post_fee_adjustment",
        "start_billing_run");

    private final Map<String, Object> serverInfo;
    private final long toolsTtlMs;

    public JsonRpcResponseSerializer(
        @Value("${micronaut.mcp.server.info.name:mcp-billing-server}") String name,
        @Value("${micronaut.mcp.server.info.version:0.1.0}") String version,
        @Value("${mcp.tools-ttl-ms:300000}") long toolsTtlMs) {
        this.serverInfo = Map.of("name", name, "version", version);
        this.toolsTtlMs = toolsTtlMs;
    }

    @Override
    public void serialize(Encoder encoder, EncoderContext context,
                          Argument<? extends McpSchema.JSONRPCResponse> type,
                          McpSchema.JSONRPCResponse value) throws IOException {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("jsonrpc", value.jsonrpc());
        message.put("id", value.id());

        McpSchema.JSONRPCResponse.JSONRPCError error = value.error();
        if (error != null && error.code() == ToolFailure.CODE) {
            message.put("result", toolErrorResult(error.message()));
        } else if (error != null && error.code() == InputRequired.CODE) {
            message.put("result", inputRequiredResult(error.data()));
        } else if (error != null && error.code() == TaskCreated.CODE) {
            message.put("result", taskResult(error.data()));
        } else if (error != null) {
            Map<String, Object> errorMap = new LinkedHashMap<>();
            errorMap.put("code", error.code());
            errorMap.put("message", error.message());
            if (error.data() != null) {
                errorMap.put("data", error.data());
            }
            message.put("error", errorMap);
        } else {
            message.put("result", decorate(asMap(context, value.result())));
        }

        Serializer<? super Map<String, Object>> mapSerializer =
            context.findSerializer(MAP_ARGUMENT).createSpecific(context, MAP_ARGUMENT);
        mapSerializer.serialize(encoder, context, MAP_ARGUMENT, message);
    }

    private static final Argument<Map<String, Object>> MAP_ARGUMENT =
        Argument.mapOf(String.class, Object.class);

    /** Adds what this revision requires and the module does not know to add. */
    private Map<String, Object> decorate(Map<String, Object> result) {
        Map<String, Object> out = new LinkedHashMap<>(result);
        out.putIfAbsent(RESULT_TYPE, COMPLETE);
        if (out.get("tools") instanceof List<?> tools) {
            out.put("tools", inDeclaredOrder(tools));
        }
        if (out.containsKey("tools") || out.containsKey("supportedVersions")) {
            // Cacheable, and neither varies by caller: entitlements filter results, never the
            // catalogue, so "public" is honest (FR-009).
            out.putIfAbsent("ttlMs", toolsTtlMs);
            out.putIfAbsent("cacheScope", "public");
        }
        attachServerInfo(out);
        return out;
    }

    /** Sorts by the declared order; anything unrecognised sorts last, by name, rather than vanishing. */
    private static List<?> inDeclaredOrder(List<?> tools) {
        return tools.stream().sorted((a, b) -> {
            String left = nameOf(a);
            String right = nameOf(b);
            int leftIndex = TOOL_ORDER.indexOf(left);
            int rightIndex = TOOL_ORDER.indexOf(right);
            if (leftIndex < 0 && rightIndex < 0) {
                return left.compareTo(right);
            }
            if (leftIndex < 0) {
                return 1;
            }
            if (rightIndex < 0) {
                return -1;
            }
            return Integer.compare(leftIndex, rightIndex);
        }).toList();
    }

    private static String nameOf(Object tool) {
        return tool instanceof Map<?, ?> map && map.get("name") instanceof String name ? name : "";
    }

    /**
     * A business-logic failure becomes a result the model can read: the actionable sentence in
     * {@code content}, {@code isError} set, and no {@code structuredContent} — there is no structure
     * to report when nothing was produced.
     */
    private Map<String, Object> toolErrorResult(String message) {
        List<Map<String, Object>> content = new ArrayList<>(1);
        content.add(Map.of("type", "text",
            "text", message == null ? "The tool could not complete." : message));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put(RESULT_TYPE, COMPLETE);
        result.put("content", content);
        result.put("isError", true);
        attachServerInfo(result);
        return result;
    }

    /**
     * The interim result of a Multi Round-Trip Request (FR-020).
     *
     * <p>Carries no {@code ttlMs} or {@code cacheScope}: the specification is explicit that a result
     * produced by the MRTR mechanism must not be cached, because it depends on inputs that are not
     * part of the cache key.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> inputRequiredResult(Object data) {
        Map<String, Object> payload = data instanceof Map<?, ?> map
            ? (Map<String, Object>) map : Map.of();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put(RESULT_TYPE, "input_required");
        Object inputRequests = payload.get(InputRequiredMapper.INPUT_REQUESTS);
        if (inputRequests != null) {
            result.put("inputRequests", inputRequests);
        }
        Object requestState = payload.get(InputRequiredMapper.REQUEST_STATE);
        if (requestState != null) {
            result.put("requestState", requestState);
        }
        attachServerInfo(result);
        return result;
    }

    /**
     * The Tasks extension's {@code CreateTaskResult} (FR-021). Not cacheable, and not a
     * {@code complete} result: a client that treated it as one would report a run finished the
     * moment it started.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> taskResult(Object data) {
        Map<String, Object> payload = data instanceof Map<?, ?> map
            ? (Map<String, Object>) map : Map.of();
        Map<String, Object> task = payload.get(TaskCreatedMapper.TASK) instanceof Map<?, ?> t
            ? (Map<String, Object>) t : Map.of();

        Map<String, Object> result = new LinkedHashMap<>(task);
        result.put(RESULT_TYPE, "task");
        attachServerInfo(result);
        return result;
    }

    @SuppressWarnings("unchecked")
    private void attachServerInfo(Map<String, Object> result) {
        Object existing = result.get(META);
        Map<String, Object> meta = existing instanceof Map<?, ?> m
            ? new LinkedHashMap<>((Map<String, Object>) m)
            : new LinkedHashMap<>();
        meta.putIfAbsent(SERVER_INFO_KEY, serverInfo);
        result.put(META, meta);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(EncoderContext context, Object result) {
        if (result instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        // The SDK's result records are @Serdeable; round-tripping through a tree is the only way to
        // add fields the record does not declare.
        try {
            io.micronaut.json.JsonMapper mapper = io.micronaut.json.JsonMapper.createDefault();
            return mapper.readValue(mapper.writeValueAsBytes(result), MAP_ARGUMENT);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot shape the MCP result", e);
        }
    }
}
