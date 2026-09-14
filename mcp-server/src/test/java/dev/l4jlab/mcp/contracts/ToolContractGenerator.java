package dev.l4jlab.mcp.contracts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.l4jlab.mcp.McpServerTestBase;
import dev.l4jlab.mcp.TestKeys;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Writes {@code specs/007-mcp-billing-server/contracts/tools/*.json} from what the server declares
 * (FR-011, T032).
 *
 * <p>This is the half of the single-source rule that makes it real. Feature 007 declared each tool's
 * shape twice by hand and added a comment promising a test would compare them; the test was never
 * written and the two drifted to 106 differences. Generating one from the other means a change
 * reaches both without a second edit, and {@link ToolDeclarationContractTest} fails the build when
 * someone changes the Java and forgets to regenerate.
 *
 * <p>It is a test class because booting the server is how the declarations become observable, and
 * this repository already has that machinery. It is tagged {@code generator} and excluded from the
 * ordinary suite: a test that rewrites committed files is not something to run by accident.
 *
 * <p>Run it with {@code ./gradlew :mcp-server:generateToolContracts}.
 */
@Tag("generator")
class ToolContractGenerator extends McpServerTestBase {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    /** The declaration's own key order, which is not the order a reader wants. */
    private static final List<String> FIELD_ORDER =
        List.of("name", "title", "description", "inputSchema", "outputSchema", "annotations");

    @Test
    @SuppressWarnings("unchecked")
    void writeTheCommittedContractsFromTheDeclarations() throws Exception {
        Path target = Path.of(System.getProperty("contracts.output.dir",
            "../specs/007-mcp-billing-server/contracts/tools"));
        assertThat(Files.isDirectory(target))
            .as("contracts.output.dir must name the committed contracts directory, got %s",
                target.toAbsolutePath())
            .isTrue();

        Map<String, Object> body = http.toBlocking().retrieve(mcp("tools/list", """
            {"jsonrpc":"2.0","id":"gen","method":"tools/list","params":{}}""", TestKeys.ADMIN_ALPHA),
            Map.class);
        Map<String, Object> result = (Map<String, Object>) body.get("result");
        List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
        assertThat(tools).hasSize(5);

        for (Map<String, Object> tool : tools) {
            String name = String.valueOf(tool.get("name"));
            Path file = target.resolve(name + ".json");
            Files.writeString(file, MAPPER.writeValueAsString(ordered(tool)) + "\n");
        }
    }

    /** Stable key order, so a regeneration that changes nothing produces no diff. */
    private static Object ordered(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((k, v) -> sorted.put(String.valueOf(k), ordered(v)));

            Map<String, Object> out = new LinkedHashMap<>();
            FIELD_ORDER.forEach(field -> {
                if (sorted.containsKey(field)) {
                    out.put(field, sorted.remove(field));
                }
            });
            out.putAll(sorted);
            return out;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(ToolContractGenerator::ordered).toList();
        }
        return value;
    }
}
