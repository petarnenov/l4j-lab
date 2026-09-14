# Contract: legacy billing REST API

The system of record. It owns the domain data and it is the **only** place entitlements are
enforced (FR-014, FR-023). Base path `/api/v1`. Described by Micronaut OpenAPI from its Java
types, per the constitution; this file is the human summary, not a second description.

## Authorization

Every endpoint requires `Authorization: Bearer <jwt>` with:

- `aud` exactly `legacy-billing-api` — any other audience is `401`
- a valid signature against the issuer's public key — otherwise `401`
- an unexpired `exp` — otherwise `401`

Scope is derived from the token's claims, never from the request:

| Claim | Meaning |
|---|---|
| `sub` | acting user id |
| `firm_id` | the caller's firm |
| `role` | `FIRM_ADMIN`, `ADVISOR`, `OPS`, `READ_ONLY` |
| `advisor_ids` | advisors the caller may act for |

Rules:

- Any resource outside `firm_id` → `403`. This is checked before existence, so a cross-firm
  id is indistinguishable from a nonexistent one.
- `ADVISOR` sees only runs whose `executed_by_advisor_id` is in `advisor_ids`.
- `FIRM_ADMIN` and `OPS` see every run of `firm_id`.
- `READ_ONLY` gets `403` on every write.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/billing-runs` | Search. Query: `firmId`, `status`, `advisorId`, `startedFrom`, `startedTo`, `offset`, `limit`. Returns `{ items, totalCount }`. Scope-filtered before paging, so `totalCount` is the caller's total. |
| `GET` | `/api/v1/billing-runs/{runId}` | One run. `403` outside scope. |
| `GET` | `/api/v1/billing-runs/{runId}/failures` | Per-household failures. Query: `limit`. Returns `{ items, totalCount }`. `409` if the run is not FAILED. |
| `POST` | `/api/v1/billing-runs` | Start a run. Body `{ firmId, executedByAdvisorId }`. Returns `201` with the run in `PENDING`. Advances through phases over 30–90s. |
| `POST` | `/api/v1/billing-runs/{runId}/cancel` | Request cancellation of a run (FR-031). No body. Returns `200` with the run's current state. **Cooperative**: a run in `PENDING` or `RUNNING` moves to `CANCELED` with `finishedAt` set; a run already `COMPLETED`, `FAILED`, or `CANCELED` is returned unchanged and the request still succeeds — cancelling a finished run is not an error. `403` outside scope, checked before existence, as for every other write. |
| `POST` | `/api/v1/fee-adjustments` | Apply an adjustment. Body `{ accountId, deltaBps, effectiveDate, reason }`. Returns `201` with `{ legacyReferenceId, accountId, deltaBps, effectiveDate, newFeeBps }`. |
| `GET` | `/health/readiness` | Readiness, as the rest of this repo already does. |

`traceparent` is accepted on every endpoint and honoured as the parent span (FR-027).

## Test-only request log

Behind the `test-capture` profile, and only there, the API records every inbound request's
`Authorization` **and `traceparent`** headers into an in-memory log exposed at
`GET /test/received-requests`. Two assertions read it: SC-005, that the MCP server's own
inbound token never appears; and FR-027, that the `traceparent` a client put in `_meta`
arrives here unchanged. It is never enabled outside tests and never writes to stdout.
