package dev.l4jlab.chain.core;

import dev.l4jlab.chain.agent.FailureClassifier;
import dev.l4jlab.chain.agent.RunTraceAssembler;
import dev.l4jlab.chain.domain.RunSummary;
import dev.l4jlab.chain.domain.Selection;
import dev.l4jlab.chain.model.ModelProperties;
import dev.l4jlab.chain.model.ProviderMode;
import dev.l4jlab.chain.support.ChainRuns;
import dev.l4jlab.chain.support.Validations;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature 005: the branches of turning a run's trace into its result that the running chain cannot easily reach, or
 * cannot reach at all. No database: {@link ChainRunService#toResult} is exercised directly, and the repositories it
 * does not use are absent.
 */
class ChainRunServiceTest {

    private static final String GENERIC_SUMMARIZE = "Summarize failed unexpectedly. See the server log for detail.";

    private static ModelProperties properties() {
        ModelProperties properties = new ModelProperties();
        properties.setProvider("local");
        properties.setBaseUrl("http://localhost:11434");
        properties.setModelId("test-model");
        return properties;
    }

    /** A service whose only used part is toResult: the chain, the database, and the executor are absent. */
    private static ChainRunService service(FailureClassifier classifier) {
        return new ChainRunService(null, null, null, null, classifier, Validations.boundary(), null, null, properties(), null, null);
    }

    private static NodeRecord record(int position, String name, Object output, String request, String response) {
        return new NodeRecord(position, name, "input", output, true, null, Instant.EPOCH, 1L, request, response,
                output instanceof RunSummary ? 120 : null, output instanceof RunSummary ? 80 : null);
    }

    @Test
    void anInvalidSummaryFailsAtSummarizeAndKeepsTheModelExchange() {
        // Not reachable through the running chain: a blank model id stops startup (research R-006).
        RunSummary invalid = new RunSummary("A summary.", "", ProviderMode.LOCAL, 120, 80);
        RunTraceAssembler.RunTrace trace = new RunTraceAssembler.RunTrace(List.of(
                record(1, "PrepareRequest", "request", null, null),
                record(2, "RetrieveRecords", "records", null, null),
                record(3, "ComputeIndicators", "indicators", null, null),
                record(4, "Summarize", invalid, "the table", "A summary.")), null);

        ChainResult result = service(new FailureClassifier(properties())).toResult(trace, null, null);

        assertThat(result.status()).isEqualTo(RunStatus.FAILED);
        assertThat(result.failedNode()).isEqualTo("Summarize");
        assertThat(result.failureReason()).isEqualTo(GENERIC_SUMMARIZE);
        NodeRecord summarize = result.nodeRecords().get(3);
        assertThat(summarize.succeeded()).isFalse();
        assertThat(summarize.outputPayload()).isNull();
        assertThat(summarize.failureReason()).isEqualTo(GENERIC_SUMMARIZE);
        assertThat(summarize.modelRequestText()).isEqualTo("the table");
        assertThat(summarize.modelResponseText()).isEqualTo("A summary.");
        assertThat(summarize.inputTokens()).isNull();
        assertThat(result.summary()).isNull();
    }

    @Test
    void anErrorBeforeAnyStepKeepsThePreviouslyStoredOutcomeAndHasNoRecords() {
        RunTraceAssembler.RunTrace trace = new RunTraceAssembler.RunTrace(List.of(), null);

        ChainResult result = service(new FailureClassifier(properties()))
                .toResult(trace, null, new IllegalStateException("scope creation failed"));

        assertThat(result.status()).isEqualTo(RunStatus.FAILED);
        assertThat(result.failedNode()).isEqualTo("ChainRunner");
        assertThat(result.failureReason()).isEqualTo("The run stopped unexpectedly before the chain completed.");
        assertThat(result.nodeRecords()).isEmpty();
    }

    @Test
    void aDeterministicStepErrorMentioningATimeoutIsAFailureNotATimeout() {
        RunTraceAssembler.RunTrace trace = new RunTraceAssembler.RunTrace(
                List.of(record(1, "PrepareRequest", "request", null, null)), "RetrieveRecords");

        ChainResult result = service(new FailureClassifier(properties()))
                .toResult(trace, null, new RuntimeException(new IllegalStateException("connection timed out")));

        assertThat(result.status()).isEqualTo(RunStatus.FAILED);
        assertThat(result.failureReason()).isEqualTo("RetrieveRecords failed unexpectedly. See the server log for detail.");
    }

    @Test
    void aBrokenBoundaryThroughTheRealSequenceShowsTheGenericReasonNotTheValidationDetail() {
        try (ApplicationContext context = ApplicationContext.builder()
                .environments("test", "invalid-boundary")
                .eagerInitSingletons(false)
                .properties(Map.of(
                        "datasources.default.enabled", "false",
                        "flyway.enabled", "false",
                        "l4j.model.provider", "local",
                        "l4j.model.base-url", "http://localhost:11434",
                        "l4j.model.model-id", "test-model"))
                .start()) {
            ChainRuns.Run run = ChainRuns.run(context, new Selection("northwind-lighting", "2025-Q2"));
            RuntimeException thrown = run.thrown();
            ChainResult result = service(context.getBean(FailureClassifier.class)).toResult(run.trace(), run.indicators(), thrown);

            assertThat(thrown).isNotNull();
            assertThat(result.status()).isEqualTo(RunStatus.FAILED);
            assertThat(result.failedNode()).isEqualTo("ComputeIndicators");
            assertThat(result.failureReason())
                    .isEqualTo("ComputeIndicators failed unexpectedly. See the server log for detail.")
                    .doesNotContain("companyName");
            assertThat(result.nodeRecords()).hasSize(3);
            assertThat(result.nodeRecords().get(2).succeeded()).isFalse();
        }
    }
}
