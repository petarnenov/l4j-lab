# Research: MCP console

**Feature**: `008-mcp-console` | **Phase**: 0

Each entry is a decision, why it was taken, and what was rejected. Nothing here restates
feature 007's contracts; where a shape matters, it is cited.

---

## R-001: How the browser reaches the MCP system

**Decision**: the browser never calls the MCP stack. The Vite dev server forwards, under one path
prefix, to four targets:

| Console path | Forwards to | Default | Environment variable |
|---|---|---|---|
| `/mcp-dev/proxy/*` | the nginx proxy | `http://localhost:8877` | `MCP_HTTP_PORT` |
| `/mcp-dev/a/*` | `mcp-a` | `http://localhost:8881` | `MCP_REPLICA_A_PORT` |
| `/mcp-dev/b/*` | `mcp-b` | `http://localhost:8882` | `MCP_REPLICA_B_PORT` |
| `/mcp-dev/c/*` | `mcp-c` | `http://localhost:8883` | `MCP_REPLICA_C_PORT` |

The prefix is stripped; everything after it is passed through unchanged, so `/mcp-dev/a/mcp`
arrives at `mcp-a` as `POST /mcp`. The variable names and defaults are exactly the ones
`compose.mcp.yaml` and `compose.mcp.topology.yaml` already read, so a stack started with
`MCP_HTTP_PORT=9001 make mcp-up` needs the same variable exported for `npm run dev` and nothing else.

**Rationale**: it is the only arrangement that satisfies all four constraints at once. The request
stays same-origin, so no CORS header is needed and feature 007's server is untouched. The two
Compose networks never meet — the dev server is a process on the developer's machine talking to
published ports, which is what `curl` already does. And the forwarder exists only while `vite` is
running, which is most of how FR-001a is satisfied (R-002 is the rest).

**Alternatives considered**:

- *Route through the Micronaut backend on 8080.* Rejected: it means Java code in a feature that
  otherwise needs none, it couples the application stack to the MCP stack at runtime, and the
  endpoint would exist in the packaged build — exactly what FR-001a forbids. The console would then
  have to be gated in two places instead of one.
- *Add CORS to the MCP server.* Rejected by the spec: "Any change to feature 007's server" is out of
  scope, and permitting a browser origin is a change to its security posture, not a convenience.
- *A separate small proxy process.* Rejected: one more thing to start, and the dev server the
  developer is already running does it in eight lines of `vite.config.ts`.

**Consequence worth stating**: the token issuer is reachable **only** through the proxy target —
`deploy/mcp/nginx.conf` routes `/dev/` and `/.well-known/` to it, and an individual replica serves
neither. So the console mints every token through `/mcp-dev/proxy/dev/token` regardless of which
target the subsequent call is aimed at. That is also honest: minting is not part of the protocol
being demonstrated.

---

## R-002: Keeping the console out of the packaged build

**Decision**: two layers, and the second is the one that counts.

1. The nav item and the page are both behind `import.meta.env.DEV`. Vite replaces that identifier
   with the literal `false` during `vite build`, so Rollup eliminates the branch and, with it, the
   dynamic `import()` of the console module inside it.
2. A committed check, `npm run check:dev-only`, runs `vite build` into a temporary directory and
   fails if a marker string that appears only in console source is present anywhere in the output.
   It is wired into `:frontend:check`.

**Rationale**: the first layer is how it works; the second is why anyone should believe it. FR-001a
is the kind of requirement that silently stops being true — a refactor that moves an import above
the guard, a future Vite version that treats the branch differently — and the failure is invisible
because the development build still behaves correctly. A check that reads the actual build output
cannot be fooled by either.

It is a build check rather than a Vitest test on purpose: Vitest runs with `DEV === true`, so it is
structurally the wrong place to ask this question, and running a full production build inside the
unit suite would slow every run for one assertion.

**Alternatives considered**:

