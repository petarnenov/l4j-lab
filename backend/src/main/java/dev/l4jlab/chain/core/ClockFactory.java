package dev.l4jlab.chain.core;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;

import java.time.Clock;

/** A clock bean, so tests can fix time rather than sleep. */
@Factory
public class ClockFactory {

    @Singleton
    Clock clock() {
        return Clock.systemUTC();
    }
}
