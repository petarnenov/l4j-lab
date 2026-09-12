package dev.l4jlab.chain.node;

import dev.l4jlab.chain.domain.FinancialRecord;
import dev.l4jlab.chain.domain.Indicator;
import dev.l4jlab.chain.domain.IndicatorSet;
import dev.l4jlab.chain.domain.RetrievedRecords;
import dev.l4jlab.chain.support.FakeChatModel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ComputeIndicatorsNodeTest {

    private final FakeChatModel model = new FakeChatModel();
    private final ComputeIndicatorsNode node = new ComputeIndicatorsNode();

    private static FinancialRecord record(
            String period, String revenue, String cogs, String netIncome,
            String currentAssets, String currentLiabilities, String totalDebt, String equity) {
        return new FinancialRecord(
                "acme-widgets", "Acme Widgets (fictional)", period,
                new BigDecimal(revenue), new BigDecimal(cogs), new BigDecimal(netIncome),
                new BigDecimal(currentAssets), new BigDecimal(currentLiabilities),
                new BigDecimal(totalDebt), new BigDecimal(equity));
    }

    private static RetrievedRecords withPrior(FinancialRecord current, FinancialRecord prior) {
        return new RetrievedRecords(
                current.companyId(), current.companyName(), current.period(), current, prior);
    }

    private static Indicator find(IndicatorSet set, String name) {
        return set.indicators().stream().filter(i -> i.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void namesItselfAsTheContractSpellsIt() {
        assertThat(node.name()).isEqualTo("ComputeIndicators");
    }

    @Test
    void computesAllFiveFormulasAtScaleFour() throws Exception {
        FinancialRecord current = record("2025-Q2", "1000", "600", "120", "500", "250", "300", "600");
        FinancialRecord prior = record("2025-Q1", "800", "500", "90", "400", "200", "280", "560");

        IndicatorSet set = node.run(withPrior(current, prior));

        assertThat(set.indicators()).hasSize(5);
        // (1000 - 800) / 800 = 0.25
        assertThat(find(set, "revenueGrowth").value()).isEqualTo("0.2500");
        // (1000 - 600) / 1000 = 0.4
        assertThat(find(set, "grossMargin").value()).isEqualTo("0.4000");
        // 120 / 1000
        assertThat(find(set, "netMargin").value()).isEqualTo("0.1200");
        // 500 / 250
        assertThat(find(set, "currentRatio").value()).isEqualTo("2.0000");
        // 300 / 600
        assertThat(find(set, "debtToEquity").value()).isEqualTo("0.5000");

        // Every value is scale-4 plain text, which is the form that gets stored and displayed.
        set.indicators().stream()
                .filter(Indicator::isApplicable)
                .forEach(i -> assertThat(i.value()).as(i.name()).containsPattern("^-?\\d+\\.\\d{4}$"));
    }

    @Test
    void repeatedRunsProduceByteIdenticalValues() throws Exception {
        // SC-005. The stored value is what gets compared, not the rendering, which is why the node
        // uses BigDecimal rather than double.
        FinancialRecord current = record("2025-Q2", "1234.56", "777.77", "111.11", "543.21", "321.12", "222.22", "333.33");
        FinancialRecord prior = record("2025-Q1", "1111.11", "700.00", "100.00", "500.00", "300.00", "200.00", "300.00");

        List<String> renderings = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            IndicatorSet set = node.run(withPrior(current, prior));
            renderings.add(
                    set.indicators().stream()
                            .map(ind -> ind.name() + '=' + (ind.isApplicable() ? ind.value() : "n/a"))
                            .reduce("", (a, b) -> a + '|' + b));
        }
        assertThat(renderings).containsOnly(renderings.getFirst());
    }

    @Test
    void reportsRevenueGrowthAsNotApplicableWithoutAPriorPeriod() throws Exception {
        IndicatorSet set = node.run(withPrior(record("2024-Q1", "1000", "600", "120", "500", "250", "300", "600"), null));

        Indicator growth = find(set, "revenueGrowth");
        assertThat(growth.isApplicable()).isFalse();
        assertThat(growth.notApplicableReason()).contains("No prior period");
        assertThat(growth.value()).isNull();
    }

    @Test
    void reportsEachZeroDenominatorAsNotApplicableWithItsReason() throws Exception {
        // FR-005: never an infinity, a NaN, or an invented value.
        FinancialRecord zeroEquity = record("2025-Q2", "1000", "600", "120", "500", "0", "300", "0");
        FinancialRecord prior = record("2025-Q1", "0", "0", "0", "0", "0", "0", "0");

        IndicatorSet set = node.run(withPrior(zeroEquity, prior));

        assertThat(find(set, "debtToEquity").notApplicableReason()).contains("Equity is zero");
        assertThat(find(set, "currentRatio").notApplicableReason()).contains("Current liabilities are zero");
        assertThat(find(set, "revenueGrowth").notApplicableReason()).contains("Prior period revenue is zero");
    }

    @Test
    void reportsMarginsAsNotApplicableWhenRevenueIsZero() throws Exception {
        FinancialRecord zeroRevenue = record("2025-Q2", "0", "0", "0", "500", "250", "300", "600");
        FinancialRecord prior = record("2025-Q1", "800", "500", "90", "400", "200", "280", "560");

        IndicatorSet set = node.run(withPrior(zeroRevenue, prior));

        assertThat(find(set, "grossMargin").notApplicableReason()).contains("Revenue is zero");
        assertThat(find(set, "netMargin").notApplicableReason()).contains("Revenue is zero");
    }

    @Test
    void handlesANegativeNetIncomeAsAValueRatherThanAFailure() throws Exception {
        FinancialRecord loss = record("2025-Q2", "1000", "600", "-250", "500", "250", "300", "600");
        IndicatorSet set = node.run(withPrior(loss, null));

        assertThat(find(set, "netMargin").value()).isEqualTo("-0.2500");
    }

    @Test
    void everyIndicatorNamesTheFieldsItCameFrom() throws Exception {
        IndicatorSet set = node.run(withPrior(record("2025-Q2", "1000", "600", "120", "500", "250", "300", "600"), null));
        set.indicators().forEach(i -> assertThat(i.derivedFrom()).as(i.name()).isNotEmpty());
    }

    @Test
    void neverCallsTheModel() throws Exception {
        node.run(withPrior(record("2025-Q2", "1000", "600", "120", "500", "250", "300", "600"), null));
        assertThat(model.callCount()).isZero();
    }
}
