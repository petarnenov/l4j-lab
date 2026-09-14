---

description: "Task list for 009-spec-drift-check"
---

# Tasks: Hold the documents to the code

**Input**: Design documents from `/specs/009-spec-drift-check/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/)

**Tests**: Included, and not optional. Every behaviour in this feature is deterministic — a file
exists or it does not — so Constitution Principle IV applies without exception. No model is
involved, so there is no live suite and none is needed. Each test task is written before the
implementation it names and must fail first.

**Organization**: by user story, one per claim kind, so each can be delivered and demonstrated alone.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: parallelisable — different files, no dependency on an incomplete task
- **[Story]**: US1–US4, mapping to the user stories in `spec.md`
- Exact file paths are given in every task

## Path Conventions

New code lives in `scripts/spec-drift/` at the repository root, because the tool is about the whole
repository and nothing else in it is (plan, Structure Decision). Its tests are `*.test.mjs` beside
it, run by Node's built-in runner.

**No file under `backend/`, `frontend/src/`, `mcp-server/`, `legacy-billing-api/` or `token-issuer/`
is touched.** This feature reads the repository; it does not participate in it.

**No specification is edited.** Drift this check finds in features 001–008 is recorded in the
baseline or in [findings.md](./findings.md), never fixed here — the spec's Out of Scope forbids
editing the record of what a past feature decided.

---

## Phase 1: Setup

**Purpose**: prove the wiring before there is anything to wire. Each task here is verifiable on its
own, and none of them checks a claim.

- [X] T001 Create `build.gradle.kts` at the repository root — the project has none today. Apply the
  `base` plugin for a `check` lifecycle, and register two `Exec` tasks: `specDrift`
  (`node scripts/spec-drift/check.mjs`) and `specDriftTest` (`node --test scripts/spec-drift/`).
  Wire **`specDriftTest` only** into the root `check` for now; `specDrift` joins it in T043, once
  every claim kind exists and the baseline records what this repository actually contains. Wiring the
  check in before then would leave the repository's build red through all four user stories — it
  would be finding real drift with nothing yet able to record it. FR-008 is satisfied at T043, not
  here. Both `dependsOn(":frontend:checkNode")`, reusing the guard that
  already reads `frontend/.nvmrc` rather than writing a second one. `outputs.upToDateWhen { false }`
  on `specDrift`: its inputs are every document in the repository, which Gradle cannot track usefully.
- [X] T002 [P] Add a `check-specs` target to `Makefile` under the Verification group, with a `##`
  help description so it appears in `make help`, running `$(GRADLEW) specDrift`.
- [X] T003 [P] Create `scripts/spec-drift/check.mjs` as a walking skeleton: exits `0`, prints the
  "not checked" line from [contracts/report-format.md](./contracts/report-format.md) and zero counts.
  This makes T001 and T002 provable before any extraction exists (FR-013's boundary statement is the
  first thing that works, which is the right order for a tool whose value depends on it).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: scope, scanning, excuses and output — everything the four claim kinds sit on.

**⚠️ CRITICAL**: no user story can begin until this phase is complete.

### Tests (write first, confirm they fail)

- [X] T004 [P] Write failing tests in `scripts/spec-drift/features.test.mjs` for the scope rule
  (FR-007, research R-001), against this repository's real `specs/`: a feature declaring itself
  implemented is in scope; one declaring `Draft` is not; **001 and 008 are in scope despite each
  carrying one open task**, which is the case that rules out counting checkboxes; and `README.md`
  belongs to no feature and is always in scope.
- [X] T005 [P] Write failing tests in `scripts/spec-drift/scanner.test.mjs`: 1-based line numbers,
  fenced-block tracking across nested and unterminated fences, inline-code extraction, and that a
  markdown link inside a fence is not seen while a file-tree line inside one is. That distinction is
  the grammar's, and it exists because a first draft of this check flagged its own examples.
- [X] T006 [P] Write failing tests in `scripts/spec-drift/exemptions.test.mjs`: an inline
  `<!-- drift-ok: reason -->` exempts the claim on its line and no other; a marker with no reason is
  an error, not an exemption; a marker on a line carrying no claim is `STALE` (FR-014).
