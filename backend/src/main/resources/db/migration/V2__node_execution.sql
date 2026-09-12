-- One node's participation in one run. Four rows per completed run. See data-model.md.
CREATE TABLE node_execution (
    id                  uuid PRIMARY KEY,
    run_id              uuid        NOT NULL REFERENCES chain_run (id) ON DELETE CASCADE,
    position            smallint    NOT NULL,
    node_name           text        NOT NULL,
    input_payload       jsonb       NOT NULL,
    output_payload      jsonb,
    succeeded           boolean     NOT NULL,
    failure_reason      text,
    started_at          timestamptz NOT NULL,
    duration_ms         integer     NOT NULL,
    model_request_text  text,
    model_response_text text,
    input_tokens        integer,
    output_tokens       integer,

    CONSTRAINT node_execution_unique_position UNIQUE (run_id, position),

    CONSTRAINT node_execution_position_range
        CHECK (position BETWEEN 1 AND 4),

    -- The node's name() must return the contract spelling, never the class name. A class renamed
    -- without its name() breaks the insert here rather than the compile.
    CONSTRAINT node_execution_name_allowed
        CHECK (node_name IN ('PrepareRequest', 'RetrieveRecords', 'ComputeIndicators', 'Summarize')),

    CONSTRAINT node_execution_output_matches_outcome
        CHECK ((output_payload IS NULL) = (succeeded IS FALSE)),

    CONSTRAINT node_execution_failure_reason_matches_outcome
        CHECK ((failure_reason IS NULL) = (succeeded IS TRUE)),

    -- The four model columns belong to the summarizing node alone.
    CONSTRAINT node_execution_model_columns_position_four
        CHECK (
            position = 4
            OR (model_request_text IS NULL
                AND model_response_text IS NULL
                AND input_tokens IS NULL
                AND output_tokens IS NULL)
        )
);

CREATE INDEX node_execution_run_id ON node_execution (run_id, position);
