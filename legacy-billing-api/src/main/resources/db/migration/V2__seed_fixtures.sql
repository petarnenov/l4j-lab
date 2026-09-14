-- Feature 007, T015: seeded fixtures, sized for the acceptance tests and no larger.
--
-- Two firms, so cross-firm refusal has something to refuse. Two advisors in firm-alpha, so an
-- ADVISOR's narrow view is visibly narrower than a FIRM_ADMIN's. More than 20 runs for firm-alpha,
-- so pagination has a second page to continue onto (FR-017). One FAILED run with per-household
-- causes, and one long PENDING run for cancellation (FR-031).

INSERT INTO legacy_billing.firm (firm_id, name) VALUES
    ('firm-alpha', 'Alpha Wealth Partners'),
    ('firm-beta', 'Beta Family Office');

INSERT INTO legacy_billing.advisor (advisor_id, firm_id, name) VALUES
    ('adv-101', 'firm-alpha', 'Rowan Ash'),
    ('adv-102', 'firm-alpha', 'Kit Marlowe'),
    ('adv-201', 'firm-beta', 'Sasha Devon');

INSERT INTO legacy_billing.household (household_id, advisor_id, name) VALUES
    ('hh-001', 'adv-101', 'Household 001'),
    ('hh-002', 'adv-101', 'Household 002'),
    ('hh-003', 'adv-101', 'Household 003'),
    ('hh-004', 'adv-101', 'Household 004'),
    ('hh-005', 'adv-102', 'Household 005'),
    ('hh-006', 'adv-102', 'Household 006'),
    ('hh-007', 'adv-102', 'Household 007'),
    ('hh-008', 'adv-201', 'Household 008'),
    ('hh-009', 'adv-201', 'Household 009');

INSERT INTO legacy_billing.account (account_id, household_id, current_fee_bps) VALUES
    ('acc-0101', 'hh-001', 100),
    ('acc-0102', 'hh-001', 90),
    ('acc-0103', 'hh-001', 110),
    ('acc-0201', 'hh-002', 75),
    ('acc-0202', 'hh-002', 75),
    ('acc-0203', 'hh-002', 125),
    ('acc-0301', 'hh-003', 75),
    ('acc-0302', 'hh-003', 100),
    ('acc-0303', 'hh-003', 125),
    ('acc-0401', 'hh-004', 75),
    ('acc-0402', 'hh-004', 125),
    ('acc-0403', 'hh-004', 90),
    ('acc-0501', 'hh-005', 75),
    ('acc-0502', 'hh-005', 75),
    ('acc-0503', 'hh-005', 110),
    ('acc-0601', 'hh-006', 110),
    ('acc-0602', 'hh-006', 75),
    ('acc-0603', 'hh-006', 90),
    ('acc-0701', 'hh-007', 75),
    ('acc-0702', 'hh-007', 125),
    ('acc-0703', 'hh-007', 110),
    ('acc-0801', 'hh-008', 75),
    ('acc-0802', 'hh-008', 125),
    ('acc-0803', 'hh-008', 75),
    ('acc-0901', 'hh-009', 90),
    ('acc-0902', 'hh-009', 125),
    ('acc-0903', 'hh-009', 75);

