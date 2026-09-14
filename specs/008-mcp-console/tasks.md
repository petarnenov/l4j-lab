---

description: "Task list for 008-mcp-console"
---

# Tasks: MCP console

**Input**: Design documents from `/specs/008-mcp-console/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: Included, and not optional here. FR-003a and SC-008 require both suites by name, and
Constitution Principle IV is non-negotiable for deterministic behaviour — every behaviour in this
feature is deterministic, since no model is involved anywhere in it. Each test task is written
before the implementation it names and must fail first.

**Organization**: by user story, so each can be implemented and demonstrated on its own.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: parallelisable — different files, no dependency on an incomplete task
- **[Story]**: US1–US4, mapping to the user stories in `spec.md`
- Exact file paths are given in every task

## Path Conventions

This feature is an addition inside the existing `frontend/` module (plan.md, Structure Decision).
New console code lives under `frontend/src/mcp/`; deterministic tests are co-located as
`*.test.ts(x)` per repository convention; live tests are `*.live.test.ts` and are excluded from the
default Vitest project.

**No backend module is created or changed.** No file under `mcp-server/`, `legacy-billing-api/`,
`token-issuer/`, `deploy/mcp/`, or `compose.mcp*.yaml` is edited — that is out of scope by the spec.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: the build and test plumbing the console needs. No console code yet.

- [ ] T001 Add the four-target development forwarder to `frontend/vite.config.ts` exactly as
  [contracts/dev-proxy.md](./contracts/dev-proxy.md) specifies: prefix `/mcp-dev/{proxy,a,b,c}`,
  prefix stripped and the remainder passed through unchanged, targets from `MCP_HTTP_PORT` (8877),
  `MCP_REPLICA_A_PORT` (8881), `MCP_REPLICA_B_PORT` (8882), `MCP_REPLICA_C_PORT` (8883). The existing
  `/api` proxy to port 8080 must be left exactly as it is.
- [ ] T002 In `frontend/vite.config.ts`, exclude `**/*.live.test.ts` from the default Vitest project
  and add a resolve alias pointing at `specs/007-mcp-billing-server/contracts/tools` so the
  deterministic suite can read the committed tool declarations rather than a copy (research R-004).
  Same file as T001, so this follows it.
- [ ] T003 [P] Create `frontend/vitest.mcp.config.ts`: the live suite's project, selecting
  `src/mcp/**/*.live.test.ts`, `environment: 'node'`, no MSW setup file, and no jsdom.
- [ ] T004 [P] Add `test:mcp` (`vitest run --config vitest.mcp.config.ts`) and `check:dev-only`
  scripts to `frontend/package.json`. **No dependency is added** — if one seems necessary, stop and
  re-read research R-003 before adding it.
- [ ] T005 [P] Create `frontend/scripts/check-dev-only.mjs`: run `vite build` into a temporary
  directory and exit non-zero if the console's marker string appears in any emitted file, with a
  message naming FR-001a. Follow the style of the existing `frontend/scripts/api-contract.mjs`.
- [ ] T006 [P] In `frontend/build.gradle.kts`, register `mcpConsoleTest` (Exec `npm run test:mcp`,
  `outputs.upToDateWhen { false }`, **not** wired into `check` — it needs containers it will not
  start) and `checkDevOnly` (Exec `npm run check:dev-only`, wired into `check`). Mirror the existing
  `test`/`checkApi` task shape, including `dependsOn(npmCi)`.
- [ ] T007 [P] Add three targets to `Makefile` under the existing "MCP billing server" and
  "Verification" groups, each with a `##` help description so it appears in `make help`:
  `mcp-up-topology` (base plus `compose.mcp.topology.yaml`, following the `mcp-up` pattern including
  the port check), `mcp-reset` (`down -v` through `scripts/make/confirm.sh`, following the existing
  `reset` target), and `test-console` (`$(GRADLEW) :frontend:mcpConsoleTest`).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the transport, identity, and page shell that every user story sits on.

**⚠️ CRITICAL**: no user story work can begin until this phase is complete.

### Tests (write first, confirm they fail)

