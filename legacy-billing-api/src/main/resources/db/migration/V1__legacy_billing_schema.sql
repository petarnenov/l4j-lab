-- Feature 007, T013 (FR-023): the system of record's own schema.
--
-- The MCP server has no credentials for this schema and maps no entity to it. That separation is
-- the point of the topology, not an implementation detail: it is what makes the token exchange and
-- the entitlement boundary real rather than decorative.
CREATE SCHEMA IF NOT EXISTS legacy_billing;

CREATE TABLE legacy_billing.firm (
    firm_id TEXT PRIMARY KEY,
    name    TEXT NOT NULL
);

CREATE TABLE legacy_billing.advisor (
    advisor_id TEXT PRIMARY KEY,
    firm_id    TEXT NOT NULL REFERENCES legacy_billing.firm (firm_id),
    name       TEXT NOT NULL
);

CREATE TABLE legacy_billing.household (
    household_id TEXT PRIMARY KEY,
    advisor_id   TEXT NOT NULL REFERENCES legacy_billing.advisor (advisor_id),
    name         TEXT NOT NULL
);

-- Fees are basis points, so every amount is an exact integer. A fee that drifts by a rounding error
-- is a bug nobody notices until an invoice is wrong.
CREATE TABLE legacy_billing.account (
    account_id      TEXT PRIMARY KEY,
    household_id    TEXT NOT NULL REFERENCES legacy_billing.household (household_id),
    current_fee_bps INTEGER NOT NULL CHECK (current_fee_bps >= 0)
);

CREATE TABLE legacy_billing.billing_run (
    run_id                 TEXT PRIMARY KEY,
    firm_id                TEXT NOT NULL REFERENCES legacy_billing.firm (firm_id),
    executed_by_advisor_id TEXT NOT NULL REFERENCES legacy_billing.advisor (advisor_id),
    status                 TEXT NOT NULL
        CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELED')),
    phase                  TEXT
        CHECK (phase IS NULL OR phase IN ('DATA_COLLECTION', 'FEE_CALC', 'INVOICING', 'POSTING')),
    accounts_processed     INTEGER NOT NULL DEFAULT 0 CHECK (accounts_processed >= 0),
    accounts_total         INTEGER NOT NULL DEFAULT 0 CHECK (accounts_total >= 0),
    failure_reason         TEXT,
    started_at             TIMESTAMPTZ NOT NULL,
    finished_at            TIMESTAMPTZ,
    -- The run cannot claim to have processed more than it has.
    CONSTRAINT processed_within_total CHECK (accounts_processed <= accounts_total),
    -- A FAILED run is never observed without its reason: the two are set in one write.
    CONSTRAINT failed_runs_carry_a_reason
        CHECK ((status = 'FAILED') = (failure_reason IS NOT NULL))
);

CREATE INDEX billing_run_by_firm ON legacy_billing.billing_run (firm_id, started_at DESC);
CREATE INDEX billing_run_by_advisor ON legacy_billing.billing_run (executed_by_advisor_id);

CREATE TABLE legacy_billing.run_failure (
    run_id       TEXT NOT NULL REFERENCES legacy_billing.billing_run (run_id),
    household_id TEXT NOT NULL REFERENCES legacy_billing.household (household_id),
    cause        TEXT NOT NULL,
    PRIMARY KEY (run_id, household_id)
);

CREATE TABLE legacy_billing.fee_adjustment (
    legacy_reference_id TEXT PRIMARY KEY,
    account_id          TEXT NOT NULL REFERENCES legacy_billing.account (account_id),
    -- A zero adjustment is a no-op worth refusing rather than recording.
    delta_bps           INTEGER NOT NULL CHECK (delta_bps <> 0),
    effective_date      DATE NOT NULL,
    reason              TEXT,
    posted_by_user_id   TEXT NOT NULL,
    posted_at           TIMESTAMPTZ NOT NULL
);

CREATE INDEX fee_adjustment_by_account ON legacy_billing.fee_adjustment (account_id);