- [X] T007 [P] Write failing tests in `scripts/spec-drift/baseline.test.mjs`: entries match by
  document and subject rather than by line, so reformatting cannot move one onto a different claim;
  an entry whose claim now holds is `STALE` and fails; every entry requires a reason (FR-015,
  research R-005).
- [X] T008 [P] Write failing tests in `scripts/spec-drift/report.test.mjs` for the whole of
  [contracts/report-format.md](./contracts/report-format.md): per-kind counts printed on every run
  including a passing one (FR-012), the "not checked" line, the `BROKEN` shape with document, line
  and claim, the `exempt` and `baselined` sections printed even when passing, and exit codes `0`, `1`
  and `2` distinguished (SC-005: a run that checked nothing must not read as a run that found
  nothing) — `2` meaning the check could not run, which must never read as "found nothing".

### Implementation

- [X] T009 [P] Implement `scripts/spec-drift/features.mjs` to make T004 pass: read each
  `specs/*/spec.md` `**Status**:` line, count `tasks.md` checkboxes, and expose the record from
  [data-model.md](./data-model.md).
- [X] T010 [P] Implement `scripts/spec-drift/scanner.mjs` to make T005 pass: one pass per document
  yielding `{line, text, inFence}`, which every extractor consumes. Nothing else may read a file.
- [X] T011 [P] Implement `scripts/spec-drift/exemptions.mjs` to make T006 pass.
- [X] T012 [P] Implement `scripts/spec-drift/baseline.mjs` and create
  `scripts/spec-drift/baseline.json` as an empty list — entries arrive in T024, T030, T033a and T036a
  as each claim kind starts finding real drift, never from guesses.
- [X] T013 Implement `scripts/spec-drift/report.mjs` to make T008 pass, with the list of unchecked
  claim kinds declared once as a named constant beside the patterns in `scripts/spec-drift/claims.mjs`
  — FR-013 asks for the boundary in the source as well as in the output, and a second hand-written
  copy in the report would be the very drift this feature exists to catch.
- [X] T014 Implement the orchestration in `scripts/spec-drift/check.mjs`: features in scope →
  documents → claims → verdicts → report → exit. No claim kind is registered yet, so a run reports
  zero of each and exits `0`, which the report tests already pin as distinguishable from success.

### The arming switch watches itself

- [X] T015 [P] Write failing tests in `scripts/spec-drift/status.test.mjs` for the `status` claim
  kind ([contracts/claim-grammar.md](./contracts/claim-grammar.md)): an unknown status value fails; a
  feature declaring itself implemented without a `tasks.md` fails; a task count stated in the status
  line that the file contradicts fails; and a feature whose tasks are complete while its status still
  says `Draft` is reported.
- [X] T016 Implement the `status` kind in `scripts/spec-drift/claims.mjs` and
  `scripts/spec-drift/verify.mjs` to make T015 pass. It belongs here rather than in a user story
  because it is the check that arms every other check, and research R-001 shows the field is
  currently maintained by nobody: three implemented features still declare themselves drafts.

**Checkpoint**: the check runs, reports honestly that it checked nothing, and knows which features it
would hold to their code. No claim is verified yet.

---

## Phase 3: User Story 1 - The build says which document stopped being true (Priority: P1) 🎯 MVP

**Goal**: rename a file an implemented feature's plan names, and the build says so.

**Independent Test**: rename `frontend/src/mcp/transport.ts`, run `make check-specs`, read the
failure; put it back and watch it pass.

### Tests for User Story 1 (write first, confirm they fail)

- [X] T017 [P] [US1] Failing tests in `scripts/spec-drift/claims.paths.test.mjs` for path extraction
  per the grammar: inline-code paths, filenames inside a fenced file tree resolved against the tree's
  root, and the exclusions — bare words, paths inside URLs, prose that merely mentions a filename
  without marking it as code (FR-006), and any path with a placeholder segment (`<name>`, `{id}`, `…`). Driven by this repository's real documents, so an extraction that stops
  matching fails here.
