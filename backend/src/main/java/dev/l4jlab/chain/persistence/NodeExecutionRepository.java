package dev.l4jlab.chain.persistence;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

@JdbcRepository(dialect = Dialect.POSTGRES)
public interface NodeExecutionRepository extends CrudRepository<NodeExecutionEntity, UUID> {

    /** Ascending position, so a running chain shows its progress in order. */
    List<NodeExecutionEntity> findByRunIdOrderByPosition(UUID runId);

    void deleteByRunId(UUID runId);
}