- *A Vite plugin resolving the console entry to an empty module in production.* Rejected as more
  machinery than the guard needs, and it would still want the same check to prove it worked.
- *Trusting the guard alone.* Rejected — see above. An untested absence is an assumption.
- *Shipping it and hiding the link.* Rejected by the clarification: "development-only" has two
  readings and only "absent from the packaged build" is testable. The spec chose that one.

**Second, independent reason it cannot work in production anyway**: the credential it depends on
comes from the token issuer, whose controllers are `@Requires(env = …)`-gated to development and
return `404` elsewhere (`007/contracts/token-issuer.md`). A console in the packaged build would draw
an empty screen. The guard makes that structural instead of incidental.

---

## R-003: Deriving argument fields from `inputSchema` (FR-008)

**Decision**: a small renderer in `schemaForm.tsx` over the JSON Schema subset the served tools
actually use, with antd form controls. No new dependency.

The supported subset, enumerated from the five committed tool contracts:

| Keyword | Rendered as |
|---|---|
| `type: "string"` | text input |
| `type: "string"` + `enum` | select |
| `type: "string"` + `format: "date"` | date picker, submitted as `YYYY-MM-DD` |
| `type: "integer"` | number input, honouring `minimum` / `maximum` |
| `type: "boolean"` | switch |
| `required: [...]` | the field is marked required, and FR-009 blocks the call while one is empty |
| `default` | prefills |
| `description` | the field's help text, verbatim from the server |
| `minLength` / `maxLength` | input constraint |

**Anything outside the subset renders as a raw-JSON text field carrying a visible "this tool
declares a shape the console does not render natively" note.** It is never dropped and never
silently ignored, because a silently missing field looks identical to a tool that does not have it.

**Rationale**: FR-008's purpose is that a tool which changes is reflected without the console
changing, and the subset above covers that for any plausible change to these five tools. A general
JSON Schema form library is roughly the size of the rest of this page put together, styles against
its own design system rather than antd, and the constitution requires dependency additions to be
justified against what is already available. Sixty lines of switch statement is the smaller claim.

**What makes this safe rather than optimistic**: the deterministic suite serves `tools/list` from
the committed contracts themselves (R-004). A future tool introducing `oneOf` or a nested object
therefore fails a test in this repository the day the contract file changes — the fallback exists
for the running system, not as an excuse for the suite.

**Alternatives considered**: `@rjsf/antd` (rejected: size, and it pulls its own validator);
hand-writing a form per tool (rejected outright — it is the precise thing FR-008 forbids).

---

## R-004: Where the deterministic suite's tool list comes from

**Decision**: the MSW handler for `tools/list` reads
`specs/007-mcp-billing-server/contracts/tools/*.json` — the same five files
`mcp-server/build.gradle.kts` copies in as its own test oracle — through a Vite alias. It does not
hold a copied fixture.

**Rationale**: Principle III says a shape must not be declared twice by hand, and a fixture that
drifts from the contract is worse than no fixture: it makes the suite pass while the console renders
the wrong thing. Reading the committed file means an annotation flipped in the contract changes what
the console test sees, in the same commit.

It also settles the credibility problem the spec raises in "Why both, and not just the first": the
deterministic suite is honest about what it proves — that the console renders what it is handed —
and by handing it the real declarations it at least cannot be wrong about *those*.

**Alternative considered**: recording a response from the running server into a fixture file.
Rejected: it is a second copy with an expiry date on it, and nothing tells you when it expired.

---

## R-005: The two test suites

**Decision**: mirror the split feature 007 already uses, by task and never by a flag.

| Suite | Command | Environment | What it proves |
|---|---|---|---|
| deterministic | `npm test`, `./gradlew :frontend:test`, `make test-frontend` | jsdom + MSW, no network, no credential | rendering, argument derivation, the branch on each result shape, error classification, cursor and poll state machines |
| live | `npm run test:mcp`, `./gradlew :frontend:mcpConsoleTest`, `make test-console` | node, real `fetch` against a running stack | the headers the server actually accepts, the result shapes it actually sends, the refusal codes, the cross-replica cursor |

