package dev.l4jlab.chain.node;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.l4jlab.chain.core.ChainFailure;
import dev.l4jlab.chain.core.ChainRunner;
import dev.l4jlab.chain.core.ChainTimeoutException;
import dev.l4jlab.chain.domain.Indicator;
import dev.l4jlab.chain.domain.IndicatorSet;
import dev.l4jlab.chain.domain.RunSummary;
import dev.l4jlab.chain.model.ModelProperties;
import dev.l4jlab.chain.model.ProviderMode;
import dev.l4jlab.chain.support.FakeChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SummarizeNodeTest {

    private static final String CREDENTIAL = "sk-test-do-not-leak-0123456789";

    private FakeChatModel model;
    private ModelProperties properties;
    private SummarizeNode node;

    @BeforeEach
    void setUp() {
        ChainRunner.ModelExchangeHolder.take();
        model = new FakeChatModel();
        properties = new ModelProperties();
        properties.setProvider("cloud");
        properties.setBaseUrl("https://ollama.com");
        properties.setModelId("gpt-oss:120b");
        properties.setApiKey(CREDENTIAL);
        properties.setTimeoutSeconds(45);
        node = new SummarizeNode(model, properties);
    }

    private static IndicatorSet indicators() {
        return new IndicatorSet(
                "Acme Widgets (fictional)",
                "2025-Q2",
                List.of(
                        Indicator.of("revenueGrowth", new BigDecimal("0.2500"), List.of("revenue")),
                        Indicator.of("grossMargin", new BigDecimal("0.4000"), List.of("revenue")),
                        Indicator.of("netMargin", new BigDecimal("0.1200"), List.of("netIncome")),
                        Indicator.of("currentRatio", new BigDecimal("2.0000"), List.of("currentAssets")),
                        Indicator.notApplicable("debtToEquity", "Equity is zero", List.of("equity"))));
    }

    @Test
    void namesItselfAsTheContractSpellsIt() {
        assertThat(node.name()).isEqualTo("Summarize");
    }

    @Test
    void sendsTheSystemInstructionAndTheRenderedIndicatorTable() throws Exception {
        node.run(indicators());

        List<ChatMessage> messages = model.lastRequest().messages();
        String systemText = ((SystemMessage) messages.getFirst()).text();
        String userText = ((UserMessage) messages.get(1)).singleText();

        assertThat(model.callCount()).isEqualTo(1);

        // The three instructions that carry weight: fictional framing, verbatim values so SC-003
        // stays checkable, and no advice (R-009).
        assertThat(systemText).contains("fictional");
        assertThat(systemText).contains("exactly as written");
        assertThat(systemText).contains("no recommendation");

        // The rendered indicator table, including the not-applicable entry with its reason.
        assertThat(userText).contains("Acme Widgets (fictional)", "2025-Q2");
        assertThat(userText).contains("revenueGrowth = 0.2500");
        assertThat(userText).contains("debtToEquity = not applicable (Equity is zero)");
    }

    @Test
    void returnsTheModelTextWithTheModelIdentifierAndProviderMode() throws Exception {
        model.respondWith("Revenue grew 0.2500 over the prior quarter.");

        RunSummary summary = node.run(indicators());

        assertThat(summary.text()).isEqualTo("Revenue grew 0.2500 over the prior quarter.");
        assertThat(summary.modelId()).isEqualTo("gpt-oss:120b");
        assertThat(summary.providerMode()).isEqualTo(ProviderMode.CLOUD);
        assertThat(summary.inputTokens()).isEqualTo(120);
        assertThat(summary.outputTokens()).isEqualTo(80);
    }

    @Test
    void failsWithANamedReasonWhenTheModelReturnsNothing() {
        model.respondWith("   ");

        assertThatThrownBy(() -> node.run(indicators()))
                .isInstanceOf(ChainFailure.class)
                .hasMessageContaining("empty response");
    }

    @Test
    void truncatesAnOverLongResponseAndMarksTheTruncation() throws Exception {
        model.respondWith("0.2500 ".repeat(2000));

        RunSummary summary = node.run(indicators());

        assertThat(summary.text()).hasSizeLessThanOrEqualTo(RunSummary.MAX_TEXT_LENGTH);
        assertThat(summary.text()).endsWith(RunSummary.TRUNCATION_MARKER);
    }

    @Test
    void theCredentialAppearsNowhereInTheOutputOrTheTrace() throws Exception {
        // FR-018. The credential lives only in the header supplier the factory builds.
        model.respondWith("A summary mentioning 0.2500 and 0.4000.");

        RunSummary summary = node.run(indicators());
        ChainRunner.ModelExchange exchange = ChainRunner.ModelExchangeHolder.take();

        assertThat(summary.text()).doesNotContain(CREDENTIAL);
        assertThat(summary.toString()).doesNotContain(CREDENTIAL);
        assertThat(exchange.requestText()).doesNotContain(CREDENTIAL);
        assertThat(exchange.responseText()).doesNotContain(CREDENTIAL);
        assertThat(properties.toString()).doesNotContain(CREDENTIAL);
    }

    @Test
    void everyNumberInTheResponseIsTraceableToAnIndicator() throws Exception {
        // SC-003: no invented figures. A fabricated number must fail this test.
        model.respondWith(
                "Revenue grew 0.2500 on the quarter. Gross margin held at 0.4000 and net margin at "
                        + "0.1200, with a current ratio of 2.0000. Debt to equity is not applicable.");

        RunSummary summary = node.run(indicators());

        assertThat(numbersIn(summary.text())).isSubsetOf(allowedNumbers(indicators()));
    }

    @Test
    void aFabricatedFigureIsDetectedByTheSameCheck() throws Exception {
        model.respondWith("Revenue grew 0.2500, and the debt to equity ratio sits at 1.7500.");

        RunSummary summary = node.run(indicators());

        // 1.7500 was never computed. The guard that protects SC-003 must see it.
        Set<BigDecimal> allowed = allowedNumbers(indicators());
        assertThat(numbersIn(summary.text()))
                .as("a fabricated figure must not be traceable to any computed indicator")
                .anySatisfy(n -> assertThat(allowed).doesNotContain(n));
    }

    @Test
    void reachesTimedOutRatherThanFailedWhenTheModelExceedsTheLimit() {
        // FR-019 and the slow-model edge case. TIMED_OUT is a different outcome to the learner.
        model.failWith(new RuntimeException("request timed out after 45s",
                new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> node.run(indicators()))
                .isInstanceOf(ChainTimeoutException.class)
                .hasMessageContaining("45 seconds")
                .hasMessageContaining("indicators above were computed");
    }

    @Test
    void aRejectedCredentialProducesAReasonTheLearnerCanActOn() {
        // FR-015. {"error":"Unauthorized"} alone told the learner nothing about what to change.
        model.failWith(new RuntimeException("{\"error\":\"Unauthorized\"}"));

        assertThatThrownBy(() -> node.run(indicators()))
                .isInstanceOf(ChainFailure.class)
                .hasMessageContaining("rejected the credential")
                .hasMessageContaining("OLLAMA_API_KEY")
                .hasMessageContaining("L4J_PROVIDER")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(CREDENTIAL));
    }

    @Test
    void aForbiddenResponseIsTreatedAsARejectedCredentialToo() {
        model.failWith(new RuntimeException("HTTP 403 Forbidden"));

        assertThatThrownBy(() -> node.run(indicators()))
                .isInstanceOf(ChainFailure.class)
                .hasMessageContaining("rejected the credential");
    }

    @Test
    void anUnreachableProviderFailsWithoutEchoingTheCredential() {
        model.failWith(new RuntimeException("401 Unauthorized"));

        assertThatThrownBy(() -> node.run(indicators()))
                .isInstanceOf(ChainFailure.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(CREDENTIAL));
    }

    @Test
    void recordsTheExactPromptEvenWhenTheCallFails() {
        model.failWith(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> node.run(indicators())).isInstanceOf(ChainFailure.class);

        ChainRunner.ModelExchange exchange = ChainRunner.ModelExchangeHolder.take();
        assertThat(exchange.requestText()).contains("revenueGrowth = 0.2500");
        assertThat(exchange.responseText()).isNull();
    }

    @Test
    void toleratesAProviderThatReportsNoTokenCounts() throws Exception {
        model.withoutTokenUsage();

        RunSummary summary = node.run(indicators());

        assertThat(summary.inputTokens()).isNull();
        assertThat(summary.outputTokens()).isNull();
    }

    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    /** Every numeric token in the text, normalised so 0.25 and 0.2500 compare equal. */
    private static Set<BigDecimal> numbersIn(String text) {
        Matcher matcher = NUMBER.matcher(text);
        return matcher.results()
                .map(r -> new BigDecimal(r.group()).stripTrailingZeros())
                .collect(Collectors.toSet());
    }

    private static Set<BigDecimal> allowedNumbers(IndicatorSet set) {
        return set.indicators().stream()
                .filter(Indicator::isApplicable)
                .map(i -> i.decimalValue().stripTrailingZeros())
                .collect(Collectors.toSet());
    }
}
