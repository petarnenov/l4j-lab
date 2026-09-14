package dev.l4jlab.legacy;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.time.Clock;

/**
 * A single injected clock, so the run simulation's timing can be asserted without waiting for it
 * (T099). {@code @Requires(missingBeans)} lets a test replace it without a bean-definition clash.
 */
@Factory
public class ClockFactory {

    @Singleton
    @Bean
    @Requires(missingBeans = Clock.class)
    Clock systemClock() {
        return Clock.systemUTC();
    }
}
