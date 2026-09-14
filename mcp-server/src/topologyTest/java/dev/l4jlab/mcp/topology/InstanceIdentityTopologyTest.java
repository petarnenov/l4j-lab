package dev.l4jlab.mcp.topology;

import io.micronaut.http.HttpRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature 010, T041 (FR-018, US5). The instance identifier, where it can actually be checked.
 *
 * <p>An in-process test can prove the field is present; only three replicas behind a proxy can prove
 * it distinguishes them. That is the whole point of finding F-003: the field is worth nothing if all
 * three carry the same value.
 *
 * <p>Requires {@code make mcp-up-topology}, which publishes the replicas individually.
 */
class InstanceIdentityTopologyTest extends TopologyFixture {

    @Test
    @DisplayName("a call addressed to a named replica is answered by that replica")
    void aCallToANamedReplicaIsAnsweredByThatReplica() {
        assertThat(instanceAt(replicaA)).isEqualTo("mcp-a");
        assertThat(instanceAt(replicaB)).isEqualTo("mcp-b");
        assertThat(instanceAt(replicaC)).isEqualTo("mcp-c");
    }

    @Test
    @DisplayName("calls through the proxy report more than one instance")
    void callsThroughTheProxyReportMoreThanOneInstance() {
        Set<String> seen = new LinkedHashSet<>();
        // Six round-robin calls over three replicas. The assertion is "more than one", not "all
        // three": a test that requires a particular distribution is a test about nginx's scheduling,
        // which is not what this proves.
        for (int i = 0; i < 6; i++) {
            seen.add(instanceAt(proxy));
        }
        assertThat(seen)
            .as("the proxy fronts three replicas and the answer now says which one")
            .hasSizeGreaterThan(1);
    }

    @SuppressWarnings("unchecked")
    private static String instanceAt(URI target) {
        String body = """
            {"jsonrpc":"2.0","id":"instance","method":"tools/list","params":{"_meta":{\
            "io.modelcontextprotocol/protocolVersion":"2026-07-28",\
            "io.modelcontextprotocol/clientCapabilities":{}}}}""";
        Map<String, Object> result = send(target, mcp("tools/list", null, body, tokenFor("admin-alpha")));
        Map<String, Object> meta = (Map<String, Object>) result.get("_meta");
        Map<String, Object> info = (Map<String, Object>) meta.get("io.modelcontextprotocol/serverInfo");
        return String.valueOf(info.get("instance"));
    }
}
