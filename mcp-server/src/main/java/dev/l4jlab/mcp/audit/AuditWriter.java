package dev.l4jlab.mcp.audit;

import dev.l4jlab.mcp.security.Principal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.json.JsonMapper;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One row per tool invocation (FR-026).
 *
 * <p>This is the analogue of Principle V's agent trace: no agent runs here, so there is nothing for
 * LangChain4j's observation mechanisms to observe, but an invocation that cannot be inspected
 * afterwards is still unfinished work. Held to the same bar — structured, no secrets, no full
 * payloads, and it records which step failed.
 */
@Singleton
public class AuditWriter {

    /** What happened. Protocol failures are audited too, or a rejected request leaves no trace. */
    public enum Outcome {
        OK, TOOL_ERROR, PROTOCOL_ERROR
    }

    private final JdbcOperations jdbc;
    private final JsonMapper json;
    private final Clock clock;

    public AuditWriter(JdbcOperations jdbc, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public void record(Principal principal, String toolName, Map<String, Object> arguments,
                       Outcome outcome, @Nullable String errorCode, long durationMs,
                       @Nullable String traceparent, @Nullable String confirmedByUserId,
                       @Nullable String legacyReferenceId) {
        String summary = writeJson(ArgumentSummary.of(toolName, arguments));
        Instant now = Instant.now(clock);
        jdbc.prepareStatement("""
            INSERT INTO mcp_ops.audit_record
                (audit_id, occurred_at, principal_user_id, principal_firm_id, principal_role,
                 tool_name, argument_summary, outcome, error_code, duration_ms, traceparent,
                 confirmed_by_user_id, legacy_reference_id)
            VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?, ?, ?, ?, ?, ?)
            """, statement -> {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, now.atOffset(java.time.ZoneOffset.UTC));
            statement.setString(3, principal.userId());
            statement.setString(4, principal.firmId());
            statement.setString(5, principal.role());
            statement.setString(6, toolName);
            statement.setString(7, summary);
            statement.setString(8, outcome.name());
            statement.setString(9, errorCode);
            statement.setInt(10, (int) durationMs);
            statement.setString(11, traceparent);
            statement.setString(12, confirmedByUserId);
            statement.setString(13, legacyReferenceId);
            return statement.executeUpdate();
        });
    }

    private String writeJson(Map<String, Object> summary) {
        try {
            return new String(json.writeValueAsBytes(summary));
        } catch (Exception e) {
            // An audit row with an unwritable summary is still worth more than no audit row.
            return "{}";
        }
    }
}
