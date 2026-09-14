---

description: "Task list for 010-close-mcp-findings"
---

# Tasks: Close the findings against the MCP billing server

**Input**: Design documents from `/specs/010-close-mcp-findings/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: Included, and required rather than chosen. Every behaviour here is deterministic, so
Constitution Principle IV applies without exception — and FR-004 and FR-014 make it explicit for a
reason particular to this feature: **a defect fixed without a failing test first is a defect nobody
proved was there.** Every one of the seven findings gets a test that fails on today's behaviour before
anything is changed.

**Organization**: by user story, one per group of findings, so each can be delivered and demonstrated
alone.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: parallelisable — different files, no dependency on an incomplete task
- **[Story]**: US1–US5, mapping to the user stories in `spec.md`
- Exact file paths are given in every task

## Path Conventions

Work lands where each finding lives: `mcp-server/`, `legacy-billing-api/`, and the documents under
`specs/007-mcp-billing-server/`. Nothing is reorganised — this feature repairs, it does not tidy.

**Feature 007's `plan.md` and `research.md` are not edited.** They record decisions taken at the time
and remain true as history. Only claims about *present behaviour* are corrected, and each correction
says in place that it was one.

---

## Phase 1: Setup

**Purpose**: pin what is true today, so every later change is measured against it rather than against
memory.

- [X] T001 Start the stack with `make mcp-up-topology` and record, in
  `specs/010-close-mcp-findings/quickstart.md` under a new "Starting state" heading, the output of each
  reproduction in its table — the HTTP status and body for every row. These are the before-values that
  SC-001 through SC-005 are measured against, and writing them down now is what stops "it seems better"
  from counting as evidence later.
- [X] T002 [P] Run `make mcp-verify` and `make test-console` and record in
  `specs/010-close-mcp-findings/quickstart.md` which of feature 008's live assertions in
  `frontend/src/mcp/*.live.test.ts` pin today's behaviour rather than the desired behaviour — at least the decline that
  arrives flagged as an error, and the schema keywords the console's suite currently expects to be
  missing. Each will change with a finding, and knowing which ones in advance stops a failing console
  test being mistaken for a regression.
- [X] T003 [P] Run `make check-specs` and list, in
  `specs/010-close-mcp-findings/quickstart.md`, every entry in `scripts/spec-drift/baseline.json` that
  belongs to one of the seven findings. Each must be **removed** when its finding closes (FR-020), and
  an entry left behind fails that check by design.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the guard that should already exist, and the boundary every failure passes through.

**⚠️ CRITICAL**: US1, US2, US3 and US5 cannot begin until this phase is complete. **US4 can** — the
reading guide is a document, and nothing in it depends on the boundary or the contract test.

### The test feature 007's build file already claims exists

- [X] T004 Write `mcp-server/src/test/java/dev/l4jlab/mcp/contracts/ToolDeclarationContractTest.java`:
  for each of the five tools, fetch what the server declares and compare it **field by field** against
  the committed file in `specs/007-mcp-billing-server/contracts/tools/`, failing on any difference in
  name, title, description, annotations, or any schema keyword (FR-010, FR-014, SC-003). It reads the copies
  `mcp-server/build.gradle.kts` already puts in `build/resources/test/contracts/` — which nothing has
  ever read (research R-003).
- [X] T005 Run T004 and record its failures in `specs/010-close-mcp-findings/quickstart.md`. It is the guard FR-014 requires, and it must
  fail immediately and extensively: this is F-001 measured mechanically for the first time, and the
  number it produces is the first thing this feature delivers.

### The boundary every failure passes through

- [X] T006 [P] Write a failing test in
  `mcp-server/src/test/java/dev/l4jlab/mcp/protocol/ToolErrorStatusFilterTest.java`: an unexpected
  exception escaping a tool becomes a JSON-RPC error whose message is non-empty, whose code is one the
  error table in `specs/007-mcp-billing-server/contracts/mcp-protocol.md` lists, and whose text carries
  no exception message, no stack frame and no hostname.
- [X] T007 Implement it in `mcp-server/src/main/java/dev/l4jlab/mcp/protocol/ToolErrorStatusFilter.java`
  to make T006 pass. Today an unexpected exception becomes an error with an **empty** message, which the
  SDK then rejects — so the caller learns that the server's validator complained and nothing else. A
  boundary that loses the message is one bad exception away from leaking one instead (FR-002).
- [X] T008 [P] Add a test in `mcp-server/src/test/java/dev/l4jlab/mcp/protocol/ToolErrorStatusFilterTest.java`
  that what actually happened is still logged server-side,
  structured, so making the caller's view safe does not make the operator's view empty.

**Checkpoint**: failures are well-formed whatever causes them, and the extent of F-001 is a number
rather than an impression.

---

## Phase 3: User Story 1 - An entitlement refusal is a refusal, not a crash (Priority: P1) 🎯 MVP

**Goal**: no query returns HTTP 500. An empty result is an empty result; a refusal says nothing about
what exists.

**Independent Test**: search with `started_from: 2030-01-01` — a date range with nothing in it — and
read what comes back.

**The finding was recorded wrongly, and the specification has been corrected to follow what is true**:
the crash has nothing to do with entitlement. Any search whose result set is empty returns 500, because
the system of record omits an empty `items` and the server dereferences it (research R-001). US1's
scenarios 4 to 6 keep the behaviour that already works written down, as regression protection (FR-003).

### Tests for User Story 1 (write first, confirm they fail)

- [X] T009 [P] [US1] Failing test (FR-004: each finding gets a test that fails on today's behaviour
  before anything changes) in
  `legacy-billing-api/src/test/java/dev/l4jlab/legacy/api/BillingRunControllerTest.java`: a search
  matching nothing serialises `items` as an empty array rather than omitting it.
- [X] T010 [P] [US1] Failing test in
  `mcp-server/src/test/java/dev/l4jlab/mcp/tools/BillingRunToolsTest.java`: a legacy page whose `items`
  is absent is read as an empty page, not dereferenced. Every one of these tools declares
  `openWorldHint: true`; a client of a system it does not control does not assume a field is present.
- [X] T011 [P] [US1] Failing test in
  `mcp-server/src/topologyTest/java/dev/l4jlab/mcp/topology/EmptyResultTopologyTest.java`: against the
  running stack, a date range matching nothing returns HTTP 200 with `runs: []`,
  `total_match_count: 0`, and no `isError` (US1-1, FR-001). No query anywhere returns 500 (FR-001a).
- [X] T012 [P] [US1] Failing test in
  `mcp-server/src/topologyTest/java/dev/l4jlab/mcp/topology/EmptyResultTopologyTest.java`: an advisor filtering by an advisor they
  may not act for receives a tool failure carried by a successful response (US1-4), and
  **indistinguishable** from the answer for an advisor that does not exist (US1-5, FR-003) — the pair
  must not become a probe.
- [X] T013 [P] [US1] Test in
  `mcp-server/src/topologyTest/java/dev/l4jlab/mcp/topology/EmptyResultTopologyTest.java` that a cross-firm search behaves exactly as it does today
  (US1-4): the path that was already correct must not move.

### Implementation for User Story 1

- [X] T014 [US1] Make the empty collection serialise in
  `legacy-billing-api/src/main/java/dev/l4jlab/legacy/api/Dtos.java`, or in the serde configuration
  that governs it, so `Page` always writes `items`. Omitting it makes "nothing matched" and "malformed
  response" the same bytes.
- [X] T015 [US1] Guard the read in
  `mcp-server/src/main/java/dev/l4jlab/mcp/tools/BillingRunTools.java` (the `.stream()` at the search
  path, and every other place a legacy collection is dereferenced) so a missing collection is an empty
  one.
- [X] T016 [US1] Confirm the five acceptance scenarios by hand against the running stack, and record
  the after-values beside T001's before-values in `specs/010-close-mcp-findings/quickstart.md`.

**Checkpoint**: User Story 1 is demonstrable alone. This is the MVP, and it is the only defect among
the seven — an ordinary query stops crashing the tool.

---

## Phase 4: User Story 2 - A client built from the contract is understood correctly (Priority: P1)

**Goal**: someone who implements a client by reading the published contract applies the change they
asked for.

**Independent Test**: build the retry exactly as the contract describes, send it, read the fee.

### Tests for User Story 2 (write first, confirm they fail)

- [X] T017 [P] [US2] Failing test in
  `mcp-server/src/test/java/dev/l4jlab/mcp/tools/FeeAdjustmentToolTest.java`: an answer that is
  **present but uninterpretable** produces an error saying so, rather than being read as a refusal
  (FR-007, US2-2). Today `confirmed()` returns false for anything it cannot parse, and its comment says
  so outright.
- [X] T018 [P] [US2] Failing test in the same file: a **declined** confirmation produces a result
  reporting that nothing was applied, without `isError` (FR-008, US2-3) — which is what
  `specs/007-mcp-billing-server/contracts/mcp-protocol.md` already says a decline is.
- [X] T019 [P] [US2] Test in `mcp-server/src/test/java/dev/l4jlab/mcp/tools/FeeAdjustmentToolTest.java`
  that an **absent** answer is still a refusal. Absent and
  uninterpretable are different: one is a decision, the other is a failure to communicate.
- [X] T020 [P] [US2] Failing test in
  `mcp-server/src/topologyTest/java/dev/l4jlab/mcp/topology/ConfirmationTopologyTest.java`: a retry
  assembled **only from what the corrected contract says** applies the change (US2-1, FR-005).

### Implementation for User Story 2

- [X] T021 [US2] Correct `specs/007-mcp-billing-server/contracts/mcp-protocol.md`: write the
  confirmation retry out in full, including the MCP `ElicitResult` envelope — `{action, content}`, with
  the requested schema's fields inside `content` — and say that the flat form is read as a refusal. The contract and the server then describe the same
  exchange (FR-006), and nothing existing clients send changes, so the protocol's versioning rules are
  not engaged (FR-009, research R-004).
  **The server is right here and the contract was incomplete** (research R-004); this is the half that
  moves.
- [X] T022 [US2] Distinguish absent from uninterpretable in
  `mcp-server/src/main/java/dev/l4jlab/mcp/tools/FeeAdjustmentTool.java`, to make T017 and T019 pass.
- [X] T023 [US2] Stop flagging a decline as an error in
  `mcp-server/src/main/java/dev/l4jlab/mcp/tools/FeeAdjustmentTool.java`, to make T018 pass (FR-008). Here the
  **contract is right and the server moves**: a decline is the system working.
- [X] T024 [US2] Update the assertion in `frontend/src/mcp/confirmation.live.test.ts` that pins today's
  `isError: true` on a decline, and the console's rendering in
  `frontend/src/mcp/components/ElicitationPanel.tsx` that explains the divergence — the divergence is
  gone, and the explanation with it (FR-019's spirit: a fix removes its own workaround).

**Checkpoint**: both P1 stories done. A contract-following client works, and nothing returns 500.

---

## Phase 5: User Story 3 - What the server declares is what the repository committed (Priority: P2)

**Goal**: one declaration, carrying everything the contract carried.

**Independent Test**: T004's contract test passes.

**This is the Principle III repair.** The shapes are declared twice by hand today — in Java
annotations, which ship, and in committed JSON, which nothing reads. The Java becomes the source and
the JSON becomes generated from it (research R-002).

### Tests for User Story 3

- [X] T025 [P] [US3] Extend
  `mcp-server/src/test/java/dev/l4jlab/mcp/contracts/ToolDeclarationContractTest.java` with the
  keyword-level assertions T005 recorded as failing: `enum`, `format`, `minimum`, `maximum`,
  `minLength`, `maxLength`, `default`, `integer` not widened to `number`, and required output fields not
  relaxed.
- [X] T026 [P] [US3] Failing test in
  `mcp-server/src/test/java/dev/l4jlab/mcp/tools/ArgumentValidationTest.java`: an argument violating a
  declared constraint is refused with a tool error naming the field. **A declared constraint is a
  validated constraint** — restoring `enum` without enforcing it would replace a lie about what is
  declared with a lie about what is enforced (FR-013, research R-002).

### Implementation for User Story 3

- [X] T027 [P] [US3] Enrich the argument declarations in
  `mcp-server/src/main/java/dev/l4jlab/mcp/tools/BillingRunTools.java` so `@ToolArg` carries the
  enumeration, format, bounds and default each argument's contract declares.
- [X] T028 [P] [US3] The same for
  `mcp-server/src/main/java/dev/l4jlab/mcp/tools/FeeAdjustmentTool.java`.
- [X] T029 [P] [US3] The same for
  `mcp-server/src/main/java/dev/l4jlab/mcp/tools/StartBillingRunTool.java`.
- [X] T030 [US3] Correct `start_billing_run`'s declared output shape in
  `mcp-server/src/main/java/dev/l4jlab/mcp/tools/StartBillingRunTool.java` so it describes (FR-012)
  **what calling the tool returns** — the handle — rather than the completed run, which arrives later
  through `tasks/get` (F-004, research R-005).
- [X] T031 [US3] Enforce the restored constraints where the server does not already, to make T026 pass,
  and record in `specs/010-close-mcp-findings/research.md` any constraint that could not be enforced
  and why.
- [X] T032 [US3] Generate the committed contracts from the declarations: add a task to
  `mcp-server/build.gradle.kts` that writes `specs/007-mcp-billing-server/contracts/tools/*.json` from
  what the server declares, so a change reaches both without a second edit (FR-011). The copy step that
  feeds the test stays; it now copies a generated file.
- [X] T033 [US3] Correct `specs/007-mcp-billing-server/contracts/README.md`, which claims the files are
  *"loaded verbatim at runtime"*. They never were; say what is true — the declarations are generated
  from the Java, and these files are generated from the same source.
- [X] T034 [US3] Prove the generation is real (SC-004): change one keyword in a `@ToolArg` in
  `mcp-server/src/main/java/dev/l4jlab/mcp/tools/BillingRunTools.java`, regenerate, confirm the file
  under `specs/007-mcp-billing-server/contracts/tools/` changes and the contract test still passes; revert. A single source that nobody
  has seen propagate is an assumption.

**Checkpoint**: what a model reads is what this repository committed, and a drift between them fails
the build — which is what feature 007's build file has claimed all along.

---

## Phase 6: User Story 4 - The reading guide leads somewhere (Priority: P2)

**Goal**: a newcomer following the quickstart's table arrives at the code.

**Independent Test**: open every file the table names.

### Tests for User Story 4

- [X] T035 [P] [US4] Confirm the failure first: run `make check-specs` and record which rows of the
  table in `specs/007-mcp-billing-server/quickstart.md` name files that do not exist. Fifty-eight of
  feature 007's baseline entries are this.

### Implementation for User Story 4

- [X] T036 [US4] Correct the reading-guide table (FR-015) in `specs/007-mcp-billing-server/quickstart.md` by
  reading the delivered code under `mcp-server/src/main/java/dev/l4jlab/mcp/`, one row at a time. The
  delivered package holds `McpMethodHandler`, `HttpMethodGate`, `BillingTransportContextExtractor` where
  the table names `McpController`, `HeaderValidationFilter`, `RequestEnvelope`.
- [X] T037 [US4] Where no single file implements a listed protocol feature, say so in the table in
  `specs/007-mcp-billing-server/quickstart.md` rather (FR-015, FR-016)
  than naming one arbitrarily (FR-016). This is the one item in the feature that cannot be verified
  mechanically — feature 009's check confirms a named file *exists*, not that it is where the feature
  lives, so the reading is the deliverable (research R-007).
- [X] T038 [US4] Note in the same table that `tasks.md` T039 records the controller as superseded by
  R-014, so a reader who finds the old name in the task list is not left wondering which is current.

**Checkpoint**: the table can be followed end to end.

---

## Phase 7: User Story 5 - A result carries what a caller needs to report it (Priority: P3)

**Goal**: stop making every client reconstruct what the server already knows.

**Independent Test**: apply a fee adjustment and read the result; call through the proxy and read
`_meta`.

### Tests for User Story 5

- [X] T039 [P] [US5] Failing test in
  `mcp-server/src/test/java/dev/l4jlab/mcp/tools/FeeAdjustmentToolTest.java`: an applied adjustment
  reports `previous_fee_bps` (FR-017). It is known at the point the change is applied and already
  quoted in the elicitation message in prose.
- [X] T040 [P] [US5] Failing test in
  `mcp-server/src/test/java/dev/l4jlab/mcp/protocol/ServerInfoTest.java`: every result's
  `_meta.serverInfo` carries an instance identifier (FR-018).
- [X] T041 [P] [US5] Failing test in
  `mcp-server/src/topologyTest/java/dev/l4jlab/mcp/topology/InstanceIdentityTopologyTest.java`: two
  calls through the proxy that land on different replicas report different instances, and a call
  addressed to a named replica reports that one.

### Implementation for User Story 5

- [X] T042 [P] [US5] Add `previous_fee_bps` to the result in
  `mcp-server/src/main/java/dev/l4jlab/mcp/tools/FeeAdjustmentResult.java` and populate it in
  `FeeAdjustmentTool.java`.
- [X] T043 [P] [US5] Add the instance identifier to `serverInfo`, read from configuration with a
  documented default as every other setting in this repository is, in
  `mcp-server/src/main/resources/application.yml` and wherever `serverInfo` is assembled. **Not an nginx
  header**: the proxy is one deployment shape among several, and an instance is a property of the server
  (research R-006).
- [X] T044 [P] [US5] Set the identifier per replica in `compose.mcp.yaml`, so `mcp-a`, `mcp-b` and
  `mcp-c` are distinguishable, and document the variable in the table in `README.md`.
- [X] T045 [US5] Simplify feature 008's console now that the server reports what it previously could
  not: `frontend/src/mcp/components/ExchangeView.tsx` says the proxy "does not report which replica
  answered", and `frontend/src/mcp/components/TargetPicker.tsx` repeats it. Both become the instance the
  server names, and the tests pinning the old wording change with them.

**Checkpoint**: all five stories done.

---

## Phase 8: Polish & closing the record

- [X] T046 Remove from `scripts/spec-drift/baseline.json` every entry T003 listed whose finding is now
  closed. An entry that no longer matches real drift is reported as `STALE` and fails the check — that
  is how the baseline is designed to shrink, and leaving one behind silences a finding instead of
  closing it (FR-020).
- [X] T047 [P] Mark each closed finding as closed in
  `specs/008-mcp-console/findings.md` and `specs/009-spec-drift-check/findings.md`, with what was done
  (FR-019). Anything left open keeps its entry and gains a sentence saying why (FR-021). **None of the
  seven ends this feature undiscussed** (SC-007).
- [X] T048 [P] Record in `specs/010-close-mcp-findings/findings.md` what this feature found that the
  seven did not name: that every empty search crashed rather than only a cross-advisor one, and that the
  contract test feature 007's build file describes was never written.
- [ ] T049 Run `make mcp-verify` and confirm every one of feature 007's acceptance scenarios in
  `mcp-server/src/topologyTest/java/dev/l4jlab/mcp/topology/` still passes (SC-008). This feature repairs; nothing it did not set out to change may move.
- [ ] T050 [P] Run `make test-console` and confirm feature 008's live suite in
  `frontend/src/mcp/*.live.test.ts` passes with the assertions
  T002 identified updated — each change there is evidence a finding closed, not a regression.
- [X] T051 [P] Run `make check-specs` and `./gradlew check` and confirm both pass with
  `scripts/spec-drift/baseline.json` smaller than it started (SC-006).
- [X] T052 Verify SC-002 the only way it can be verified: have someone who has not read the server
  implement the confirmation retry from `specs/007-mcp-billing-server/contracts/mcp-protocol.md` alone
  and apply a fee change on the first attempt. If nobody is available, record that it is unverified
  rather than marking it done — the whole finding is that the contract reads convincingly and is wrong.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies. T001 first (it needs the stack); T002 and T003 parallel.
- **Foundational (Phase 2)**: needs Phase 1. **Blocks every user story** — T004's test measures F-001
  and T007's boundary is what makes any assertion about a failure meaningful.
- **US1 (Phase 3)** and **US2 (Phase 4)**: need Phase 2 and nothing else. Independent of each other.
- **US3 (Phase 5)**: needs T004 and T005. Independent of US1 and US2.
- **US4 (Phase 6)**: needs only Phase 1. Could be done at any point; it is P2 because it cannot be
  verified mechanically, not because it is blocked.
- **US5 (Phase 7)**: needs Phase 2. Independent of the rest.
- **Polish (Phase 8)**: T046 and T047 need whichever stories are being delivered; T049–T051 need all.

### Within Each Story

Tests first and failing, then the implementation, then the acceptance scenarios by hand. For the
stories that touch a document as well as code, the document moves in the same task group as the
behaviour it describes — separating them is how the two drifted in the first place.

### Parallel Opportunities

- Phase 1: T002 and T003 together.
- Phase 2: T006 and T008 alongside T004.
- Each story's test tasks are `[P]` — separate files.
- US1, US2, US4 and US5 can be worked concurrently by four people once Phase 2 is done. US3 shares
  `claims`-adjacent files with nobody and is also independent.

---

## Parallel Example: User Story 1

```bash
# The five failing tests first, together:
Task: "empty items serialised in legacy-billing-api/.../BillingRunControllerTest.java"
Task: "absent items read as empty in mcp-server/.../BillingRunToolsTest.java"
Task: "empty date range returns 200 in .../topology/EmptyResultTopologyTest.java"
Task: "refusal indistinguishable from not-found, same file"
Task: "cross-firm search unchanged, same file"
```

---

## Implementation Strategy

### MVP First (Setup + Foundational + User Story 1)

1. Phase 1 — pin what is true today.
2. Phase 2 — the guard and the boundary. **Blocks everything.**
3. Phase 3 — the defect.
4. **Stop and validate**: search a date range with nothing in it and get an empty result instead of
   HTTP 500. That alone is the only defect among the seven, and it is reachable by any model exploring
   the data.

### Incremental Delivery

1. Setup + Foundational → failures are well-formed, and F-001's extent is a number.
2. + US1 → **MVP**: no query returns 500.
3. + US2 → a contract-following client works.
4. + US3 → one declaration, carrying what the contract carried, with a test that keeps it so.
5. + US4 → the reading guide leads somewhere.
6. + US5 → results carry what clients were reconstructing.
7. + Phase 8 → the record closed, the baseline smaller, and what this feature found that the findings
   did not name, written down.

---

## Notes

- `[P]` means a different file and no dependency on an incomplete task.
- **No dependency is added or upgraded.** Every finding is a disagreement between what exists and what
  was promised; if something seems to need a new library, that is a sign the task has been misread.
- **Feature 007's `plan.md` and `research.md` are not edited.** Only claims about present behaviour
  are corrected, and each correction says in place that it was one.
- Feature 008's console suite pins some of today's behaviour deliberately. A test there failing after a
  fix is evidence, not a regression — T002 identifies which ones in advance so the two are never
  confused.
- Commit after each task or logical group; stop at any checkpoint to demonstrate the story.
