# Research: Close the findings against the MCP billing server

**Feature**: `010-close-mcp-findings` | **Phase**: 0

Phase 0 read the code instead of the findings, and three of the seven turned out to be recorded in the
wrong direction. Each entry below records what was found, what was decided, and what was rejected.

---

## R-001: F-006 is not about entitlement. Every empty search result crashes.

**What the finding said**: a cross-advisor search inside the caller's own firm returns HTTP 500 with
`-32603 "message must not be empty"`.

**What is actually happening**, traced from the running stack:

```
advisor-alpha-101 searches firm-alpha, advisor_id=adv-102
  legacy API  →  HTTP 200  {"totalCount":0}          ← `items` omitted, not empty
  mcp-server  →  LegacyRunPage(items = null, totalCount = 0)
                 page.items().stream()               ← NullPointerException
  boundary    →  JSON-RPC -32603, message ""         ← rejected by the SDK as empty
  caller      →  HTTP 500 "message must not be empty"
```

**The entitlement was a red herring.** `adv-102` produces an empty page for that caller, and an empty
page is what crashes. Confirmed by a search that has nothing to do with entitlement:

| Search, as `admin-alpha` | Result |
|---|---|
| `firm_id: firm-alpha` | 200 |
| `firm_id: firm-alpha, advisor_id: adv-102` | 200 — that admin may act for adv-102, so the page is not empty |
| `firm_id: firm-alpha, started_from: 2030-01-01` | **500** |

A date range with nothing in it crashes the tool. That is an ordinary query, reachable by any model
exploring the data, and far more likely to be hit than the case the finding names.

**Decision**: three defects, fixed at all three levels rather than patched at the cheapest one.

1. **The legacy API omits an empty collection.** Its own response shape declares `items`; it emits
   `{"totalCount":0}`. An empty array is a fact and must be serialised.
2. **The MCP server trusts it.** `page.items()` is dereferenced without a guard. A client of a system
   of record it does not control should not assume a nullable field is present — 007's own
   `openWorldHint: true` says as much about every one of these tools.
3. **The error boundary loses the message.** An unexpected exception becomes a JSON-RPC error with an
   empty message and code `-32603`, which appears nowhere in 007's error table.

**Why all three and not just the first.** Fixing the serialiser alone leaves the server one null away
from the same crash from any other source. Fixing the guard alone leaves the legacy API violating its
own shape. Fixing the boundary alone leaves the crash and makes it quieter, which is worse. The third
is the one that turns any future unexpected exception from a leak-shaped failure into a refusal a
caller can read — and SC-006 of feature 007 asks for exactly that.

**Alternative considered**: treating the null as an empty page in the MCP server only, as a one-line
defensive fix. Rejected as the cheapest possible reading of a finding that has already been
under-read once.

---

## R-002: F-001 is a Principle III violation, not a serialiser losing keywords

**What the finding said**: `tools/list` does not serve the committed tool declarations verbatim,
though `007/contracts/README.md` says *"The five files under `tools/` are loaded verbatim at runtime:
`tools/list` serves them."*

**What is actually true**: those files are **never read at runtime**. The declarations are generated
from Java annotations —

```java
@Tool(name = "get_billing_run_status", title = "…", description = "…",
      annotations = @Tool.ToolAnnotations(readOnlyHint = true, …))
public BillingRunStatus getBillingRunStatus(
    @ToolArg(name = "run_id", description = "The run to look up.") String runId, …)
```

— and the JSON files are copied into *test* resources by `build.gradle.kts`, whose own comment says so:
*"the committed contracts are the oracle, not a runtime resource."*

So the eighteen missing keywords are not lost in transit. **They were never there.** `String runId`
carries no enumeration, no format and no bounds, because a Java `String` has none; `int` becomes
`number` because that is what the generator emits. The two descriptions that differ in wording differ
because they are two hand-written texts.

**This is the constitution's single-source rule, broken**: *"Contract definitions MUST be shared
between backend and frontend from a single source; the same shape MUST NOT be declared twice by
hand."* They are declared twice by hand today.

**Decision**: the Java declaration becomes the single source, and the committed JSON becomes
**generated from it**. The annotations gain the constraints they are missing — enumerations, formats,
bounds, defaults — so that what ships carries them.

**Rationale**: the generator is library-first, which Principle I requires, and it is what actually
serves the traffic. Making the JSON authoritative would mean hand-feeding raw schemas past the SDK's
generator and keeping two mechanisms alive. Making the Java authoritative means one declaration, in
the place the SDK already reads, with the committed file derived — the same arrangement the frontend
already uses for its API types, which the constitution names approvingly.