- [X] T018 [P] [US1] Failing tests in `scripts/spec-drift/verify.paths.test.mjs`: a named path that
  exists `holds`; one that does not is `broken` and carries its document and line (FR-001, FR-011).
- [X] T019 [P] [US1] Failing tests appended to `scripts/spec-drift/verify.paths.test.mjs` for the
  other direction (FR-002): a file directly beneath a directory whose tree line carries `[complete]`,
  named by no document of that feature, is `broken`; a file beneath an unmarked directory is not.
  The case this is built from is real — feature 008 delivered
  `frontend/src/mcp/components/ExchangeLog.tsx` and its plan's tree never mentioned it — and so is the
  reason for the marker: inferring completeness from a named directory demanded that same plan account
  for `node_modules` and `package-lock.json` (research R-009).
- [X] T020 [P] [US1] Failing test in `scripts/spec-drift/verify.paths.test.mjs` that a feature which is
  **not** implemented is not held to its paths (US1-5, FR-007): a plan written before its code is supposed to name files that do not exist.

### Implementation for User Story 1

- [X] T021 [US1] Implement path extraction in `scripts/spec-drift/claims.mjs` to make T017 pass.
- [X] T022 [US1] Implement path verification in `scripts/spec-drift/verify.mjs` to make T018 and T020
  pass.
- [X] T023 [US1] Implement marked-directory completeness in `scripts/spec-drift/verify.mjs` to make
  T019 pass, bounded as the grammar bounds it: only `[complete]` directories are inventories, and
  because no existing feature carries the marker, FR-002 applies to nothing written before this check.
  The report must say so rather than let a reader assume wider cover.
- [X] T024 [US1] Register the kind in `scripts/spec-drift/check.mjs`, confirm the five acceptance
  scenarios by hand against the real repository, and record whatever path drift it finds in
  `scripts/spec-drift/baseline.json` with a reason each. Recording as you go means every story ends
  with an honest number rather than deferring one large reckoning to the end (C5).

**Checkpoint**: User Story 1 is demonstrable on its own. This is the MVP, and it is the story that
changes the economics — until a document can fail, keeping it true depends on someone remembering.

---

## Phase 4: User Story 2 - Every command a document names exists (Priority: P1)

**Goal**: a quickstart cannot tell a newcomer to run something the project does not offer.

**Independent Test**: rename `make check-specs`, run the check, read the failure.

### Tests for User Story 2 (write first, confirm they fail)

- [X] T025 [P] [US2] Failing tests in `scripts/spec-drift/claims.commands.test.mjs`: `make <target>`,
  `npm run <script>` and `./gradlew <task>` are extracted from fenced blocks and inline code (FR-003);
  **nothing else is** — `docker`, `curl`, `git`, `node` and `jq` are the reader's machine, not this
  project's promise (research R-004).
- [X] T026 [P] [US2] Failing tests in `scripts/spec-drift/verify.commands.test.mjs`: targets are read
  from `Makefile`, scripts from every `package.json`, tasks from every `build.gradle.kts` **as text**;
  a renamed command is `broken`; and nothing is ever executed (FR-010, US2-3).
- [X] T027 [P] [US2] Failing test in `scripts/spec-drift/verify.commands.test.mjs` that a Gradle task
  registered dynamically is missed rather than
  falsely reported, which the grammar states outright — a check that overclaims its own reach is
  worse than one with a stated limit.

### Implementation for User Story 2

- [X] T028 [US2] Implement command extraction in `scripts/spec-drift/claims.mjs`.
- [X] T029 [US2] Implement command verification in `scripts/spec-drift/verify.mjs`, reading the three
  manifests as text. No process is started, not even to list tasks: `./gradlew tasks` would configure
  every project, which is a side effect for a question about existence.
- [X] T030 [US2] Register the kind in `scripts/spec-drift/check.mjs`, confirm US2's three scenarios,
  and record any command drift found in `scripts/spec-drift/baseline.json`.

**Checkpoint**: the two P1 stories both work, and the drift with the sharpest edge for a reader —
a command that no longer exists — now fails the build.

---

## Phase 5: User Story 3 - Every requirement is accounted for (Priority: P2)

