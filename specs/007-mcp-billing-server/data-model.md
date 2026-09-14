# Data Model: MCP Billing Server

**Feature**: `007-mcp-billing-server` | **Date**: 2026-09-13

Two schemas in the one PostgreSQL instance (Constitution: PostgreSQL is the only datastore).
The split is the point of the topology, so it is stated first:

- **`legacy_billing`** — owned by the legacy REST API. All domain data. The MCP server has
  no credentials for it and no entity mapped to it.
- **`mcp_ops`** — owned by the MCP server. Protocol and operational state only. Contains no
  billing entity. This is what "the MCP server has no database of its own for domain data"
  means concretely.

---

## Part 1 — `legacy_billing` (system of record)

### Firm

| Field | Type | Rules |
|---|---|---|
| `firm_id` | text, PK | e.g. `firm-alpha` |
| `name` | text | not null |

### Advisor

| Field | Type | Rules |
|---|---|---|
| `advisor_id` | text, PK | e.g. `adv-101` |
| `firm_id` | text, FK → Firm | not null |
| `name` | text | not null |

### Household

| Field | Type | Rules |
|---|---|---|
| `household_id` | text, PK | |
| `advisor_id` | text, FK → Advisor | not null |
| `name` | text | not null |

### Account

| Field | Type | Rules |
|---|---|---|
| `account_id` | text, PK | |
| `household_id` | text, FK → Household | not null |
| `current_fee_bps` | integer | `>= 0`; basis points, so fees are exact integers, never floats |

Hierarchy: `Firm 1—* Advisor 1—* Household 1—* Account`. An account's firm is reached by
walking up; the legacy API's entitlement check uses that walk, which is why a cross-firm
account is refused without any special case.

### BillingRun

| Field | Type | Rules |
|---|---|---|
| `run_id` | text, PK | |
| `firm_id` | text, FK → Firm | not null |
| `executed_by_advisor_id` | text, FK → Advisor | not null; must belong to `firm_id` |
| `status` | text | one of `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELED` |
| `phase` | text | one of `DATA_COLLECTION`, `FEE_CALC`, `INVOICING`, `POSTING`; null once terminal |
| `accounts_processed` | integer | `>= 0`, `<= accounts_total` |
| `accounts_total` | integer | `>= 0` |
| `failure_reason` | text | null unless `status = FAILED`; non-null when `status = FAILED` |
| `started_at` | timestamptz | not null |
| `finished_at` | timestamptz | null until terminal |

**State transitions**

```
PENDING ──► RUNNING ──► COMPLETED
   │           │
   │           ├──────► FAILED
   │           │
   └───────────┴──────► CANCELED
```

- `PENDING` is the state a run is created in and the state `start_billing_run` returns
  against.
- While `RUNNING`, `phase` advances `DATA_COLLECTION → FEE_CALC → INVOICING → POSTING` and
  `accounts_processed` climbs toward `accounts_total`. The simulated run spends 30–90
  seconds total across the four phases (FR-024).
- `COMPLETED`, `FAILED`, and `CANCELED` are terminal; nothing leaves them.
- `CANCELED` is reached only by `tasks/cancel` (FR-031) — callable by any client holding the
  task handle, extension declared or not — which asks the legacy API to cancel
  the run. Cancellation is cooperative: a run already terminal keeps its status and the
  request is still acknowledged. Note the spelling difference from the Tasks extension's
  `cancelled` in `task_record.status` — the domain enum and the wire enum are separate
  vocabularies and neither is a typo.
- `failure_reason` is set in the same write that sets `status = FAILED`, so a FAILED run is
  never observed without its reason.

### RunFailure

Per-household failure detail for a FAILED run.

| Field | Type | Rules |
|---|---|---|
| `run_id` | text, FK → BillingRun | part of PK |
| `household_id` | text, FK → Household | part of PK |
| `cause` | text | not null; short, caller-safe phrasing |

### FeeAdjustment

| Field | Type | Rules |
|---|---|---|
| `legacy_reference_id` | text, PK | minted by the legacy API; returned to the caller |
| `account_id` | text, FK → Account | not null |
| `delta_bps` | integer | signed, non-zero |
| `effective_date` | date | not null |
| `posted_by_user_id` | text | not null; the principal on whose behalf the call was made |
| `posted_at` | timestamptz | not null |

Applying an adjustment moves `Account.current_fee_bps` by `delta_bps` in the same
transaction that inserts the row, so the two can never disagree.

---

## Part 2 — `mcp_ops` (MCP server, shared across the three replicas)

### OperationRecord — idempotency for `post_fee_adjustment` (R-006)