- [ ] T008 [P] Write failing tests in `frontend/src/mcp/transport.test.ts` for the envelope builder:
  `MCP-Protocol-Version` mirrors `params._meta[…/protocolVersion]`, `Mcp-Method` mirrors `method`,
  `Mcp-Name` mirrors `params.name` on `tools/call` and is absent otherwise, the `_meta` block matches
  [contracts/mcp-client.md](./contracts/mcp-client.md) including both declared capabilities, and ids
  are monotonic strings with a **new** id on a confirmation retry.
- [ ] T009 [P] Write failing tests in `frontend/src/mcp/outcome.test.ts` for the four-way
  classification in [data-model.md](./data-model.md): `ok`; `tool-error` (HTTP 200, `result`,
  `isError: true`); `protocol-error` (a JSON-RPC `error`, **including `-32602` arriving at HTTP 200**,
  which is the case that breaks any status-only classifier); `transport-error` (401).
- [ ] T010 [P] Write failing tests in `frontend/src/mcp/targets.test.ts`: the base URL for each of the
  four targets, the three reachability states, an unreachable replica carrying an `absenceReason` that
  names `make mcp-up-topology`, and an unreachable proxy being a distinct state from an unreachable
  replica.
- [ ] T011 [P] Write failing tests in `frontend/src/mcp/principals.test.ts`: the six fixture names from
  `007/contracts/token-issuer.md`, user/firm/role/advisors read from the token's `sub`, `firm_id`,
  `role`, `advisor_ids` claims, expiry taken from `exp` rather than from a failed call, and **nothing
  written to `localStorage`** (research R-009).
- [ ] T012 [P] Create `frontend/src/mcp/test/mcpHandlers.ts`: MSW handlers for `POST /mcp-dev/*/mcp`
  and `POST /mcp-dev/proxy/dev/token`, serving `tools/list` from the committed
  `specs/007-mcp-billing-server/contracts/tools/*.json` through the T002 alias. **No copied fixture**
  (research R-004). Register them alongside the existing handlers in `frontend/src/test/handlers.ts`
  without changing any existing handler.

### Implementation

- [ ] T013 [P] Create `frontend/src/mcp/wire.ts`: TypeScript types for the JSON-RPC **envelope only** —
  `resultType`, `isError`, `content`/`structuredContent`, `inputRequests`, `requestState`, the task
  fields, and the JSON-RPC error shape. Carry a comment recording the Principle III exception from
  plan.md's Complexity Tracking and its two bounds: no tool argument or result shape may be typed
  here, and the live suite asserts each field against the running server.
- [ ] T014 [P] Implement `frontend/src/mcp/targets.ts` to make T010 pass, with the base-URL seam
  research R-005 describes (dev-proxy paths in the browser, absolute URLs in the live suite).
- [ ] T015 [P] Implement `frontend/src/mcp/principals.ts` and
  `frontend/src/mcp/hooks/usePrincipalToken.ts` to make T011 pass: the six names are the only thing
  written down; mint through `/mcp-dev/proxy/dev/token`; decode claims **without verification, for
  display only**, with a comment saying exactly that; cache in memory; re-mint on expiry.
- [ ] T016 Implement `frontend/src/mcp/transport.ts` to make T008 and T009 pass: build headers,
  `_meta`, and envelope; send; classify; return an `Exchange` with the `Authorization` value replaced
  by the labelled redaction research R-009 specifies and every other header and the whole body
  byte-for-byte.
- [ ] T017 [P] Implement `frontend/src/mcp/hooks/useReachableTargets.ts`: probe `GET /lb-health` on
  the proxy and `GET /health/readiness` on each replica (research R-006), distinguishing "not
  published" from "answered with an error".
- [ ] T018 Add the console's navigation item to `frontend/src/App.tsx` behind `import.meta.env.DEV`,
  carrying a visible development tag (FR-002) and lazily importing the console page so the guard
  encloses the import. Change nothing else in that file.
- [ ] T019 Create `frontend/src/mcp/McpConsolePage.tsx`: the page shell with its opening sentence
  saying what it is for, the named regions from
  [contracts/console-surface.md](./contracts/console-surface.md), and the marker constant
  `check-dev-only.mjs` greps for.
