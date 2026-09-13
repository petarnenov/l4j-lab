package dev.l4jlab.chain.domain;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.StringJoiner;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Boundary 3 to 4. Produced by ComputeIndicators, and the last structure that is entirely
 * deterministic. Exactly five entries with unique names; a set that fails either check is a
 * programming error and fails the run rather than reaching the model.
 */
@Serdeable
public record IndicatorSet(
        @NotBlank String companyName,
        @Pattern(regexp = "\\d{4}-Q[1-4]") String period,
        List<Indicator> indicators) {

    /** The five indicators named in FR-005, in the order they are rendered. */
    public static final List<String> REQUIRED_NAMES =
            List.of("revenueGrowth", "grossMargin", "netMargin", "currentRatio", "debtToEquity");

    public IndicatorSet {
        if (indicators == null || indicators.size() != 5) {
            throw new IllegalArgumentException(
                    "IndicatorSet must hold exactly 5 indicators, got "
                            + (indicators == null ? "null" : indicators.size()));
        }
        Set<String> names = indicators.stream().map(Indicator::name).collect(Collectors.toSet());
        if (names.size() != 5) {
            throw new IllegalArgumentException("IndicatorSet names must be unique, got " + names);
        }
        if (!names.containsAll(REQUIRED_NAMES)) {
            throw new IllegalArgumentException(
                    "IndicatorSet must hold " + REQUIRED_NAMES + ", got " + names);
        }
        indicators = List.copyOf(indicators);
    }

    /**
     * The prompt form of this set: exactly the table the summarizer's user message template
     * {@code {{indicators}}} renders, because LangChain4j fills a template variable with
     * {@code toString()} (feature 005, research R-003). Deterministic, so the prompt is too.
     *
     * <p>Changing this changes what the model is sent. It is pinned by IndicatorSetPromptTest and by the
     * golden run snapshots.
     */
    @Override
    public String toString() {
        StringJoiner lines = new StringJoiner("\n");
        lines.add("Company: " + companyName);
        lines.add("Reporting period: " + period);
        lines.add("");
        lines.add("Indicators:");
        for (Indicator indicator : indicators) {
            if (indicator.isApplicable()) {
                lines.add("- " + indicator.name() + " = " + indicator.value());
            } else {
                lines.add("- " + indicator.name() + " = not applicable (" + indicator.notApplicableReason() + ")");
            }
        }
        return lines.toString();
    }
}
