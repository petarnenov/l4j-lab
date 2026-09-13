# Implementation Plan: LangChain4j Declarative Migration

**Branch**: `005-langchain4j-declarative-migration` | **Date**: 2026-09-13 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/005-langchain4j-declarative-migration/spec.md`

## Summary

Replace the hand-written chain with LangChain4j's agentic orchestration, as constitution v3.0.0 requires. The summarizer becomes
a `Summarizer` AI agent interface with its system and user messages declared and an output guardrail for empty responses. The
four steps become an agentic sequence (`FinancialChain`) whose three deterministic steps are the existing node beans annotated as
non-AI agents. An `AgentListener` turns library events into the existing step records per run. A deterministic classifier maps
failures to today's outcomes. The API, database, UI, and indicator values do not change, which a golden snapshot recorded before
the change proves afterwards.

## Technical Context

**Language/Version**: Java 25 (unchanged).

**Primary Dependencies**: `dev.langchain4j:langchain4j-agentic:1.18.0-beta28` (new; managed by `langchain4j-bom:1.18.0` through
the Micronaut Platform BOM 5.1.5, verified, R-001), which brings `dev.langchain4j:langchain4j:1.18.0` (AI Services, guardrails).
Existing: `langchain4j-ollama:1.18.0`, Micronaut 5.1.5. Not added: `micronaut-langchain4j-agentic` (R-002).

**Storage**: PostgreSQL, unchanged. No migration.

**Testing**: JUnit 5 with the existing counting `FakeChatModel`; Testcontainers for database tests; Vitest unchanged. A golden
snapshot test is written and committed against the pre-migration code first (R-010). Tests of the removed runner and node are
replaced by tests of the declarations, the listener, and the classifier (FR-019).

**Target Platform**: unchanged (JVM service, containerized per feature 004).

**Project Type**: Web application, backend-only change.

**Performance Goals**: no regression a learner can see: a run's non-model steps still complete in well under a second on the sample
dataset.

**Constraints**: byte-identical API description and indicator values; stored record formats unchanged; no credential in any record,
log, or reason; compile-time dependency injection preserved.

**Scale/Scope**: about 6 backend production classes added or changed, 3 removed, 5 test classes replaced or added, README reading
path.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Checked against constitution v3.0.0.

| Principle | Gate | Status |
|-----------|------|--------|
| I. Declarative-First | Agent declared as an AI Service interface | PASS. `Summarizer` with `@Agent`, `@SystemMessage`, `@UserMessage`, `@V` (R-003). |
| I. Declarative-First | Multi-step flow uses LangChain4j agentic orchestration; no hand-written runner | PASS. `FinancialChain` sequence; `ChainRunner` and `ChainNode` removed (R-002, R-009). |
| I. Declarative-First | Every LangChain4j capability taken from the library | PASS with exceptions below. Orchestration, AI agent, output guardrail, and observation all come from LangChain4j. |
| I. Declarative-First | Explicit API only with a justified exception | JUSTIFIED EXCEPTION 1: sequence assembled with `sequenceBuilder` instead of `@SequenceAgent`, because the declarative form instantiates sub-agents and suppliers statically and cannot receive Micronaut beans (R-002). |
| I. Declarative-First | Deterministic steps stay code | PASS. The three node beans keep their logic and receive no model (R-008). |
| I. Declarative-First | Missing or beta capability stated | PASS. `langchain4j-agentic` is beta at 1.18.0-beta28; risk recorded (R-001). |
| II. Provider-Agnostic | Configuration, startup failure, credential handling | PASS. `ChatModelFactory` unchanged (R-004). JUSTIFIED EXCEPTION 2: model bean built by LangChain4j's builder in a hand-written factory rather than the Micronaut integration, to keep Principle II guarantees already tested. |
| II. Provider-Agnostic | Plan states what leaves the machine | PASS. Unchanged: the system instruction and the indicator table, in cloud mode only. |
| III. Contracts | Single source, no API change | PASS. `:frontend:checkApi` must pass with `schema.d.ts` untouched (FR-002). |
| IV. Test-First | Failing tests before implementation | PASS. Golden snapshot and new declaration, listener, and classifier tests precede production changes in task order. |
| IV. Test-First | Declared services and workflows tested with a fake model implementing the LangChain4j interface | PASS. `FakeChatModel` implements `ChatModel`. |
| IV. Test-First | No exact model text asserted; no credential or network in default tests | PASS, unchanged. |
| V. Observable | Trace captured through LangChain4j observation hooks | PASS with JUSTIFIED EXCEPTION 3, revised by R-012: `AgentMonitor` (timing, inputs, outputs, tokens, first error), `ChatMessagesAccess` (request and response), `AgenticScopeAccess` (indicators), and a thin `AgentListener` (current step, failure instant). Hand-filled and named: request text on a call that failed before a response, the Summarize `RunSummary`, input unwrapping, the failed step's duration, `LocalDateTime` to `Instant`. |
| V. Observable | Traces persisted and shown | PASS. Same records, same screens. |
| Stack | Compile-time DI; proxies exposed as beans | PASS. `FinancialChainFactory` exposes `Summarizer` and `FinancialChain` as singletons. |
| Stack | One creation path per AI Service, stated | PASS. Both via LangChain4j builders in one factory (R-004). |
| Stack | Every LangChain4j module at one version | PASS. All from `langchain4j-bom:1.18.0` (R-001). |
| Additional | Dependency addition justified against Principle I | PASS. `langchain4j-agentic` is the LangChain4j module for the capability. |
| Workflow | Agent or orchestration change includes a recorded run trace naming the provider mode | PENDING at merge: quickstart Scenario 7. |

**Result**: all gates pass, with three justified exceptions recorded in Complexity Tracking.

Boundary validation (R-006) is Micronaut's capability, not LangChain4j's, and is not an exception to Principle I.

### Data Egress

Unchanged from feature 001. In cloud mode the provider receives the fixed system instruction and the rendered indicator table of
fictional data. Nothing else.

### Post-Design Re-Check

Re-evaluated after Phase 1, against the artifacts now on disk.

- **Principle I**: `contracts/declarations.md` shows the only non-declarative assembly is the sequence builder call, and names the
  removed classes so a reviewer can check SC-006 with one search (quickstart Scenario 6).
- **Principle V**: `contracts/trace-and-outcomes.md` maps every stored field to its source, and marks the three hand-filled ones. The
  finding that listener exceptions are swallowed moved validation out of the listener (R-005, R-006), so no trace mechanism can let an
  invalid boundary through silently.
- **Principle IV**: the golden snapshot makes behavior preservation a test, not a claim.

No gate changed status.

## Project Structure

### Documentation (this feature)

```text
specs/005-langchain4j-declarative-migration/
├── plan.md                        # This file
├── research.md                    # R-001 … R-012, including the capability inventory (R-011)
├── data-model.md                  # Scope keys, steps, run trace, Summarize record sources
├── quickstart.md                  # Scenarios 0 to 8
├── contracts/
│   ├── declarations.md            # Summarizer, deterministic steps, FinancialChain, removed classes
│   └── trace-and-outcomes.md      # Event mapping, field sources, failure classification
├── checklists/
│   └── requirements.md
└── tasks.md                       # Created by /speckit-tasks
```

### Source Code (repository root)

```text
backend/
├── build.gradle.kts                                   # MODIFY: add langchain4j-agentic (BOM-managed)
├── src/main/java/dev/l4jlab/chain/
│   ├── agent/                                         # NEW package: the declarations and their assembly
│   │   ├── Summarizer.java                            # NEW: AI agent interface (R-003)
│   │   ├── NonBlankSummaryGuardrail.java              # NEW: output guardrail (R-007)
│   │   ├── FinancialChain.java                        # NEW: sequence interface (R-002)
│   │   ├── FinancialChainFactory.java                 # NEW: builds Summarizer and FinancialChain beans (R-004)
│   │   ├── RunProgressListener.java                   # NEW: AgentListener for current step and failure instant (R-012)
│   │   ├── RunTraceAssembler.java                     # NEW: AgentMonitor + ChatMessagesAccess → step records (R-012)
│   │   └── FailureClassifier.java                     # NEW: cause → status and reason (R-007)
│   ├── core/
│   │   ├── ChainRunService.java                       # MODIFY: invoke FinancialChain, persist trace, classify failures
│   │   ├── BoundaryValidation.java                    # NEW: shared step output validation (R-006)
│   │   ├── BoundaryViolation.java                     # NEW: unchecked, classified as unexpected (R-006, R-007)
│   │   ├── ChainRunner.java                           # DELETE
│   │   └── ChainNode.java                             # DELETE
│   ├── domain/
│   │   ├── IndicatorSet.java                          # MODIFY: toString renders the prompt table (R-003)
│   │   └── RunSummary.java                            # MODIFY: deterministic factory from a response (R-005)
│   └── node/
│       ├── PrepareRequestNode.java                    # MODIFY: @Agent, validate output
│       ├── RetrieveRecordsNode.java                   # MODIFY: @Agent, validate output
│       ├── ComputeIndicatorsNode.java                 # MODIFY: @Agent, validate output
│       └── SummarizeNode.java                         # DELETE
├── src/test/java/dev/l4jlab/chain/
│   ├── integration/GoldenRunSnapshotTest.java         # NEW, written first (R-010)
│   ├── agent/SummarizerTest.java                      # NEW, replaces node/SummarizeNodeTest
│   ├── agent/FinancialChainTest.java                  # NEW, replaces core/ChainRunnerTest
│   ├── agent/FinancialChainFailureTest.java           # NEW
│   ├── core/ChainRunServiceTest.java                  # NEW: unreachable RunSummary branch with a stubbed trace (R-006)
│   ├── agent/ModelCallBudgetTest.java                 # NEW, replaces core/DeterministicNodeBudgetTest
│   ├── support/ChainRuns.java                         # NEW: runs the chain without a database, as ChainRunService does (R-012)
│   └── agent/FailureClassifierTest.java               # NEW, carries SummarizeNodeTest's failure cases
├── src/test/resources/golden/                         # NEW: committed snapshot
└── src/liveTest/java/dev/l4jlab/chain/live/SummarizeNodeLiveTest.java   # MODIFY: exercise Summarizer
README.md                                              # MODIFY: reading path (FR-022)
```

**Structure Decision**: The declarations and their assembly live in a new `agent/` package, so a reader opens one directory to see what
the agents are and how they are composed. Deterministic steps stay in `node/`, domain types in `domain/`, and run lifecycle in `core/`.

## Complexity Tracking

| Exception | Principle | Why needed | Simpler or more declarative alternative rejected because |
|-----------|-----------|------------|--------------------------------------------------------|
| 1. `sequenceBuilder` instead of `@SequenceAgent` | I | Sub-agents are Micronaut beans with injected dependencies | `@SequenceAgent` creates sub-agents and suppliers statically; reaching beans would need a static service locator, forbidden by compile-time DI (R-002) |
| 2. `ChatModelFactory` kept | I (library-first), II | Its startup failure, base URL check, and credential header supplier are tested Principle II guarantees | The Micronaut LangChain4j Ollama configuration would need those guarantees re-proved for no capability this feature uses (R-004) |
| 4. One-step `step:<Step>` sequence around each deterministic step (added in implementation, T020) | I, V | In langchain4j-agentic 1.18.0-beta28 a plain object step placed directly in a sequence never receives the sequence's inherited listener (`NonAiAgentInstance.setParent` does not register it), so its events would be lost | Hand-instrumenting the steps would put tracing into business code; implementing the internal `AgentSpecsProvider` would rely on a non-public API of a beta module. The wrapper uses only the public builder and is commented for removal when the library is fixed (R-005) |
| 3. Hand-filled trace fields (revised by R-012) | V | `AgentInvocationError` has no `ChatRequest`; the Summarize record stores a `RunSummary`, not raw text; `inputs()` is a map | No library event exposes the failed request or the stored summary shape; each fill is deterministic and tested (R-005). The blank response text on an empty-response failure is deliberately not filled; it is an accepted, specified deviation (spec FR-003, R-007) |
