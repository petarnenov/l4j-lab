-- One execution of the four-node chain. See data-model.md.
CREATE TABLE chain_run (
    id             uuid PRIMARY KEY,
    company_id     text        NOT NULL,
    period         text        NOT NULL,
    status         text        NOT NULL,
    current_node   text,
    failed_node    text,
    failure_reason text,
    provider_mode  text        NOT NULL,
    model_id       text        NOT NULL,
    summary_text   text,
    started_at     timestamptz NOT NULL,
    ended_at       timestamptz,

    CONSTRAINT chain_run_status_allowed
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'TIMED_OUT')),

    CONSTRAINT chain_run_provider_mode_allowed
        CHECK (provider_mode IN ('LOCAL', 'CLOUD')),

    -- Both present for FAILED and for TIMED_OUT, so the learner can name the node in either
    -- case (SC-006). Both absent otherwise.
    CONSTRAINT chain_run_failure_pair
        CHECK ((failed_node IS NULL) = (failure_reason IS NULL)),

    CONSTRAINT chain_run_failure_only_when_failed
        CHECK (
            (status IN ('FAILED', 'TIMED_OUT') AND failed_node IS NOT NULL)
            OR (status NOT IN ('FAILED', 'TIMED_OUT') AND failed_node IS NULL)
        ),

    -- ended_at is null exactly when the run has not reached a terminal state.
    CONSTRAINT chain_run_ended_at_matches_status
        CHECK (
            (status IN ('PENDING', 'RUNNING') AND ended_at IS NULL)
            OR (status NOT IN ('PENDING', 'RUNNING') AND ended_at IS NOT NULL)
        )
);

-- The only ordering the history list uses.
CREATE INDEX chain_run_started_at_desc ON chain_run (started_at DESC, id DESC);
