package dev.l4jlab.mcp.protocol;

import dev.l4jlab.mcp.security.Principal;
import io.modelcontextprotocol.common.McpTransportContext;

import java.util.Map;

/**
 * What a tool needs to know about the request it is answering, carried through the MCP layer.
 *
 * <p>Per-request and nothing else: the protocol is stateless, so none of this may be remembered
 * between calls (FR-002). It reaches a tool through {@link McpTransportContext}, which the module
 * already threads from the HTTP layer to the tool method — using that seam rather than a
 * {@code ThreadLocal} is what keeps the path visible.
 */
public record RequestContext(Principal principal, String inboundToken, String traceparent,
                             String protocolVersion, Map<String, Object> clientCapabilities,
                             Map<String, Object> inputResponses, String requestState) {

    /** True when the client is coming back with the input a previous turn asked for (FR-020). */
    public boolean hasInputResponses() {
        return inputResponses != null && !inputResponses.isEmpty();
    }

    /** The single key everything of ours lives under in the transport context's metadata. */
    public static final String KEY = "dev.l4jlab/requestContext";

    /**
     * @return the context this request carries, or {@code null} when the tool is invoked outside an
     *     authenticated HTTP request — which in this server means a test calling it directly
     */
    public static RequestContext from(McpTransportContext transport) {
        Object value = transport == null ? null : transport.get(KEY);
        return value instanceof RequestContext context ? context : null;
    }

    /** True when the client declared the named extension, e.g. the Tasks extension (FR-021). */
    @SuppressWarnings("unchecked")
    public boolean declaresExtension(String identifier) {
        Object extensions = clientCapabilities.get("extensions");
        return extensions instanceof Map<?, ?> map && map.containsKey(identifier);
    }

    /** True when the client declared elicitation, without which the server may not ask (FR-020). */
    public boolean declaresElicitation() {
        return clientCapabilities.containsKey("elicitation");
    }
}
