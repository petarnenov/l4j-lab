# Findings about feature 007, recorded rather than acted on

**Feature**: `008-mcp-console`

The MCP console's spec puts feature 007's server out of scope: *"If the console needs something the
server does not offer, that is a finding to record, not a licence to change the server inside this
feature."* These are those findings. None of them is fixed here.

---

## F-001: `tools/list` does not serve the committed input schemas verbatim

**Severity**: material. It weakens FR-008 against the real server, and it contradicts a claim feature
007 makes about itself.

**What was expected.** `007/contracts/README.md` states: *"The five files under `tools/` are loaded
verbatim at runtime: `tools/list` serves them and the tool handlers validate arguments against
`inputSchema`."*

**What is served.** The schemas round-trip through the MCP Java SDK's `Tool` record, and **eighteen
declared keywords do not survive**, measured against the running stack:

| Tool | Property | Dropped |
|---|---|---|
| `search_billing_runs` | `status` | `enum: [PENDING, RUNNING, COMPLETED, FAILED, CANCELED]` |
| `search_billing_runs` | `started_from`, `started_to` | `format: "date"` |
| `search_billing_runs` | `page_size` | `default: 20`, `minimum: 1`, `maximum: 20` |
| `get_run_failures` | `limit` | `default: 50`, `minimum: 1`, `maximum: 50` |
| `post_fee_adjustment` | `operation_id` | `minLength: 8`, `maxLength: 128` |
| `post_fee_adjustment` | `effective_date` | `format: "date"` |
| `post_fee_adjustment` | `reason` | `maxLength: 200` |
| all five | *(schema root)* | `additionalProperties: false` |

A nineteenth difference is a change rather than a loss: every `type: "integer"` arrives as
`type: "number"`. The console treats both as an integer field, so nothing renders wrongly, but a
model reading the schema is no longer told the value must be whole.

Property order also changes, and `annotations` gains two fields the SDK's `ToolAnnotations` record
supplies of its own: `title: ""` and `returnDirect: false`.

`outputSchema` fares worse. It is served as another document altogether — gaining `$id` and
`$schema` and a schema-level `description`, losing every per-property `description` and
`additionalProperties` — and, more substantially, **nineteen fields the contracts declare mandatory
arrive optional**, across all five tools:

| Tool | Declared required, served optional |
|---|---|
| `search_billing_runs` | `runs` |
| `get_billing_run_status` | `run_id`, `status`, `phase`, `failure_reason` |
| `get_run_failures` | `run_id`, `failures` |
| `post_fee_adjustment` | `operation_id`, `account_id`, `effective_date`, `legacy_reference_id`, `confirmed_by_user_id` |

(`start_billing_run` is excluded from that table; its output schema differs in kind, not in degree —
see F-004.)

The field *names* survive, which is what the console depends on — it renders `structuredContent` and
does not validate it. But a client that did validate would accept a `search_billing_runs` result
with no `runs` at all.

Every input property's `description` does survive, which matters: the console shows it verbatim as
help text under each field.

**And two descriptions have already drifted.** These are not serialisation artifacts — they are
differently *worded*, which means the server is not reading these files at runtime at all. It holds
its own copies, and they have diverged:

| Property | Committed contract | Served |
|---|---|---|
| `post_fee_adjustment.operation_id` | "…Reuse **the same value** for retries of the same change; use **a new value** for a different change." | "…Reuse **it** for retries of the same change; use **a new one** for a different change." |
| `post_fee_adjustment.effective_date` | "Date the adjustment takes effect." | "Date the adjustment takes effect, **as YYYY-MM-DD**." |

The served wording is arguably better in both cases, which is the point: somebody improved the copy
they could see, and the committed contract — the artifact Principle III makes the single source —
was never told. This is precisely the failure that rule exists to prevent, and nothing in feature
007's own suite could notice it, because that suite compares handler behaviour against the committed
files and never reads what `tools/list` puts on the wire.

