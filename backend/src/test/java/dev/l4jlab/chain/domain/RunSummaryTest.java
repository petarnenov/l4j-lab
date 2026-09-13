package dev.l4jlab.chain.domain;

import dev.l4jlab.chain.model.ModelProperties;
import dev.l4jlab.chain.model.ProviderMode;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature 005. The summarizing step's stored output is built from the model's reply by this factory,
 * rather than inside the old summarizing node. Truncation and the token cases are carried over from the
 * former SummarizeNodeTest.
 */
class RunSummaryTest {

    private static final String CREDENTIAL = "sk-test-do-not-leak-0123456789";

    private static ModelProperties properties() {
        ModelProperties properties = new ModelProperties();
        properties.setProvider("cloud");
        properties.setBaseUrl("https://ollama.com");
        properties.setModelId("gpt-oss:120b");
        properties.setApiKey(CREDENTIAL);
        return properties;
    }

    @Test
    void carriesTheTextModelIdentifierProviderModeAndTokens() {
        RunSummary summary = RunSummary.from("Revenue grew 0.2500.", new TokenUsage(120, 80), properties());

        assertThat(summary.text()).isEqualTo("Revenue grew 0.2500.");
        assertThat(summary.modelId()).isEqualTo("gpt-oss:120b");
        assertThat(summary.providerMode()).isEqualTo(ProviderMode.CLOUD);
        assertThat(summary.inputTokens()).isEqualTo(120);
        assertThat(summary.outputTokens()).isEqualTo(80);
    }

    @Test
    void truncatesAnOverLongResponseAndMarksTheTruncation() {
        RunSummary summary = RunSummary.from("0.2500 ".repeat(2000), new TokenUsage(1, 1), properties());

        assertThat(summary.text()).hasSizeLessThanOrEqualTo(RunSummary.MAX_TEXT_LENGTH);
        assertThat(summary.text()).endsWith(RunSummary.TRUNCATION_MARKER);
    }

    @Test
    void toleratesAProviderThatReportsNoTokenCounts() {
        RunSummary summary = RunSummary.from("A summary.", null, properties());

        assertThat(summary.inputTokens()).isNull();
        assertThat(summary.outputTokens()).isNull();
    }

    @Test
    void neverCarriesTheCredential() {
        RunSummary summary = RunSummary.from("A summary.", new TokenUsage(1, 1), properties());

        assertThat(summary.toString()).doesNotContain(CREDENTIAL);
    }
}
