package dev.l4jlab.issuer;

import io.micronaut.runtime.Micronaut;

/**
 * Development-only token issuer (FR-025).
 *
 * <p>Mints signed JWTs for both audiences from a committed RSA key pair so the whole system runs
 * offline. Every controller here is gated to the development and {@code test-capture} environments
 * (research.md R-013): in any other environment the beans do not exist and the paths return 404.
 */
public final class Application {

    private Application() {
    }

    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }
}