**Alternatives considered**:
- *Load the JSON at runtime and bypass the generator.* Rejected: it discards the library's mechanism
  and would leave the annotations still present and still second-guessable.
- *Leave both and add a test that they match.* Rejected as the arrangement that produced this
  finding — two hand-maintained copies plus a promise to compare them is what feature 007 believed it
  had (R-003).

**FR-013's question, answered**: restoring `enum` and bounds to the declarations exposes whether the
server validates them. Where it does not, **the validation follows** rather than the declaration being
weakened. A declared enumeration the server ignores is a new lie in place of an old one, and the tool
arguments are few enough that validating them is a smaller change than explaining why they are not
validated.

---

### R-002 addendum, written during implementation: where the constraints ended up

R-002 said *"the annotations gain the constraints they are missing"*. They cannot. In
micronaut-mcp 2.0.0, `@ToolArg` declares exactly two members:

```java
public interface ToolArg extends Annotation {
    String name();
    String description();
}
```

There is nowhere to put an enumeration, a format, a bound or a default.

**Java types were tried first, because that is the library-first answer** and it is what R-002's own
reasoning points at: a `String` carries no enumeration *because a Java `String` has none*, so the
repair looks like it should be `RunStatus status` and `LocalDate startedFrom`. It was measured, and
the input-schema generator in this version is too crude for it:

| declared as | served as |
|---|---|
| `RunStatus` (a Java enum) | `"type": "string"` — no `enum` |
| `LocalDate` | `"type": "object"` — **worse than the `String` it replaced** |
| `Integer` | `"type": "number"` |

So the types were reverted and the keywords live in `ToolArgumentConstraints`: one Java class, read
in exactly two places — `JsonRpcResponseSerializer` merges it into what `tools/list` serves, and
`McpRequestGate` enforces it on `tools/call`. R-002's **decision** stands unchanged — the Java is the
source and the committed JSON is generated from it — and no dependency was added to get there. Only
the mechanism differs from what R-002 predicted, and it is recorded here rather than left as a
surprise for whoever reads the annotation and wonders why it is bare.

**Nothing was left unenforced** (T031). Every keyword that is declared is checked: enumeration
membership, `YYYY-MM-DD` dates, integrality, numeric bounds, and string lengths. One is worth naming
because it changes behaviour: `page_size` above 20 used to be **clamped silently**, and is now
refused. Clamping is a reasonable thing to do with a value you have chosen to accept — but the
declaration says `maximum: 20`, and accepting 100 anyway is the declaration being untrue again, one
level down.

**What the generated contracts cannot capture, and why it does not matter**: `outputSchema` carries
`$id` and `$schema` from micronaut-json-schema, which the hand-written contracts never had. They are
now in the committed files because the files are generated from what ships. A contract that omits
what the server actually sends is the problem this feature exists to close.

## R-003: the guard that was supposed to prevent F-001 was never written

`mcp-server/build.gradle.kts`:

> *"T042, revised by R-014: the committed contracts are the oracle, not a runtime resource. `@Tool`
> generates the schemas from the Java types; a contract test asserts the generated schema matches the
> committed JSON, so Principle III's ordering holds and drift fails the build."*

**There is no such test.** `ProtocolSurfaceTest` checks tool *names*, their order, and the envelope. The
copied contracts sit in `build/resources/test/contracts/` and nothing reads them.

**This is the finding that matters most**, and it was not among the seven. It is why F-001, F-004 and
the drifted descriptions could stand: the mechanism that would have failed the build on the day they
appeared was documented, believed, and absent.

**Decision**: write it. `ToolDeclarationContractTest` fetches what the server declares and compares it
to the committed files, field by field, and fails on any difference. It is the first thing this
feature builds, because everything else in R-002 is only durable if it exists.

**A note on how this was missed.** The build file's comment is written in the present tense and reads
like a description of something that is there. Nothing in the repository contradicted it until a
client compared the two artefacts from outside. That is the same shape as feature 009's G-005 and
G-007, and it is worth naming a third time: **a guard nobody exercises and a guard that does not exist
look identical from inside.**

---

## R-004: which side of the confirmation exchange moves (F-005)

The specification left this open on purpose; here is the answer.

**The server is right and the contract is incomplete.** `FeeAdjustmentTool.confirmed()` reads
`inputResponses.confirm_adjustment.content.confirmed` — the MCP `ElicitResult` envelope, `{action,
content}`, which is the protocol's own shape. The contract says only *"the retry carries
`params.inputResponses.confirm_adjustment`"* and stops, and the elicitation's `requestedSchema` is
`{confirmed: boolean}`, so a reader assembles the flat form and is understood to have declined.

**Decision**: correct the contract, not the server, and separately fix what the server does with an
answer it cannot read.

