---

description: "Task list for Monorepo Integration"
---

# Tasks: Monorepo Integration

**Input**: Design documents from `/specs/003-monorepo-integration/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/root-commands.md,
contracts/ci-workflows.md, quickstart.md

**Tests**: The feature adds one piece of frontend logic, `frontend/scripts/api-contract.mjs`. Principle
IV requires frontend logic to be tested with Vitest, test first, so US2 writes its tests before the
script. Build wiring and CI are verified by the quickstart scenarios, each run as its own task.

**Organization**: Tasks are grouped by user story. Paths are relative to the repository root.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1 to US5, from spec.md

---

## Phase 1: Setup

**Purpose**: Record the baseline and declare the Node prerequisite

- [X] T001 Record the baseline: run `./gradlew :backend:test` and `npm test` in `frontend/`, confirm both pass, and write the two reported test counts (expected 112 and 120) as a note under this task in `specs/003-monorepo-integration/tasks.md` (SC-007)
  - **Baseline 2026-09-12**: backend 120 tests, 0 skipped, 0 failed (`./gradlew :backend:test --rerun`, Docker running, no credential); frontend 12 files, 120 tests passed. The README's and the plan's "112" is stale; 120 is the figure SC-007 compares against. `npm run lint` passes.
- [X] T002 [P] Create `frontend/.nvmrc` containing `24` and add `"engines": { "node": ">=24" }` to `frontend/package.json`, leaving every other field unchanged (R-005)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Give the API description its fixed, version-free path. Every story reads from it.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T003 In `backend/build.gradle.kts`, add `"-Amicronaut.openapi.filename=openapi"` to the existing `compilerArgs.addAll(...)` inside `tasks.withType<JavaCompile>().configureEach`, with a one-line comment in the file's existing style citing R-001 and saying why the version must not appear in the file name
- [X] T004 Verify R-001 on disk: run `./gradlew :backend:clean :backend:classes` and confirm `backend/build/classes/java/main/META-INF/swagger/openapi.yml` exists and no `financial-agent-chain-0.1.yml` does; then add a temporary field to a record in `backend/src/main/java/dev/l4jlab/chain/web/`, run `./gradlew :backend:classes` without clean, confirm the field appears in `openapi.yml`, and revert the field. Append the outcome under a "Verified during implementation" line in R-001 of `specs/003-monorepo-integration/research.md`. If the incremental compile left the file stale, record that and make the later `checkApi` task depend on a non-incremental compile as R-001 describes
- [X] T005 Point the existing `generate:api` and `check:api` scripts in `frontend/package.json` at `../backend/build/classes/java/main/META-INF/swagger/openapi.yml` instead of the versioned file name, then run `npm run check:api` in `frontend/` and confirm it passes with `frontend/src/api/schema.d.ts` unchanged
  - **Note**: this surfaced false drift from incremental annotation processing. Resolved by `incremental(false)` in `backend/build.gradle.kts`; see R-001 "But incremental processing corrupts the description". `check:api` now matches with `schema.d.ts` unchanged.

**Checkpoint**: The description has a stable path and the frontend scripts use it.

---

## Phase 3: User Story 1 - One command verifies the whole project (Priority: P1) 🎯 MVP

**Goal**: `./gradlew check` at the root runs the backend suite, the frontend suite, and the contract
check, in order, from a clean tree, with no credential.

**Independent Test**: quickstart Scenario 1 succeeds on a clean copy; Scenario 2 fails at
`:frontend:checkApi` and passes after regeneration.

- [X] T006 [US1] In `settings.gradle.kts`, change `include("backend")` to `include("backend", "frontend")`. Leave `rootProject.name` unchanged; US3 changes it
- [X] T007 [US1] Create `frontend/build.gradle.kts` applying only the `base` plugin, with a header comment citing R-004 and stating that npm remains the frontend's build tool and Gradle only invokes it. Define one value holding the npm executable name, `npm.cmd` when `System.getProperty("os.name")` starts with `Windows`, otherwise `npm`
- [X] T008 [US1] In `frontend/build.gradle.kts`, register `checkNode`: read the required major version from `frontend/.nvmrc`, run `node --version`, and fail with `GradleException` using the messages in `specs/003-monorepo-integration/contracts/root-commands.md` when `node` cannot be started (must name "Node.js", the required version, and `frontend/.nvmrc`) or when the major version differs (must name found and required versions)
- [X] T009 [US1] In `frontend/build.gradle.kts`, register `npmCi` as an `Exec` running `npm ci` in the project directory, `dependsOn("checkNode")`, with `inputs.file("package-lock.json")` and `outputs.file("node_modules/.package-lock.json")` so a second run with an unchanged lock file is up to date
- [X] T010 [US1] In `frontend/build.gradle.kts`, register `test` as an `Exec` running `npm test`, `dependsOn("npmCi")`, in group `verification`
- [X] T011 [US1] In `frontend/build.gradle.kts`, register `checkApi` as an `Exec` running `npm run check:api`, `dependsOn("npmCi", ":backend:classes")`, in group `verification`, with a comment citing FR-006
- [X] T012 [US1] In `frontend/build.gradle.kts`, make the `base` plugin's `check` task `dependsOn("test", "checkApi")`
- [X] T013 [US1] Run `./gradlew check --dry-run` from the root and confirm the plan contains `:backend:test`, `:frontend:checkNode`, `:frontend:npmCi`, `:frontend:test`, `:backend:classes`, `:frontend:checkApi`, and does not contain `:backend:liveTest` (FR-008)
- [X] T014 [US1] Run quickstart Scenario 1 on a clean copy: copy the repository to a temporary directory excluding `node_modules`, `build`, `.gradle`, `dist`, and `.env`, unset `OLLAMA_API_KEY`, run `./gradlew check`, and confirm success with test counts equal to T001's baseline
  - **Result**: clean copy in the session scratchpad, `./gradlew check` BUILD SUCCESSFUL in 26s; backend 120 tests 0 skipped 0 failed, frontend 120 passed. A second `:frontend:npmCi` was UP-TO-DATE.
- [X] T015 [US1] Run quickstart Scenario 2 in the working copy: add a field to a web record, confirm `./gradlew check` fails at `:frontend:checkApi`, run `npm run generate:api` in `frontend/`, confirm `./gradlew check` passes, then revert the field and regenerate so `frontend/src/api/schema.d.ts` is back to its original content
  - **Result**: failed at `:frontend:checkApi` ("files differ"); after `generate:api` the check passed; after reverting and regenerating, `schema.d.ts` is byte-identical to the original.

**Checkpoint**: MVP. The whole project verifies from one command.

---

## Phase 4: User Story 2 - The contract link survives a version change (Priority: P2)

**Goal**: The contract scripts explain a missing description and a stale file, and keep working
across a version change.

**Independent Test**: quickstart Scenario 4 prints the named message; Scenario 3 step 2 passes after a
version bump with no script edit.

- [X] T016 [P] [US2] Write failing Vitest tests in `frontend/scripts/api-contract.test.mjs` for functions exported by `frontend/scripts/api-contract.mjs`: `descriptionPath()` returns the path from R-001 resolved from the frontend directory; `missingDescriptionMessage(path)` names the path and `./gradlew :backend:classes`; `compareGenerated(expected, actual)` returns ok for identical content, and for differing content returns a failure message naming `src/api/schema.d.ts` and `npm run generate:api`. Run `npm test` in `frontend/` and confirm these tests fail because the module does not exist
  - **Result**: red with "Failed to resolve import ./api-contract.mjs". The file runs under the suite's jsdom environment, because `src/test/setup.ts` needs `window`; Node built-ins work there. 6 tests.
- [X] T017 [US2] Implement `frontend/scripts/api-contract.mjs` (R-006): export the three functions from T016; when run directly with argument `generate` or `check`, exit 1 with `missingDescriptionMessage` if the description is absent, run `node_modules/.bin/openapi-typescript` (resolving the `.cmd` shim on Windows) to `src/api/schema.d.ts` for `generate` or to `node_modules/.cache/schema.check.d.ts` for `check`, and in `check` mode compare byte for byte using `compareGenerated`, printing its message and exiting 1 on difference. Use only Node built-ins. Run `npm test` in `frontend/` and confirm T016's tests pass
  - **Result**: 6 of 6 pass. The CLI is started as `node node_modules/openapi-typescript/bin/cli.js` with `process.execPath`, which sidesteps the Windows `.cmd` shim entirely.
- [X] T018 [US2] Change `generate:api` to `node scripts/api-contract.mjs generate` and `check:api` to `node scripts/api-contract.mjs check` in `frontend/package.json`, then run `npm run check:api` and `npm run lint` in `frontend/` and confirm both pass
  - **Result**: both pass. `eslint.config.js` gained a `scripts/**/*.mjs` block declaring `console` as a Node global; ignores gained `build/` and `.gradle/` (also in `.prettierignore`). The two new files are Prettier-formatted; 17 pre-existing files under `src/` were already unformatted and were left alone.
- [X] T019 [US2] Run quickstart Scenario 4: delete `backend/build`, run `npm run check:api` in `frontend/`, confirm the non-zero exit and message from `contracts/root-commands.md`, then run `./gradlew :frontend:checkApi` and confirm it passes because `:backend:classes` runs first
  - **Result**: after `:backend:clean`, `npm run check:api` exited 1 naming the path and `./gradlew :backend:classes`; `./gradlew :frontend:checkApi` compiled the backend and passed.
- [X] T020 [US2] Run quickstart Scenario 3 steps 1 and 2 only: set `version` in `backend/build.gradle.kts` to `0.2.0`, run `./gradlew :frontend:checkApi`, confirm it passes with no script edit, then revert the version
  - **Result**: passed with version `0.2.0`; the only file in `META-INF/swagger/` was still `openapi.yml`. Reverted.

**Checkpoint**: The contract link is independent of the version and self-explaining.

---

## Phase 5: User Story 3 - One name and one version (Priority: P3)

**Goal**: One base name and one declared version across the build, the API description, and the
frontend manifest.

**Independent Test**: quickstart Scenario 5 shows one name and one version; Scenario 3 step 3 fails on
a mismatch naming both values.

**Depends on**: US1 (`frontend/build.gradle.kts` exists).

- [X] T021 [US3] In `settings.gradle.kts`, change `rootProject.name` from `l4j-lab` to `financial-agent-chain` with a comment citing R-003
- [X] T022 [US3] In `backend/build.gradle.kts`, change `version = "0.1"` to `version = "0.1.0"` and add `"-Amicronaut.openapi.expand.api.version=${project.version}"` to the same `compilerArgs.addAll(...)` as T003, with a comment citing R-002
- [X] T023 [US3] In `backend/src/main/java/dev/l4jlab/chain/Application.java`, change the `@Info` version from `"0.1"` to `"${api.version}"`. Run `./gradlew :backend:classes` and confirm `info.version` in `openapi.yml` is `0.1.0`. If the placeholder is emitted literally, record the finding in R-002 of `specs/003-monorepo-integration/research.md`, try the processor's documented alternative placement for expand properties, and stop for a maintainer decision rather than hard-coding the version a second time
  - **Result**: the placeholder expanded; `info.version: 0.1.0` in `openapi.yml`. Bumping to `0.2.0` produced `version: 0.2.0`.
- [X] T024 [US3] Run `npm run check:api` in `frontend/`; if `src/api/schema.d.ts` changed because of the version or file name, run `npm run generate:api` and confirm the only differences are metadata, not type shapes
  - **Result**: no change. `schema.d.ts` still matches (sha1 `c3ef8e21…`); openapi-typescript does not emit `info`.
- [X] T025 [US3] In `frontend/build.gradle.kts`, register `checkVersion` in group `verification`: parse `package.json` with `groovy.json.JsonSlurper`, compare its `version` with `project(":backend").version`, and fail with the message in `contracts/root-commands.md` naming both versions and both files. Add `checkVersion` to `check`'s `dependsOn`
  - **Result**: added, plus `evaluationDependsOn(":backend")` so reading the backend's version never depends on project evaluation order.
- [X] T026 [US3] Run quickstart Scenario 3 step 3 and Scenario 5: set `frontend/package.json` version to `0.2.0`, confirm `./gradlew :frontend:checkVersion` fails naming both values, revert, confirm it passes; then compare the name and version across `settings.gradle.kts`, `openapi.yml`, and `frontend/package.json`
  - **Result**: mismatch failed naming `0.2.0`, `0.1.0` and both files; revert passed. Identity: `financial-agent-chain` / title `financial-agent-chain` / package `financial-agent-chain-frontend`; version `0.1.0` in all three.

**Checkpoint**: Identity is consistent and guarded.

---

## Phase 6: User Story 4 - CI runs only what a change touches (Priority: P3)

**Goal**: Three GitHub Actions workflows with path filters from `contracts/ci-workflows.md`.

**Independent Test**: quickstart Scenario 7, once a GitHub remote exists.

**Depends on**: US1 and US3 (the contract workflow runs `:frontend:checkApi` and `:frontend:checkVersion`).

- [X] T027 [P] [US4] Create `.github/workflows/backend.yml`: triggers `push` and `pull_request` with paths `backend/**` plus the shared paths from `contracts/ci-workflows.md`; one job on `ubuntu-latest` with `actions/checkout`, `actions/setup-java` (`distribution: temurin`, `java-version: 25`), `gradle/actions/setup-gradle`, then `./gradlew :backend:check`. No `env` or `secrets` reference to `OLLAMA_API_KEY`
- [X] T028 [P] [US4] Create `.github/workflows/frontend.yml`: triggers with paths `frontend/**` plus the shared paths; one job with `actions/checkout`, `actions/setup-node` (`node-version-file: frontend/.nvmrc`, `cache: npm`, `cache-dependency-path: frontend/package-lock.json`), then `npm ci` and `npm test` with `working-directory: frontend`
- [X] T029 [P] [US4] Create `.github/workflows/contract.yml`: triggers with paths `backend/**`, `frontend/**`, plus the shared paths; one job with checkout, setup-java as in T027, setup-node as in T028, setup-gradle, then `./gradlew :frontend:checkApi :frontend:checkVersion`
- [X] T030 [US4] Review the three workflow files against the selection table in `contracts/ci-workflows.md` row by row, and run `actionlint` on `.github/workflows/` if it is installed; record in this task's note whether Scenario 7 was run or is deferred until a GitHub remote exists
  - **Result**: action majors looked up with `gh api …/releases/latest` on 2026-09-12: `actions/checkout@v7`, `actions/setup-java@v6`, `actions/setup-node@v7`, `gradle/actions/setup-gradle@v6`. `actionlint` is not installed. Paths are written out in full rather than as YAML anchors. A Ruby script parsed all three files, confirmed `push` and `pull_request` share one path list and that no file mentions `OLLAMA` or `secrets.`, and matched six sample paths against the selection table in `contracts/ci-workflows.md`: 6 of 6 as expected. **Scenario 7 is deferred**: the repository has no GitHub remote yet.

**Checkpoint**: CI definitions exist and match the contract.

---

## Phase 7: User Story 5 - The README teaches the root-level workflow (Priority: P3)

**Goal**: A newcomer verifies the project and fixes contract drift from the README alone.

**Independent Test**: quickstart Scenario 8.

**Depends on**: US1, US2, US3.

- [X] T031 [US5] In `README.md`, update the "Run it" section to list Node.js 24 (from `frontend/.nvmrc`) as a prerequisite beside Docker, and replace the "Test it" code block so `./gradlew check` comes first, commented as running both halves and the contract check with no credential, followed by the existing per-half commands and `./gradlew :backend:liveTest` as the separate deliberate step
- [X] T032 [US5] In `README.md`, update the "Contracts" section so the drift check command is `./gradlew :frontend:checkApi` (or `./gradlew check`), and the recovery instruction is `cd frontend && npm run generate:api`, keeping the existing "never edit `schema.d.ts`" sentence
- [X] T033 [US5] Run quickstart Scenario 8 as a read-through: using only `README.md`, perform Scenario 1 and recover from Scenario 2, and note any step that required opening a build file; fix `README.md` if so
  - **Result**: the README's Test section gives `./gradlew check` for Scenario 1 and `cd frontend && npm run generate:api` for recovery, both already exercised in T014 and T015. No build file is needed. Counts corrected to 120 (backend) and 126 (frontend).

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T034 [P] Run quickstart Scenario 6: run `./gradlew :frontend:checkNode` with a PATH that has no `node` and confirm the message from `contracts/root-commands.md`
  - **Result**: with `PATH=/usr/bin:/bin`: "Node.js was not found on the PATH. The frontend needs Node.js 24, as declared in frontend/.nvmrc…". With `.nvmrc` temporarily set to 22: "Node.js v24.14.1 is on the PATH, but frontend/.nvmrc requires major version 22…". `.nvmrc` restored to 24.
- [X] T035 [P] Confirm `git status --short` shows no new untracked build output under `frontend/` (for example `frontend/build/` or `frontend/.gradle/`); extend `.gitignore` only if something appears
  - **Result**: nothing. The frontend Gradle project writes no `frontend/build/` or `frontend/.gradle/`; every untracked file is source, configuration, or spec. `.gitignore` unchanged.
- [X] T036 Run `./gradlew check` one final time from the root and confirm the backend and frontend test counts equal T001's baseline (SC-007)
  - **Result**: `./gradlew check --rerun-tasks` four times. Backend 120 tests, 0 skipped, 0 failed, every run, equal to the baseline. Frontend 126 = the 120 baseline tests plus the 6 new `api-contract` tests; none lost. Runs 2 to 4 passed. **Run 1 failed 3 frontend tests** (`App.test.tsx` keyboard access, light and dark; `RunDetailPage.test.tsx` summary and indicators), all waiting on `findBy`/`waitFor` with the default 1 s timeout. The same tests pass under `npm test` alone and under `./gradlew :frontend:test --rerun` twice. Read as timing sensitivity under load right after the backend suite, not a wiring fault. Left unchanged as outside this feature's scope (FR-019); reported to the maintainer.
- [X] T037 Update `specs/003-monorepo-integration/spec.md` status to implemented with the date, and confirm `research.md` R-001 and R-002 carry their "Verified during implementation" notes
  - **Result**: spec status updated; R-001 carries both the verification and the incremental-processing finding. R-002's expansion was confirmed in T023.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: none
- **Foundational (Phase 2)**: after Setup. Blocks every story
- **US1 (Phase 3)**: after Foundational
- **US2 (Phase 4)**: after Foundational. T019 uses `:frontend:checkApi`, so run T019 after US1
- **US3 (Phase 5)**: after US1
- **US4 (Phase 6)**: after US1 and US3
- **US5 (Phase 7)**: after US1, US2, US3
- **Polish (Phase 8)**: after all stories

### Story Completion Order

```text
Setup → Foundational ─┬─► US1 ─► US3 ─┬─► US4 ──────┐
                      │                └─► US5 ◄─┐  ├─► Polish
                      └─► US2 ───────────────────┘  │
                          (T019 waits for US1)  US5 ┘
```

### Within Each Story

- US1: T006 → T007 → T008 → T009 → T010, T011 → T012 → T013 → T014 → T015. T007 to T012 all edit `frontend/build.gradle.kts`, so they are sequential
- US2: T016 (red) → T017 (green) → T018 → T019, T020
- US3: T021, T022 → T023 → T024 → T025 → T026
- US4: T027, T028, T029 in parallel → T030
- US5: T031 → T032 → T033

### Parallel Opportunities

- T002 alongside T001
- US2's T016 to T018 alongside US1, since they touch only `frontend/scripts/` and `frontend/package.json` scripts, while US1 touches `settings.gradle.kts` and `frontend/build.gradle.kts`. Coordinate the `package.json` edit in T018 with T002 and T005, which are already done by then
- T027, T028, T029
- T034, T035

## Parallel Example: User Story 4

```bash
Task: "Create .github/workflows/backend.yml per contracts/ci-workflows.md"
Task: "Create .github/workflows/frontend.yml per contracts/ci-workflows.md"
Task: "Create .github/workflows/contract.yml per contracts/ci-workflows.md"
```

## Parallel Example: US1 with US2

```bash
Task: "T006–T012 wire frontend/build.gradle.kts and settings.gradle.kts"
Task: "T016–T018 test-first api-contract.mjs in frontend/scripts/"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 and Phase 2
2. Phase 3 (US1)
3. **Stop and validate** with T014 and T015: one command verifies everything and catches stale types

### Incremental Delivery

1. MVP as above
2. US2: self-explaining contract script, test-first
3. US3: one name, one version
4. US4: CI, deferred validation until a remote exists
5. US5: README, then Polish

Each step leaves `./gradlew check` green and the application untouched (FR-019).
