package dev.l4jlab.chain.live;

import dev.l4jlab.chain.agent.NonBlankSummaryGuardrail;
import dev.l4jlab.chain.agent.Summarizer;
import dev.l4jlab.chain.domain.Indicator;
import dev.l4jlab.chain.domain.IndicatorSet;
import dev.l4jlab.chain.domain.RunSummary;
import dev.l4jlab.chain.model.ChatModelFactory;
import dev.l4jlab.chain.model.ModelProperties;
import dev.langchain4j.agentic.AgenticServices;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one test that reaches a real provider.
 *
 * <p>It lives in its own source set with its own Gradle task, {@code ./gradlew :backend:liveTest},
 * excluded from {@code check}. Selecting it is a deliberate act rather than a flag someone has to
 * remember (Principle IV, R-007).
 *
 * <p>Provider mode is read from configuration like everything else, so this exercises whichever
 * backend is configured. It never asserts the model's exact words, only structure and the invariants
 * that must hold whatever the model says.
 */
class SummarizerLiveTest {

    private ModelProperties properties;

    @BeforeEach
    void requireCredential() {
        String provider = System.getenv("L4J_PROVIDER");
        String apiKey = System.getenv("OLLAMA_API_KEY");
        boolean hasKey = apiKey != null && !apiKey.isBlank();

        // Skip, never fail, when no provider has been configured. The constitution requires an
        // explicit message naming what is missing rather than a silent pass or a confusing
        // connection error (Principle IV, R-007).
        Assumptions.assumeTrue(
                provider != null || hasKey,
                "Skipped: no provider is configured. Set OLLAMA_API_KEY with L4J_PROVIDER=cloud to "
                        + "run against Ollama Cloud, or L4J_PROVIDER=local with a running local Ollama.");

        if (provider == null) {
            provider = "cloud";
        }

        Assumptions.assumeTrue(
                !"cloud".equalsIgnoreCase(provider) || hasKey,
                "Skipped: L4J_PROVIDER=cloud but OLLAMA_API_KEY is not set. Set the credential to "
                        + "run the live model test, or set L4J_PROVIDER=local to use a local Ollama.");

        System.out.println("Live test provider mode: " + provider.toUpperCase());

        properties = new ModelProperties();
        properties.setProvider(provider);
        properties.setBaseUrl(
                System.getenv().getOrDefault(
                        "L4J_MODEL_BASE_URL",
                        "cloud".equalsIgnoreCase(provider) ? "https://ollama.com" : "http://localhost:11434"));
        properties.setModelId(System.getenv().getOrDefault("L4J_MODEL_ID", "gpt-oss:120b"));
        properties.setApiKey(apiKey == null ? "" : apiKey);
        properties.setTimeoutSeconds(
                Integer.parseInt(System.getenv().getOrDefault("L4J_MODEL_TIMEOUT_SECONDS", "45")));
    }

    private static IndicatorSet indicators() {
        return new IndicatorSet(
                "Northwind Lighting (fictional)",
                "2025-Q2",
                List.of(
                        Indicator.of("revenueGrowth", new BigDecimal("0.0600"), List.of("revenue")),
                        Indicator.of("grossMargin", new BigDecimal("0.3800"), List.of("revenue")),
                        Indicator.of("netMargin", new BigDecimal("0.0900"), List.of("netIncome")),
                        Indicator.of("currentRatio", new BigDecimal("1.9000"), List.of("currentAssets")),
                        Indicator.notApplicable("debtToEquity", "Equity is zero", List.of("equity"))));
    }

    @Test
    void aRealProviderReturnsAUsableSummary() throws Exception {
        // Feature 005: the summarizer is a declared AI agent, built the way FinancialChainFactory builds it.
        Summarizer summarizer = AgenticServices.agentBuilder(Summarizer.class)
                .chatModel(new ChatModelFactory(properties).chatModel())
                .outputGuardrails(new NonBlankSummaryGuardrail())
                .build();

        RunSummary summary = RunSummary.from(summarizer.summarize(indicators()), null, properties);

        // Structure and invariants only. Never the model's exact words (Principle IV).
        assertThat(summary.text()).isNotBlank();
        assertThat(summary.text()).hasSizeLessThanOrEqualTo(RunSummary.MAX_TEXT_LENGTH);
        assertThat(summary.modelId()).isEqualTo(properties.getModelId());
        assertThat(summary.providerMode()).isEqualTo(properties.providerMode());

        if (properties.hasApiKey()) {
            assertThat(summary.text()).doesNotContain(properties.getApiKey());
        }

        System.out.println(
                "Live run against " + summary.providerMode() + " using " + summary.modelId()
                        + " (token counts are recorded by the run trace, not by this direct call)");
    }
}
