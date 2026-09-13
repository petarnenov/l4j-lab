package dev.l4jlab.chain.core;

/**
 * A step produced output that breaks its boundary contract. That is a programming error, not a
 * condition a learner can act on.
 *
 * <p>Deliberately not a {@link ChainFailure}: a ChainFailure's message is shown to the learner, and this
 * one's detail ("period must match ...") belongs in the server log. The failure classifier gives it the
 * same generic reason the hand-written runner gave (feature 005, research R-006).
 */
public class BoundaryViolation extends RuntimeException {

    public BoundaryViolation(String message) {
        super(message);
    }
}
