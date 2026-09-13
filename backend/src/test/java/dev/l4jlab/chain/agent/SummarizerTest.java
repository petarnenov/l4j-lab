package dev.l4jlab.chain.agent;

import dev.l4jlab.chain.domain.Indicator;
import dev.l4jlab.chain.domain.IndicatorSet;
import dev.l4jlab.chain.support.FakeChatModel;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Feature 005, contracts/declarations.md. The summarizer is now a declaration, so its behavior is tested
 * through the declaration against a fake model: the messages it produces, and how its reply is handled.
 * Never the model's wording (constitution, Principle IV). Carried over from the former SummarizeNodeTest.
 */
class SummarizerTest {

    private FakeChatModel model;
    private Summarizer summarizer;

    @BeforeEach
    void setUp() {
        model = new FakeChatModel();
        summarizer = AgenticServices.agentBuilder(Summarizer.class)
                .chatModel(model)
                .outputGuardrails(new NonBlankSummaryGuardrail())
                .build();
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
    void sendsTheSystemInstructionAndTheRenderedIndicatorTable() {
        summarizer.summarize(indicators());

        List<ChatMessage> messages = model.lastRequest().messages();
        String systemText = messages.stream()
                .filter(SystemMessage.class::isInstance)
                .map(m -> ((SystemMessage) m).text())
                .findFirst()
                .orElseThrow();
        String userText = messages.stream()
                .filter(UserMessage.class::isInstance)
                .map(m -> ((UserMessage) m).singleText())
                .reduce((first, second) -> second)
                .orElseThrow();

        assertThat(model.callCount()).isEqualTo(1);
        // The instruction is unchanged character for character (FR-008).
        assertThat(systemText).isEqualTo(Summarizer.SYSTEM_INSTRUCTION);
        assertThat(systemText).contains("fictional", "exactly as written", "no recommendation");
        // The user message is exactly the rendered table, including the not-applicable entry.
        assertThat(userText).isEqualTo(indicators().toString());
        assertThat(userText).contains("Acme Widgets (fictional)", "2025-Q2");
        assertThat(userText).contains("revenueGrowth = 0.2500");
        assertThat(userText).contains("debtToEquity = not applicable (Equity is zero)");
    }

    @Test
    void returnsTheModelText() {
        model.respondWith("Revenue grew 0.2500 over the prior quarter.");

        assertThat(summarizer.summarize(indicators())).isEqualTo("Revenue grew 0.2500 over the prior quarter.");
    }

    @Test
    void failsThroughTheGuardrailWhenTheModelReturnsNothing() {
        model.respondWith("   ");

        assertThatThrownBy(() -> summarizer.summarize(indicators()))
                .hasStackTraceContaining("OutputGuardrailException")
                .hasStackTraceContaining(NonBlankSummaryGuardrail.MESSAGE);
        assertThat(model.callCount()).as("a blank reply is not retried").isEqualTo(1);
    }

    @Test
    void everyNumberInTheResponseIsTraceableToAnIndicator() {
        // SC-003 of feature 001: no invented figures. A fabricated number must fail this check.
        model.respondWith(
                "Revenue grew 0.2500 on the quarter. Gross margin held at 0.4000 and net margin at "
                        + "0.1200, with a current ratio of 2.0000. Debt to equity is not applicable.");

        String text = summarizer.summarize(indicators());

        assertThat(numbersIn(text)).isSubsetOf(allowedNumbers(indicators()));
    }

    @Test
    void aFabricatedFigureIsDetectedByTheSameCheck() {
        model.respondWith("Revenue grew 0.2500, and the debt to equity ratio sits at 1.7500.");

        String text = summarizer.summarize(indicators());

        Set<BigDecimal> allowed = allowedNumbers(indicators());
        assertThat(numbersIn(text))
                .as("a fabricated figure must not be traceable to any computed indicator")
                .anySatisfy(n -> assertThat(allowed).doesNotContain(n));
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
