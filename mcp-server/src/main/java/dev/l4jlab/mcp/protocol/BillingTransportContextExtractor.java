package dev.l4jlab.mcp.protocol;

import dev.l4jlab.mcp.security.InboundTokenFilter;
import dev.l4jlab.mcp.security.Principal;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpRequest;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.Map;

/**
 * The first of the seven 2026-07-28 deltas (research.md R-014), written against the seam the module
 * advertises rather than around it.
 *
 * <p>Replaces {@code DefaultMcpTransportContextExtractor}, which belongs to an earlier revision: it
 * defaults the protocol version to {@code 2025-03-26} and reads {@code Mcp-Session-Id}, a header this
 * revision removed. This one carries what the stateless core actually needs — the principal derived
 * from the request's own token, the {@code traceparent} to propagate, and the protocol version and
 * client capabilities the request declared for itself.
 *
 * <p>Retired by java-sdk#1011 (SEP-2575), when the SDK carries per-request metadata natively.
 */
@Singleton
@Replaces(McpTransportContextExtractor.class)
public class BillingTransportContextExtractor implements McpTransportContextExtractor<HttpRequest<?>> {

    @Override
    public McpTransportContext extract(HttpRequest<?> request) {
        Principal principal = request
            .getAttribute(InboundTokenFilter.PRINCIPAL_ATTRIBUTE, Principal.class).orElse(null);
        String inboundToken = request
            .getAttribute(InboundTokenFilter.INBOUND_TOKEN_ATTRIBUTE, String.class).orElse(null);
        if (principal == null || inboundToken == null) {
            // The filter refuses unauthenticated requests before this runs, so reaching here means
            // something bypassed it. An empty context fails closed: every tool needs a principal.
            return McpTransportContext.EMPTY;
        }
        // Parsed once by ParsedBody, at the head of the chain. Reading it again here would be a
        // second subscription to the request body, which Micronaut refuses.
        Map<String, Object> params = ParsedBody.paramsOf(request);

        Map<String, Object> metadata = new HashMap<>(1);
        metadata.put(RequestContext.KEY, new RequestContext(
            principal,
            inboundToken,
            request.getHeaders().getFirst("traceparent").orElse(null),
            request.getHeaders().getFirst("MCP-Protocol-Version").orElse(null),
            capabilitiesOf(params),
            mapOf(params.get("inputResponses")),
            params.get("requestState") instanceof String state ? state : null));
        return McpTransportContext.create(metadata);
    }

    /**
     * Client capabilities travel in the request's own {@code _meta}, not in a handshake — which is
     * the whole point of the stateless core (FR-002).
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> capabilitiesOf(Map<String, Object> params) {
        if (params.get("_meta") instanceof Map<?, ?> meta
            && meta.get("io.modelcontextprotocol/clientCapabilities") instanceof Map<?, ?> caps) {
            return (Map<String, Object>) caps;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapOf(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