The live suite is a separate Vitest project (`vitest.mcp.config.ts`) selecting `**/*.live.test.ts`,
which the default config excludes. It is **not** in `./gradlew check`, for the same reason
`topologyTest` is not: it needs containers it deliberately will not start.

Its fixture probes `GET /lb-health` on the proxy before anything else and, when that fails, throws
the message `TopologyFixture` throws — naming `make mcp-up` and `make mcp-verify`, and saying
plainly that these tests do not start containers behind your back. That is SC-008's second half, and
copying the wording is intentional: a developer who has seen one of these messages recognises the
other.

**Rationale**: SC-008 and Principle IV both require the second suite to be selectable alone and to
fail with an instruction rather than an unexplained error. R-017 of feature 007 is the evidence for
why the first suite alone is not enough, and the spec quotes it: substituting the only real exchange
on a path removes the only thing that can fail.

**What the seam is, and why it is not a test hook**: `targets.ts` resolves a target name to a base
URL. In the browser that is `/mcp-dev/{target}`; in the live suite it is
`http://localhost:${MCP_HTTP_PORT:-8877}`. This seam exists because targets are plural and their
reachability is discovered at runtime (R-006) — it would exist with no tests at all. FR-003a's "may
not require altering the console to suit it" is met because both callers supply the same kind of
value through the same interface.

---

## R-006: Discovering whether the individual replicas are reachable (FR-016, FR-017)

**Decision**: on mounting the console, probe each of the three replica targets with
`GET /health/readiness` — the endpoint their Compose health check already uses — and probe the proxy
with `GET /lb-health`. A replica that does not answer is shown disabled, with the reason and the
command that would publish it. The proxy answering while all three replicas do not is the normal,
expected state: `compose.mcp.yaml` publishes one port on purpose.

**Rationale**: FR-017 requires the console to explain the absence rather than fail, which means it
has to know the difference between "not published" and "broken". Probing is the only way: nothing in
the protocol advertises the topology, and nothing should.

**The command it names**: the overlay is currently started only inside `make mcp-verify`, which stops
it again. So this feature adds `make mcp-up-topology`, one line following the existing `mcp-up`
pattern, running `compose.mcp.yaml` plus `compose.mcp.topology.yaml`. Naming a raw
`docker compose -f … -f … up -d --wait` instead was rejected: the repository's own convention since
feature 006 is that a documented command is a make target, and this is the command a reader is most
likely to mistype. It changes no file belonging to feature 007's server.

---

## R-007: The confirmation round trip, and showing the fee before and after (FR-012, FR-012a)

**Decision**: the console drives all three calls and shows the elicitation's `message` **verbatim**
as the question. It computes the fee before the change as `new_fee_bps - delta_bps`, and shows that
against `new_fee_bps`.

**Rationale**: FR-012 says the question must be presented as the server phrased it, so it is not
paraphrased or restructured. FR-012a needs a before value, and the `post_fee_adjustment`
`outputSchema` does not carry one — it has `new_fee_bps` and `delta_bps` and nothing else. The
subtraction is exact, deterministic, and correct on the replay path too, because a replayed result
returns the original `new_fee_bps` alongside the original `delta_bps`.

**Alternative considered**: parsing the before value out of the elicitation message, which does
contain it (`"0.90% → 1.05%"`). Rejected: it is prose written for a human, the server is free to
reword it, and arithmetic that cannot fail is available.

**Finding recorded against feature 007** (not acted on — the spec forbids changing that server
here): `post_fee_adjustment`'s `outputSchema` has no `previous_fee_bps`. Any client wanting to report
the change must reconstruct it. Worth considering in a future revision of that contract.