| Field | Type | Rules |
|---|---|---|
| `principal_user_id` | text | part of PK |
| `operation_id` | text | part of PK; client-supplied |
| `request_digest` | text | SHA-256 over the salient arguments |
| `outcome` | text | `SUCCEEDED` or `FAILED` |
| `legacy_reference_id` | text | null when `outcome = FAILED` |
| `result_json` | jsonb | the original tool result, returned verbatim on replay |
| `created_at` | timestamptz | not null |

Rules:

- PK is `(principal_user_id, operation_id)` — one principal cannot replay another's
  operation id.
- A second call with the same key and a **matching** `request_digest` returns `result_json`
  and does not call the legacy API.
- A second call with the same key and a **differing** `request_digest` is a tool error: the
  operation id was reused for a different change. Returning the first result would be
  worse than refusing.
- The row is written in the same transaction that records the legacy outcome, so a crash
  between the legacy write and the record leaves the record absent, not wrong. That failure
  mode is accepted and documented: the legacy `legacy_reference_id` is the tiebreaker.

### TaskRecord — Tasks extension state (R-003)

| Field | Type | Rules |
|---|---|---|
| `task_id` | text, PK | opaque, server-minted |
| `principal_user_id` | text | not null; a poll by another principal is refused |
| `tool_name` | text | not null |
| `status` | text | `working`, `input_required`, `completed`, `failed`, `cancelled` |
| `status_message` | text | nullable |
| `legacy_run_id` | text | the run this task is tracking |
| `result_json` | jsonb | set when `status = completed` |
| `error_json` | jsonb | set when `status = failed` |
| `created_at` | timestamptz | not null; `createdAt` on the wire |
| `last_updated_at` | timestamptz | not null; `lastUpdatedAt` on the wire |
| `ttl_ms` | bigint | nullable, matching the wire type `number \| null` |
| `poll_interval_ms` | integer | nullable |

Rules:

- The row is committed **before** the `CreateTaskResult` is returned — the extension
  requires the task to be durably created first, and it is also what makes an immediate poll
  on another replica succeed.
- `completed`, `failed`, `cancelled` are terminal.
- Status is refreshed from the legacy run on each `tasks/get`, so no background worker is
  needed and no replica owns a task.
- An unknown or expired `task_id` produces a tool-shaped error saying so, per the spec's
  guidance on expiry errors.

### AuditRecord — FR-026

| Field | Type | Rules |
|---|---|---|
| `audit_id` | uuid, PK | |
| `occurred_at` | timestamptz | not null |
| `principal_user_id` | text | not null |
| `principal_firm_id` | text | not null |
| `principal_role` | text | not null |
| `tool_name` | text | not null |
| `argument_summary` | jsonb | **identifiers only** — never a full payload |
| `outcome` | text | `OK`, `TOOL_ERROR`, `PROTOCOL_ERROR` |
| `error_code` | text | nullable, short and stable |
| `duration_ms` | integer | not null |
| `traceparent` | text | nullable; the W3C value taken from `_meta` |
| `confirmed_by_user_id` | text | nullable; set for `post_fee_adjustment` (FR-020). Always equals `principal_user_id` — an `ElicitResult` carries no identity, so the protocol supplies no separate confirmer. The column exists because the audit question "who confirmed this fee change" must be answerable without a join or a convention |
| `legacy_reference_id` | text | nullable; set for `post_fee_adjustment` |

`argument_summary` is built by an allow-list of key names per tool, not by redacting a
serialized payload. A deny-list would leak the first time a field was added.

---

## Part 3 — Principal (not persisted)

Derived per request from the validated inbound token (FR-013). It exists only for the life
of the request; nothing about it is cached between requests, which is what statelessness
requires.

| Field | Source claim | Rules |
|---|---|---|
| `userId` | `sub` | not null |
| `firmId` | `firm_id` | not null |
| `role` | `role` | `FIRM_ADMIN`, `ADVISOR`, `OPS`, `READ_ONLY` |
| `allowedAdvisorIds` | `advisor_ids` | non-empty; for `ADVISOR` it is exactly that advisor |

The MCP server does **not** decide entitlements from these fields — it passes them onward in
the exchanged token and lets the legacy API decide (FR-014). They are on the principal for
the audit record and for the cursor and request-state bindings.

## Validation rules gathered from the requirements

| Rule | Source |
|---|---|
| Page size clamped to 20, not rejected | FR-017, spec Edge Cases |
| `delta_bps` non-zero | FR-020; a zero adjustment is a no-op worth refusing |
| `effective_date` must be a valid date; no ordering constraint imposed | FR-020 |
| A cursor's principal must match the caller | R-009 |
| A `requestState`'s principal must match the caller, and it must be unexpired | R-002 |
| A confirmation must match the proposed change | spec Edge Cases |
| A task poll's principal must match the task's principal | R-003 |
