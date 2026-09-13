package dev.l4jlab.chain.agent;

import dev.l4jlab.chain.core.BoundaryViolation;
import dev.l4jlab.chain.core.ChainFailure;
import dev.l4jlab.chain.core.RunStatus;
import dev.l4jlab.chain.model.ModelProperties;
import dev.langchain4j.guardrail.OutputGuardrailException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature 005, contracts/trace-and-outcomes.md "Outcome classification". The chain's orchestration
 * changed; what a learner reads when a run fails must not. The first four cases are carried over from
 * the former SummarizeNodeTest, whose node used to produce these reasons itself.
 */
class FailureClassifierTest {

    private static final String CREDENTIAL = "sk-test-do-not-leak-0123456789";

    private FailureClassifier classifier;

    @BeforeEach
    void setUp() {
        ModelProperties properties = new ModelProperties();
        properties.setProvider("cloud");
        properties.setBaseUrl("https://ollama.com");
        properties.setModelId("gpt-oss:120b");
        properties.setApiKey(CREDENTIAL);
        properties.setTimeoutSeconds(45);
        classifier = new FailureClassifier(properties);
    }

    @Test
    void reachesTimedOutRatherThanFailedWhenTheModelExceedsTheLimit() {
        FailureClassifier.Outcome outcome = classifier.classify(
                "Summarize",
                new RuntimeException("request timed out after 45s", new SocketTimeoutException("Read timed out")));

        assertThat(outcome.status()).isEqualTo(RunStatus.TIMED_OUT);
        assertThat(outcome.failedNode()).isEqualTo("Summarize");
        assertThat(outcome.reason()).contains("45 seconds").contains("indicators above were computed");
    }

    @Test
    void aRejectedCredentialProducesAReasonTheLearnerCanActOn() {
        FailureClassifier.Outcome outcome =
                classifier.classify("Summarize", new RuntimeException("{\"error\":\"Unauthorized\"}"));

        assertThat(outcome.status()).isEqualTo(RunStatus.FAILED);
        assertThat(outcome.reason())
                .contains("rejected the credential")
                .contains("OLLAMA_API_KEY")
                .contains("L4J_PROVIDER")
                .doesNotContain(CREDENTIAL);
    }

    @Test
    void aForbiddenResponseIsTreatedAsARejectedCredentialToo() {
        FailureClassifier.Outcome outcome =
                classifier.classify("Summarize", new RuntimeException("HTTP 403 Forbidden"));

        assertThat(outcome.reason()).contains("rejected the credential");
    }

    @Test
    void anUnreachableProviderFailsWithoutEchoingTheCredential() {
        FailureClassifier.Outcome outcome = classifier.classify(
                "Summarize", new RuntimeException("401 Unauthorized for header Authorization: Bearer " + CREDENTIAL));

        assertThat(outcome.status()).isEqualTo(RunStatus.FAILED);
        assertThat(outcome.reason()).doesNotContain(CREDENTIAL).contains("<redacted>");
    }

    @Test
    void aHandledStepFailurePassesItsOwnMessageThrough() {
        ChainFailure failure = new ChainFailure("RetrieveRecords", "The sample dataset holds no record for 1999-Q1.");

        FailureClassifier.Outcome outcome =
                classifier.classify("RetrieveRecords", new RuntimeException("wrapped", failure));

        assertThat(outcome.status()).isEqualTo(RunStatus.FAILED);
        assertThat(outcome.failedNode()).isEqualTo("RetrieveRecords");
        assertThat(outcome.reason()).isEqualTo("The sample dataset holds no record for 1999-Q1.");
    }

    @Test
    void anEmptyModelResponseStoppedByTheGuardrailSaysTheResponseWasEmpty() {
        FailureClassifier.Outcome outcome = classifier.classify(
                "Summarize", new RuntimeException(new OutputGuardrailException("The model returned an empty response")));

        assertThat(outcome.status()).isEqualTo(RunStatus.FAILED);
        assertThat(outcome.reason()).contains("empty response").contains("only the summary is missing");
    }

    @Test
    void anyOtherErrorAtSummarizeIsReportedAsTheModelNotBeingReachable() {
        // SummarizeNode turned every runtime exception from the model call into this reason.
        FailureClassifier.Outcome outcome =
                classifier.classify("Summarize", new IllegalStateException("boom ".repeat(200)));

        assertThat(outcome.status()).isEqualTo(RunStatus.FAILED);
        assertThat(outcome.reason())
                .startsWith("The model could not be reached or rejected the request.")
                .doesNotContain("\tat ", ".java:")
                .hasSizeLessThan(500);
    }

    @Test
    void anUnexpectedErrorInADeterministicStepGetsTheGenericReason() {
        FailureClassifier.Outcome outcome =
                classifier.classify("ComputeIndicators", new IllegalArgumentException("index 7 out of bounds"));

        assertThat(outcome.status()).isEqualTo(RunStatus.FAILED);
        assertThat(outcome.failedNode()).isEqualTo("ComputeIndicators");
        assertThat(outcome.reason()).isEqualTo("ComputeIndicators failed unexpectedly. See the server log for detail.");
    }

    @Test
    void aBoundaryViolationShowsTheGenericReasonNotTheValidationDetail() {
        FailureClassifier.Outcome outcome = classifier.classify(
                "PrepareRequest",
                new BoundaryViolation("PrepareRequest produced an invalid ChainRequest: period must match"));

        assertThat(outcome.reason()).isEqualTo("PrepareRequest failed unexpectedly. See the server log for detail.");
    }

    @Test
    void aDeterministicStepMentioningATimeoutOrA401IsNeverATimeoutOrACredentialProblem() {
        // Only the summarizing step's model call ever produced those outcomes.
        FailureClassifier.Outcome timeout =
                classifier.classify("RetrieveRecords", new IllegalStateException("connection timed out"));
        FailureClassifier.Outcome unauthorized =
                classifier.classify("RetrieveRecords", new IllegalStateException("401 Unauthorized"));

        assertThat(timeout.status()).isEqualTo(RunStatus.FAILED);
        assertThat(timeout.reason()).isEqualTo("RetrieveRecords failed unexpectedly. See the server log for detail.");
        assertThat(unauthorized.status()).isEqualTo(RunStatus.FAILED);
        assertThat(unauthorized.reason()).isEqualTo("RetrieveRecords failed unexpectedly. See the server log for detail.");
    }

    @Test
    void anErrorBeforeAnyStepKeepsThePreviouslyStoredOutcome() {
        FailureClassifier.Outcome outcome = classifier.classify(null, new IllegalStateException("scope creation failed"));

        assertThat(outcome.status()).isEqualTo(RunStatus.FAILED);
        assertThat(outcome.failedNode()).isEqualTo("ChainRunner");
        assertThat(outcome.reason()).isEqualTo("The run stopped unexpectedly before the chain completed.");
    }
}
