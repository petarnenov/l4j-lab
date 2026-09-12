package dev.l4jlab.chain.core;

import io.micronaut.serde.annotation.Serdeable;

/**
 * The run state machine from data-model.md. No transition moves backwards and no terminal state
 * changes again.
 *
 * <pre>
 * PENDING --> RUNNING --> SUCCEEDED
 *                   |
 *                   +---> FAILED     (a node raised a handled failure)
 *                   +---> TIMED_OUT  (the model call exceeded its timeout)
 * </pre>
 */
@Serdeable
public enum RunStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    TIMED_OUT;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == TIMED_OUT;
    }
}
