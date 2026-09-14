package dev.l4jlab.legacy.domain;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.Optional;

/** Accounts, and the firm walk that decides whether a caller may touch one. */
@JdbcRepository(dialect = Dialect.POSTGRES)
public interface AccountRepository extends CrudRepository<AccountRecord, String> {

    /**
     * An account's firm is reached by walking account → household → advisor → firm. Doing the walk
     * in one query is what lets a cross-firm account be refused with no special case in the caller.
     */
    @Query(value = """
        SELECT a.firm_id
        FROM legacy_billing.account acc
        JOIN legacy_billing.household h ON h.household_id = acc.household_id
        JOIN legacy_billing.advisor a ON a.advisor_id = h.advisor_id
        WHERE acc.account_id = :accountId
        """, nativeQuery = true)
    Optional<String> findFirmIdOfAccount(String accountId);

    @Query(value = "UPDATE legacy_billing.account SET current_fee_bps = current_fee_bps + :deltaBps "
        + "WHERE account_id = :accountId RETURNING current_fee_bps", nativeQuery = true)
    Optional<Integer> applyDelta(String accountId, int deltaBps);
}
