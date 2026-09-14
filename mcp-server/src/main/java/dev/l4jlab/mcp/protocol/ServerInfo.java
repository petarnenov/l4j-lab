package dev.l4jlab.mcp.protocol;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What every result says about the server that produced it (FR-003, FR-018).
 *
 * <p>Three replicas sit behind one round-robin proxy, and until feature 010 a result said only
 * {@code {name, version}} — identical from all three. A caller that got an unexpected answer twice
 * could not tell whether it had asked one instance twice or two instances once, and neither could
 * anyone reading the console: feature 008's screen said in prose that the proxy *"does not report
 * which replica answered"*, because there was nothing to report (finding F-003).
 *
 * <p>{@code instance} closes that. It is a property of the **server**, read from configuration like
 * every other setting here, and deliberately not an nginx header: the proxy is one deployment shape
 * among several, and a fact that only exists when a particular proxy is in front of you is a fact
 * that disappears the moment the topology changes (research R-006).
 *
 * <p>It is one bean rather than a map built in each place that needs one. The serialiser and
 * {@link McpRequestGate} both answer requests, and two hand-built copies of the same three fields is
 * how this repository got a 106-difference drift in the first place.
 */
@Singleton
public class ServerInfo {

    private final Map<String, Object> fields;

    public ServerInfo(
        @Value("${micronaut.mcp.server.info.name:mcp-billing-server}") String name,
        @Value("${micronaut.mcp.server.info.version:0.1.0}") String version,
        @Value("${mcp.instance-id:local}") String instanceId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", name);
        out.put("version", version);
        out.put("instance", instanceId);
        this.fields = Map.copyOf(out);
    }

    /** The map that goes into {@code _meta["io.modelcontextprotocol/serverInfo"]}. */
    public Map<String, Object> asMap() {
        return fields;
    }
}
