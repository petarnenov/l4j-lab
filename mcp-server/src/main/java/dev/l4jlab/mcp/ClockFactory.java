package dev.l4jlab.mcp;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import java.time.Clock;

/** One injected clock, so audit timestamps and task TTLs can be asserted rather than waited for. */
@Factory
public class ClockFactory {

    @Singleton
    @Bean
    @Requires(missingBeans = Clock.class)
    Clock systemClock() {
        return Clock.systemUTC();
    }
}
