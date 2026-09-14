# Data Model: MCP console

**Feature**: `008-mcp-console` | **Phase**: 1

Nothing here is persisted. Every entity below lives in React state for the life of a browser tab
(R-010), and the console's server state is held by TanStack Query rather than copied into a store.
Field types are TypeScript; shapes belonging to feature 007 are cited, not restated.

---

## Principal

One of the six seeded identities the console can act as. **Only `name` is written down in this
repository** — everything else is read from the claims of the token the issuer mints for that name,
decoded for display and never verified (R-009).

| Field | Type | Source | Notes |
|---|---|---|---|
| `name` | `string` | `principals.ts` | One of the six in `007/contracts/token-issuer.md` |
| `userId` | `string` | token claim `sub` | FR-004 |
| `firmId` | `string` | token claim `firm_id` | FR-004 |
| `role` | `string` | token claim `role` | `ADVISOR`, `FIRM_ADMIN`, `OPS`, `READ_ONLY` |
| `advisorIds` | `string[]` | token claim `advisor_ids` | FR-004's "permitted advisors" |
| `expiresAt` | `Date` | token claim `exp` | Drives re-minting |

**Rules**

- A principal's claims are unavailable until its token is minted; the picker shows the name and a
  loading state, never invented claims.
- Switching principal affects only subsequent calls (FR-006). Results already on screen keep the
  principal they were obtained as, recorded on the `Exchange` and displayed with it.
- The token itself is held in memory, is never persisted, and is never rendered in full (R-009).

**State**: `unminted → minting → active → expired → minting`. Expiry is detected from `exp`, not from
a failed call, so the first call after an hour does not have to fail to be corrected.

---

## Target

One of the four places a call can be aimed at.

| Field | Type | Notes |
|---|---|---|
| `id` | `'proxy' \| 'a' \| 'b' \| 'c'` | |
| `label` | `string` | "Proxy (round-robin)", "mcp-a", … |
| `baseUrl` | `string` | `/mcp-dev/{id}` in the browser; an absolute URL in the live suite (R-005) |
| `reachability` | `'unknown' \| 'reachable' \| 'unreachable'` | From the probe in R-006 |
| `absenceReason` | `string \| null` | Set only when unreachable; names `make mcp-up-topology` |

**Rules**

- `proxy` is always offered. When it is unreachable the whole console shows the offline state and
  names `make mcp-up` (FR-003).
- A replica is selectable only while `reachable`; otherwise it is shown disabled with its reason
  (FR-017). All three unreachable is the normal state of a plain `make mcp-up`, not a fault.
- Token minting always goes to `proxy`, whatever target the call uses — a replica serves neither
  `/dev/` nor `/.well-known/` (R-001).

---

## Tool

A capability the server offers, taken from `tools/list` and never written down in the console.

| Field | Type | Source |
|---|---|---|
| `name` | `string` | server |
| `title` | `string` | server |
| `description` | `string` | server, shown verbatim |
| `inputSchema` | JSON Schema object | server — the sole source of the argument fields (FR-008) |
| `outputSchema` | JSON Schema object | server |
| `annotations` | `{ readOnlyHint, destructiveHint, idempotentHint, openWorldHint }` | server — all four shown (FR-007) |

**Rules**

- Order is the server's, preserved exactly (FR-007). The console does not sort.
- `cacheScope` is `public` and `ttlMs` is 300 000 on this result; the console shows both and treats
  the list as cacheable for that long, because displaying a caching directive it then ignores would
  be its own small lie.
- An `inputSchema` keyword outside the rendered subset produces a raw-JSON field with a visible note,
  never a dropped field (R-003).

---

## ToolArguments

The values a person has typed for the selected tool. Derived from `Tool.inputSchema`, so it has no
fixed shape.

| Field | Type | Notes |
|---|---|---|
| `values` | `Record<string, unknown>` | Keyed by property name |
| `missingRequired` | `string[]` | Computed from `inputSchema.required` |

**Rules**

- FR-009: while `missingRequired` is non-empty the call button is disabled and the missing fields are
  marked. The console does not send a call it can already tell is incomplete.
- Empty optional fields are omitted from `arguments` entirely rather than sent as `""` or `null` —
  every tool schema is `additionalProperties: false` with typed properties, and an empty string is
  not the same statement as an absent field.
- `cursor` is populated by the console from the previous page, not typed (FR-015).

---

## Exchange

One call and its answer. The console's central record, and what FR-010 and FR-011 are about.

| Field | Type | Notes |
|---|---|---|
| `id` | `string` | Monotonic within the tab |
| `principalName` | `string` | Which principal it was made as (FR-006) |
| `targetId` | `Target['id']` | Which target it was addressed to (FR-016) |
| `method` | `string` | `tools/call`, `tasks/get`, … |
| `toolName` | `string \| null` | |
| `requestHeaders` | `Record<string, string>` | Exactly as sent, `Authorization` redacted (R-009) |
| `requestBody` | `unknown` | Exactly as sent |
| `httpStatus` | `number` | |
| `responseHeaders` | `Record<string, string>` | Exactly as received |
| `responseBody` | `unknown` | Exactly as received |
| `durationMs` | `number` | Measured client-side |
| `outcome` | see below | |
| `deliberate` | `boolean` | True for the malformed catalogue (FR-011a), so a refusal is not read as a fault |

**`outcome` is the classification FR-011 requires**, and it has exactly four values:

