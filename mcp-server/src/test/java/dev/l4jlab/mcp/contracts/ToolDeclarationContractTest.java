package dev.l4jlab.mcp.contracts;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.l4jlab.mcp.McpServerTestBase;
import dev.l4jlab.mcp.TestKeys;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature 010, T004: the test {@code mcp-server/build.gradle.kts} has always said exists.
 *
 * <p>That file's comment reads: <em>"a contract test asserts the generated schema matches the
 * committed JSON, so Principle III's ordering holds and drift fails the build."</em> No such test was
 * written. The contracts were copied into {@code build/resources/test/contracts/} and nothing read
 * them, which is how eighteen schema keywords, two descriptions and one output shape drifted for a
 * week without anything noticing (feature 008 finding F-001, feature 010 research R-003).
 *
 * <p>This is that test. It compares what the server declares against what this repository commits,
 * field by field, and fails on any difference. It is deliberately unforgiving: a check that tolerates
 * "close enough" is how the two drifted apart in the first place.
 */
class ToolDeclarationContractTest extends McpServerTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<String> TOOLS = List.of(
        "search_billing_runs", "get_billing_run_status", "get_run_failures",
        "post_fee_adjustment", "start_billing_run");

    @Test
    void everyToolDeclaresWhatTheRepositoryCommitted() throws Exception {
        Map<String, Map<String, Object>> served = servedDeclarations();
        List<String> differences = new ArrayList<>();

        for (String name : TOOLS) {
            Map<String, Object> committed = committedContract(name);
            Map<String, Object> declaration = served.get(name);
            assertThat(declaration).as("the server does not serve %s at all", name).isNotNull();

            compare(name + ".description", committed.get("description"), declaration.get("description"),
                differences);
            compare(name + ".annotations", committed.get("annotations"), declaration.get("annotations"),
                differences);
            compare(name + ".inputSchema", committed.get("inputSchema"), declaration.get("inputSchema"),
                differences);
            compare(name + ".outputSchema", committed.get("outputSchema"), declaration.get("outputSchema"),
                differences);
        }

        assertThat(differences)
            .as("""
                What the server declares differs from what specs/007-mcp-billing-server/contracts/tools/
                commits. One of the two is wrong, and this test does not guess which — see feature 010
                research R-002 for which side is the source.""")
            .isEmpty();
    }

    /** Every leaf that differs, named by its path, so a failure can be acted on without re-deriving it. */
    private static void compare(String path, Object committed, Object declared, List<String> differences) {
        if (committed == null && declared == null) {
            return;
        }
        if (committed == null || declared == null) {
            differences.add("%s: committed=%s declared=%s".formatted(path, committed, declared));
            return;
        }
        if (committed instanceof Map<?, ?> a && declared instanceof Map<?, ?> b) {
            Set<String> keys = new TreeSet<>();
            a.keySet().forEach(k -> keys.add(String.valueOf(k)));
            b.keySet().forEach(k -> keys.add(String.valueOf(k)));
            for (String key : keys) {
                compare(path + "." + key, a.get(key), b.get(key), differences);
            }
            return;
        }
        if (!committed.equals(declared)) {
            differences.add("%s: committed=%s declared=%s".formatted(path, committed, declared));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> servedDeclarations() {
        Map<String, Object> body = http.toBlocking().retrieve(mcp("tools/list", """
            {"jsonrpc":"2.0","id":"c1","method":"tools/list","params":{}}""", TestKeys.ADMIN_ALPHA),
            Map.class);
        Map<String, Object> result = (Map<String, Object>) body.get("result");
        List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
        Map<String, Map<String, Object>> byName = new LinkedHashMap<>();
        for (Map<String, Object> tool : tools) {
            byName.put(String.valueOf(tool.get("name")), tool);
        }
        return byName;
    }

    /**
     * The committed file, copied here by {@code copyToolContracts} in the build — the step that has
     * existed all along, feeding a reader that did not.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> committedContract(String name) throws Exception {
        try (InputStream in = ToolDeclarationContractTest.class
            .getResourceAsStream("/contracts/" + name + ".json")) {
            assertThat(in).as("committed contract for %s is not on the test classpath", name).isNotNull();
            return MAPPER.readValue(in, Map.class);
        }
    }
}
