package dev.l4jlab.chain.core;

/**
 * A handled failure inside one node. Carries a message written for a learner to act on and the
 * name of the node that raised it. The runner catches it, marks the run failed at that node, and
 * stops. Any other exception is wrapped into one of these with a generic message so a stack trace
 * never reaches the screen (FR-015).
 */
public class ChainFailure extends Exception {

    private final String nodeName;

    public ChainFailure(String nodeName, String message) {
        super(message);
        this.nodeName = nodeName;
    }

    public ChainFailure(String nodeName, String message, Throwable cause) {
        super(message, cause);
        this.nodeName = nodeName;
    }

    public String nodeName() {
        return nodeName;
    }
}