- The contract gains the envelope, in full, with an example.
- `confirmed()` currently returns `false` for anything it cannot interpret, and its comment says so:
  *"True only for an explicit `confirmed: true`; anything else is a refusal."* That is the behaviour
  that turns a misunderstanding into a silent decline. An answer that is **absent** is a refusal; an
  answer that is **present and unreadable** is an error, and must be reported as one (FR-007).

**Its sibling, and why it goes the other way.** The contract says a declined confirmation is answered
*"with a tool result saying the change was not applied — not an error"*, and the server returns it with
`isError: true`. Here the **contract is right**: a decline is the system working. The server changes.

**On breaking clients** (FR-009): no client sends the flat form today, because no client that sent it
ever worked. The only client in this repository is feature 008's console, which already sends the
envelope — it learned the hard way. So correcting the contract breaks nothing and the protocol's
versioning rules are not engaged. Making the *decline* stop being an error is a visible change, and
the console's live suite pins today's behaviour; that test changes with it.

---

## R-005: F-004, and what `start_billing_run` should declare

The committed contract's `outputSchema` describes a **completed run** — `status`, `phase`,
`accounts_processed`. The server declares the **immediate handle** — `run_id`, `task_id`, `poll_with`.

Both shapes are real: the handle is what the call returns, and the completion payload is what
`tasks/get` carries on `completed`.

**Decision**: the tool's `outputSchema` describes **what calling the tool returns**, which is the
handle. The completion payload is a property of the tasks extension, and belongs in the protocol
contract where that extension is described, not in the tool declaration.

**Rationale**: a tool's output schema is a promise about that tool's result. A client validating the
call's result against a schema describing something it will only receive later has been given the
wrong instrument. With R-002 in place the committed file is generated from the Java declaration, so
this resolves itself — and the fact that it does is a small proof that R-002's direction is the right
one.

---

## R-006: F-002 and F-003, the two that only cost clients work

**F-002, the previous fee.** `post_fee_adjustment` returns `new_fee_bps` and `delta_bps` and no
previous value, so every client computes `new − delta`. Feature 008's console does exactly that.

**Decision**: add `previous_fee_bps` to the result. It is known at the point the adjustment is
applied, the subtraction is a small tax on every client forever, and the elicitation message already
quotes both values in prose — so the server has it and chooses not to return it.

**F-003, which replica answered.** Nothing in `serverInfo` or the response headers identifies an
instance, so a caller through the proxy cannot say which replica produced an answer. Feature 008's
console says so plainly rather than inventing it.

**Decision**: add an instance identifier to `_meta.serverInfo`. **Not** an nginx header: the proxy is
one deployment shape among several, and a property of the *server* should be reported by the server.
The value comes from configuration with a documented default, as every other setting in this
repository does.

**What is deliberately not done**: correlating it with anything. This is an identifier to display, not
a tracing mechanism — `traceparent` already exists for that and is already propagated.

---

## R-007: G-006, and the limit on correcting a reading guide

The quickstart's table names twelve files under headings like *"Header-based routing and validation"*.
Most were never written: `McpController.java`, `HeaderValidationFilter.java`, `RequestEnvelope.java`.
The delivered package holds `McpMethodHandler`, `HttpMethodGate`, `BillingTransportContextExtractor`.
`tasks.md` T039 says outright *"Superseded by R-014. No controller is written."*

**Decision**: correct the table by reading the delivered code, one row at a time, and where no single
file owns a row, say so instead of choosing one.

**The limit, stated** (FR-016): this is the one item in the feature that cannot be verified
mechanically. Feature 009's check will confirm every named file *exists*; that the file named against
"Header-based routing" is where header routing lives is a claim only a reader can make. The
corresponding rows are therefore reviewed by reading, and the review is the deliverable — which is
also why this story is P2 rather than P1 despite being the most visible to a newcomer.

---

## R-008: LangChain4j capability inventory (Constitution Principle I)

**Decision**: **this feature touches no LangChain4j module**, so the inventory covers an empty set, and
this entry is the record the amendment requires rather than a skipped gate.

**Evidence**: the change set is `mcp-server/` (MCP Java SDK and Micronaut), `legacy-billing-api/`
(Micronaut only), documents under `specs/`, and one line of a JSON baseline. `mcp-server` has never
depended on LangChain4j — feature 007 built it on the MCP Java SDK deliberately, and its research
records why. Nothing under `backend/`, where LangChain4j lives, is edited.

**The condition that re-opens this gate**: if closing F-001 leads to declaring these tools *to* a
LangChain4j agent — which is a natural next step and explicitly not this feature — the inventory
applies to that work.