**FR-012b** is a display decision with no mechanism: after an applied change the console names the
reset command and does not offer an undo. The reset is `make mcp-reset`, added alongside
`mcp-up-topology` and following the existing `make reset` pattern, including `scripts/make/confirm.sh`
— `down -v` deletes the seeded data and should ask first.

---

## R-008: The long-running operation (FR-013, FR-014)

**Decision**: the console declares `io.modelcontextprotocol/tasks` in its
`clientCapabilities`, so `start_billing_run` returns `resultType: "task"`. It shows `taskId` and
`status` from the first response, then polls `tasks/get` through TanStack Query's `refetchInterval`,
set to the **server's** `pollIntervalMs`, stopping when the status is terminal. Cancellation posts
`tasks/cancel` with the same `taskId`.

**Rationale**: FR-013 forbids making a person poll by hand, and TanStack Query already owns polling —
the stack rules forbid duplicating server state into a second store, and an interval hook writing
into `useState` would be exactly that. Taking the interval from the response rather than choosing one
here is the same instinct as FR-008: the server said what it wanted.

**Deliberately not built**: a toggle that withholds the tasks capability to demonstrate feature 007's
fallback path (`complete` with `task_id`, `run_id`, `poll_with`). It is a genuinely interesting
behaviour and it is covered by 007's own suite (US3-4); adding it here is a second polling code path
for a console whose brief is to stay light. Recorded so the omission is a choice and not an oversight.

---

## R-009: Principals, tokens, and the one place a requirement collides with a principle

**Decision**: `principals.ts` holds the six fixture **names** and nothing else. Everything the
console displays about a principal — user, firm, role, permitted advisors — is read from the claims
of the token the issuer mints (`sub`, `firm_id`, `role`, `advisor_ids`), decoded without
verification, for display only, with a comment saying exactly that.

Tokens are cached in memory per principal for the tab's life, re-minted on expiry, never written to
`localStorage`.

**Rationale**: it is the only way FR-004's four facts are guaranteed to be the ones the server will
actually act on. Copying the table out of `007/contracts/token-issuer.md` would put a second
declaration in the console, and the copy would be believed even after it went stale. The six names
have to be written down somewhere — the issuer offers no "list principals" endpoint — so that is the
minimum, and it is a list of strings rather than a table of claims.

### The collision, and how it is resolved

Principle II: *"A credential MUST NOT be committed, written into a log line, persisted into a run
record, or shown on screen."* FR-010: *"the console MUST show the request and the response exactly as
they travelled."* A request that travelled carried `Authorization: Bearer <jwt>`.

**Resolution**: the exchange view renders the `Authorization` header as
`Bearer «token for advisor-alpha-101, expires 14:32»`, and renders the token's decoded claims in the
principal panel where they are more useful anyway. Every other header and the entire body appear
byte-for-byte.

This keeps FR-010's substance intact — everything that determines what the server did is visible, and
the two headers FR-011a is about are shown exactly — while not teaching, in a repository whose
purpose is teaching, the habit of painting a bearer token onto a screen. That the key is committed
and worthless (007 FR-025) is an argument for why it would be harmless here, not for why it would be
a good thing to demonstrate. The principle is written without an exemption for cheap credentials, and
the cost of honouring it is one line of display logic.

The redaction is itself labelled in the UI, so nobody mistakes it for the console failing to send a
header.

---

## R-010: Two people, one console (edge case)

**Decision**: nothing to build. Every piece of console state — chosen principal, chosen target,
cached tokens, exchange log, cursor, task handle — is React state in one browser tab. The dev server
forwards bytes and holds nothing. Two developers running `npm run dev` on their own machines share
only the MCP stack, and the spec's edge case is satisfied structurally.

Recorded because "nothing to build" is a conclusion, and an unrecorded one looks like an omission.

---

## R-011: Saying which server answered (FR-016)

**Decision**: the console reports the **target it addressed**, and when that target is the proxy it
says so plainly: the proxy chose a replica and does not report which.

