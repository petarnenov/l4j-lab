package dev.l4jlab.chain.integration;

import dev.l4jlab.chain.core.ChainRunService;
import dev.l4jlab.chain.core.RunStatus;
import dev.l4jlab.chain.domain.Selection;
import dev.l4jlab.chain.support.PostgresTest;
import dev.l4jlab.chain.web.CatalogController;
import dev.l4jlab.chain.web.RunController;
import dev.l4jlab.chain.web.dto.CatalogResponse;
import dev.l4jlab.chain.web.dto.RunDetailResponse;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Feature 005, research R-010. The chain's orchestration was rewritten from a hand-written runner to
 * LangChain4j's agentic sequence, and the API, the stored records, and the indicator values must not
 * change. This test is the evidence. Its files were written from the code before the rewrite, for
 * every company and period in the sample dataset, and every later run must reproduce them byte for
 * byte.
 *
 * <p>Only what legitimately differs between two runs is removed before comparing: run identifiers,
 * timestamps, durations, and the request timestamp the first node stamps. The fake model's fixed
 * reply makes the summary and the model exchange comparable too.
 *
 * <p>To rewrite the files, which should only ever be done from code whose behavior is already known to
 * be right, run with the environment variable {@code GOLDEN_WRITE=1}.
 */
class GoldenRunSnapshotTest extends PostgresTest {

    private static final Path GOLDEN = Path.of("src/test/resources/golden");
    private static final Set<String> VOLATILE_KEYS =
            Set.of("runId", "startedAt", "endedAt", "durationMs", "requestedAt");

    @Test
    void everySelectionMatchesItsGoldenSnapshot() throws IOException {
        boolean write = "1".equals(System.getenv("GOLDEN_WRITE"));
        CatalogResponse catalog = context.getBean(CatalogController.class).catalog();
        JsonMapper json = JsonMapper.createDefault();
        List<String> mismatches = new ArrayList<>();
        int selections = 0;

        if (write) {
            Files.createDirectories(GOLDEN);
        }
        for (CatalogResponse.CompanyEntry company : catalog.companies()) {
            for (String period : company.periods()) {
                selections++;
                String canonical = canonical(json, runToCompletion(company.companyId(), period));
                Path file = GOLDEN.resolve(company.companyId() + "_" + period + ".json");
                if (write) {
                    Files.writeString(file, canonical, StandardCharsets.UTF_8);
                    continue;
                }
                if (!Files.exists(file)) {
                    mismatches.add(file + " is missing");
                    continue;
                }
                String expected = Files.readString(file, StandardCharsets.UTF_8);
                if (!expected.equals(canonical)) {
                    mismatches.add(file.getFileName() + ": " + firstDifference(json, expected, canonical));
                }
            }
        }

        assertThat(selections).as("selections in the catalog").isPositive();
        if (!mismatches.isEmpty()) {
            fail("Runs differ from the golden snapshot:\n" + String.join("\n", mismatches));
        }
    }

    private RunDetailResponse runToCompletion(String companyId, String period) {
        UUID runId = context.getBean(ChainRunService.class).start(new Selection(companyId, period));
        RunController controller = context.getBean(RunController.class);
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        while (Instant.now().isBefore(deadline)) {
            RunDetailResponse detail = controller.detail(runId.toString());
            if (RunStatus.valueOf(detail.status()).isTerminal()) {
                return detail;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("Run for " + companyId + " " + period + " never reached a terminal state");
    }

    /** Serialized as the API would, volatile keys removed, object keys sorted, one line per file plus newline. */
    private static String canonical(JsonMapper json, RunDetailResponse detail) throws IOException {
        Object tree = json.readValue(json.writeValueAsBytes(detail), Argument.OBJECT_ARGUMENT);
        return new String(json.writeValueAsBytes(normalise(tree)), StandardCharsets.UTF_8) + "\n";
    }

    private static Object normalise(Object node) {
        if (node instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            map.forEach((key, value) -> {
                if (!VOLATILE_KEYS.contains(String.valueOf(key))) {
                    sorted.put(String.valueOf(key), normalise(value));
                }
            });
            return sorted;
        }
        if (node instanceof List<?> list) {
            return list.stream().map(GoldenRunSnapshotTest::normalise).toList();
        }
        return node;
    }

    /** The path of the first value that differs, so a failure says what changed rather than only that something did. */
    private static String firstDifference(JsonMapper json, String expected, String actual) throws IOException {
        Object a = json.readValue(expected, Argument.OBJECT_ARGUMENT);
        Object b = json.readValue(actual, Argument.OBJECT_ARGUMENT);
        String path = diff("$", a, b);
        return path == null ? "formatting only" : path;
    }

    private static String diff(String path, Object a, Object b) {
        if (a instanceof Map<?, ?> ma && b instanceof Map<?, ?> mb) {
            TreeMap<String, Object> keys = new TreeMap<>();
            ma.keySet().forEach(k -> keys.put(String.valueOf(k), null));
            mb.keySet().forEach(k -> keys.put(String.valueOf(k), null));
            for (String key : keys.keySet()) {
                String found = diff(path + "." + key, ma.get(key), mb.get(key));
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        if (a instanceof List<?> la && b instanceof List<?> lb) {
            if (la.size() != lb.size()) {
                return path + " has " + la.size() + " entries, now " + lb.size();
            }
            for (int i = 0; i < la.size(); i++) {
                String found = diff(path + "[" + i + "]", la.get(i), lb.get(i));
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        return java.util.Objects.equals(a, b) ? null : path + " was " + a + ", now " + b;
    }
}