**Goal**: no requirement quietly dropped, no work nobody asked for.

**Independent Test**: delete the only task citing a requirement and run the check.

### Tests for User Story 3 (write first, confirm they fail)

- [X] T031 [P] [US3] Failing tests in `scripts/spec-drift/claims.requirements.test.mjs`: `**FR-nnn**`
  and `**SC-nnn**` are extracted from `spec.md` only, in the bold form the template uses, and a
  mention of an identifier in any other document is not a separate claim.
- [X] T032 [P] [US3] Failing tests in `scripts/spec-drift/verify.requirements.test.mjs`: an
  identifier cited by no task in that feature's `tasks.md` is `broken` (FR-004); a task citing no
  requirement outside a setup or maintenance phase is reported (US3-2); and a requirement recorded as
  deliberately unmet is not a failure (US3-3) — feature 008's `SC-001` is the live example, left open
  because it needs a person who has never seen the page.

### Implementation for User Story 3

- [X] T033 [US3] Implement requirement extraction in `scripts/spec-drift/claims.mjs` to make T031 pass.
- [X] T033a [US3] Implement requirement verification in `scripts/spec-drift/verify.mjs` to make T032
  pass, register the kind in `scripts/spec-drift/check.mjs`, and record any drift found in
  `scripts/spec-drift/baseline.json`.

**Checkpoint**: three stories done. This one catches a different failure from the others — not a
document that went stale, but one that was never finished.

---

## Phase 6: User Story 4 - A reference that points nowhere (Priority: P3)

**Goal**: every link between these documents resolves.

**Independent Test**: point a link at a moved file and run the check.

### Tests for User Story 4 (write first, confirm they fail)

- [X] T034 [P] [US4] Failing tests in `scripts/spec-drift/claims.references.test.mjs` (FR-005): relative
  markdown links outside a fence are claims; `http:`, `https:`, `mailto:` and same-file anchors are
  not; and a link inside a fence is not, because rendered markdown makes it literal text rather than
  something a reader can follow.
- [X] T035 [P] [US4] Failing tests in `scripts/spec-drift/verify.references.test.mjs`: a link to a
  moved file is `broken` naming both ends (US4-1); nothing external is fetched (US4-2, FR-009).

### Implementation for User Story 4

- [X] T036 [US4] Implement reference extraction in `scripts/spec-drift/claims.mjs` to make T034 pass.
- [X] T036a [US4] Implement reference verification in `scripts/spec-drift/verify.mjs` to make T035
  pass, register the kind in `scripts/spec-drift/check.mjs`, and record any drift found in
  `scripts/spec-drift/baseline.json`.

**Checkpoint**: all four claim kinds are live.

---

## Phase 7: Polish & the first honest run

- [X] T037 Run `make check-specs` across the whole repository and record the total in
  `specs/009-spec-drift-check/findings.md` (SC-002: someone must be able to learn which documents
  broke without reading any of them). Each story recorded its own drift as it went, so this is the
  reckoning rather than the discovery — and the number is this feature's first real deliverable,
  whatever it turns out to be.
- [X] T038 Review every entry accumulated in `scripts/spec-drift/baseline.json` during the four
  stories: each has a reason and a date, none is a blanket exclusion, and anything fixable *within
  this feature's scope* was fixed rather than recorded. Drift inside a past feature's record stays
  baselined — editing that record is out of scope, and that is what FR-016 means by checking every
  implemented feature without repairing the ones that predate the check.
- [X] T039 Append to [findings.md](./findings.md) whatever T037 surfaces beyond the three already
  recorded, so the drift is findable from the specification and not only from a build log.
- [X] T040 [P] Verify SC-001 against the real corpus, driving `scripts/spec-drift/check.mjs` by hand: reintroduce the three structural drifts feature 008 hit — the
  test file named for a source file that did not exist, three delivered modules absent from a plan's
  tree, and three requirements cited by no task — and confirm the check catches each. A check for a problem that has happened twice should be measured against what
  actually happened, not against invented cases.
