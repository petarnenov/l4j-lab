package dev.l4jlab.legacy.runsim;

import io.micronaut.context.annotation.Value;
import io.micronaut.scheduling.TaskScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.List;
import java.util.Random;

/**
 * Simulates a billing run advancing through its four phases (FR-024).
 *
 * <p>The real duration is a random value in [30s, 90s], which is what the requirement specifies and
 * what a reader should see. {@code LEGACY_RUN_DURATION_MS} overrides it, and the topology suite sets
 * it to a few seconds — without that lever every acceptance run would cost minutes of wall clock for
 * no additional confidence (research.md R-012).
 *
 * <p>The clock is injected so the unit test can assert the real band without waiting for it.
 */
@Singleton
public class RunSimulator {

    private static final List<String> PHASES =
        List.of("DATA_COLLECTION", "FEE_CALC", "INVOICING", "POSTING");
    private static final long MIN_MS = 30_000;
    private static final long MAX_MS = 90_000;

    private static final Logger LOG = LoggerFactory.getLogger(RunSimulator.class);

    private final RunProgressWriter writer;
    private final TaskScheduler scheduler;
    private final Random random = new Random();
    private final long configuredDurationMs;

    public RunSimulator(RunProgressWriter writer,
                        TaskScheduler scheduler,
                        @Value("${legacy.run-duration-ms:-1}") long configuredDurationMs) {
        // Injected rather than inlined: @Transactional is proxy-applied, and a scheduled lambda
        // calling this bean's own method would bypass the proxy and find no connection.
        this.writer = writer;
        this.scheduler = scheduler;
        this.configuredDurationMs = configuredDurationMs;
    }

    /**
     * Wraps a scheduled write so a failure is logged rather than swallowed.
     *
     * <p>A {@code ScheduledFuture} nobody holds discards the exception silently, and the symptom is
     * a run that simply never advances — which looks like a scheduling problem and is not.
     */
    private static Runnable guarded(String what, Runnable work) {
        return () -> {
            try {
                work.run();
            } catch (RuntimeException e) {
                LOG.error("Simulated billing run step failed: {}", what, e);
            }
        };
    }

    /** The duration this run will take, honouring FR-024 unless configuration overrides it. */
    public long durationMs() {
        return configuredDurationMs > 0
            ? configuredDurationMs
            : MIN_MS + (long) (random.nextDouble() * (MAX_MS - MIN_MS));
    }

    /**
     * Schedules the four phase transitions. Each is an independent write, so a poll landing between
     * two of them sees a coherent state rather than a half-updated row.
     */
    public void start(String runId, int accountsTotal) {
        long total = durationMs();
        long step = total / PHASES.size();
        for (int i = 0; i < PHASES.size(); i++) {
            String phase = PHASES.get(i);
            int processed = (int) Math.round(accountsTotal * ((i + 1) / (double) PHASES.size()));
            scheduler.schedule(Duration.ofMillis(step * (i + 1L)),
                guarded("advance " + runId + " to " + phase,
                    () -> writer.advance(runId, phase, processed)));
        }
        scheduler.schedule(Duration.ofMillis(total + 50),
            guarded("complete " + runId, () -> writer.complete(runId, accountsTotal)));
    }

}