- [ ] T020 [P] Append to `frontend/src/App.test.tsx` an assertion that the console item is present in
  the header menu with its development marker. **The file's existing tests must not be modified** —
  SC-007 requires them to pass unchanged, and research R-014 records why a third item does not
  disturb them.

**Checkpoint**: the console opens, knows who it can act as and where it can call, and can put a
correctly-formed request on the wire. No tool is rendered yet.

---

## Phase 3: User Story 1 - Someone sees what the server offers and calls a tool (Priority: P1) 🎯 MVP

**Goal**: the five tools with their declared behaviour, a form built from each tool's declared
arguments, and both the readable result and the exact exchange.

**Independent Test**: open the console against a running stack, call `search_billing_runs` with
`firm_id: firm-alpha`, and confirm the result and the raw exchange both appear. With the stack
stopped, confirm the console explains itself and names `make mcp-up`.

### Tests for User Story 1 (write first, confirm they fail)

- [ ] T021 [P] [US1] Failing test in `frontend/src/mcp/components/ToolList.test.tsx`: the five tools
  in the server's order (never sorted), each showing its read-only, destructive, idempotent and
  open-world hint, with `title` and `description` verbatim (FR-007, US1-1).
- [ ] T022 [P] [US1] Failing test in `frontend/src/mcp/schemaForm.test.tsx`, driven by the five
  committed `inputSchema`s: a field per property, required ones marked, `enum` as a select,
  `format: "date"` as a date picker submitting `YYYY-MM-DD`, `integer` honouring `minimum`/`maximum`,
  `default` prefilled, `description` as help text — and a keyword outside the subset rendering a
  **marked raw-JSON field rather than nothing** (FR-008, research R-003).
- [ ] T023 [P] [US1] Failing test in `frontend/src/mcp/components/ToolCallPanel.test.tsx`: the call is
  disabled while any required argument is empty, and empty optional fields are omitted from
  `arguments` entirely rather than sent as `""` (FR-009, data-model ToolArguments).
- [ ] T024 [P] [US1] Failing test in `frontend/src/mcp/components/ExchangeView.test.tsx`: the
  structured result, the request and response exactly as they travelled, the elapsed time, and the
  `Authorization` value as a labelled redaction that is visibly a redaction and not a missing header
  (FR-010, SC-003, research R-009).
- [ ] T025 [P] [US1] Failing test in `frontend/src/mcp/components/StackOffline.test.tsx`: with the
  proxy unreachable the console says so and names `make mcp-up`, and renders neither an empty page nor
  a generic failure (US1-4, FR-003, SC-006).

### Implementation for User Story 1

- [ ] T026 [P] [US1] Implement `frontend/src/mcp/hooks/useDiscover.ts` and
  `frontend/src/mcp/hooks/useTools.ts` with TanStack Query, taking `staleTime` from the server's own
  `ttlMs` rather than a number chosen here (data-model Tool).
- [ ] T027 [P] [US1] Implement `frontend/src/mcp/components/StackOffline.tsx` to make T025 pass.
- [ ] T028 [US1] Implement `frontend/src/mcp/schemaForm.tsx` over the subset in research R-003, to
  make T022 pass.
- [ ] T029 [US1] Implement `frontend/src/mcp/components/ToolList.tsx` to make T021 pass.
- [ ] T030 [US1] Implement `frontend/src/mcp/hooks/useToolCall.ts` and
  `frontend/src/mcp/components/ToolCallPanel.tsx` to make T023 pass.
- [ ] T031 [US1] Implement `frontend/src/mcp/components/ExchangeView.tsx` rendering all four outcomes
  from data-model, to make T024 pass.
- [ ] T032 [US1] Compose the above into `frontend/src/mcp/McpConsolePage.tsx` so the page is usable end
  to end against a running stack.
- [ ] T033 [P] [US1] Live test `frontend/src/mcp/discovery.live.test.ts`, with the precheck described
  in research R-005 — probe `GET /lb-health` first and throw the `make mcp-up` instruction, wording
  it as `mcp-server/src/topologyTest/.../TopologyFixture.java` does. Assert against the running
  server: `server/discover` returns `supportedVersions`, capabilities and `serverInfo`; `tools/list`
  returns the five in the documented order with annotations equal to the committed contracts; a
  read-only `tools/call` returns `structuredContent` (SC-002, SC-008).

