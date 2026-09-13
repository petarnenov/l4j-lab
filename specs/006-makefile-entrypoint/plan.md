# Implementation Plan: Makefile Entry Point

**Branch**: `006-makefile-entrypoint` (work stays on `main`, as for 003 to 005) | **Date**: 2026-09-13 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/006-makefile-entrypoint/spec.md`

## Summary

Add a root `Makefile` that is a launcher only. Twenty-four named targets in four groups (development,
packaged system, verification, maintenance) plus generated help, each delegating to a command the README
already documents. Four small bash 3.2 helpers in `scripts/make/` carry what a recipe line cannot do
readably:

- running the backend and frontend together with labeled output and joint shutdown;
- confirmation for destructive targets;
- tool checks;
- port checks.

A self-test with stub tools verifies the contract on macOS's Make 3.81 and on Linux in a new path-filtered
workflow.

In the same change, the packaged system's published port moves from 8000 to **8866**. It is declared once in
`compose.stack.yaml` and still overridable with `L4J_HTTP_PORT`. The README, the stack workflow, and
feature 004's design documents are updated to match.

The README makes the targets the primary way to run the project. `./gradlew` stays the only build entry
point, and CI keeps calling the underlying commands.

## Technical Context

**Language/Version**: GNU Make 3.81+ (Makefile), bash 3.2+ (helpers), POSIX awk. No Java or TypeScript change.
One line of Gradle build configuration, found during implementation (research R-013).

**Primary Dependencies**: None added. Delegates to the committed Gradle wrapper (9.4.1), npm (Node 24 per
`frontend/.nvmrc`), and Docker Compose v2 (`compose.yaml`, `compose.stack.yaml`).

**Storage**: N/A. The `reset` target deletes the stack's `pgdata` and `ollamadata` volumes, behind
confirmation.

**Testing**: `scripts/make/selftest.sh` (stub `docker`, `npm`, and wrapper on `PATH`), run locally and by
`.github/workflows/make.yml` on `macos-latest` and `ubuntu-latest`. The stack port change is exercised by
the existing `stack` workflow. `./gradlew check` must stay green with unchanged counts (145 backend, 126
frontend).

**Target Platform**: macOS and Linux, including WSL. Native Windows keeps the underlying commands.

**Project Type**: Developer tooling for the existing web application (backend and frontend monorepo).

**Performance Goals**: Help prints in under 1 second. Targets add no measurable time over the delegated
command.

**Constraints**:

- Make 3.81 compatibility: no `wait -n`, no `.ONESHELL`, no `$(file)`, no gawk extensions.
- No credential in any file or output.
- The Makefile declares no configuration value: the port, the model, and the replica counts stay in
  compose.
- A failed recipe exits 2 under Make (R-007).

**Scale/Scope**:

- 1 Makefile with 24 targets and help.
- 4 helper scripts plus the self-test.
- 1 new workflow.
- 1 compose line.
- README.
- 1 workflow edit.
- 7 feature-004 documents.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle / rule | Applies? | How this plan satisfies it |
|------------------|----------|----------------------------|
| I. Declarative-First, Library-First, capability inventory gate | No LangChain4j touched | Inventory recorded as N/A with reason (R-000). No agent, workflow, or listener code changes. |
| II. Provider-Agnostic Inference | Credentials, documented variables | Provider variables pass through untouched. The Makefile never includes `.env` or prints values (R-012). Nothing new leaves the machine. The README variable table is unchanged. A fresh clone still reaches a running system through documented commands, now also through `make up`. |
| III. Protocol Contracts Before Implementation | No MCP, A2A, or API change | The target contract is committed before the Makefile ([contracts/make-targets.md](./contracts/make-targets.md)). OpenAPI is untouched. |
| IV. Test-First | Deterministic tooling added | The self-test is written first and fails against a missing Makefile. Deterministic suites need no credential or network. The live test stays its own task (`make test-live` calls it). CI's credential dependency does not widen. |
| V. Observable Agent Runs | No run behavior change | N/A. |
| Stack: Gradle the only backend build tool, every documented command through the wrapper | Yes | Every backend build or test target calls `./gradlew`. Make builds nothing. `./gradlew check` stays the root verification (R-001). |
| Stack: build scripts carry no application logic | Yes | Launch logic lives in `scripts/make/`, not in Gradle. The one build-script change (R-013) is task configuration, `outputs.upToDateWhen { false }` on `liveTest`, not application logic. |
| Additional: configuration from environment with documented defaults | Yes | The 8866 default is documented in the compose file, README, and 004 contracts. The Makefile reads it back through `compose config` rather than restating it (R-006). |
| Additional: dependency additions justified | None added | `concurrently`, `just`, and `tmux` were rejected (R-001, R-003). |
| Workflow: principles declared, review for compliance | Yes | This table. |
| FR-019: reconcile with 003 R-004 | Yes | R-001: a launcher beside, not instead of, the wrapper. 003's concern (a second *build* entry point) does not arise. |

**Gate result (pre-research)**: PASS, no violations.

**Gate result (post-design)**: PASS. The design adds three documented deviations from literal README
commands, and none changes a principle's obligation:

- `--wait` on the development `up` (R-008).
- `--profile local` on `down` and `reset` (R-008; `down` found in T022).
- `--rerun` on `golden` (R-008).

Two spec statements that Make or the repository cannot satisfy as written were corrected in `spec.md` during
planning, not left to fail at implementation:

- FR-014 and SC-003: exit status under Make (R-007).
- FR-012: format check versus formatter (R-009).

## Project Structure

### Documentation (this feature)

```text
specs/006-makefile-entrypoint/
├── plan.md                    # This file
├── research.md                # R-000 to R-013
├── data-model.md              # Target, group, confirmation, dev session states
├── quickstart.md              # Validation scenarios 1 to 7
├── contracts/
│   └── make-targets.md        # Every target, what it needs, checks, and runs
├── checklists/
│   └── requirements.md
└── tasks.md                   # /speckit-tasks
```

### Source Code (repository root)

```text
Makefile                          # NEW: help + 4 groups, delegation only
scripts/make/
├── dev.sh                        # NEW: backend + frontend together, labeled, joint shutdown (R-003)
├── confirm.sh                    # NEW: CONFIRM=yes / prompt / refuse without terminal (R-004)
├── require.sh                    # NEW: named missing-tool failure (R-005)
├── port-free.sh                  # NEW: named port-in-use failure (R-006)
└── selftest.sh                   # NEW: stub-based contract test (R-010)
.github/workflows/
├── make.yml                      # NEW: selftest on macos-latest (Make 3.81) and ubuntu-latest
└── stack.yml                     # EDIT: probes on 8866
compose.stack.yaml                # EDIT: ${L4J_HTTP_PORT:-8866}:8080 and header comment
README.md                         # EDIT: make targets first, underlying commands kept, platform scope, 8866
backend/build.gradle.kts          # EDIT: liveTest never UP-TO-DATE (R-013, found in T027)
specs/004-containerized-deployment/
├── contracts/stack-topology.md   # EDIT: 8866
├── contracts/operations.md       # EDIT: 8866
├── data-model.md                 # EDIT: 8866
├── quickstart.md                 # EDIT: 8866
├── research.md                   # EDIT: R-011 → 8866, note on 006
└── tasks.md                      # EDIT: one note under the title; historical results untouched
```

**Structure Decision**: The helpers live in `scripts/make/` so everything the Makefile depends on is in one
directory the new workflow can path-filter on. No backend or frontend source changes.

## Complexity Tracking

No constitution violations to justify.
