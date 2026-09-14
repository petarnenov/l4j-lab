package dev.l4jlab.mcp.tools;

/**
 * The stage a running billing run has reached. Absent once the run has finished.
 *
 * <p>Caller-facing for the same reason as {@link RunStatus}: this text reaches the model.
 */
public enum RunPhase {
    DATA_COLLECTION, FEE_CALC, INVOICING, POSTING
}
