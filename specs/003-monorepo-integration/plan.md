# Implementation Plan: Monorepo Integration

**Branch**: `003-monorepo-integration` | **Date**: 2026-09-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-monorepo-integration/spec.md`

## Summary

Wire the co-located backend and frontend into one verifiable project without adding an orchestration
tool. The frontend becomes a Gradle subproject whose tasks call npm explicitly, so `./gradlew check`
at the root runs the backend suite, the frontend suite, and the contract drift check, in the right
order, from a clean clone. The API description is written to a fixed, version-free file name, the
version is declared once in the backend build, the project takes one name, and three path-filtered
GitHub Actions workflows run only what a change touches. No application behavior changes.

## Technical Context

**Language/Version**: Java 25 through the Gradle toolchain (unchanged); Gradle 9.4.1 wrapper with
Kotlin DSL; Node.js 24 (declared in `frontend/.nvmrc`); TypeScript 5.7 (unchanged).

**Primary Dependencies**: Micronaut OpenAPI 7.1.3 through the Platform BOM (existing, now configured
with `micronaut.openapi.filename` and `micronaut.openapi.expand.api.version`); `openapi-typescript`
(existing); GitHub Actions `actions/checkout`, `actions/setup-java`, `gradle/actions/setup-gradle`,
`actions/setup-node`. No new Gradle plugin and no new npm dependency.

**Storage**: N/A. No schema or migration change.

**Testing**: Existing JUnit 5 backend suite (120, measured in T001; the 112 in the README was stale) and Vitest frontend suite (120, plus 6 new tests for the contract script).
The feature's own behavior is verified by the scenarios in [quickstart.md](./quickstart.md), which
exercise build wiring rather than application logic.

**Target Platform**: Developer machines on macOS, Linux, and Windows; GitHub-hosted Ubuntu runners.

**Project Type**: Web application, backend plus frontend, in one repository.

**Performance Goals**: A no-change second run of `./gradlew check` skips `npm ci`. CI runs only the
workflows a change's paths select.

**Constraints**: No orchestration tool (Nx, Turborepo, pnpm workspaces). npm stays the frontend's
build tool. No credential anywhere in the default path. No application source change except the
version placeholder in `@Info`.

**Scale/Scope**: Two subprojects, one generated contract file, three workflows, roughly ten files
touched.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Checked against constitution v2.2.0.

| Principle | Gate | Status |
|-----------|------|--------|
| I. Learning-First Transparency | Mechanism readable without framework internals | PASS. Every frontend task is an `Exec` whose command line is visible in `frontend/build.gradle.kts`. The Node plugin that would hide the binary is refused (R-004). |
| I. Learning-First Transparency | No dependency added merely to save lines | PASS. No Gradle plugin, no npm package, no third-party path-filter action is added. |
| I. Learning-First Transparency | Non-obvious decisions carry a rationale | PASS. Each build change carries a comment pointing at its research entry, following the existing `T004`/`C2` comment style in `backend/build.gradle.kts`. |
| II. Provider-Agnostic Inference | Credentials from environment, never committed | PASS. No workflow is given `OLLAMA_API_KEY`. No new configuration is read. |
| II. Provider-Agnostic Inference | Fresh clone reaches a running system with documented commands | PASS, strengthened. The README gains the root verification command and the Node prerequisite. |
| III. Protocol Contracts | Contracts shared from a single source, never declared twice by hand | PASS, strengthened. The drift check now runs in the root `check` and in CI, so a hand-declared or stale type cannot pass verification. |
| III. Protocol Contracts | MCP and A2A schemas | NOT APPLICABLE. |
| IV. Test-First | Deterministic behavior has tests | PASS. The feature adds no application behavior. Its build behavior is validated by the quickstart scenarios, each with a stated failure and fix. |
| IV. Test-First | Non-model tests need no credential or network to a provider | PASS. `./gradlew check` has no edge to `:backend:liveTest` (data-model task graph). |
| IV. Test-First | Live model tests separately selectable | PASS, unchanged. `:backend:liveTest` stays a separate task outside `check`. |
| IV. Test-First | Widening the credential dependency must be stated | PASS. Not widened. |
| V. Observable Agent Runs | Traces and logs | NOT APPLICABLE. No runtime change. |
| Stack | Locked entries respected | PASS. Java 25, Micronaut 5.1.5, LangChain4j 1.18.0, React on Vite, Vitest, PostgreSQL unchanged. |
| Stack | Gradle is the only build tool for the backend | PASS. Backend build tool unchanged. See R-008. |
| Stack | Build scripts in Kotlin DSL, no application logic | PASS. `frontend/build.gradle.kts` is Kotlin DSL holding task wiring and two checks. |
| Stack | Documented commands go through the wrapper | PASS with a recorded note. The root `check` now depends on an installed Node. R-008 records why this is not a violation and the PATCH clarification that would settle a stricter reading. |
| Stack | OpenAPI generated by Micronaut OpenAPI, no hand-maintained description | PASS. Only the file name and a version placeholder change. |
| Workflow | Spec and plan exist before implementation | PASS. |
| Workflow | Review verifies constitutional compliance | PASS. This table and R-008 are the review input. |

**Result**: all applicable gates pass. No entry in Complexity Tracking.

### Data Egress

Nothing new leaves the machine. CI runners receive the repository contents, which contain only
fictional data and no credential. No model call occurs in any workflow.

### Post-Design Re-Check

Re-evaluated after Phase 1, against the artifacts now on disk.

- **Principle I**: `data-model.md` draws the whole task graph in one short diagram; a learner can predict
  what `./gradlew check` does without running it. `contracts/root-commands.md` lists every task's
  literal command.
- **Principle III**: R-006 turns the drift check's failure into a message naming the fix, and
  `contracts/ci-workflows.md` runs it on any change to either half, not a narrower filter that could
  miss an indirectly reached type.
- **Principle IV**: `quickstart.md` records the baseline test counts (SC-007), so the wiring cannot
  silently drop a suite.
- **Stack**: R-008 examined each Language and build bullet individually. The only tension is the
  wrapper bullet, and it is documented rather than hidden.

No gate changed status. Complexity Tracking stays empty.

## Project Structure

### Documentation (this feature)

```text
specs/003-monorepo-integration/
├── plan.md              # This file
├── research.md          # Phase 0: R-001 … R-008
├── data-model.md        # Phase 1: project identity, API description, task graph
├── quickstart.md        # Phase 1: eight validation scenarios
├── contracts/
│   ├── root-commands.md # Commands, dependencies, failure messages
│   └── ci-workflows.md  # Workflow path sets and expected selection
├── checklists/
│   └── requirements.md
└── tasks.md             # Created by /speckit-tasks, not by this command
```

### Source Code (repository root)

```text
settings.gradle.kts                  # MODIFY: rootProject.name, include("frontend")
README.md                            # MODIFY: root command first, Node prerequisite, regenerate step
.github/workflows/
├── backend.yml                      # NEW: R-007
├── frontend.yml                     # NEW: R-007
└── contract.yml                     # NEW: R-007
backend/
├── build.gradle.kts                 # MODIFY: version 0.1.0, -A openapi filename and api.version,
                                     #   incremental(false) (added in implementation, see R-001)
└── src/main/java/dev/l4jlab/chain/
    └── Application.java             # MODIFY: @Info(version = "${api.version}")
frontend/
├── build.gradle.kts                 # NEW: base plugin, checkNode, npmCi, test, checkApi, checkVersion
├── .nvmrc                           # NEW: 24
├── package.json                     # MODIFY: version stays 0.1.0, engines.node, script targets
└── scripts/
    └── api-contract.mjs             # NEW: generate and check modes, R-006
```

**Structure Decision**: The existing two-directory layout stays. `frontend/` gains a Gradle build file
beside its `package.json`, making it a Gradle subproject that delegates to npm. Nothing moves between
halves, and no top-level `packages/` or `apps/` directory is introduced.

## Complexity Tracking

No constitution violations to justify.
