package dev.l4jlab.chain.core;

import dev.l4jlab.chain.domain.ChainRequest;
import io.micronaut.context.ApplicationContext;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Feature 005, research R-006. The hand-written runner validated every boundary between nodes. Listener
 * exceptions are swallowed by the orchestrator, so each step now validates its own output with this
 * helper. The message format is the runner's; the exception type is deliberately not a ChainFailure, so
 * the detail reaches the server log and never the screen.
 */
class BoundaryValidationTest {

    private static ApplicationContext context;
    private static BoundaryValidation validation;

    @BeforeAll
    static void start() {
        context = ApplicationContext.builder()
                .eagerInitSingletons(false)
                .properties(Map.of(
                        "datasources.default.enabled", "false",
                        "flyway.enabled", "false",
                        "l4j.model.provider", "local",
                        "l4j.model.base-url", "http://localhost:11434",
                        "l4j.model.model-id", "test-model"))
                .start();
        validation = new BoundaryValidation(context.getBean(Validator.class));
    }

    @AfterAll
    static void stop() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void returnsAValidOutputUnchanged() {
        ChainRequest request = new ChainRequest("northwind-lighting", "2025-Q2", Instant.parse("2026-09-12T10:15:30Z"));

        assertThat(validation.requireValid("PrepareRequest", request)).isSameAs(request);
    }

    @Test
    void rejectsAnInvalidOutputWithTheRunnersMessageFormat() {
        ChainRequest invalid = new ChainRequest("Not Valid", "2025-13", Instant.parse("2026-09-12T10:15:30Z"));

        assertThatThrownBy(() -> validation.requireValid("PrepareRequest", invalid))
                .isInstanceOf(BoundaryViolation.class)
                .isNotInstanceOf(ChainFailure.class)
                .hasMessageStartingWith("PrepareRequest produced an invalid ChainRequest: ")
                .hasMessageContaining("companyId ")
                .hasMessageContaining("period ")
                .hasMessageContaining("; ");
    }

    @Test
    void rejectsAMissingOutput() {
        assertThatThrownBy(() -> validation.requireValid("RetrieveRecords", null))
                .isInstanceOf(BoundaryViolation.class)
                .hasMessage("RetrieveRecords produced no output");
    }
}
