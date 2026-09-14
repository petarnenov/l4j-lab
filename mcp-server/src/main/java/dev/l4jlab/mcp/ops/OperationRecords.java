package dev.l4jlab.mcp.ops;

import dev.l4jlab.mcp.protocol.ToolFailure;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * At-most-once execution of a fee adjustment (FR-020, research.md R-006).
 *
 * <p>Shared across the three replicas, because the three-call scenario is explicitly allowed to land
 * on three different ones. This is the state that cannot travel in the request: whether something
 * already happened is not something the client can be trusted to tell us.
 *
 * <p>Keyed by {@code (principal, operation_id)} rather than operation id alone, so one caller cannot
 * replay another's key — a client-supplied identifier is a name the client chose, not a secret.
 *
 * <p>The digest is stored so that an operation id reused for a <em>different</em> change is refused
 * rather than answered with the first change's result. Returning the earlier result there would be
 * the worst outcome available: the caller would believe a change happened that never did.
 */
@Singleton
public class OperationRecords {

    private final JdbcOperations jdbc;
    private final Clock clock;

    public OperationRecords(JdbcOperations jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** A previously completed operation, as it was answered the first time. */
    public record Completed(String outcome, String legacyReferenceId, String resultJson) {
    }

    /**
     * @return the original result when this exact operation already ran
     * @throws ToolFailure when the operation id was reused for different arguments
     */
    @Transactional
    public Optional<Completed> findMatching(String principalUserId, String operationId,
                                            String requestDigest) {
        return jdbc.prepareStatement("""
            SELECT request_digest, outcome, legacy_reference_id, result_json::text
              FROM mcp_ops.operation_record
             WHERE principal_user_id = ? AND operation_id = ?
            """, statement -> {
            statement.setString(1, principalUserId);
            statement.setString(2, operationId);
            var rs = statement.executeQuery();
            if (!rs.next()) {
                return Optional.<Completed>empty();
            }
            String storedDigest = rs.getString(1);
            if (!storedDigest.equals(requestDigest)) {
                throw new ToolFailure("That operation_id was already used for a different change. "
                    + "Use a new operation_id for a new change.");
            }
            return Optional.of(new Completed(rs.getString(2), rs.getString(3), rs.getString(4)));
        });
    }

    /**
     * Records the outcome. The unique primary key is what makes this safe under a race: two replicas
     * answering the same retry at once cannot both write, and the loser reads the winner's row.
     */
    @Transactional
    public void record(String principalUserId, String operationId, String requestDigest,
                       String outcome, String legacyReferenceId, String resultJson) {
        Instant now = Instant.now(clock);
        jdbc.prepareStatement("""
            INSERT INTO mcp_ops.operation_record
                (principal_user_id, operation_id, request_digest, outcome, legacy_reference_id,
                 result_json, created_at)
            VALUES (?, ?, ?, ?, ?, CAST(? AS JSONB), ?)
            ON CONFLICT (principal_user_id, operation_id) DO NOTHING
            """, statement -> {
            statement.setString(1, principalUserId);
            statement.setString(2, operationId);
            statement.setString(3, requestDigest);
            statement.setString(4, outcome);
            statement.setString(5, legacyReferenceId);
            statement.setString(6, resultJson);
            statement.setObject(7, now.atOffset(ZoneOffset.UTC));
            return statement.executeUpdate();
        });
    }
}
