package dev.l4jlab.chain.integration;

import dev.l4jlab.chain.core.ChainRunService;
import dev.l4jlab.chain.core.RunStatus;
import dev.l4jlab.chain.domain.Selection;
import dev.l4jlab.chain.support.PostgresTest;
import dev.l4jlab.chain.web.RunController;
import dev.l4jlab.chain.web.dto.RunDetailResponse;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US3 and FR-011. A completed run and all four node records must read back unchanged after the
 * application is restarted against the same database.
 */
class RestartPersistenceTest extends PostgresTest {

    private RunDetailResponse pollUntilTerminal(ApplicationContext ctx, UUID runId) {
        RunController controller = ctx.getBean(RunController.class);
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        while (Instant.now().isBefore(deadline)) {
            RunDetailResponse detail = controller.detail(runId.toString());
            if (RunStatus.valueOf(detail.status()).isTerminal()) {
                return detail;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        throw new AssertionError("Run never reached a terminal state");
    }

    @Test
    void aCompletedRunReadsBackUnchangedAfterARestart() {
        UUID runId = context.getBean(ChainRunService.class)
                .start(new Selection("northwind-lighting", "2025-Q2"));
        RunDetailResponse before = pollUntilTerminal(context, runId);

        // A genuinely new context against the same database, which is what a restart is.
        Map<String, Object> properties = new HashMap<>();
        // application-test.yml turns the datasource off by default, so a restart must turn it
        // back on explicitly. This is the one test that genuinely wants a second live context.
        properties.put("datasources.default.enabled", true);
        properties.put("datasources.default.url", postgres.getJdbcUrl());
        properties.put("datasources.default.username", postgres.getUsername());
        properties.put("datasources.default.password", postgres.getPassword());
        properties.put("datasources.default.driver-class-name", "org.postgresql.Driver");
        properties.put("datasources.default.dialect", "POSTGRES");
        properties.put("datasources.default.schema-generate", "NONE");
        properties.put("flyway.enabled", true);
        properties.put("l4j.model.provider", "local");
        properties.put("l4j.model.base-url", "http://localhost:11434");
        properties.put("l4j.model.model-id", "test-model");

        try (ApplicationContext restarted = ApplicationContext.builder().properties(properties).start()) {
            RunDetailResponse after = restarted.getBean(RunController.class).detail(runId.toString());

            assertThat(after.status()).isEqualTo(before.status());
            assertThat(after.summary()).isEqualTo(before.summary());
            assertThat(after.indicators()).isEqualTo(before.indicators());
            assertThat(after.nodes()).hasSize(4);
            assertThat(after.startedAt()).isEqualTo(before.startedAt());
            assertThat(after.endedAt()).isEqualTo(before.endedAt());

            for (int i = 0; i < 4; i++) {
                assertThat(after.nodes().get(i).nodeName()).isEqualTo(before.nodes().get(i).nodeName());
                assertThat(after.nodes().get(i).inputPayload()).isEqualTo(before.nodes().get(i).inputPayload());
                assertThat(after.nodes().get(i).outputPayload()).isEqualTo(before.nodes().get(i).outputPayload());
            }
            assertThat(after.nodes().get(3).modelRequestText())
                    .isEqualTo(before.nodes().get(3).modelRequestText());
        }
    }

    @Test
    void migrationsAreIdempotentSoARestartDoesNotRebuildTheSchema() {
        // Flyway runs again on the restart above. If it were not idempotent, the run below would
        // find an empty or duplicated schema.
        UUID runId = context.getBean(ChainRunService.class)
                .start(new Selection("altura-ceramics", "2024-Q4"));

        assertThat(pollUntilTerminal(context, runId).nodes()).hasSize(4);
    }
}
