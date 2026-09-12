package dev.l4jlab.chain.node;

import dev.l4jlab.chain.core.ChainFailure;
import dev.l4jlab.chain.core.ChainNode;
import dev.l4jlab.chain.domain.FinancialRecord;
import dev.l4jlab.chain.domain.Indicator;
import dev.l4jlab.chain.domain.IndicatorSet;
import dev.l4jlab.chain.domain.RetrievedRecords;
import jakarta.inject.Singleton;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Node 3 of 4. Computes the five indicators from the retrieved records.
 *
 * <p>Every value is BigDecimal at scale 4 with HALF_UP, never binary floating point. SC-005 demands
 * byte-identical output for identical input across repeated runs, and it is the stored value that
 * gets compared, not the rendering (R-005).
 *
 * <p>Every denominator is guarded. A zero denominator produces an explicit not-applicable marker
 * with its reason, never an infinity, a NaN, or an invented number (FR-005).
 *
 * <p>No model call. This node is the reason the chain is testable at all.
 */
@Singleton
public class ComputeIndicatorsNode implements ChainNode<RetrievedRecords, IndicatorSet> {

    /** Declared once. Changing either changes every stored value, so SC-005 would need a re-baseline. */
    public static final int SCALE = 4;

    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    @Override
    public String name() {
        return "ComputeIndicators";
    }

    @Override
    public IndicatorSet run(RetrievedRecords input) throws ChainFailure {
        FinancialRecord now = input.current();
        FinancialRecord prior = input.prior();

        List<Indicator> indicators =
                List.of(
                        revenueGrowth(now, prior),
                        ratio("grossMargin",
                                now.revenue().subtract(now.costOfGoodsSold()),
                                now.revenue(),
                                "Revenue is zero, so gross margin has no defined value",
                                List.of("revenue", "costOfGoodsSold")),
                        ratio("netMargin",
                                now.netIncome(),
                                now.revenue(),
                                "Revenue is zero, so net margin has no defined value",
                                List.of("netIncome", "revenue")),
                        ratio("currentRatio",
                                now.currentAssets(),
                                now.currentLiabilities(),
                                "Current liabilities are zero, so the current ratio has no defined value",
                                List.of("currentAssets", "currentLiabilities")),
                        ratio("debtToEquity",
                                now.totalDebt(),
                                now.equity(),
                                "Equity is zero, so debt to equity has no defined value",
                                List.of("totalDebt", "equity")));

        return new IndicatorSet(input.companyName(), input.period(), indicators);
    }

    private Indicator revenueGrowth(FinancialRecord now, FinancialRecord prior) {
        List<String> derivedFrom = List.of("revenue", "prior.revenue");
        if (prior == null) {
            return Indicator.notApplicable(
                    "revenueGrowth",
                    "No prior period is on file for this company, so growth has nothing to compare against",
                    derivedFrom);
        }
        if (isZero(prior.revenue())) {
            return Indicator.notApplicable(
                    "revenueGrowth",
                    "Prior period revenue is zero, so growth has no defined value",
                    derivedFrom);
        }
        BigDecimal change = now.revenue().subtract(prior.revenue());
        return Indicator.of("revenueGrowth", divide(change, prior.revenue()), derivedFrom);
    }

    private Indicator ratio(
            String name,
            BigDecimal numerator,
            BigDecimal denominator,
            String notApplicableReason,
            List<String> derivedFrom) {

        if (isZero(denominator)) {
            return Indicator.notApplicable(name, notApplicableReason, derivedFrom);
        }
        return Indicator.of(name, divide(numerator, denominator), derivedFrom);
    }

    private static BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        return numerator.divide(denominator, SCALE, ROUNDING);
    }

    private static boolean isZero(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) == 0;
    }
}
