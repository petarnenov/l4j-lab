package dev.l4jlab.chain.persistence;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface ChainRunRepository extends CrudRepository<ChainRunEntity, UUID> {

    /** First page of the history list. Newest first (FR-014). */
    @io.micronaut.data.annotation.Query(
            "SELECT * FROM chain_run ORDER BY started_at DESC, id DESC LIMIT :limit")
    List<ChainRunEntity> findFirstPage(int limit);

    /**
     * Subsequent pages. Keyset paging on (started_at, id) rather than OFFSET, so a run inserted
     * while the learner is paging cannot duplicate or skip a row.
     */
    @io.micronaut.data.annotation.Query(
            "SELECT * FROM chain_run WHERE (started_at, id) < (:startedAt, :id) "
                    + "ORDER BY started_at DESC, id DESC LIMIT :limit")
    List<ChainRunEntity> findPageAfter(Instant startedAt, UUID id, int limit);
}
