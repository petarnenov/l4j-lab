package dev.l4jlab.legacy.api;

import dev.l4jlab.legacy.domain.AccountRepository;
import dev.l4jlab.legacy.security.BearerTokenFilter;
import dev.l4jlab.legacy.security.CallerScope;
import dev.l4jlab.legacy.security.EntitlementGuard;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Applying a fee adjustment (FR-020's legacy half).
 *
 * <p>The insert and the balance change happen in one transaction, so the recorded adjustment and the
 * account's fee can never disagree. Idempotency is <em>not</em> handled here: the operation id is an
 * MCP-level concern, and the system of record deliberately stays a system of record.
 */
@Controller("/api/v1/fee-adjustments")
public class FeeAdjustmentController {

    private final AccountRepository accounts;
    private final JdbcOperations jdbc;
    private final EntitlementGuard guard;
    private final Clock clock;

    public FeeAdjustmentController(AccountRepository accounts, JdbcOperations jdbc,
                                   EntitlementGuard guard, Clock clock) {
        this.accounts = accounts;
        // Written directly rather than through a repository: the adjustment has no entity of its own
        // to map, and a GenericRepository<Object, ...> makes Micronaut Data try to introspect
        // Object, which fails at runtime with a message about the wrong class entirely.
        this.jdbc = jdbc;
        this.guard = guard;
        this.clock = clock;
    }

    @Post
    @Transactional
    public HttpResponse<Dtos.FeeAdjustmentResult> apply(HttpRequest<?> request,
                                                        @Body Dtos.FeeAdjustmentRequest body) {
        CallerScope scope = request.getAttribute(BearerTokenFilter.SCOPE_ATTRIBUTE, CallerScope.class)
            .orElseThrow(() -> new HttpStatusException(HttpStatus.UNAUTHORIZED, "No caller"));
        guard.requireWrite(scope);

        // Entitlement before existence, as everywhere else: an account of another firm and an
        // account that does not exist are the same 403.
        String firmId = accounts.findFirmIdOfAccount(body.accountId())
            .orElseThrow(() -> new HttpStatusException(HttpStatus.FORBIDDEN, "Not entitled"));
        guard.requireOwnFirm(scope, firmId);

        if (body.deltaBps() == 0) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "delta_bps must not be zero");
        }

        String referenceId = "adj-" + UUID.randomUUID().toString().substring(0, 12);
        Instant now = Instant.now(clock);
        jdbc.prepareStatement("""
            INSERT INTO legacy_billing.fee_adjustment
                (legacy_reference_id, account_id, delta_bps, effective_date, reason,
                 posted_by_user_id, posted_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, statement -> {
            statement.setString(1, referenceId);
            statement.setString(2, body.accountId());
            statement.setInt(3, body.deltaBps());
            statement.setObject(4, body.effectiveDate());
            statement.setString(5, body.reason());
            statement.setString(6, scope.userId());
            statement.setObject(7, now.atOffset(java.time.ZoneOffset.UTC));
            return statement.executeUpdate();
        });
        int newFee = accounts.applyDelta(body.accountId(), body.deltaBps())
            .orElseThrow(() -> new HttpStatusException(HttpStatus.FORBIDDEN, "Not entitled"));

        return HttpResponse.created(new Dtos.FeeAdjustmentResult(
            referenceId, body.accountId(), body.deltaBps(), body.effectiveDate(), newFee));
    }
}
