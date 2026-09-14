-- Feature 007, T014: the MCP server's own state.
--
-- Protocol and operational state only. There is deliberately no billing entity here — the legacy
-- API owns the domain, and a reader should be able to confirm that by reading this file.
--
-- These three tables exist because three things must survive between requests and be seen
-- identically by all three replicas: whether an operation already executed, what a task is doing,
-- and what was done. Cursors and MRTR request state are NOT here: they are signed and carried in
-- the request itself, which is what the revision prefers (research.md R-005).
CREATE SCHEMA IF NOT EXISTS mcp_ops;

-- FR-020: at-most-once execution of a fee adjustment, keyed by the client's operation id.
CREATE TABLE mcp_ops.operation_record (
    principal_user_id   TEXT NOT NULL,
    operation_id        TEXT NOT NULL,
    -- A digest of the salient arguments, so an operation id reused for a *different* change is
    -- refused rather than answered with the first change's result.
    request_digest      TEXT NOT NULL,
    outcome             TEXT NOT NULL CHECK (outcome IN ('SUCCEEDED', 'FAILED')),
    legacy_reference_id TEXT,
    result_json         JSONB NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    -- Keyed by principal as well as operation id: one principal cannot replay another's.
    PRIMARY KEY (principal_user_id, operation_id)
);

-- FR-021: Tasks extension state. Committed before the handle is returned, which is both what the
-- extension requires and what lets an immediate poll on another replica succeed.
CREATE TABLE mcp_ops.task_record (
    task_id           TEXT PRIMARY KEY,
    principal_user_id TEXT NOT NULL,
    tool_name         TEXT NOT NULL,
    status            TEXT NOT NULL
        CHECK (status IN ('working', 'input_required', 'completed', 'failed', 'cancelled')),
    status_message    TEXT,
    legacy_run_id     TEXT,
    result_json       JSONB,
    error_json        JSONB,
    created_at        TIMESTAMPTZ NOT NULL,
    last_updated_at   TIMESTAMPTZ NOT NULL,
    ttl_ms            BIGINT,
    poll_interval_ms  INTEGER
);

CREATE INDEX task_record_by_principal ON mcp_ops.task_record (principal_user_id);

-- FR-026: one row per tool invocation. The argument summary is built from a per-tool allow-list of
-- key names, never by redacting a serialized payload — a deny-list leaks the first time a field is
-- added.
CREATE TABLE mcp_ops.audit_record (
    audit_id             UUID PRIMARY KEY,
    occurred_at          TIMESTAMPTZ NOT NULL,
    principal_user_id    TEXT NOT NULL,
    principal_firm_id    TEXT NOT NULL,
    principal_role       TEXT NOT NULL,
    tool_name            TEXT NOT NULL,
    argument_summary     JSONB NOT NULL,
    outcome              TEXT NOT NULL CHECK (outcome IN ('OK', 'TOOL_ERROR', 'PROTOCOL_ERROR')),
    error_code           TEXT,
    duration_ms          INTEGER NOT NULL,
    traceparent          TEXT,
    confirmed_by_user_id TEXT,
    legacy_reference_id  TEXT
);

CREATE INDEX audit_record_by_time ON mcp_ops.audit_record (occurred_at DESC);