- [X] T040a [P] Verify SC-001a using `scripts/spec-drift/report.mjs`'s output: the four items from
  feature 008 this check cannot catch — two counts written in prose, a root-level file no file tree
  covers, and a build script added without being documented — pass silently, and the "not checked"
  line is what tells a reader why. A check whose limits are discoverable only by experiment has not
  stated them.
- [X] T041 [P] Verify SC-004 and FR-006 by reading `scripts/spec-drift/report.mjs`'s output cold, and
  by changing a document's prose to something false and confirming the check passes: someone who has not built this should be
  able to state what it does not cover from the report alone.
- [X] T042 [P] Confirm SC-003 and SC-006 by timing `./gradlew specDrift` (declared in the root
  `build.gradle.kts`) and running it with the network disabled.
- [X] T043 Wire `specDrift` into the root `check` in `build.gradle.kts`. This is where FR-008 is
  actually satisfied — from here the check is part of ordinary verification rather than a command to
  remember. Deferred to now on purpose: before a baseline existed it would have left the build red
  through every user story.
- [X] T043a [P] Add a short section to `README.md` naming `make check-specs`, what it checks, and —
  with equal prominence — what it does not.
- [X] T044 Re-run `./gradlew check` from the root `build.gradle.kts` lifecycle and confirm nothing
  else regressed, including the frontend's 266 deterministic tests.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies. T001 first; T002 and T003 parallel after it.
- **Foundational (Phase 2)**: needs Phase 1. **Blocks every user story.**
- **User Stories (Phases 3–6)**: each needs Phase 2 and nothing else. In priority order
  US1 → US2 → US3 → US4, or in parallel across people.
- **Polish (Phase 7)**: T037 needs at least US1 and US2 to be worth running; T040 needs all four.

### User Story Dependencies

Each story adds one claim kind to a registry. They share `claims.mjs` and `verify.mjs`, so two people
working at once will touch the same two files — the split is by function within them, and the tests
are in separate files precisely so the work can be reviewed apart.

### Within Each Story

Tests first and failing, then extraction, then verification, then registration, then the acceptance
scenarios by hand.

### Parallel Opportunities

- Phase 2: all five test tasks (T004–T008) together; then T009–T012 together.
- Each story's test tasks are `[P]` — separate files.
- US3 and US4 can be built concurrently once US1 and US2 are done.

---

## Parallel Example: Phase 2

```bash
# The five failing tests first, together:
Task: "scope rule in scripts/spec-drift/features.test.mjs"
Task: "fence and line tracking in scripts/spec-drift/scanner.test.mjs"
Task: "inline exemptions in scripts/spec-drift/exemptions.test.mjs"
Task: "baseline matching and staleness in scripts/spec-drift/baseline.test.mjs"
Task: "the output contract in scripts/spec-drift/report.test.mjs"
```

---

## Implementation Strategy

### MVP First (Setup + Foundational + User Story 1)

1. Phase 1 — the wiring, provable before any logic exists.
2. Phase 2 — scope, scanning, excuses, output. **Blocks everything.**
3. Phase 3 — paths.
4. **Stop and validate**: rename a file a plan names and watch the build fail with the document, the
   line and the path. That alone changes how this repository behaves.

### Incremental Delivery

1. Setup + Foundational → the check runs and reports honestly that it checked nothing.
2. + US1 → **MVP**: paths.
3. + US2 → commands, the drift a newcomer feels first.
4. + US3 → requirement coverage.
5. + US4 → references.
6. + Phase 7 → the baseline, and the first honest count of what this repository's documents claim
   that is no longer true.

---

## Notes

- `[P]` means a different file and no dependency on an incomplete task.
- **No dependency is added by any task here.** Node's built-ins and its built-in test runner are the
  whole toolchain; if something seems to need a package, re-read research R-002 first.
- The check never executes what it checks for, never opens a network connection, and never writes to
  a file it did not create. Any task that seems to need one of those is the wrong task.
- No specification is edited. Drift found in features 001–008 goes to the baseline or to
  `findings.md`; the spec's Out of Scope is what makes that the rule rather than a preference.
- Commit after each task or logical group; stop at any checkpoint to demonstrate the story.