**Rationale**: `_meta["io.modelcontextprotocol/serverInfo"]` carries `{name, version}` and no
instance identity, and `deploy/mcp/nginx.conf` adds no upstream header to the response. So via the
proxy the information genuinely does not exist, and the console must not invent it. Addressing a
replica by name is how the cross-replica property is demonstrated — which is exactly why
`compose.mcp.topology.yaml` exists, and why US4-4 phrases the scenario as beginning a page on one
replica and continuing it on another.

**Finding recorded against feature 007** (not acted on): neither `serverInfo` nor any response header
identifies the answering replica. A `X-Mcp-Instance` header from nginx, or an instance field in
`serverInfo`, would let any client show it. Filed for a future feature; changing it here is out of
scope.

---

## R-012: Carrying the page cursor (FR-015)

**Decision**: after a `search_billing_runs` result with `truncated: true`, the console keeps
`next_cursor` in the call panel's state and offers "Next page", which re-sends the same arguments
with `cursor` filled in. The target selector is deliberately left free between pages, which is how
US4-4 is demonstrated from the UI: begin on `mcp-a`, switch to `mcp-b`, continue. The console shows
`total_match_count` and the server's own `refine_hint` so "more results remain" is the server's
statement rather than the console's inference.

Pages are listed one under another rather than replacing each other, so the absence of overlap
between page 1 and page 2 is something a person can see rather than take on trust (SC-005).

An expired or foreign cursor comes back as a tool error, which the console renders through the same
path as any other `isError: true` result (see 007's error table) — not as an empty page. That is the
edge case the spec names.

---

## R-013: LangChain4j capability inventory (Constitution Principle I)

**Decision**: **this feature touches no LangChain4j module**, so the inventory covers an empty set,
and this entry is the record the amendment requires rather than a skipped gate.

**Evidence**: the planned change set is `frontend/src/mcp/**` (new TypeScript), three touched
frontend files (`App.tsx`, `vite.config.ts`, `build.gradle.kts`), one new node script, one new Vitest
config, and three Makefile targets. No file under `backend/`, `mcp-server/`, `legacy-billing-api/`, or
`token-issuer/` is edited; no Java is compiled that was not compiled before; no Gradle dependency is
added to any JVM module. The console is a client of an HTTP endpoint, and the endpoint is feature
007's, which was itself built on the MCP Java SDK rather than on LangChain4j's MCP client.

**The condition that re-opens this gate**: if implementation discovers a need for backend code —
for instance if forwarding turns out to be insufficient and a Micronaut endpoint is proposed — the
inventory required by Principle I must be produced before any such code is written, and the plan
amended. R-001 exists partly to make that unlikely.

**The library-first habit, applied where it does bite**: R-003 is the one place this feature was
tempted to write what a library provides. It is answered there with a size-and-fit argument and a
test that fails if the hand-written subset stops covering the contracts.

---

## R-014: Where the console goes in the existing application

**Decision**: a third item in the existing header menu, rendered only under `import.meta.env.DEV`,
carrying a visible "dev" tag, opening a page whose first line says what it is for. The app's
`useState`-based tab switching is reused as-is; `react-router-dom` stays unused.

**Rationale**: FR-001 asks for a page alongside the existing ones and forbids changing their
behaviour; FR-002 asks that it not be presented as part of the product. Reusing the tab mechanism is
the smallest change that does both. Introducing routing for one page would touch `App.tsx`
structurally, which is the file whose tests SC-007 requires to pass unchanged.

**Checked, not assumed**: `src/App.test.tsx` asserts the presence of named items
(`getByRole('menuitem', { name: 'New run' })`) rather than the menu's full contents, and its keyboard
test tabs to the menu as a single stop — antd's horizontal menu is one tab stop regardless of item
count. A third item therefore breaks none of them. The suite runs with `DEV === true`, so the item is
present during those tests; that it does not disturb them is the point, and one added test will
assert the item exists rather than leaving its presence incidental.
