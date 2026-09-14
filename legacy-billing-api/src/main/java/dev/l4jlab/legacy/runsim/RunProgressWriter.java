package dev.l4jlab.legacy.runsim;

import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * The writes that move a simulated run along.
 *
 * <p>Two things here exist because of how transactions cross threads, and both were found by running
 * the stack rather than by reading the code:
 *
 * <ul>
 *   <li>A separate bean rather than methods on {@link RunSimulator}: {@code @Transactional} is
 *       applied by a proxy, so a scheduled lambda calling {@code this.advance(...)} bypasses it and
 *       fails with "Expected an existing connection".</li>
 *   <li>{@code REQUIRES_NEW}: the scheduled task inherits the propagated context of the request that
 *       started the run, whose connection is long closed by the time a phase advances. It must open
 *       its own, and saying so is clearer than clearing the context by hand.</li>
 * </ul>
 */
@Singleton
public class RunProgressWriter {

    private final JdbcOperations jdbc;
    private final Clock clock;

    public RunProgressWriter(JdbcOperations jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Each phase is its own write, so a poll between two of them sees a coherent state. */
    @Transactional(propagation = TransactionDefinition.Propagation.REQUIRES_NEW)
    public void advance(String runId, String phase, int processed) {
        jdbc.prepareStatement("""
            UPDATE legacy_billing.billing_run
               SET status = 'RUNNING', phase = ?, accounts_processed = ?
             WHERE run_id = ? AND status IN ('PENDING', 'RUNNING')
            """, statement -> {
            statement.setString(1, phase);
            statement.setInt(2, processed);
            statement.setString(3, runId);
            return statement.executeUpdate();
        });
    }

    /** Only a RUNNING run completes: a cancelled one must stay cancelled (FR-031). */
    @Transactional(propagation = TransactionDefinition.Propagation.REQUIRES_NEW)
    public void complete(String runId, int accountsTotal) {
        Instant now = Instant.now(clock);
        jdbc.prepareStatement("""
            UPDATE legacy_billing.billing_run
               SET status = 'COMPLETED', phase = NULL, accounts_processed = ?, finished_at = ?
             WHERE run_id = ? AND status = 'RUNNING'
            """, statement -> {
            statement.setInt(1, accountsTotal);
            statement.setObject(2, now.atOffset(ZoneOffset.UTC));
            statement.setString(3, runId);
            return statement.executeUpdate();
        });
    }
}
