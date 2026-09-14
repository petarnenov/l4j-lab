package dev.l4jlab.mcp.legacy;

import dev.l4jlab.mcp.protocol.ToolFailure;

/**
 * Turns a legacy outcome into something a model can act on (FR-010, FR-014, SC-006).
 *
 * <p>One code path, a fixed vocabulary, and nothing else. No exception message, SQL fragment, or
 * hostname reaches a caller — not because each call site remembers to strip them, but because no
 * call site ever has them to strip.
 *
 * <p>The distinction that matters is retryability. "No access" tells the model to stop asking for
 * this thing; "do not retry" tells it to stop asking altogether. A model that retries a broken
 * system of record makes an outage worse.
 */
public final class LegacyErrorTranslator {

    private LegacyErrorTranslator() {
    }

    public static ToolFailure asToolFailure(LegacyBillingClient.Outcome<?> outcome) {
        return new ToolFailure(message(outcome));
    }

    public static String message(LegacyBillingClient.Outcome<?> outcome) {
        return switch (outcome) {
            // Says nothing about whether the record exists. The legacy API refuses a foreign id and
            // an invented one identically, and so must this, or the pair becomes a probe.
            case LegacyBillingClient.Outcome.Forbidden<?> ignored -> "No access to that record.";
            case LegacyBillingClient.Outcome.NotFound<?> ignored -> "No such record.";
            case LegacyBillingClient.Outcome.Conflict<?> ignored ->
                "That request does not apply to this record in its current state.";
            case LegacyBillingClient.Outcome.Unavailable<?> ignored ->
                "The billing system is unavailable. Do not retry; report this and stop.";
            case LegacyBillingClient.Outcome.Ok<?> ignored ->
                throw new IllegalStateException("Ok is not an error");
        };
    }
}
