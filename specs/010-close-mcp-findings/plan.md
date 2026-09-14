# Implementation Plan: Close the findings against the MCP billing server

**Branch**: `010-close-mcp-findings` | **Date**: 2026-09-14 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/010-close-mcp-findings/spec.md`

## Summary

Planning read the code rather than the findings, and three of the seven turned out to be recorded
wrongly — each in the direction of being smaller than it is. The plan is shaped by what was actually
found:

1. **F-006 is not an entitlement bug.** Any search whose result set is empty returns HTTP 500. The
   legacy API omits `items` from an empty page, the server calls `.stream()` on it, and the resulting
   `NullPointerException` escapes as a JSON-RPC error with no message. A date range with nothing in it
   crashes the tool. **The specification was written before this was known and described the
   entitlement framing the finding used; it has since been corrected**, and US1 now describes the
   defect that exists. The scenarios about refusals remain, as regression protection for behaviour
   that already works.
2. **F-001 is not a serialisation loss.** The committed tool contracts are **never read at runtime**.
   The declarations are generated from Java annotations, and the JSON files are a second hand-written
   copy — which is a Principle III violation in feature 007, not a bug in its serialiser.
3. **The guard against exactly that drift does not exist.** `mcp-server/build.gradle.kts` says a
   contract test "asserts the generated schema matches the committed JSON, so drift fails the build".
   No such test was written. The contracts are copied into test resources and nothing reads them.

The third is the finding this feature most needs to close, because it is the one that let the other
two stand for a week.

## Technical Context

**Language/Version**: Java 25, as the constitution pins. The work is in `mcp-server/` and
`legacy-billing-api/`, plus documents under `specs/`.

**Primary Dependencies**: unchanged. Micronaut 5.1.5, the Micronaut MCP integration 2.0.0, the MCP
Java SDK. **No dependency is added or upgraded** — every finding is a disagreement between what
exists and what was promised.

**Storage**: unchanged. No migration; the seeded fixtures stay as they are.

**Testing**: the split feature 007 already uses — `:mcp-server:test` deterministic, `topologyTest`
against the running stack. Both grow. The frontend's live console suite (feature 008) is the third
witness and must go on passing, since it pinned several of these behaviours as they are.

**Target Platform**: unchanged.

**Project Type**: repair of an existing service. No new capability, no new module.

**Performance Goals**: none beyond not regressing. Nothing here is on a hot path.

**Constraints**:
- No new tool, method, or protocol feature (spec Out of Scope)
- Feature 007's plan and research are records of decisions taken then and are not edited; only claims
  false about the **code today** are in scope
- Every one of 007's existing acceptance scenarios keeps passing
- Feature 009's drift baseline must **shrink**, and a closed finding's entry must be removed rather
  than silenced (FR-020) — an entry that no longer matches real drift fails that check by design

**Scale/Scope**: five tools, one error boundary, one serialiser, one reading guide, and the test that
should have caught all of it.

## Constitution Check

*GATE: evaluated before Phase 0, re-evaluated after Phase 1.*

| Principle | Applies? | Verdict | How |
|---|---|---|---|
| **I. Declarative-First, Library-First** | Yes | **PASS** | The feature touches no LangChain4j module — `mcp-server` is built on the MCP Java SDK and Micronaut, not on LangChain4j. The inventory the amendment requires is recorded as an empty set with evidence in [research.md R-008](./research.md). Library-first bites elsewhere and is answered there: the declaration problem is solved by making the SDK's generator the single source rather than by hand-writing a second one (R-002). |
| **II. Provider-Agnostic Inference** | No | **N/A** | No inference, no provider. |
| **III. Protocol Contracts Before Implementation** | Yes | **CURRENTLY VIOLATED — closing the violation is the feature** | *"Contract definitions MUST be shared between backend and frontend from a single source; the same shape MUST NOT be declared twice by hand."* The tool shapes are declared twice by hand today: in Java annotations, which ship, and in committed JSON, which does not. F-001 is that violation observed from outside. R-002 chooses which of the two becomes derived. |
| **IV. Test-First at Deterministic Boundaries** | Yes | **PASS** | Everything here is deterministic. Each finding gets a test that fails on today's behaviour before anything is changed — FR-004 makes that a requirement rather than a habit, because a defect fixed without a failing test first is a defect nobody proved was there. |
| **V. Observable Agent Runs** | No | **N/A** | No agent runs. |
| **Stack constraints** | Yes | **PASS** | No JVM language added, no framework introduced, no dependency added. The Gradle wrapper stays the only entry point. |
| **Workflow gates** | Yes | **PASS** | Spec and plan before implementation; the LangChain4j question answered explicitly rather than skipped. |

**Post-Phase-1 re-evaluation**: unchanged, with one thing worth stating. This feature *closes* a
Principle III violation rather than creating one, and the mechanism it adds — a test comparing what is
served against what is committed — is the thing that makes the principle enforceable here rather than
aspirational. Feature 007 believed it had that test; the comment in its build file says so. It did
not.

## Project Structure

### Documentation (this feature)

```text
specs/010-close-mcp-findings/
├── plan.md                  # This file
├── spec.md
├── research.md              # Phase 0 — R-001..R-008, the investigation and its decisions
├── data-model.md            # Phase 1 — the shapes this feature changes
├── quickstart.md            # Phase 1 — reproduce each finding, then verify it closed
├── checklists/
│   └── requirements.md
└── contracts/
    ├── README.md
    ├── empty-and-refused.md # what an empty result and a refusal look like, and how they differ
    └── declaration-source.md # which side declares a tool, and what the other one must match
```

### Source Code (repository root)

```text
mcp-server/src/main/java/dev/l4jlab/mcp/
├── tools/
│   ├── BillingRunTools.java        # the null page (F-006); enriched argument declarations (F-001)
│   ├── FeeAdjustmentTool.java      # the confirmation answer (F-005); previous fee (F-002)
│   └── StartBillingRunTool.java    # the declared output shape (F-004)
├── legacy/
│   └── LegacyBillingClient.java    # nothing changes; its outcome mapping is already total
└── protocol/
    └── <error boundary>            # an unexpected exception must not become an empty message

mcp-server/src/test/java/dev/l4jlab/mcp/
└── contracts/
    └── ToolDeclarationContractTest.java   # NEW: the test 007's build file already claims exists

legacy-billing-api/src/main/java/dev/l4jlab/legacy/
└── api/                            # an empty page must serialise its empty collection

specs/007-mcp-billing-server/
├── contracts/                      # corrected where they describe today's code wrongly
└── quickstart.md                   # the reading guide (G-006)

scripts/spec-drift/baseline.json    # entries for closed findings removed, not silenced
```

**Structure Decision**: the work lands where each finding lives, and nothing is reorganised. The one
new file is the contract test, which goes under `mcp-server/src/test/` beside the suite that should
have contained it. Feature 007's own layout is left alone: this feature repairs, it does not tidy.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| Editing feature 007's committed contracts and quickstart | Four findings *are* statements in those documents that are false about the code today — the reading guide names classes never written, the retry shape is under-specified, two descriptions have drifted, and one output schema describes the wrong result. Leaving them is leaving the findings open. | Fixing only the code and leaving the documents was rejected: for F-005 and G-006 the document *is* the defect, and a reader of a published contract cannot be told to consult the source instead. **Bounded**: 007's `plan.md` and `research.md` are not touched — they record decisions taken at the time and remain true as history. Only claims about present behaviour are corrected, and each correction says in place that it was one. |
