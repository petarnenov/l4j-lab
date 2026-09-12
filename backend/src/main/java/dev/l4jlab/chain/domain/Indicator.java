package dev.l4jlab.chain.domain;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;
import java.util.List;

/**
 * One computed indicator. Exactly one of {@code value} and {@code notApplicableReason} is present,
 * enforced below. That invariant is what keeps an infinity, a NaN, or an invented number out of the
 * record when a denominator is zero (FR-005).
 *
 * <p>{@code value} is the exact decimal text at scale 4, not a number. This matters: serialized as
 * a JSON number, {@code 0.3400} comes back from the JSONB column as {@code 0.34}, which is
 * numerically equal and not byte-identical. SC-005 compares the stored value, so the text is what
 * gets stored, and the payload, the API, and the screen then agree by construction.
 *
 * <p>{@code derivedFrom} names the record fields the value came from, which is what lets the screen
 * show a figure's provenance next to it.
 */
@Serdeable
public record Indicator(
        String name,
        @Nullable String value,
        @Nullable String notApplicableReason,
        List<String> derivedFrom) {

    /** Declared once. Changing it changes every stored value, so SC-005 would need a re-baseline. */
    public static final int SCALE = 4;

    public Indicator {
        if ((value == null) == (notApplicableReason == null)) {
            throw new IllegalArgumentException(
                    "Indicator '"
                            + name
                            + "' must carry exactly one of a value and a not-applicable reason, not "
                            + (value == null ? "neither" : "both"));
        }
        if (derivedFrom == null || derivedFrom.isEmpty()) {
            throw new IllegalArgumentException("Indicator '" + name + "' must name at least one source field");
        }
        derivedFrom = List.copyOf(derivedFrom);
    }

    /** Canonicalises to scale 4 plain text, so every caller stores the same bytes. */
    public static Indicator of(String name, BigDecimal value, List<String> derivedFrom) {
        return new Indicator(name, canonical(value), null, derivedFrom);
    }

    public static Indicator notApplicable(String name, String reason, List<String> derivedFrom) {
        return new Indicator(name, null, reason, derivedFrom);
    }

    public static String canonical(BigDecimal value) {
        return value.setScale(SCALE, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    public boolean isApplicable() {
        return value != null;
    }

    /** The value as a number, for a caller that needs to compare rather than display. */
    @Nullable
    public BigDecimal decimalValue() {
        return value == null ? null : new BigDecimal(value);
    }
}
