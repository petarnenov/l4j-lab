package dev.l4jlab.legacy.api;

import dev.l4jlab.legacy.domain.BillingRunRecord;
import dev.l4jlab.legacy.domain.BillingRunRepository;
import dev.l4jlab.legacy.runsim.RunSimulator;
import dev.l4jlab.legacy.security.BearerTokenFilter;
import dev.l4jlab.legacy.security.CallerScope;
import dev.l4jlab.legacy.security.EntitlementGuard;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Billing runs: searching, reading, starting, and cancelling (contracts/legacy-billing-api.md). */
@Controller("/api/v1/billing-runs")
public class BillingRunController {

    private static final int MAX_LIMIT = 20;
    private static final int MAX_FAILURE_LIMIT = 50;

    private final BillingRunRepository runs;
    private final EntitlementGuard guard;
    private final RunSimulator simulator;
    private final JdbcOperations jdbc;
    private final Clock clock;

    public BillingRunController(BillingRunRepository runs, EntitlementGuard guard,
                                RunSimulator simulator, JdbcOperations jdbc, Clock clock) {
        this.runs = runs;
        this.guard = guard;
        this.simulator = simulator;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Get
    public Dtos.Page<Dtos.BillingRun> search(HttpRequest<?> request,
                                             @QueryValue String firmId,
                                             @QueryValue @Nullable String status,
                                             @QueryValue @Nullable String advisorId,
                                             @QueryValue @Nullable LocalDate startedFrom,
                                             @QueryValue @Nullable LocalDate startedTo,
                                             @QueryValue(defaultValue = "0") int offset,
                                             @QueryValue(defaultValue = "20") int limit) {
        CallerScope scope = scopeOf(request);
        guard.requireOwnFirm(scope, firmId);

        String advisorIds = "{" + String.join(",", scope.advisorIds()) + "}";
        Instant from = startedFrom == null ? null : startedFrom.atStartOfDay(clock.getZone()).toInstant();
        Instant to = startedTo == null ? null : startedTo.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        int capped = Math.min(Math.max(limit, 1), MAX_LIMIT);

        List<Dtos.BillingRun> items = runs
            .search(firmId, scope.seesWholeFirm(), advisorIds, status, advisorId, from, to, offset, capped)
            .stream().map(BillingRunController::toDto).toList();
        long total = runs.countMatching(firmId, scope.seesWholeFirm(), advisorIds, status, advisorId, from, to);
        return new Dtos.Page<>(items, total);
    }

    @Get("/{runId}")
    public Dtos.BillingRun one(HttpRequest<?> request, @PathVariable String runId) {
        return toDto(loadEntitled(request, runId));
    }

    @Get("/{runId}/failures")
    public Dtos.Page<Dtos.RunFailure> failures(HttpRequest<?> request, @PathVariable String runId,
                                               @QueryValue(defaultValue = "50") int limit) {
        BillingRunRecord run = loadEntitled(request, runId);
        if (!"FAILED".equals(run.status())) {
            // Asking for the failures of a run that did not fail is a mistake worth naming, not an
            // empty list that looks like "it failed for no reason".
            throw new HttpStatusException(HttpStatus.CONFLICT, "Run has not failed");
        }
        int capped = Math.min(Math.max(limit, 1), MAX_FAILURE_LIMIT);
        List<Dtos.RunFailure> items = runs.failures(runId, capped).stream()
            .map(f -> new Dtos.RunFailure(f.householdId(), f.householdName(), f.cause())).toList();
        return new Dtos.Page<>(items, runs.countFailures(runId));
    }

    @Post
    @Transactional
    public HttpResponse<Dtos.BillingRun> start(HttpRequest<?> request, @Body Dtos.StartRunRequest body) {
        CallerScope scope = scopeOf(request);
        guard.requireWrite(scope);
        guard.requireOwnFirm(scope, body.firmId());
        guard.requireMayActFor(scope, body.executedByAdvisorId());

        String runId = "run-" + UUID.randomUUID().toString().substring(0, 8);
        int accountsTotal = countAccountsOfFirm(body.firmId());
        Instant now = Instant.now(clock);
        jdbc.prepareStatement("""
            INSERT INTO legacy_billing.billing_run
                (run_id, firm_id, executed_by_advisor_id, status, phase, accounts_processed,
                 accounts_total, failure_reason, started_at, finished_at)
            VALUES (?, ?, ?, 'PENDING', 'DATA_COLLECTION', 0, ?, NULL, ?, NULL)
            """, statement -> {
            statement.setString(1, runId);
            statement.setString(2, body.firmId());
            statement.setString(3, body.executedByAdvisorId());
            statement.setInt(4, accountsTotal);
            statement.setObject(5, now.atZone(clock.getZone()).toOffsetDateTime());
            return statement.executeUpdate();
        });
        simulator.start(runId, accountsTotal);
        return HttpResponse.created(toDto(runs.findByRunId(runId).orElseThrow()));
    }

    /**
     * FR-031. Cooperative by design: a run that has already finished keeps its status and the
     * request still succeeds. Refusing it would make a client that cancelled a moment too late look
     * like a client that did something wrong.
     */
    @Post("/{runId}/cancel")
    @Transactional
    public Dtos.BillingRun cancel(HttpRequest<?> request, @PathVariable String runId) {
        CallerScope scope = scopeOf(request);
        guard.requireWrite(scope);
        BillingRunRecord run = loadEntitled(request, runId);
        if (run.isTerminal()) {
            return toDto(run);
        }
        Instant now = Instant.now(clock);
        jdbc.prepareStatement("""
            UPDATE legacy_billing.billing_run
               SET status = 'CANCELED', phase = NULL, finished_at = ?
             WHERE run_id = ? AND status IN ('PENDING', 'RUNNING')
            """, statement -> {
            statement.setObject(1, now.atZone(clock.getZone()).toOffsetDateTime());
            statement.setString(2, runId);
            return statement.executeUpdate();
        });
        return toDto(runs.findByRunId(runId).orElseThrow());
    }

    /**
     * Entitlement before existence: a run of another firm and a run that does not exist are both a
     * 403, so a caller cannot probe for real identifiers by comparing status codes.
     */
    private BillingRunRecord loadEntitled(HttpRequest<?> request, String runId) {
        CallerScope scope = scopeOf(request);
        BillingRunRecord run = runs.findByRunId(runId)
            .orElseThrow(() -> new HttpStatusException(HttpStatus.FORBIDDEN, "Not entitled"));
        guard.requireOwnFirm(scope, run.firmId());
        guard.requireMayActFor(scope, run.executedByAdvisorId());
        return run;
    }

    private int countAccountsOfFirm(String firmId) {
        return jdbc.prepareStatement("""
            SELECT COUNT(*) FROM legacy_billing.account acc
            JOIN legacy_billing.household h ON h.household_id = acc.household_id
            JOIN legacy_billing.advisor a ON a.advisor_id = h.advisor_id
            WHERE a.firm_id = ?
            """, statement -> {
            statement.setString(1, firmId);
            var rs = statement.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        });
    }

    private static CallerScope scopeOf(HttpRequest<?> request) {
        return request.getAttribute(BearerTokenFilter.SCOPE_ATTRIBUTE, CallerScope.class)
            .orElseThrow(() -> new HttpStatusException(HttpStatus.UNAUTHORIZED, "No caller"));
    }

    private static Dtos.BillingRun toDto(BillingRunRecord r) {
        return new Dtos.BillingRun(r.runId(), r.firmId(), r.executedByAdvisorId(), r.status(),
            r.phase(), r.accountsProcessed(), r.accountsTotal(), r.failureReason(),
            r.startedAt(), r.finishedAt());
    }
}
