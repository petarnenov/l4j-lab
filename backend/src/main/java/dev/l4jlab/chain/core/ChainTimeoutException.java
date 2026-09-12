package dev.l4jlab.chain.core;

/**
 * Raised when the model call exceeds its configured limit. Distinct from ChainFailure so the runner
 * can reach TIMED_OUT rather than FAILED, which the learner sees as a different outcome (FR-019).
 */
public class ChainTimeoutException extends ChainFailure {

    public ChainTimeoutException(String nodeName, String message) {
        super(nodeName, message);
    }
}
