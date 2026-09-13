package dev.l4jlab.chain.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature 005, research R-003. The summarizer's user message is the template {@code {{indicators}}},
 * which LangChain4j renders with {@code toString()}. So {@code toString()} must be exactly the table the
 * old summarizing node sent, character for character.
 */
class IndicatorSetPromptTest {

    private static IndicatorSet fixture() {
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
    void rendersExactlyTheTableTheSummarizingNodeSent() {
        // The former SummarizeNode.renderPrompt output for this fixture, written out. This assertion first ran
        // against that method itself, before the node was removed in feature 005.
        assertThat(fixture().toString()).isEqualTo(
                "Company: Acme Widgets (fictional)\n"
                        + "Reporting period: 2025-Q2\n"
                        + "\n"
                        + "Indicators:\n"
                        + "- revenueGrowth = 0.2500\n"
                        + "- grossMargin = 0.4000\n"
                        + "- netMargin = 0.1200\n"
                        + "- currentRatio = 2.0000\n"
                        + "- debtToEquity = not applicable (Equity is zero)");
    }

    @Test
    void rendersANotApplicableIndicatorWithItsReason() {
        assertThat(fixture().toString()).contains("- debtToEquity = not applicable (Equity is zero)");
    }
}
