package dev.l4jlab.chain.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test that genuinely needs PostgreSQL. Skips with a named message when Docker is not
 * running, rather than failing with a connection error that says nothing useful.
 *
 * <p>The default suite must pass with no credential and no network (Principle IV). Docker is a
 * different kind of prerequisite: the constitution allows Testcontainers, so these tests are real
 * coverage on a machine with Docker and an explicit skip on one without.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(RequiresDocker.DockerAvailable.class)
public @interface RequiresDocker {

    class DockerAvailable implements ExecutionCondition {

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            try {
                if (DockerClientFactory.instance().isDockerAvailable()) {
                    return ConditionEvaluationResult.enabled("Docker is available");
                }
            } catch (RuntimeException e) {
                // Fall through to the skip below: an unreachable daemon is the same situation.
            }
            return ConditionEvaluationResult.disabled(
                    "Skipped: this test needs PostgreSQL through Testcontainers, and the Docker "
                            + "daemon is not running. Start Docker, or run `docker compose up -d` and "
                            + "the rest of the suite still covers every deterministic boundary.");
        }
    }
}