**Checkpoint**: User Story 1 is fully functional and demonstrable on its own. This is the MVP.

---

## Phase 4: User Story 2 - Someone sees that identity decides what is visible (Priority: P1)

**Goal**: switching principal changes what the same search returns, and a cross-firm refusal is
visibly a *tool* failure carried by a successful response.

**Independent Test**: run the same `search_billing_runs` as `advisor-alpha-101` and as `admin-alpha`
and compare; then search `firm-beta` and read the refusal.

### Tests for User Story 2 (write first, confirm they fail)

- [ ] T034 [P] [US2] Failing test in `frontend/src/mcp/components/PrincipalPicker.test.tsx`: the six
  principals are offered; the chosen one's user, firm, role and permitted advisors are shown from the
  token's claims; nothing is minted or pasted by hand (FR-004, FR-005, US2-1).
- [ ] T035 [P] [US2] Failing test appended to `frontend/src/mcp/components/ExchangeView.test.tsx`: a
  tool failure is labelled a tool failure **and shown as a successful response carrying an error
  flag**; a protocol failure is labelled as such and shows its code. The two must not render through
  the same component (FR-011, US2-3).
- [ ] T036 [P] [US2] Failing test in `frontend/src/mcp/McpConsolePage.principal.test.tsx`: switching
  principal affects only subsequent calls, results already on screen keep the principal they were
  obtained as, and every displayed result names it (FR-006).

### Implementation for User Story 2

- [ ] T037 [US2] Implement `frontend/src/mcp/components/PrincipalPicker.tsx` to make T034 pass,
  including the loading state that shows the name without inventing claims (data-model Principal).
- [ ] T038 [US2] Record `principalName` on every `Exchange` in `frontend/src/mcp/transport.ts` and
  display it in `frontend/src/mcp/components/ExchangeView.tsx`, to make T035 and T036 pass.
- [ ] T039 [US2] Wire the picker into `frontend/src/mcp/McpConsolePage.tsx` so the active principal's
  token is used for subsequent calls only.
- [ ] T040 [P] [US2] Live test `frontend/src/mcp/entitlement.live.test.ts`: against the running stack,
  the same search as `advisor-alpha-101` returns fewer runs than as `admin-alpha` and only `adv-101`'s;
  a `firm-beta` search returns HTTP 200 with `isError: true` and no run data (SC-004).

**Checkpoint**: User Stories 1 and 2 both work independently.

---

## Phase 5: User Story 3 - Someone follows a change that asks before it acts (Priority: P2)

**Goal**: the three-call confirmation sequence, with the fee before and after, and no undo.

**Independent Test**: walk the three calls on `acc-0101` and read the resulting fee.

### Tests for User Story 3 (write first, confirm they fail)

- [ ] T041 [P] [US3] Failing test in `frontend/src/mcp/components/ElicitationPanel.test.tsx`: an
  `input_required` result shows the server's `message` **verbatim**, states that nothing has been
  applied, and renders no result panel (FR-012, US3-1).
- [ ] T042 [P] [US3] Failing test in `frontend/src/mcp/transport.test.ts` for the retry: it carries
  `params.inputResponses.<key>`, echoes `params.requestState` byte-for-byte, and uses a **different**
  JSON-RPC id — and the console never constructs, decodes, or edits `requestState`
  ([contracts/mcp-client.md](./contracts/mcp-client.md)).
- [ ] T043 [P] [US3] Failing test appended to
  `frontend/src/mcp/components/ElicitationPanel.test.tsx`: an applied change shows
  `legacy_reference_id` (US3-2) and the fee before, computed as `new_fee_bps - delta_bps`, against
  after (FR-012a, research R-007); `replayed: true` shows the original result and says nothing
  happened a second time (US3-3); `confirmed: false` shows that nothing was applied and is **not**
  rendered as an error (US3-4).
- [ ] T044 [P] [US3] Failing test appended to `frontend/src/mcp/components/ElicitationPanel.test.tsx`: no undo control exists anywhere in the panel, and
  `make mcp-reset` is named with the one-sentence reason from FR-012b (US3-6).

### Implementation for User Story 3

