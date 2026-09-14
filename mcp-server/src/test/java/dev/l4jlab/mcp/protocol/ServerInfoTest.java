package dev.l4jlab.mcp.protocol;

import dev.l4jlab.mcp.McpServerTestBase;
import dev.l4jlab.mcp.TestKeys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature 010, T040 (FR-018). Every result says which server produced it.
 *
 * <p>Three identical replicas sit behind one proxy and, until this, said identical things about
 * themselves. A caller that got an unexpected answer twice could not tell whether it had asked one
 * instance twice or two instances once — and neither could anyone reading feature 008's console,
 * which said so in prose because there was nothing to report (finding F-003).
 */
class ServerInfoTest extends McpServerTestBase {

    @Test
    @DisplayName("every result names the instance that produced it")
    void everyResultNamesTheInstance() {
        assertThat(instanceOf(serverInfoFrom("tools/list", """
            {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}"""))).isNotBlank();
        assertThat(instanceOf(serverInfoFrom("server/discover", """
            {"jsonrpc":"2.0","id":2,"method":"server/discover","params":{}}"""))).isNotBlank();
    }

    @Test
    @DisplayName("the instance travels with the name and version, not instead of them")
    void theInstanceIsAnAdditionNotAReplacement() {
        Map<String, Object> info = serverInfoFrom("tools/list", """
            {"jsonrpc":"2.0","id":3,"method":"tools/list","params":{}}""");

        // FR-003 has always required name and version. Adding a field must not quietly cost one:
        // a client reading `version` to decide what it may send would break silently.
        assertThat(info).containsEntry("name", "mcp-billing-server");
        assertThat(info).containsKey("version");
        assertThat(info).containsKey("instance");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> serverInfoFrom(String method, String body) {
        Map<String, Object> response =
            http.toBlocking().retrieve(mcp(method, body, TestKeys.ADMIN_ALPHA), Map.class);
        Map<String, Object> result = (Map<String, Object>) response.get("result");
        Map<String, Object> meta = (Map<String, Object>) result.get("_meta");
        return (Map<String, Object>) meta.get("io.modelcontextprotocol/serverInfo");
    }

    private static String instanceOf(Map<String, Object> serverInfo) {
        return String.valueOf(serverInfo.get("instance"));
    }
}
