package dev.l4jlab.mcp.tasks;

import dev.l4jlab.mcp.protocol.ToolFailure;
import io.micronaut.context.annotation.Value;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable state for the Tasks extension (FR-021, research.md R-003).
 *
 * <p>Shared across replicas, which is the whole reason it is a table rather than a field. A client
 * polls whichever replica the proxy hands it, and every one of them must answer identically.
 *
 * <p>The row is committed <em>before</em> the handle is returned. The extension requires it, and the
 * practical consequence is what matters: a client that polls immediately, on a different replica,
 * must not be told its task does not exist.
 */
@Singleton
public class TaskStore {

    /** A task as the wire sees it. */
    public record Task(String taskId, String status, String statusMessage, String legacyRunId,
                       Instant createdAt, Instant lastUpdatedAt, Long ttlMs, Integer pollIntervalMs) {

        /** Terminal states never change again, which is what lets a poller stop. */
        public boolean isTerminal() {
            return "completed".equals(status) || "failed".equals(status) || "cancelled".equals(status);
        }
    }

    private static final int POLL_INTERVAL_MS = 2000;

    private final JdbcOperations jdbc;
    private final Clock clock;
    private final long ttlMs;

    public TaskStore(JdbcOperations jdbc, Clock clock,
                     @Value("${mcp.task-ttl-ms:900000}") long ttlMs) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.ttlMs = ttlMs;
    }

    @Transactional
    public Task create(String principalUserId, String toolName, String legacyRunId,
                       String statusMessage) {
        String taskId = "tsk_" + UUID.randomUUID().toString().replace("-", "");
        Instant now = Instant.now(clock);
        jdbc.prepareStatement("""
            INSERT INTO mcp_ops.task_record
                (task_id, principal_user_id, tool_name, status, status_message, legacy_run_id,
                 created_at, last_updated_at, ttl_ms, poll_interval_ms)
            VALUES (?, ?, ?, 'working', ?, ?, ?, ?, ?, ?)
            """, statement -> {
            statement.setString(1, taskId);
            statement.setString(2, principalUserId);
            statement.setString(3, toolName);
            statement.setString(4, statusMessage);
            statement.setString(5, legacyRunId);
            statement.setObject(6, now.atOffset(ZoneOffset.UTC));
            statement.setObject(7, now.atOffset(ZoneOffset.UTC));
            statement.setLong(8, ttlMs);
            statement.setInt(9, POLL_INTERVAL_MS);
            return statement.executeUpdate();
        });
        return new Task(taskId, "working", statusMessage, legacyRunId, now, now, ttlMs,
            POLL_INTERVAL_MS);
    }

    /**
     * @throws ToolFailure when the task is unknown, expired, or belongs to another caller — all with
     *     one message, because a poller learning that a task exists but is not theirs learns
     *     something they were not entitled to
     */
    @Transactional
    public Task requireOwn(String taskId, String principalUserId) {
        return find(taskId, principalUserId).orElseThrow(() -> new ToolFailure(
            "No such task, or it has expired. Start the operation again to get a new handle."));
    }

    @Transactional
    public Optional<Task> find(String taskId, String principalUserId) {
        return jdbc.prepareStatement("""
            SELECT task_id, status, status_message, legacy_run_id, created_at, last_updated_at,
                   ttl_ms, poll_interval_ms
              FROM mcp_ops.task_record
             WHERE task_id = ? AND principal_user_id = ?
            """, statement -> {
            statement.setString(1, taskId);
            statement.setString(2, principalUserId);
            var rs = statement.executeQuery();
            if (!rs.next()) {
                return Optional.<Task>empty();
            }
            return Optional.of(new Task(
                rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getObject(5, java.time.OffsetDateTime.class).toInstant(),
                rs.getObject(6, java.time.OffsetDateTime.class).toInstant(),
                rs.getObject(7) == null ? null : rs.getLong(7),
                rs.getObject(8) == null ? null : rs.getInt(8)));
        });
    }

    /** Moves a task on. Terminal states are never left, so a late update cannot resurrect one. */
    @Transactional
    public void update(String taskId, String status, String statusMessage, String resultJson,
                       String errorJson) {
        Instant now = Instant.now(clock);
        jdbc.prepareStatement("""
            UPDATE mcp_ops.task_record
               SET status = ?, status_message = ?, last_updated_at = ?,
                   result_json = COALESCE(CAST(? AS JSONB), result_json),
                   error_json = COALESCE(CAST(? AS JSONB), error_json)
             WHERE task_id = ? AND status NOT IN ('completed', 'failed', 'cancelled')
            """, statement -> {
            statement.setString(1, status);
            statement.setString(2, statusMessage);
            statement.setObject(3, now.atOffset(ZoneOffset.UTC));
            statement.setString(4, resultJson);
            statement.setString(5, errorJson);
            statement.setString(6, taskId);
            return statement.executeUpdate();
        });
    }

    @Transactional
    public Optional<String> resultJson(String taskId) {
        return jdbc.prepareStatement(
            "SELECT result_json::text FROM mcp_ops.task_record WHERE task_id = ?",
            statement -> {
                statement.setString(1, taskId);
                var rs = statement.executeQuery();
                return rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.<String>empty();
            });
    }
}