| Value | Recognised by | Displayed as |
|---|---|---|
| `ok` | HTTP 2xx, JSON-RPC `result`, `isError` absent or false | the structured result |
| `tool-error` | HTTP 200, JSON-RPC `result`, `isError: true` | the server's sentence, marked as a *tool* failure |
| `protocol-error` | JSON-RPC `error` present | the message **and its code**, marked as a *protocol* failure |
| `transport-error` | HTTP 401, or the request never arrived | the status, and for 401 the likely cause |

The distinction between the middle two is the point of the screen: a tool failure arrives as a
successful response carrying a flag (US2-3 asks for exactly this to be visible), and a protocol
failure arrives as a JSON-RPC error with one of the codes in 007's error table. The console derives
the classification from the response, not from the HTTP status alone — `-32602` for an unknown tool
name arrives with HTTP 200.

**Rules**

- Exchanges accumulate in a list for the tab's life and are not persisted, edited, or replayed —
  the spec excludes all three.
- Every exchange records its principal and target, so a result cannot be read without knowing whose
  it was.

---

## Elicitation

The confirmation `post_fee_adjustment` asks for on its first call.

| Field | Type | Source |
|---|---|---|
| `key` | `string` | the `inputRequests` key, e.g. `confirm_adjustment` |
| `message` | `string` | server, shown **verbatim** (FR-012) |
| `requestedSchema` | JSON Schema object | server; rendered by the same renderer as tool arguments |
| `requestState` | `string` (opaque) | server; echoed back untouched, never displayed as meaningful |
| `answered` | `boolean \| null` | what the person chose |

**Rules**

- While an elicitation is outstanding the console states that nothing has been applied. It does not
  render a result panel (FR-012).
- The retry echoes `requestState` and carries a **different** JSON-RPC id, per 007's contract.
- `confirmed: false` produces a tool result saying the change was not applied — not an error — and
  the console shows it that way (US3-4).
- The console never constructs, decodes, or edits `requestState`.

---

## FeeChange

What the console shows after an applied adjustment, so the outcome is readable whatever the account
started from (FR-012a).

| Field | Type | Source |
|---|---|---|
| `accountId` | `string` | result |
| `deltaBps` | `number` | result |
| `newFeeBps` | `number` | result |
| `previousFeeBps` | `number` | **computed**: `newFeeBps - deltaBps` (R-007) |
| `legacyReferenceId` | `string` | result — the identifier the billing system assigned (US3-2) |
| `confirmedByUserId` | `string` | result |
| `replayed` | `boolean` | result — true on the third call, and shown as "nothing happened a second time" (US3-3) |

**Rule**: no undo is offered. The panel names `make mcp-reset` instead, with the spec's reason in one
sentence: an undo would be a second, opposite change, and the audit log would then describe two
events where one happened (FR-012b).

---

## Handle

A reference to work that outlives the call that started it.

| Field | Type | Source |
|---|---|---|
| `taskId` | `string` | `resultType: "task"` |
| `status` | `'working' \| 'input_required' \| 'completed' \| 'failed' \| 'cancelled'` | first result, then each `tasks/get` |
| `statusMessage` | `string` | e.g. `"DATA_COLLECTION, 0/240 accounts"` |
| `pollIntervalMs` | `number` | server — the console polls at this rate and no other (R-008) |
| `ttlMs` | `number` | server |
| `createdAt` / `lastUpdatedAt` | `string` | server |
| `result` | `unknown \| null` | present on `completed` |
| `error` | `unknown \| null` | present on `failed` |

**State**: `working → completed | failed | cancelled`, with `input_required` reachable in the
protocol though no current tool moves a task there.

**Rules**

- FR-013: the handle is shown the moment the first result returns, before any poll.
- Polling stops at any terminal state, and each poll is an `Exchange` like any other, so the
  cross-replica scenario (US4-3) is visible in the log rather than hidden inside a hook.
- FR-014: cancel is offered while non-terminal. Cancelling an already-terminal task is acknowledged
  and not an error (007 FR-031), and the console shows it that way rather than as a failure.

---

## MalformedRequest

One of the three prepared, deliberately wrong requests (FR-011a). The full catalogue, with what each
sends and what each must produce, is in [contracts/malformed-requests.md](./contracts/malformed-requests.md).

| Field | Type | Notes |
|---|---|---|
| `id` | `string` | |
| `label` | `string` | plain language, e.g. "Header disagreeing with the body" |
| `explanation` | `string` | what is wrong and why the server should refuse |
| `expectedCode` | `number` | `-32020`, `-32022`, `-32020` |
| `build` | function | returns the headers and body to send |

**Rules**

- Every resulting `Exchange` has `deliberate: true` and is marked in the log, so a refusal is never
  mistaken for a fault (spec edge case).
- The set is fixed in code. There is no free-form editor for headers or body — that is `curl`, and
  the spec says so.
- The unsupported-version case shows `data.supported` from the server's own error, which is what the
  edge case "must show the versions the server does support" asks for.

---

## Relationships

```text
Principal ──mints──> Token ──authorises──> Exchange <──addressed to── Target
                                              │
                     ┌────────────────────────┼────────────────────────┐
                     │                        │                        │
                 Tool.inputSchema ──derives──> ToolArguments      MalformedRequest
                                              │
                     ┌────────────────────────┼────────────────────────┐
                     │                        │                        │
               Elicitation ──confirms──> FeeChange                  Handle ──polled by──> Exchange
```

Every arrow into `Exchange` is the point: whatever happened, there is one record of it carrying the
principal, the target, the bytes, and the classification.
