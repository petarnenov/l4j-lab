# Implementation Plan: Hold the documents to the code

**Branch**: `009-spec-drift-check` | **Date**: 2026-09-14 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/009-spec-drift-check/spec.md`

## Summary

A check that extracts the *structural* claims a specification document makes about the repository —
paths, commands, requirement identifiers, references — and fails the build when one of them stops
being true. It reads documents and the filesystem, runs nothing, needs no network, and is explicit
about the far larger set of claims it cannot check.

Two decisions carry the design, and planning produced both by looking at the repository rather than
by reasoning from the spec:

1. **A feature is held to its code when it says it is implemented**, not when every task is ticked.
   Two features carry a deliberately open task — 001's `T101` and 008's `T071` — and both are
   delivered. Task completion is therefore the wrong signal, and the `Status` field is the right one
   *if the check also verifies it*, which it does.
2. **Existing drift is recorded, not fixed.** The spec's own Out of Scope forbids editing what a past
   feature decided, so a check that forced 001 through 006 to pass would push against that rule. A
   committed baseline makes the old drift visible and the new drift fatal.

## Technical Context

**Language/Version**: JavaScript, Node 24 ESM (`.mjs`), matching `frontend/scripts/api-contract.mjs`
and `frontend/scripts/check-dev-only.mjs` — the two build tools this repository already has.

**Primary Dependencies**: **none.** Node's built-ins (`node:fs`, `node:path`) and its built-in test
runner. No markdown parser and no lint framework (research R-002).

**Storage**: two committed files — a baseline of accepted pre-existing drift, and nothing else. No
cache, because a check that can be stale is a check that can lie.

**Testing**: `node --test`, the built-in runner. The frontend's Vitest reaches
`frontend/scripts/*.test.mjs` and would have been free, but this tool is repository-wide and putting
it under `frontend/` to borrow a test runner would misfile it (research R-003).

**Target Platform**: the developer's machine and CI, through `./gradlew check`.

**Project Type**: build tooling. Not backend, not frontend; the third thing this repository already
has in `frontend/scripts/` and `scripts/make/`.

**Performance Goals**: SC-003 — no noticeable addition to the verification it joins. The corpus is
nine features of roughly a dozen documents each; reading them is milliseconds, and the budget is set
by what people will tolerate, not by what is achievable.

**Constraints**:
- No network, no credentials, no process started (FR-009, FR-010)
- Never executes a command it is checking for (FR-010) — existence is a filesystem and manifest
  question, and running things to find out would make the check a hazard
- Changes no existing specification (spec Out of Scope)
- Reports what it does *not* check, in its output and its source (FR-013)

**Scale/Scope**: 9 features, ~60 documents, an estimated 1,500–2,500 extractable claims.

## Constitution Check

*GATE: evaluated before Phase 0, re-evaluated after Phase 1.*

| Principle | Applies? | Verdict | How |
|---|---|---|---|
| **I. Declarative-First, Library-First** | Partly | **PASS** | No LangChain4j module is touched; the inventory the amendment requires is recorded as an empty set with evidence in [research.md R-008](./research.md). The library-first habit still bites on the frontend side of the house, and R-002 answers it: no markdown parser, no lint framework, and the reason is the claim surface is a dozen regular forms rather than a grammar. |
| **II. Provider-Agnostic Inference** | No | **N/A** | No inference, no provider, no credential. FR-009 makes the absence a requirement rather than an accident. |
| **III. Protocol Contracts Before Implementation** | Partly | **PASS** | No MCP tool and no A2A message. The check does expose one interface — what it prints and what it exits with — and that is committed as a contract before the code, in [contracts/report-format.md](./contracts/report-format.md). The claim grammar is committed likewise, so what counts as a claim is decided in writing first. |
| **IV. Test-First at Deterministic Boundaries** | Yes | **PASS** | Every behaviour here is deterministic — a file exists or it does not. Tests first, red, green. No model is involved, so no live suite exists and none is needed. |
| **V. Observable Agent Runs** | No | **N/A** | No agent runs. |
| **Stack constraints** | Yes | **PASS** | No JVM language added; no frontend framework touched; the Gradle wrapper remains the only entry point, and the new task is wired into `check` rather than left as a flag someone must remember. |
| **Workflow gates** | Yes | **PASS** | Spec and plan before implementation; the LangChain4j question answered explicitly rather than skipped. |

**Post-Phase-1 re-evaluation**: unchanged. Phase 1 added two contracts, one data model and a
quickstart, introduced no dependency, and produced three findings about this repository's own
documents — recorded in [findings.md](./findings.md) rather than fixed, on the same reasoning the
spec applies to feature 007.

## Project Structure

### Documentation (this feature)

```text
specs/009-spec-drift-check/
├── plan.md                  # This file
├── spec.md
├── research.md              # Phase 0 — R-001..R-008
├── data-model.md            # Phase 1 — Claim, Feature record, Exemption, Baseline entry, Report
├── findings.md              # Phase 1 — drift found while planning the drift checker
├── quickstart.md            # Phase 1 — run and verify
├── checklists/
│   └── requirements.md
└── contracts/
    ├── README.md            # index
    ├── claim-grammar.md     # what counts as a claim, and what deliberately does not
    └── report-format.md     # what the check prints, and what it exits with
```

### Source Code (repository root)

```text
scripts/spec-drift/
├── check.mjs                # entry point: collect, verify, report, exit
├── features.mjs             # which features are in scope, and why (R-001)
├── claims.mjs               # extraction, per contracts/claim-grammar.md
├── verify.mjs               # one verifier per claim kind
├── report.mjs               # output, per contracts/report-format.md
├── baseline.json            # accepted pre-existing drift, each entry with a reason
├── claims.test.mjs          # extraction, against this repository's real documents
├── verify.test.mjs          # each verdict, on fixtures
├── features.test.mjs        # the in-scope rule, including both open-task features
└── report.test.mjs          # the output contract

build.gradle.kts             # NEW at the root: the `base` plugin and the specDrift task
Makefile                     # + check-specs
```

**Structure Decision**: a new top-level `scripts/spec-drift/` directory, because the tool is about
the whole repository and nothing else in it is. The alternative — `frontend/scripts/`, where the two
existing Node tools live — was rejected: they are there because they are *about* the frontend, and
borrowing that directory to inherit a test runner would file a repository-wide checker under one
module and mislead every future reader about its scope.

A root `build.gradle.kts` is new. The repository has none today; `settings.gradle.kts` declares the
modules and every build file lives in one. Adding a root file with the `base` plugin gives the root
project a `check` lifecycle to attach to, which is what FR-008 asks for: part of ordinary
verification, not a command to remember.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| A second test runner (`node --test`) alongside the pinned Vitest | The constitution pins Vitest as the **frontend** test framework, and this is not frontend code. Its tests need to run without `frontend/node_modules`, because the thing under test reads `specs/` and `Makefile` and has no relationship to the web application. | Moving the tool under `frontend/` to reuse Vitest was rejected as misfiling (see Structure Decision). Adding Vitest at the root was rejected as a dependency — and a `node_modules` — for a tool that otherwise needs neither. **Bounded**: `node --test` ships with the Node version `frontend/.nvmrc` already pins, so it adds no install, no lockfile and no version to track; and the boundary is legible — if a test file lives under `scripts/`, the built-in runner runs it. |
| A committed baseline of accepted drift | The spec forbids editing what a past feature decided, and features 001–006 predate this check. Without a baseline the choice is between failing the build on historical records or not checking them at all. | Fixing the old documents was rejected by the spec's own Out of Scope. Checking only new features was rejected because it leaves known-stale documents with nothing saying so. **Bounded**: every entry carries a reason, the whole baseline is printed on every run (FR-015), and a baseline entry that no longer corresponds to real drift is itself a failure — so it cannot quietly outlive its cause. |
