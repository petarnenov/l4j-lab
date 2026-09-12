package dev.l4jlab.chain.persistence;

import dev.l4jlab.chain.support.PostgresTest;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The committed migrations, applied by Flyway exactly as at startup. Asserts the constraints that
 * the application relies on being enforced by the database rather than only by code.
 */
class MigrationTest extends PostgresTest {

    /**
     * A plain JDBC connection to the container. Deliberately not the injected DataSource: Micronaut
     * Data wraps that one in a transaction-aware proxy, and this test is about the schema the
     * migrations produce, not about the framework in front of it.
     */
    private Connection connect() throws SQLException {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private UUID insertRun(String status, String failedNode, String failureReason, Instant endedAt)
            throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection c = connect();
                PreparedStatement ps =
                        c.prepareStatement(
                                "INSERT INTO chain_run (id, company_id, period, status, provider_mode, "
                                        + "model_id, started_at, failed_node, failure_reason, ended_at) "
                                        + "VALUES (?,?,?,?,?,?,?,?,?,?)")) {
            ps.setObject(1, id);
            ps.setString(2, "northwind-lighting");
            ps.setString(3, "2025-Q2");
            ps.setString(4, status);
            ps.setString(5, "LOCAL");
            ps.setString(6, "test-model");
            ps.setTimestamp(7, Timestamp.from(Instant.now()));
            ps.setString(8, failedNode);
            ps.setString(9, failureReason);
            ps.setTimestamp(10, endedAt == null ? null : Timestamp.from(endedAt));
            ps.executeUpdate();
        }
        return id;
    }

    @Test
    void createsBothTables() throws SQLException {
        try (Connection c = connect();
                Statement s = c.createStatement()) {
            for (String table : new String[] {"chain_run", "node_execution"}) {
                try (ResultSet rs = s.executeQuery("SELECT count(*) FROM " + table)) {
                    assertThat(rs.next()).isTrue();
                }
            }
        }
    }

    @Test
    void acceptsAFailedRunCarryingBothTheNodeAndTheReason() throws SQLException {
        assertThat(insertRun("FAILED", "Summarize", "The model returned nothing.", Instant.now()))
                .isNotNull();
    }

    @Test
    void acceptsATimedOutRunCarryingBothTheNodeAndTheReason() throws SQLException {
        // The widened constraint: a timeout must name its node, or SC-006 cannot hold.
        assertThat(insertRun("TIMED_OUT", "Summarize", "The model did not answer in time.", Instant.now()))
                .isNotNull();
    }

    @Test
    void rejectsAFailedRunWithNoFailingNode() {
        assertThatThrownBy(() -> insertRun("FAILED", null, null, Instant.now()))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void rejectsATerminalRunWithNoEndTime() {
        assertThatThrownBy(() -> insertRun("SUCCEEDED", null, null, null))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void rejectsANodeNameOutsideTheContractSpelling() throws SQLException {
        UUID runId = insertRun("RUNNING", null, null, null);

        // "PrepareRequestNode" is the class name, not the contract name. The constraint exists
        // precisely so that mistake fails loudly.
        assertThatThrownBy(() -> insertNode(runId, 1, "PrepareRequestNode"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void acceptsEveryContractNodeName() throws SQLException {
        UUID runId = insertRun("RUNNING", null, null, null);
        String[] names = {"PrepareRequest", "RetrieveRecords", "ComputeIndicators", "Summarize"};
        for (int i = 0; i < names.length; i++) {
            insertNode(runId, i + 1, names[i]);
        }
    }

    @Test
    void roundTripsAJsonbPayload() throws SQLException {
        UUID runId = insertRun("RUNNING", null, null, null);
        insertNode(runId, 1, "PrepareRequest");

        try (Connection c = connect();
                Statement s = c.createStatement();
                ResultSet rs =
                        s.executeQuery(
                                "SELECT input_payload ->> 'companyId' FROM node_execution WHERE run_id = '"
                                        + runId + "'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualTo("northwind-lighting");
        }
    }

    @Test
    void deletingARunCascadesToItsNodeRecords() throws SQLException {
        UUID runId = insertRun("RUNNING", null, null, null);
        insertNode(runId, 1, "PrepareRequest");

        try (Connection c = connect();
                Statement s = c.createStatement()) {
            s.executeUpdate("DELETE FROM chain_run WHERE id = '" + runId + "'");
            try (ResultSet rs =
                    s.executeQuery("SELECT count(*) FROM node_execution WHERE run_id = '" + runId + "'")) {
                rs.next();
                assertThat(rs.getInt(1)).isZero();
            }
        }
    }

    @Test
    void rejectsTwoRecordsAtTheSamePositionInOneRun() throws SQLException {
        UUID runId = insertRun("RUNNING", null, null, null);
        insertNode(runId, 1, "PrepareRequest");

        assertThatThrownBy(() -> insertNode(runId, 1, "RetrieveRecords"))
                .isInstanceOf(SQLException.class);
    }

    private void insertNode(UUID runId, int position, String nodeName) throws SQLException {
        try (Connection c = connect();
                PreparedStatement ps =
                        c.prepareStatement(
                                "INSERT INTO node_execution (id, run_id, position, node_name, input_payload, "
                                        + "output_payload, succeeded, started_at, duration_ms) "
                                        + "VALUES (?,?,?,?,?::jsonb,?::jsonb,?,?,?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, runId);
            ps.setInt(3, position);
            ps.setString(4, nodeName);
            ps.setString(5, "{\"companyId\":\"northwind-lighting\",\"period\":\"2025-Q2\"}");
            ps.setString(6, "{\"ok\":true}");
            ps.setBoolean(7, true);
            ps.setTimestamp(8, Timestamp.from(Instant.now()));
            ps.setInt(9, 12);
            ps.executeUpdate();
        }
    }
}