- [ ] T045 [US3] Implement `frontend/src/mcp/components/ElicitationPanel.tsx` to make T041, T043 and
  T044 pass, reusing `schemaForm.tsx` for the elicitation's `requestedSchema`.
- [ ] T046 [US3] Add the `input_required` branch and the retry path to
  `frontend/src/mcp/hooks/useToolCall.ts` and `frontend/src/mcp/transport.ts`, to make T042 pass.
- [ ] T047 [US3] Wire the panel into `frontend/src/mcp/McpConsolePage.tsx`.
- [ ] T048 [P] [US3] Live test `frontend/src/mcp/confirmation.live.test.ts`: the three-call sequence
  against the running stack — first call applies nothing, the confirmed call applies exactly once, a
  third with the same `operation_id` replays. Use a fresh `operation_id` per run. This test **writes
  real data and the drift is accepted** (spec clarification); its comment must say so and name
  `make mcp-reset`.

**Checkpoint**: User Stories 1, 2 and 3 all work independently.

---

## Phase 6: User Story 4 - Someone watches a long operation and the replicas behind it (Priority: P2)

**Goal**: a handle reported at once and polled to completion, cancellation, addressing a named
replica, and a page begun on one replica continued on another.

**Independent Test**: start a run, poll it to completion, then continue a search page on a different
replica than the one that began it.

### Tests for User Story 4 (write first, confirm they fail)

- [ ] T049 [P] [US4] Failing test in `frontend/src/mcp/components/TaskWatcher.test.tsx`: the handle is
  shown from the first result **before any poll**; polling uses the server's `pollIntervalMs` and no
  other number; it stops at each terminal state (FR-013, US4-1).
- [ ] T050 [P] [US4] Failing test appended to `frontend/src/mcp/components/TaskWatcher.test.tsx`: cancellation is offered while non-terminal and
  drives the task to `cancelled`; cancelling an already-terminal task is acknowledged and **not**
  shown as an error (FR-014, US4-2, 007 FR-031).
- [ ] T051 [P] [US4] Failing test in `frontend/src/mcp/components/TargetPicker.test.tsx`: the proxy is
  always offered; reachable replicas are selectable; unreachable ones are disabled with a reason
  naming `make mcp-up-topology`; all three unreachable is presented as normal and not as a fault; and
  a result obtained through the proxy says a replica answered without claiming which (FR-016, FR-017,
  US4-5, research R-011).
- [ ] T052 [P] [US4] Failing test in `frontend/src/mcp/components/PageCursor.test.tsx`: the next page
  re-sends the same arguments with `cursor` filled from `next_cursor` and nothing typed by hand; pages
  stack rather than replace, so absence of overlap is visible; `total_match_count` and the server's
  `refine_hint` are shown; an expired or foreign cursor renders as the server's tool error and not as
  an empty page (FR-015, spec edge case).

### Implementation for User Story 4

- [ ] T053 [P] [US4] Implement `frontend/src/mcp/hooks/useTask.ts` using TanStack Query's
  `refetchInterval` — server state stays in the query cache and is not copied into a second store
  (stack constraints, research R-008).
- [ ] T054 [US4] Implement `frontend/src/mcp/components/TaskWatcher.tsx` to make T049 and T050 pass,
  recording each poll as its own `Exchange` so a poll landing on another replica is visible in the log.
- [ ] T055 [P] [US4] Implement `frontend/src/mcp/components/TargetPicker.tsx` to make T051 pass.
- [ ] T056 [P] [US4] Implement `frontend/src/mcp/components/PageCursor.tsx` to make T052 pass.
- [ ] T057 [US4] Wire target selection into every call in `frontend/src/mcp/McpConsolePage.tsx` and
  record `targetId` on each `Exchange`, leaving the target free between pages so a page begun on one
  replica can be continued on another.
- [ ] T058 [P] [US4] Live test `frontend/src/mcp/topology.live.test.ts`: start, poll and cancel a run;
  take page 1 from `mcp-a` and page 2 from `mcp-b` with `page_size: 1` and assert no overlap. When the
  topology overlay is not running it must **skip with a message naming `make mcp-up-topology`**, never
  fail silently or pass vacuously (SC-005, SC-008, Principle IV).

**Checkpoint**: all four user stories are independently functional.

