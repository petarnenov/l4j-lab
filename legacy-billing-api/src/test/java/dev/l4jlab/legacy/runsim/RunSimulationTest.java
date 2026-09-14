package dev.l4jlab.legacy.runsim;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T099, FR-024: a run takes between 30 and 90 seconds.
 *
 * <p>Asserted on the duration the simulator <em>chooses</em>, not by waiting one out. A test that
 * spent ninety seconds proving a ninety-second property would buy no more confidence than this one
 * and would be quietly deleted by the third person who ran the suite.
 *
 * <p>The override the topology suite relies on is checked here too: a knob that silently failed to
 * apply would make every acceptance run cost minutes without anyone understanding why.
 */
class RunSimulationTest {

    @Test
    void theDefaultDurationAlwaysFallsInTheBandTheRequirementNames() {
        RunSimulator simulator = new RunSimulator(null, null, -1);

        // Sampled rather than checked once: the duration is random, and a bound that holds for a
        // single draw is not a bound.
        assertThat(IntStream.range(0, 500).mapToObj(i -> simulator.durationMs()))
            .allSatisfy(duration -> assertThat(duration).isBetween(30_000L, 90_000L));
    }

    @Test
    void theDurationVariesRatherThanBeingFixed() {
        RunSimulator simulator = new RunSimulator(null, null, -1);
        assertThat(IntStream.range(0, 50).mapToLong(i -> simulator.durationMs()).distinct().count())
            .as("a fixed 'random' duration would hide an off-by-one at either end of the band")
            .isGreaterThan(1);
    }

    @Test
    void configurationOverridesTheBandSoTheAcceptanceSuiteDoesNotWaitItOut() {
        assertThat(new RunSimulator(null, null, 3000).durationMs()).isEqualTo(3000);
    }

    @Test
    void aNonPositiveOverrideMeansTheRequirementsOwnBand() {
        // application.yml defaults this to -1, which must read as "unset" and not as "instant".
        assertThat(new RunSimulator(null, null, -1).durationMs()).isBetween(30_000L, 90_000L);
        assertThat(new RunSimulator(null, null, 0).durationMs()).isBetween(30_000L, 90_000L);
    }
}
