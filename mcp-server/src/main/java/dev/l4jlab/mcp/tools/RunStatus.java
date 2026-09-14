package dev.l4jlab.mcp.tools;

/**
 * The state a billing run is in.
 *
 * <p>Kept caller-facing on purpose: this Javadoc becomes the enum's {@code description} in the
 * generated schema, so it is read by models, not only by people.
 */
public enum RunStatus {
    PENDING, RUNNING, COMPLETED, FAILED, CANCELED
}