---

## Phase 7: Deliberate refusals (FR-011a, SC-003a)

**Purpose**: the cross-cutting requirement that belongs to no single user story — producing, on
demand, each refusal the protocol defines for a malformed request. It depends on the exchange view,
so it follows the stories rather than preceding them.

- [ ] T059 [P] Failing test in `frontend/src/mcp/malformed.test.ts`: each of the three builds exactly
  the request [contracts/malformed-requests.md](./contracts/malformed-requests.md) describes, and
  **only the one named thing is wrong** — in particular the version case keeps header and body in
  agreement, or it would produce `-32020` and silently demonstrate a different case.
- [ ] T060 [P] Failing test in `frontend/src/mcp/components/MalformedPanel.test.tsx`: each case
  explains what is wrong before it is sent, is sendable in one action, marks its exchange
  `deliberate` and keeps the mark visible in the log, and shows the refusal with its code; the version
  case shows `data.supported`; and no free-form editing of headers or body exists anywhere.
- [ ] T061 Implement `frontend/src/mcp/malformed.ts` to make T059 pass.
- [ ] T062 Implement `frontend/src/mcp/components/MalformedPanel.tsx` and wire it into
  `frontend/src/mcp/McpConsolePage.tsx`, to make T060 pass.
- [ ] T063 [P] Live test `frontend/src/mcp/malformed.live.test.ts`: each of the three produces, from
  the running server, the HTTP status and JSON-RPC code the contract documents, and the version case
  really carries `data.supported` (SC-003a).

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T064 Prove `frontend/scripts/check-dev-only.mjs` can fail: temporarily import the console page
  unguarded in `frontend/src/App.tsx`, confirm `npm run check:dev-only` exits non-zero, revert, and
  confirm it passes. A check never seen to fail is not evidence (research R-002).
- [ ] T065 [P] Run `make test-frontend` and confirm every pre-existing suite under `frontend/src/`
  passes **unchanged** — `frontend/src/App.test.tsx`, `frontend/src/components/*.test.tsx`,
  `frontend/src/pages/*.test.tsx` and `frontend/src/theme/*.test.*` with no assertion edited and no
  existing component touched (SC-007).
- [ ] T066 [P] Bring the console's controls to the standard the existing pages are held to: both
  themes, keyboard-reachable, named regions — as
  [contracts/console-surface.md](./contracts/console-surface.md) requires. Follow the patterns already
  in `frontend/src/components/`.
- [ ] T067 [P] Run `npm run lint` and `npm run format` in `frontend/` over the new code in
  `frontend/src/mcp/`, `frontend/vite.config.ts`, `frontend/vitest.mcp.config.ts` and
  `frontend/scripts/check-dev-only.mjs`, and commit the result.
- [ ] T068 Walk [quickstart.md](./quickstart.md) end to end against `make mcp-up-topology` and correct
  anything that does not behave as written — including the timing note and the troubleshooting table.
- [ ] T069 [P] Add a short "MCP console" section to `README.md` pointing at this feature's quickstart
  and saying plainly that it is development-only and absent from the packaged build.
- [ ] T070 [P] Create `specs/008-mcp-console/findings.md` recording the two observations about feature
  007 that this feature deliberately did not act on: `post_fee_adjustment`'s `outputSchema` carries no
  `previous_fee_bps` (research R-007), and nothing in `serverInfo` or the response headers identifies
  the answering replica (research R-011). The spec requires these be recorded, not fixed here.
- [ ] T071 Check SC-001 the only way it can be checked: give `frontend/src/mcp/McpConsolePage.tsx` as
  it renders to someone who has not seen it, and confirm they reach a read-only tool's result within
  two minutes without consulting documentation. Fix what confused them in that file, and note the
  outcome in `specs/008-mcp-console/findings.md`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies. T001 → T002 (same file); T003–T007 parallel.
- **Foundational (Phase 2)**: needs Phase 1. **Blocks every user story.**
- **User Stories (Phases 3–6)**: each needs Phase 2 and nothing else. In priority order
  US1 → US2 → US3 → US4, or in parallel across people.
- **Deliberate refusals (Phase 7)**: needs Phase 2 and T031 (the exchange view). Independent of
  US2–US4.
