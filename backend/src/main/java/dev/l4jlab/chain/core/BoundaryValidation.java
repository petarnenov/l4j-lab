package dev.l4jlab.chain.core;

import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates one step's output against its boundary contract before the orchestrator hands it on.
 *
 * <p>Why inside the step rather than around it: LangChain4j's agent listeners catch and log anything they
 * throw, so a listener cannot stop a run. The exception has to come from the step itself to reach the
 * orchestrator (feature 005, research R-005 and R-006). The message format is the former runner's.
 */
@Singleton
public class BoundaryValidation {

    private final Validator validator;

    public BoundaryValidation(Validator validator) {
        this.validator = validator;
    }

    public <T> T requireValid(String stepName, T output) {
        if (output == null) {
            throw new BoundaryViolation(stepName + " produced no output");
        }
        Set<ConstraintViolation<T>> violations = validator.validate(output);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; "));
            throw new BoundaryViolation(
                    stepName + " produced an invalid " + output.getClass().getSimpleName() + ": " + detail);
        }
        return output;
    }
}