**Why it matters here.** FR-008 requires the console to build its argument fields from the tool's
declared input shape, so that a tool which changes is reflected without the console changing. It
does — but against the real server it can only build what the real server declares. A person using
the console against the running stack gets a free-text box for `status` rather than a chooser, a
plain text field rather than a date field for `effective_date`, and no prefilled `page_size`.

**Why it matters beyond here.** The same impoverished schema is what a *model* sees. The enum is the
part that most helps a model pick a valid status, and the bounds are what stop it asking for a page
of 500. This costs feature 007 something real, and it is invisible from inside that feature: its own
suite compares handler behaviour against the committed files, and never asks what `tools/list`
actually put on the wire.

**How it was found.** The console's live suite compared the served schema against the committed
contracts. Its deterministic suite could not have found it — that suite serves the committed files
itself (research R-004), so both sides of the comparison would have been the same object. This is
the failure mode feature 007's own R-017 records, arriving a second time from the other direction,
and it is the clearest possible argument for SC-008's two suites.

**What a fix would look like** (not done here): serve the parsed JSON of each contract file directly
rather than re-serialising an SDK record, or give the SDK's `Tool` a raw-schema passthrough. The
console needs no change either way: it renders whatever is declared.

---

## F-002: `post_fee_adjustment` reports no previous fee

**Severity**: minor.

Its `outputSchema` carries `new_fee_bps` and `delta_bps` and nothing else about the fee. FR-012a
requires the console to show the value before and after, because the underlying data keeps every
change and will differ between uses, so the console computes the before value as
`new_fee_bps - delta_bps`.

That subtraction is exact and correct on the replay path too, so nothing is wrong. But every client
wanting to report the change has to reconstruct it, and the elicitation message already contains the
before value in prose — meaning the server knows it and chooses not to return it. A
`previous_fee_bps` field would settle it for all callers.

---

## F-003: nothing identifies which replica answered

**Severity**: minor, but it bounds what any client can show.

`_meta["io.modelcontextprotocol/serverInfo"]` carries `{name, version}` and no instance identity,
and `deploy/mcp/nginx.conf` adds no upstream header to the response. Through the proxy, the
information genuinely does not exist, so the console says a replica answered and that the proxy does
not report which (research R-011) rather than inventing it.

The cross-replica property therefore can only be demonstrated by addressing replicas by name, which
is why `compose.mcp.topology.yaml` exists. An `X-Mcp-Instance` response header from nginx, or an
instance field in `serverInfo`, would let any client show it without the overlay.


---

## F-004: `start_billing_run` declares a different output shape than its contract documents

**Severity**: material for any client that validates.

The committed `start_billing_run.json` declares an `outputSchema` describing a **completed** run:
`run_id`, `status`, `phase`, `accounts_processed`, `accounts_total`, `failure_reason`.

The server declares, on the wire, a schema describing the **immediate handle**: `run_id`, `task_id`,
`poll_with`, `next_step_hint`.

Both shapes exist in feature 007 and both are correct in their place — the fallback client gets the
handle straight away, and `tasks/get` on `completed` carries "the `start_billing_run` `outputSchema`
payload", as `contracts/mcp-protocol.md` puts it. The problem is that a tool has one `outputSchema`,
and these are two shapes. A client validating the immediate result against the committed contract
would reject a correct result; one validating the completion payload against the served schema would
do the same.

It is also the place where the protocol contract and the tool contract disagree with each other,
which is worth more than either disagreeing with the code.

**Not a problem for this console**, which renders `structuredContent` rather than validating it —
and which is why it took a client that compares the two to notice.

---

## F-005: the confirmation retry's shape is under-specified, and a contract-following client is silently understood as "no"

**Severity**: high. A client built from the contract alone sends a confirmation the server reads as a
refusal, and nothing tells it so.

**What the contract says.** `007/contracts/mcp-protocol.md`: *"The retry carries
`params.inputResponses.confirm_adjustment` and echoes `params.requestState`, with a different
JSON-RPC `id`."* The elicitation it answers declares
`requestedSchema: { properties: { confirmed: { type: "boolean" } }, required: ["confirmed"] }`.

Read together, those say: send `{ "confirm_adjustment": { "confirmed": true } }`.

