# Data Model: Close the findings against the MCP billing server

**Feature**: `010-close-mcp-findings` | **Phase**: 1

Only the shapes this feature changes. Everything else in feature 007 stays as it is, and is not
restated here.

---

## Legacy run page

What the system of record returns for a search, and the shape at the centre of F-006.

| Field | Type | Today | After |
|---|---|---|---|
| `items` | array of run | **omitted when empty** | always present, empty array when there is nothing |
| `totalCount` | integer | present | unchanged |

**Rules**

- An empty collection is a fact and is serialised as one. Omitting it makes "no results" and
  "malformed response" the same bytes, and the reader has to guess.
- The MCP server treats a missing `items` as an empty page regardless. The system of record is
  outside its control — `openWorldHint: true` on every one of these tools says so — and a client of
  such a system does not dereference a field it did not put there.
- Both halves change. Either alone leaves the other wrong.

---

## Tool declaration

The shape of a tool as the server declares it, and as this repository commits it. The point of F-001
is that these are two things today.

| Aspect | Today | After |
|---|---|---|
| Source of truth | Java annotations **and** committed JSON, both hand-written | Java annotations alone |
| Committed JSON | hand-written, copied to test resources, read by nothing | **generated** from the declaration |
| `enum`, `format`, `minimum`, `maximum`, `default` | absent from what is served | declared, and served |
| `integer` | arrives as `number` | arrives as declared |
| Required output fields | relaxed on the wire | as declared |
| Guard | a build comment describing a test that does not exist | a test that reads both and fails on any difference |

**Rules**

- One declaration. A change to it reaches what is served and what is committed without a second edit
  (FR-011).
- A declared constraint is a validated constraint. Where restoring one exposes an argument the server
  does not check, the check follows — a declaration the server ignores is a new lie in place of an
  old one (FR-013, research R-002).
- The comparison is field by field, not a shape test. A difference in any keyword fails.

---

## Confirmation answer

What the caller sends back, and what the server makes of it (F-005).

| | Today | After |
|---|---|---|
| Shape the server accepts | `inputResponses.<key>.content.confirmed` | unchanged — this is the protocol's own `ElicitResult` |
| Shape the contract describes | unspecified beyond the key | the envelope, in full, with an example |
| An answer that is absent | refusal | unchanged — a refusal is a refusal |
| An answer present but unreadable | **silently a refusal** | an error saying the answer could not be read |
| A decline | `isError: true` | a result reporting nothing was applied |

**Rules**

- Absent and unreadable are different. One is a decision; the other is a failure to communicate, and
  treating them alike is what turned a contract-following client into a declining one.
- The decline is the system working. Feature 007's own contract says so and its server disagrees;
  here the contract wins.

---

## Fee adjustment result

| Field | Today | After |
|---|---|---|
| `new_fee_bps` | present | unchanged |
| `delta_bps` | present | unchanged |
| `previous_fee_bps` | **absent** | present |

**Rule**: known at the point the change is applied, and quoted in the elicitation message in prose, so
the server has it. Every client currently reconstructs it by subtraction.

---

## Server identity

| Field | Today | After |
|---|---|---|
| `_meta.serverInfo.name` | `mcp-billing-server` | unchanged |
| `_meta.serverInfo.version` | `0.1.0` | unchanged |
| `_meta.serverInfo.instance` | **absent** | the replica that answered |

**Rules**

- Reported by the server, not added by the proxy: the proxy is one deployment shape among several, and
  an instance is a property of the server.
- Configuration with a documented default, as every other setting in this repository is.
- For display, not correlation. `traceparent` already exists for that and is already propagated.

---

## Finding

The record each of the seven leaves behind.

| Field | Values |
|---|---|
| `id` | F-001…F-006, G-006 |
| `disposition` | `closed` — with what was done · `open` — with why |
| `baselineEntry` | removed when closed; an entry that no longer matches real drift fails the check |

**Rule**: none of the seven ends this feature undiscussed (SC-007). A finding left open is a decision
and is recorded as one.
