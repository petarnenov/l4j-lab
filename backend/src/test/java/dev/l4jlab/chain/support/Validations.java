package dev.l4jlab.chain.support;

import dev.l4jlab.chain.core.BoundaryValidation;
import io.micronaut.validation.validator.Validator;

/**
 * The boundary validation the deterministic steps use, without starting an application context.
 *
 * <p>Micronaut's validator works from compile-time introspection, so the instance here applies exactly the
 * constraints production applies, and the node unit tests stay free of Docker, network, and credential
 * (constitution, Principle IV).
 */
public final class Validations {

    private Validations() {}

    public static BoundaryValidation boundary() {
        return new BoundaryValidation(Validator.getInstance());
    }
}