INSERT INTO legacy_billing.billing_run (run_id, firm_id, executed_by_advisor_id, status,
    phase, accounts_processed, accounts_total, failure_reason, started_at, finished_at) VALUES
    ('run-a001', 'firm-alpha', 'adv-101', 'COMPLETED', NULL, 15, 15, NULL, TIMESTAMPTZ '2026-08-01 09:00:00+00', TIMESTAMPTZ '2026-08-01 11:30:00+00'),
    ('run-a002', 'firm-alpha', 'adv-102', 'COMPLETED', NULL, 18, 18, NULL, TIMESTAMPTZ '2026-08-02 09:00:00+00', TIMESTAMPTZ '2026-08-02 11:30:00+00'),
    ('run-a003', 'firm-alpha', 'adv-101', 'COMPLETED', NULL, 21, 21, NULL, TIMESTAMPTZ '2026-08-03 09:00:00+00', TIMESTAMPTZ '2026-08-03 11:30:00+00'),
    ('run-a004', 'firm-alpha', 'adv-102', 'COMPLETED', NULL, 24, 24, NULL, TIMESTAMPTZ '2026-08-04 09:00:00+00', TIMESTAMPTZ '2026-08-04 11:30:00+00'),
    ('run-a005', 'firm-alpha', 'adv-101', 'COMPLETED', NULL, 12, 12, NULL, TIMESTAMPTZ '2026-08-05 09:00:00+00', TIMESTAMPTZ '2026-08-05 11:30:00+00'),
    ('run-a006', 'firm-alpha', 'adv-102', 'COMPLETED', NULL, 15, 15, NULL, TIMESTAMPTZ '2026-08-06 09:00:00+00', TIMESTAMPTZ '2026-08-06 11:30:00+00'),
    ('run-a007', 'firm-alpha', 'adv-101', 'COMPLETED', NULL, 18, 18, NULL, TIMESTAMPTZ '2026-08-07 09:00:00+00', TIMESTAMPTZ '2026-08-07 11:30:00+00'),
    ('run-a008', 'firm-alpha', 'adv-102', 'COMPLETED', NULL, 21, 21, NULL, TIMESTAMPTZ '2026-08-08 09:00:00+00', TIMESTAMPTZ '2026-08-08 11:30:00+00'),
    ('run-a009', 'firm-alpha', 'adv-101', 'COMPLETED', NULL, 24, 24, NULL, TIMESTAMPTZ '2026-08-09 09:00:00+00', TIMESTAMPTZ '2026-08-09 11:30:00+00'),
    ('run-a010', 'firm-alpha', 'adv-102', 'COMPLETED', NULL, 12, 12, NULL, TIMESTAMPTZ '2026-08-10 09:00:00+00', TIMESTAMPTZ '2026-08-10 11:30:00+00'),
    ('run-a011', 'firm-alpha', 'adv-101', 'COMPLETED', NULL, 15, 15, NULL, TIMESTAMPTZ '2026-08-11 09:00:00+00', TIMESTAMPTZ '2026-08-11 11:30:00+00'),
    ('run-a012', 'firm-alpha', 'adv-102', 'COMPLETED', NULL, 18, 18, NULL, TIMESTAMPTZ '2026-08-12 09:00:00+00', TIMESTAMPTZ '2026-08-12 11:30:00+00'),
    ('run-a013', 'firm-alpha', 'adv-101', 'COMPLETED', NULL, 21, 21, NULL, TIMESTAMPTZ '2026-08-13 09:00:00+00', TIMESTAMPTZ '2026-08-13 11:30:00+00'),
    ('run-a014', 'firm-alpha', 'adv-102', 'COMPLETED', NULL, 24, 24, NULL, TIMESTAMPTZ '2026-08-14 09:00:00+00', TIMESTAMPTZ '2026-08-14 11:30:00+00'),
    ('run-a015', 'firm-alpha', 'adv-101', 'COMPLETED', NULL, 12, 12, NULL, TIMESTAMPTZ '2026-08-15 09:00:00+00', TIMESTAMPTZ '2026-08-15 11:30:00+00'),
    ('run-a016', 'firm-alpha', 'adv-102', 'COMPLETED', NULL, 15, 15, NULL, TIMESTAMPTZ '2026-08-16 09:00:00+00', TIMESTAMPTZ '2026-08-16 11:30:00+00'),
    ('run-a017', 'firm-alpha', 'adv-101', 'COMPLETED', NULL, 18, 18, NULL, TIMESTAMPTZ '2026-08-17 09:00:00+00', TIMESTAMPTZ '2026-08-17 11:30:00+00'),
    ('run-a018', 'firm-alpha', 'adv-102', 'COMPLETED', NULL, 21, 21, NULL, TIMESTAMPTZ '2026-08-18 09:00:00+00', TIMESTAMPTZ '2026-08-18 11:30:00+00'),
    ('run-a019', 'firm-alpha', 'adv-101', 'FAILED', NULL, 12, 24, 'Three households could not be priced', TIMESTAMPTZ '2026-08-19 09:00:00+00', TIMESTAMPTZ '2026-08-19 11:30:00+00'),
    ('run-a020', 'firm-alpha', 'adv-102', 'FAILED', NULL, 6, 12, 'Three households could not be priced', TIMESTAMPTZ '2026-08-20 09:00:00+00', TIMESTAMPTZ '2026-08-20 11:30:00+00'),
    ('run-a021', 'firm-alpha', 'adv-101', 'FAILED', NULL, 7, 15, 'Three households could not be priced', TIMESTAMPTZ '2026-08-21 09:00:00+00', TIMESTAMPTZ '2026-08-21 11:30:00+00'),
    ('run-a022', 'firm-alpha', 'adv-102', 'CANCELED', NULL, 9, 18, NULL, TIMESTAMPTZ '2026-08-22 09:00:00+00', TIMESTAMPTZ '2026-08-22 11:30:00+00'),
    ('run-a023', 'firm-alpha', 'adv-101', 'CANCELED', NULL, 10, 21, NULL, TIMESTAMPTZ '2026-08-23 09:00:00+00', TIMESTAMPTZ '2026-08-23 11:30:00+00'),
    ('run-a024', 'firm-alpha', 'adv-102', 'RUNNING', 'FEE_CALC', 12, 24, NULL, TIMESTAMPTZ '2026-08-24 09:00:00+00', NULL),
    ('run-a025', 'firm-alpha', 'adv-101', 'RUNNING', 'FEE_CALC', 6, 12, NULL, TIMESTAMPTZ '2026-08-25 09:00:00+00', NULL),
    ('run-a026', 'firm-alpha', 'adv-102', 'PENDING', 'DATA_COLLECTION', 0, 15, NULL, TIMESTAMPTZ '2026-08-26 09:00:00+00', NULL),
    ('run-b001', 'firm-beta', 'adv-201', 'COMPLETED', NULL, 6, 6, NULL, TIMESTAMPTZ '2026-08-01 09:00:00+00', TIMESTAMPTZ '2026-08-01 10:00:00+00'),
    ('run-b002', 'firm-beta', 'adv-201', 'COMPLETED', NULL, 6, 6, NULL, TIMESTAMPTZ '2026-08-02 09:00:00+00', TIMESTAMPTZ '2026-08-02 10:00:00+00'),
    ('run-b003', 'firm-beta', 'adv-201', 'COMPLETED', NULL, 6, 6, NULL, TIMESTAMPTZ '2026-08-03 09:00:00+00', TIMESTAMPTZ '2026-08-03 10:00:00+00'),
    ('run-b004', 'firm-beta', 'adv-201', 'COMPLETED', NULL, 6, 6, NULL, TIMESTAMPTZ '2026-08-04 09:00:00+00', TIMESTAMPTZ '2026-08-04 10:00:00+00');

INSERT INTO legacy_billing.run_failure (run_id, household_id, cause) VALUES
    ('run-a019', 'hh-001', 'Missing market value on the valuation date'),
    ('run-a019', 'hh-002', 'Missing market value on the valuation date'),
    ('run-a019', 'hh-005', 'Missing market value on the valuation date'),
    ('run-a020', 'hh-001', 'Missing market value on the valuation date'),
    ('run-a020', 'hh-002', 'Missing market value on the valuation date'),
    ('run-a020', 'hh-005', 'Missing market value on the valuation date'),
    ('run-a021', 'hh-001', 'Missing market value on the valuation date'),
    ('run-a021', 'hh-002', 'Missing market value on the valuation date'),
    ('run-a021', 'hh-005', 'Missing market value on the valuation date');

-- The FAILED runs the acceptance tests open: run-a019, run-a020, run-a021
