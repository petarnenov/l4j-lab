package dev.l4jlab.chain.web;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;

import java.net.URI;

/**
 * RFC 9457 problem details. The {@code detail} is written for a learner to act on and never carries
 * a stack trace, a class name, or anything a client could mistake for internals.
 */
public final class ApiExceptionHandler {

    /** The problem detail body. One shape for every error this API returns. */
    @Serdeable
    public record Problem(URI type, String title, int status, String detail, String instance) {

        static Problem of(HttpStatus status, String title, String detail, HttpRequest<?> request) {
            return new Problem(
                    URI.create("about:blank"), title, status.getCode(), detail, request.getPath());
        }
    }

    @Singleton
    @Produces("application/problem+json")
    @Requires(classes = {RunNotFoundException.class, ExceptionHandler.class})
    public static class RunNotFound
            implements ExceptionHandler<RunNotFoundException, HttpResponse<Problem>> {

        @Override
        public HttpResponse<Problem> handle(HttpRequest request, RunNotFoundException e) {
            return HttpResponse.notFound(
                    Problem.of(HttpStatus.NOT_FOUND, "Run not found", e.getMessage(), request));
        }
    }

    @Singleton
    @Produces("application/problem+json")
    @Requires(classes = {InvalidSelectionException.class, ExceptionHandler.class})
    public static class InvalidSelection
            implements ExceptionHandler<InvalidSelectionException, HttpResponse<Problem>> {

        @Override
        public HttpResponse<Problem> handle(HttpRequest request, InvalidSelectionException e) {
            return HttpResponse.badRequest(
                    Problem.of(HttpStatus.BAD_REQUEST, "Invalid selection", e.getMessage(), request));
        }
    }
}
