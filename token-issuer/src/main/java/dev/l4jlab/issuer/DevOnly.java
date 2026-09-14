package dev.l4jlab.issuer;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * R-013: what makes "development only" a constraint rather than a comment.
 *
 * <p>A committed key pair deserves enforcement. Every endpoint of this service carries this
 * annotation, so outside development and {@code test-capture} the beans are never created and the
 * paths 404 — including the deliberately-broken-token levers, which no production profile can then
 * reach even by accident.
 */
@Retention(RetentionPolicy.RUNTIME)
@Requires(env = {Environment.DEVELOPMENT, "test-capture"})
public @interface DevOnly {
}