**What the server requires.** `FeeAdjustmentTool.confirmed()` reads
`inputResponses.confirm_adjustment.content.confirmed` — the MCP `ElicitResult` envelope,
`{ action, content }`, with the requested schema's fields inside `content`:

```json
{ "confirm_adjustment": { "action": "accept", "content": { "confirmed": true } } }
```

The server is right and the contract is incomplete: `ElicitResult` is the protocol's own shape. But
the method comment is *"True only for an explicit `confirmed: true`; anything else is a refusal"*,
and a refusal is exactly what the contract-shaped payload produces — **a successful-looking response
saying the change was not applied, for a client that asked for it to be applied.** There is no
diagnostic. This console was built from the contract and hit it on the first live run.

**A second divergence, in the same area.** The contract says *"A `confirmed: false` response is
answered with a tool result saying the change was not applied — **not an error**."* The server
returns that result with `isError: true`. The console therefore shows a declined change as "nothing
was applied" while its exchange log classifies it, correctly, as the tool failure the server said it
was — and names this divergence rather than tidying either half away.

**What a fix would look like** (not done here): state the `ElicitResult` envelope in
`mcp-protocol.md`'s retry example, and drop `isError` from the declined result — or amend the
contract to say the decline *is* an error, if that is the intent. Either resolves it; the current
pair cannot both be right.

**How it was found.** The live suite, on its first run. A stub would have accepted whatever shape
this console sent, because this console would have written the stub.

---

## F-006: a cross-advisor search returns HTTP 500 and an internal error message

**Severity**: the highest here. It is a defect, not a documentation gap, and it violates three
things feature 007 states about itself.

**Reproduction**, against a stack started with `make mcp-up`:

```
principal advisor-alpha-101, search_billing_runs { firm_id: firm-alpha, advisor_id: adv-102 }
→ HTTP 500
→ {"code": -32603, "message": "message must not be empty"}
```

`adv-102` is a different advisor **inside the caller's own firm**. An unknown advisor id
(`adv-does-not-exist`) produces exactly the same answer, so this is the code path and not the
entitlement decision.

**What it should be.** The cross-*firm* refusal is handled correctly and is what the contract
describes:

| Request | Answer |
|---|---|
| `firm_id: firm-beta` (another firm) | HTTP 200, `isError: true` — correct |
| `advisor_id: adv-102` (another advisor, same firm) | **HTTP 500, `-32603`** |
| `advisor_id: adv-101` (own advisor) | HTTP 200, results — correct |

**Three things it contradicts**, all of them feature 007's own:

1. `contracts/mcp-protocol.md`'s error table maps a legacy 403 to *"200, tool: `isError`, no access"*.
   This is 500.
2. `-32603` appears nowhere in that table. Every code it lists is `-32020`, `-32021`, `-32022`,
   `-32602`, `-32601`, `-32700`.
3. SC-006 requires that no internal detail reach a caller. *"message must not be empty"* is a
   validation complaint from constructing the result, not a sentence written for anyone to read.

**Likely cause**, from the shape of the message: the advisor-scope refusal builds a tool error whose
text is empty, and the SDK's `CallToolResult`/`TextContent` rejects an empty string — so the refusal
throws on its way out and is caught by the generic handler. The fix is one sentence of refusal text
on that path, plus the same guard the cross-firm path already has.

**Why 007's own suite does not catch it.** Its acceptance scenarios cover the cross-firm case
(US1-3, US1-4) and the *narrower view* an advisor gets when it searches without a filter (US1-1,
US1-2). Nothing asks an advisor to filter by an advisor it may not act for — the one combination
that reaches this path.

**How it was found.** The console's live suite, while trying to assert entitlement in a way that
would not break as the seeded data drifted. Pinned by two tests asserting the behaviour *as it is*,
so that correcting the server fails them and the finding gets closed rather than forgotten.

**In the console**, this renders as what it is: a protocol failure with code `-32603` at HTTP 500,
next to the cross-firm refusal rendered as a tool failure at HTTP 200. Putting the two on the same
screen is precisely what this console was built to make possible.
