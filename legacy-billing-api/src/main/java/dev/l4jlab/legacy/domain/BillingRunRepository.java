package dev.l4jlab.legacy.domain;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Searching runs, scoped by the caller's entitlements.
 *
 * <p>The scope is applied <em>inside</em> the query rather than by filtering afterwards, so the
 * total count a caller sees is the caller's total and never leaks the size of the unfiltered set
 * (FR-017).
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
public interface BillingRunRepository extends CrudRepository<BillingRunRecord, String> {

    String SCOPED_WHERE = """
        WHERE r.firm_id = :firmId
          AND (:wholeFirm OR r.executed_by_advisor_id = ANY (CAST(:advisorIds AS text[])))
          AND (CAST(:status AS text) IS NULL OR r.status = :status)
          AND (CAST(:advisorId AS text) IS NULL OR r.executed_by_advisor_id = :advisorId)
          AND (CAST(:startedFrom AS timestamptz) IS NULL OR r.started_at >= :startedFrom)
          AND (CAST(:startedTo AS timestamptz) IS NULL OR r.started_at <= :startedTo)
        """;

    @Query(value = "SELECT r.* FROM legacy_billing.billing_run r " + SCOPED_WHERE
        + " ORDER BY r.started_at DESC, r.run_id DESC OFFSET :offset LIMIT :limit",
        nativeQuery = true)
    List<BillingRunRecord> search(String firmId, boolean wholeFirm, String advisorIds,
                                  // Optional filters: absent means "do not narrow on this", which
                                  // the CAST(... AS text) IS NULL branches above express in SQL.
                                  @Nullable String status, @Nullable String advisorId,
                                  @Nullable Instant startedFrom, @Nullable Instant startedTo,
                                  int offset, int limit);

    @Query(value = "SELECT COUNT(*) FROM legacy_billing.billing_run r " + SCOPED_WHERE,
        nativeQuery = true)
    long countMatching(String firmId, boolean wholeFirm, String advisorIds,
                       @Nullable String status, @Nullable String advisorId,
                       @Nullable Instant startedFrom, @Nullable Instant startedTo);

    @Query(value = """
        -- Aliased to the record's component names: Micronaut Data maps a projection by column
        -- name, and `h.name` would arrive as `name` while the record expects `household_name`.
        SELECT h.household_id AS household_id, h.name AS household_name, f.cause AS cause
        FROM legacy_billing.run_failure f
        JOIN legacy_billing.household h ON h.household_id = f.household_id
        WHERE f.run_id = :runId
        ORDER BY h.household_id
        LIMIT :limit
        """, nativeQuery = true)
    List<RunFailureRecord> failures(String runId, int limit);

    @Query(value = "SELECT COUNT(*) FROM legacy_billing.run_failure WHERE run_id = :runId",
        nativeQuery = true)
    long countFailures(String runId);

    Optional<BillingRunRecord> findByRunId(String runId);
}