- **Polish (Phase 8)**: needs whichever stories are being delivered. T064, T065, T069 and T070 can be
  done as soon as Phase 2 is complete.

### User Story Dependencies

- **US1 (P1)** — no dependency on another story. The MVP.
- **US2 (P1)** — extends `ExchangeView` (T031) with principal attribution and the tool/protocol
  labelling. Practically: do US1 first. Demonstrable on its own once done.
- **US3 (P2)** — independent of US2 and US4; needs the tool call path from US1.
- **US4 (P2)** — independent of US2 and US3; needs the tool call path from US1. Its cross-replica
  half additionally needs the topology overlay running.

### Within Each Story

Tests are written first and must fail. Then hooks, then components, then composition into the page,
then the live test.

### Parallel Opportunities

- Phase 1: T003, T004, T005, T006, T007 together.
- Phase 2: all five test tasks (T008–T012) together; then T013, T014, T015, T017 together.
- Each story's test tasks are all `[P]` — different files, no shared state.
- US2, US3 and US4 can be built concurrently by three people once US1 is done.
- Every live test (T033, T040, T048, T058, T063) can be written while its story's deterministic tests
  are being made to pass, since it touches a different file.

---

## Parallel Example: User Story 1

```bash
# The five failing tests first, together:
Task: "ToolList ordering and annotations in frontend/src/mcp/components/ToolList.test.tsx"
Task: "Argument derivation from the committed inputSchemas in frontend/src/mcp/schemaForm.test.tsx"
Task: "Required-argument blocking in frontend/src/mcp/components/ToolCallPanel.test.tsx"
Task: "Raw exchange and bearer redaction in frontend/src/mcp/components/ExchangeView.test.tsx"
Task: "Offline state naming make mcp-up in frontend/src/mcp/components/StackOffline.test.tsx"

# Then the two independent pieces, together:
Task: "useDiscover and useTools in frontend/src/mcp/hooks/"
Task: "StackOffline in frontend/src/mcp/components/StackOffline.tsx"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1: Setup — the forwarder, the two Vitest projects, the Gradle tasks, the Makefile targets.
2. Phase 2: Foundational — transport, principals, targets, page shell. **Blocks everything.**
3. Phase 3: User Story 1.
4. **Stop and validate**: `make mcp-up`, open the console, call a read-only tool, read the raw
   exchange. Then stop the stack and confirm the offline state. That is SC-001, SC-003 and SC-006.

At this point the console already replaces reaching for `curl`, which is the whole justification for
the feature.

### Incremental Delivery

1. Setup + Foundational → the page opens and can speak the protocol.
2. + US1 → **MVP**: the five tools, callable, with the exchange visible.
3. + US2 → entitlement is demonstrable in under a minute (SC-004).
4. + US3 → the confirmation round trip, and the fee before and after.
5. + US4 → the handle-and-poll shape and the cross-replica property (SC-005).
6. + Phase 7 → a refusal of each protocol kind, on demand (SC-003a).
7. + Phase 8 → the dev-only absence proven, the quickstart walked, the findings filed.

Each step leaves the console usable and every earlier demonstration intact.

### Parallel Team Strategy

Phases 1 and 2 together, then US1 together — everything afterwards leans on the transport and the
exchange view, and getting those wrong twice is the expensive failure. Once US1 is green: one person
on US2, one on US3, one on US4 and Phase 7. Phase 8 is shared.

---

## Notes

- `[P]` means a different file and no dependency on an incomplete task.
- Two suites, always: `make test-frontend` must stay runnable with no network and no credential, and
  `make test-console` must stay selectable on its own and must fail with an instruction rather than an
  unexplained error. Neither rule is negotiable (Principle IV, SC-008).
- **No runtime dependency is added by any task here.** If one appears necessary, that is a signal to
  re-read research R-003 first.
- No task edits feature 007's server, its Compose files, or `deploy/mcp/nginx.conf`. Anything the
  console seems to need from it is a finding for T070, not a change.
- Opaque values — `cursor`, `requestState`, `taskId` — are echoed exactly and never constructed,
  decoded, or displayed as meaningful.
- Commit after each task or logical group; stop at any checkpoint to demonstrate the story.
