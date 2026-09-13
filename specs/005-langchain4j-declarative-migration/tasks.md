---

description: "Task list for LangChain4j Declarative Migration"
---

# Tasks: LangChain4j Declarative Migration

**Input**: Design documents from `/specs/005-langchain4j-declarative-migration/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/declarations.md, contracts/trace-and-outcomes.md,
quickstart.md

**Tests**: Required. Constitution Principle IV requires failing tests before implementation, and FR-019 requires every behavior
tested today to keep a test. A golden snapshot is taken from the unmodified code before any production change (R-010). Each
replaced test class is listed with the task that replaces it, so the backend test count change is fully accounted for (SC-008).

**Organization**: Tasks are grouped by user story. Paths are relative to the repository root. All backend paths are under
`backend/`. Package root `dev/l4jlab/chain` is abbreviated `…/chain`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1 to US5, from spec.md

## Test accounting (SC-008)

| Removed test class | `@Test` count | Replaced by |
|--------------------|---------------|-------------|
| `src/test/java/…/chain/core/ChainRunnerTest.java` | 13 | T017 (`FinancialChainTest`), T024 (`FinancialChainFailureTest`) |
| `src/test/java/…/chain/node/SummarizeNodeTest.java` | 14 | T005 (`FailureClassifierTest`), T011 (`SummarizerTest`), T007 (`RunSummaryTest`), T009 (`IndicatorSetPromptTest`) |
| `src/test/java/…/chain/core/DeterministicNodeBudgetTest.java` | 1 | T030 (`ModelCallBudgetTest`) |

Rewritten in place, count unchanged: `security/CredentialLeakTest.java` (4). Unchanged: every other test class.

---

## Phase 1: Setup

**Purpose**: Freeze today's behavior as evidence and add the dependency

- [X] T001 Record the baseline: run `./gradlew check` and note backend and frontend test counts (expected 121 and 126) under this task in `specs/005-langchain4j-declarative-migration/tasks.md`
  - **Result**: baseline 2026-09-13: backend 121, frontend 126, `./gradlew check` green.
- [X] T002 Write `backend/src/test/java/…/chain/integration/GoldenRunSnapshotTest.java` extending `PostgresTest`: for every (company, period) in the catalog, start a run through `ChainRunService` with the test `FakeChatModel` bean, wait until terminal, and serialize the `RunDetailResponse` plus its four step records to canonical JSON with run ids, timestamps, and durations removed. When the environment variable `GOLDEN_WRITE=1` is set, write one file per selection to `backend/src/test/resources/golden/<companyId>_<period>.json`; otherwise compare byte for byte with the committed file and fail naming the first differing field. Comment why the snapshot exists (R-010)
  - **Result**: written as specified, reading the catalog through `CatalogController`. `requestedAt` (stamped by PrepareRequest from the clock) is removed along with ids, timestamps, and durations. Mismatches name the JSON path of the first difference.
- [X] T003 Run `GOLDEN_WRITE=1 ./gradlew :backend:test --tests '*GoldenRunSnapshotTest'` on the unmodified code, confirm one file per catalog selection exists in `backend/src/test/resources/golden/`, then run the test again without the variable and confirm it passes. Record the file count under this task
  - **Result**: 48 files written from the unmodified code (6 companies x 8 periods). Compare mode passed. Checked the test can fail: changing one stored value (`0.0600` to `0.0601`) failed it; reverted.
- [X] T004 Add `implementation("dev.langchain4j:langchain4j-agentic")` with no version to `backend/build.gradle.kts`, with a comment citing research R-001 (BOM-managed `1.18.0-beta28`, beta risk accepted). Run `./gradlew :backend:dependencyInsight --configuration runtimeClasspath --dependency langchain4j-agentic` and confirm `1.18.0-beta28`, and that every `dev.langchain4j` core module still resolves to `1.18.0`; then `./gradlew check` passes unchanged
  - **Result**: `langchain4j-agentic -> 1.18.0-beta28`; `langchain4j`, `langchain4j-core`, `langchain4j-ollama` all `1.18.0`. `./gradlew check` green; backend 122 (121 + golden), frontend 126.

**Checkpoint**: behavior is captured; the library is on the classpath; nothing else has changed.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The deterministic pieces and the summarizer declaration every story builds on, each test first

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T005 [P] Write failing `backend/src/test/java/…/chain/agent/FailureClassifierTest.java` per `contracts/trace-and-outcomes.md` "Outcome classification", porting the failure cases from `SummarizeNodeTest` (`reachesTimedOutRatherThanFailedWhenTheModelExceedsTheLimit`, `aRejectedCredentialProducesAReasonTheLearnerCanActOn`, `aForbiddenResponseIsTreatedAsARejectedCredentialToo`, `anUnreachableProviderFailsWithoutEchoingTheCredential`) and adding, per the step-scoped table in `contracts/trace-and-outcomes.md`: `ChainFailure` at any step passes its message through with FAILED; at Summarize, an `OutputGuardrailException` yields the empty-response reason and any unrecognized `RuntimeException` yields the "could not be reached or rejected the request" reason; at PrepareRequest, RetrieveRecords, or ComputeIndicators, an unrecognized `RuntimeException` and a `BoundaryViolation` both yield "`<step>` failed unexpectedly. See the server log for detail."; **a deterministic step's exception whose message contains "timed out" or "401" still yields the generic reason, never TIMED_OUT or the credential reason**; with no failed step recorded (null), any exception yields failed step `ChainRunner` and "The run stopped unexpectedly before the chain completed." (row 0); each reason contains no credential and is capped as today. The classifier takes the failed step name (nullable), the thrown exception (possibly wrapped in `AgentInvocationException`), and `ModelProperties`
  - **Result**: red: compile failure, the class did not exist. 11 tests.
- [X] T006 Implement `backend/src/main/java/…/chain/agent/FailureClassifier.java` returning an outcome (status, reason), moving `isTimeout`, `isRejectedCredential`, `safeReason`, `redactCredential`, and `MAX_REASON_LENGTH` from `node/SummarizeNode.java` unchanged in behavior and walking the full cause chain. Leave `SummarizeNode` untouched until T036. T005 passes
  - **Result**: green, 11/11. Timeout, credential, guardrail, and could-not-be-reached rows are reached only when the failed step is Summarize; a null step gives ChainRunner.
- [X] T007 [P] Write failing `backend/src/test/java/…/chain/domain/RunSummaryTest.java` for a new static factory `RunSummary.from(String text, TokenUsage usage, ModelProperties properties)`: text longer than `MAX_TEXT_LENGTH` is truncated and ends with `TRUNCATION_MARKER` (ported from `SummarizeNodeTest.truncatesAnOverLongResponseAndMarksTheTruncation`); model id and provider mode come from properties; null usage gives null token counts (ported from `toleratesAProviderThatReportsNoTokenCounts`); the credential never appears in `toString()`
  - **Result**: red: compile failure. 4 tests.
- [X] T008 Implement `RunSummary.from` in `backend/src/main/java/…/chain/domain/RunSummary.java`. T007 passes
  - **Result**: green, 4/4; truncation moved from SummarizeNode.
- [X] T009 [P] Write failing `backend/src/test/java/…/chain/domain/IndicatorSetPromptTest.java`: for the fixtures used in `SummarizeNodeTest`, `indicatorSet.toString()` equals `SummarizeNode.renderPrompt(indicatorSet)` exactly, including the `debtToEquity = not applicable (Equity is zero)` line
  - **Result**: red: compiled against the record default toString and would fail. 2 tests.
- [X] T010 Override `toString()` in `backend/src/main/java/…/chain/domain/IndicatorSet.java` with the rendering from `SummarizeNode.renderPrompt`, with a comment that this is the prompt form consumed by the `{{indicators}}` template (research R-003), and change `SummarizeNode.renderPrompt` to return `indicators.toString()`. T009 and all existing tests pass
  - **Result**: green, 2/2, first compared against the untouched SummarizeNode.renderPrompt (made public for the test); only after it passed did renderPrompt delegate to toString, so the comparison was not circular. Full backend suite and golden snapshot still green (147).
- [X] T011 [P] Write failing `backend/src/test/java/…/chain/agent/SummarizerTest.java` building the agent with `AgenticServices.agentBuilder(Summarizer.class).chatModel(fake).outputGuardrails(new NonBlankSummaryGuardrail()).build()` against a `FakeChatModel`: the request carries a system message equal to today's instruction text and a user message equal to the fixture's rendered table (ported from `sendsTheSystemInstructionAndTheRenderedIndicatorTable`); the call returns the fake's text (ported from `returnsTheModelTextWithTheModelIdentifierAndProviderMode`, text part); a blank response raises `OutputGuardrailException` (ported from `failsWithANamedReasonWhenTheModelReturnsNothing`); the traceability check detects a fabricated figure and passes for a faithful one (ported from `everyNumberInTheResponseIsTraceableToAnIndicator` and `aFabricatedFigureIsDetectedByTheSameCheck`); the model is called exactly once
  - **Result**: red: compile failure. 5 tests. One initial failure was the test, not the code: it compared against SYSTEM_INSTRUCTION.strip(), while the library sends the text block with its trailing newline, exactly as SummarizeNode did; the assertion now compares character for character.
- [X] T012 Create `backend/src/main/java/…/chain/agent/Summarizer.java` per `contracts/declarations.md`, moving `SYSTEM_INSTRUCTION` into the interface as a constant with its rationale comment, and `backend/src/main/java/…/chain/agent/NonBlankSummaryGuardrail.java` implementing LangChain4j `OutputGuardrail` that fails (no reprompt) on a blank response. T011 passes
  - **Result**: green, 5/5. The guardrail uses fatal(), and the blank-reply test confirms one model call (no retry).
- [X] T013 [P] Write failing `backend/src/test/java/…/chain/core/BoundaryValidationTest.java`: an object violating a Jakarta constraint makes `BoundaryValidation.requireValid(stepName, output)` throw `BoundaryViolation` (an unchecked exception that is **not** a `ChainFailure`) whose message has today's runner format "`<step>` produced an invalid `<Type>`: `<path> <message>`" (sorted, `; `-joined); null output throws `BoundaryViolation` with "`<step>` produced no output"; a valid object is returned unchanged
  - **Result**: red: compile failure. 3 tests.
- [X] T014 Implement `backend/src/main/java/…/chain/core/BoundaryViolation.java` (extends `RuntimeException`, with a comment that it is deliberately not a `ChainFailure` so its detail never reaches the screen, research R-006) and `backend/src/main/java/…/chain/core/BoundaryValidation.java` as a Micronaut singleton using the injected `Validator`, copying the message format from `ChainRunner.validateBoundary`. T013 passes
  - **Result**: green, 3/3.

**Checkpoint**: classifier, summary factory, prompt rendering, summarizer declaration, and boundary validation exist and are tested; the old chain still runs.

---

## Phase 3: User Story 1 - A run behaves exactly as before (Priority: P1) 🎯 MVP

**Goal**: runs execute through the agentic sequence with identical records and outcomes on the success path.

**Independent Test**: `GoldenRunSnapshotTest`, `ChainEndToEndTest`, `RunControllerTest`, `RestartPersistenceTest`, and `:frontend:checkApi` pass; quickstart Scenario 1.

- [X] T015 [US1] Annotate the three deterministic steps per `contracts/declarations.md`: `@Agent(name, outputKey, description)` on `run` and `@V` on its argument in `backend/src/main/java/…/chain/node/PrepareRequestNode.java`, `RetrieveRecordsNode.java`, and `ComputeIndicatorsNode.java`; inject `BoundaryValidation` and return `validation.requireValid(name(), output)`. Keep `implements ChainNode` until T036. All existing tests pass
  - **Result**: the three steps carry @Agent (name, outputKey, description) and @V, and return through BoundaryValidation.requireValid. Constructors gained BoundaryValidation; unit tests build it without a context via the new test helper support/Validations (Micronaut Validator.getInstance()). 147 backend tests green.
- [X] T016 [US1] Create `backend/src/main/java/…/chain/agent/FinancialChain.java` per `contracts/declarations.md` (`String run(@V("runId") UUID runId, @V("selection") Selection selection)`) with a comment naming the sub-agents and the scope keys from `data-model.md`
  - **Result**: created with the scope-key diagram in its Javadoc.
- [X] T017 [US1] Write failing `backend/src/test/java/…/chain/agent/FinancialChainTest.java` against beans from a Micronaut test context with the `FakeChatModel`, porting from `ChainRunnerTest`: `runsExactlyFourNodesInTheFixedOrder`, `everyNodeNameMatchesTheSpellingTheCheckConstraintAllows`, `announcesEachNodeBeforeItRunsSoTheScreenCanNameIt` (via the listener's current-step callback), `recordsInputOutputAndDurationForEveryNode`, `attachesTheModelExchangeToTheFourthNodeAndNoOther` (request text equals the rendered table, response text, tokens), `producesIdenticalIndicatorsAcrossRepeatedRunsOfTheSameInput`, `reportsDebtToEquityAsNotApplicableForTheZeroEquityRecord`; plus two runs executed concurrently on two threads each get only their own four records; and every run has exactly four records with four distinct step names (no duplicated event)
  - **Result**: 11 tests, red before T018 and T019 (the beans did not exist). Includes the concurrent-runs test and the no-duplicate-step test.
- [X] T018 [US1] Implement `backend/src/main/java/…/chain/agent/RunTraceListener.java` per `contracts/trace-and-outcomes.md` "Event mapping" and "Record field sources": a singleton `AgentListener` keeping a `ConcurrentHashMap<UUID, RunTrace>`, reading `runId` from the event's `AgenticScope`, ignoring non-step agent names, unwrapping the single data input, building the Summarize output with `RunSummary.from`, and exposing `start(runId, Consumer<String> onStepStart)`, `finish(runId)` returning the records and failed step name. Comment the hand-filled fields (plan, Complexity Tracking 3)
  - **Result**: implemented; FailureClassifier supplies each failure record's reason.
- [X] T019 [US1] Implement `backend/src/main/java/…/chain/agent/FinancialChainFactory.java` as a Micronaut `@Factory` producing `@Singleton Summarizer` (`agentBuilder(Summarizer.class).chatModel(chatModel).outputGuardrails(guardrail)`) and `@Singleton FinancialChain` (`sequenceBuilder(FinancialChain.class).subAgents(prepareRequest, retrieveRecords, computeIndicators, summarizer).listener(runTraceListener).outputKey("summaryText")`), with a comment on the justified exception to `@SequenceAgent` (research R-002)
  - **Result**: implemented. See T020 for the one-step wrappers added around the deterministic steps.
- [X] T020 [US1] Verify research R-005's open point: run `FinancialChainTest` and confirm the sequence-level listener receives before, after, and error events for all four sub-agents. If sub-agent events are missing, make `RunTraceListener.inheritedBySubagents()` return `true`, keeping the sequence as the **only** registration point; never also attach it to the `Summarizer` builder. Confirm T017's no-duplicate assertion. Record the registration path used under R-005 in `specs/005-langchain4j-declarative-migration/research.md`. T017 passes
  - **Result**: with inheritedBySubagents() true, only Summarize reported events. Cause, from the 1.18.0-beta28 sources: NonAiAgentInstance.setParent never registers an inherited listener, while workflow and AI agents do. Resolved with public API only: each deterministic step is wrapped in a one-step sequence named step:<Step>; the listener ignores the wrapper and records the inner step. Still one registration point. Debug run showed one before and one after event per step; 11/11 green. Recorded under R-005.
- [X] T021 [US1] Rewrite the execution path in `backend/src/main/java/…/chain/core/ChainRunService.java`: replace `runner.run(...)` with `runTraceListener.start(runId, node -> markCurrentNode(runId, node))`, `financialChain.run(runId, selection)`, and `runTraceListener.finish(runId)`; build the `ChainResult` for SUCCEEDED from the trace (indicators from step 3 output, `RunSummary` from step 4 output) after validating that `RunSummary` with `BoundaryValidation`; if it is invalid, finish FAILED at Summarize with "Summarize failed unexpectedly. See the server log for detail." and replace the step 4 record with a failure record that keeps request and response text and has no output or tokens (`contracts/trace-and-outcomes.md`, Success outcome); on any exception, classify with `FailureClassifier` using the trace's failed step (null when none was recorded, giving row 0's `ChainRunner` outcome, stored from a constant commented as a preserved value) and build FAILED or TIMED_OUT, and log a `BoundaryViolation`'s message at error level with the run id; persist records and finish the run exactly as before. Remove the `ChainRunner` dependency from this class
  - **Result**: ChainRunService now starts the trace, runs FinancialChain, and builds the result in a package-visible toResult(trace, thrown) (so rare branches are testable without a database). Deterministic-step unexpected errors are logged with the exception; Summarize failures are not logged, because provider errors can quote headers.
- [X] T022 [US1] Run `./gradlew :backend:test --tests '*GoldenRunSnapshotTest' --tests '*ChainEndToEndTest' --tests '*RunControllerTest' --tests '*RunHistoryControllerTest' --tests '*RestartPersistenceTest'` and fix until all pass without editing the golden files. Record any golden difference found and how it was resolved under this task
  - **Result**: all green, run fresh: GoldenRunSnapshotTest 1 (48 selections byte-identical, golden files untouched), ChainEndToEndTest 9, RunControllerTest 8, RunHistoryControllerTest 8, RestartPersistenceTest 2. No golden difference found.

**Checkpoint**: MVP. Successful runs go through the agentic sequence and match the pre-migration snapshot.

---

## Phase 4: User Story 2 - Failures still stop the chain and say why (Priority: P1)

**Goal**: every failure path yields the pre-migration status, failed step, reason, and preserved records.

**Independent Test**: `FinancialChainFailureTest`, `FailedRunTraceTest`, `CredentialLeakTest`; quickstart Scenario 3.

**Depends on**: US1.

- [X] T023 [US2] Verify research R-007's open point: with a `FakeChatModel` configured to throw, and with a deterministic step forced to throw `ChainFailure`, log the exception type and cause chain `FinancialChain.run` raises in each case and confirm no retry occurs (the fake's call count stays 1). Record the observed chains under R-007 in `specs/005-langchain4j-declarative-migration/research.md`
  - **Result**: chains recorded under R-007 from a temporary probe test (deleted). Model failure: AgentInvocationException wrapping the model exception through reflection layers; deterministic failure: nested AgentInvocationException (outer sequence and the step:<Step> wrapper) ending in ChainFailure; blank reply: OutputGuardrailException. No retry in any case (model call counts 1, 0, 1).
- [X] T024 [US2] Write the characterization test `backend/src/test/java/…/chain/agent/FinancialChainFailureTest.java` through `ChainRunService` with a database-backed context. Its assertions describe pre-migration behavior; if written before T021, run it once against the unmodified service and confirm it passes there too, so a failure after T021 means a regression, not a wrong test. Port from `ChainRunnerTest`: `stopsAtTheFirstFailureAndKeepsTheRecordsOfNodesThatCompleted`, `failsAtTheFirstNodeBeforeAnyLaterNodeRuns`, `carriesTheIndicatorsForwardEvenWhenTheSummarizingNodeFails`, `reachesTimedOutRatherThanFailedWhenTheModelExceedsItsLimit`, `wrapsAProviderFailureIntoABoundedReasonWithNoStackTrace`; and from `SummarizeNodeTest`: `recordsTheExactPromptEvenWhenTheCallFails`; plus: an empty model response → FAILED at Summarize with the empty-response reason, the request text recorded, and the response text **null** (the accepted deviation in spec FR-003); a deterministic step returning an invalid boundary object → FAILED at that step with the generic reason "`<step>` failed unexpectedly. See the server log for detail.", **not** the validation detail; a deterministic step throwing an exception whose message contains "timed out" → FAILED (not TIMED_OUT) with the generic reason; an exception raised before any step (for example a `FinancialChain` test double that throws immediately) → FAILED at `ChainRunner` with "The run stopped unexpectedly before the chain completed." and no step records. The invalid-`RunSummary` branch is not reachable through the running chain (research R-006); cover it in `backend/src/test/java/…/chain/core/ChainRunServiceTest.java` (new) by driving `ChainRunService` with a stubbed `RunTraceListener` whose step 4 record holds a `RunSummary` with a blank model id, asserting FAILED at Summarize with the generic reason and a step 4 failure record keeping request and response text
  - **Result**: FinancialChainFailureTest, 7 tests through ChainRunService against PostgreSQL: the five ChainRunnerTest failure cases, recordsTheExactPromptEvenWhenTheCallFails, and the empty response (response text null). Written after T021, so not run against the old runner; noted in its Javadoc. The branches the running chain cannot reach are in the new core/ChainRunServiceTest (4 tests): invalid RunSummary, error before any step (ChainRunner), deterministic error mentioning a timeout, and a broken boundary driven through the real sequence with a test-only replacement step (support/InvalidBoundaryComputeIndicatorsNode, env invalid-boundary), asserting the generic reason with no validation detail.
- [X] T025 [US2] Make T024 pass by adjusting `RunTraceListener.java`, `FailureClassifier.java`, or `ChainRunService.java` only; do not weaken an assertion
  - **Result**: no production change needed: all 11 passed on first run. ChainRunService.toResult was made public so CredentialLeakTest (another package) can use it.
- [X] T026 [US2] Rewrite `backend/src/test/java/…/chain/security/CredentialLeakTest.java` to build the chain through `FinancialChainFactory` (or a test context) instead of `new ChainRunner(...)` and `new SummarizeNode(...)`, keeping all four tests and their assertions that no credential appears in records, reasons, logs, or `toString()`
  - **Result**: rewritten to assemble the chain through FinancialChainFactory methods, RunTraceListener, FailureClassifier, and ChainRunService.toResult, with the credential under test in the properties; no ApplicationContext needed any more. 4/4 green, including the redacted 401 reason.
- [X] T027 [US2] Run `./gradlew :backend:test --tests '*FinancialChainFailureTest' --tests '*FailedRunTraceTest' --tests '*CredentialLeakTest'` and confirm all pass
  - **Result**: CredentialLeakTest 4, FinancialChainFailureTest 7, FailedRunTraceTest 2: all green.

**Checkpoint**: failure behavior matches feature 001 (SC-005).

---

## Phase 5: User Story 3 - Only the summarizing step reaches the model (Priority: P2)

**Goal**: model call count proves the deterministic steps never reach the model.

**Independent Test**: `ModelCallBudgetTest`; quickstart Scenario 4.

**Depends on**: US1, US2.

- [X] T028 [US3] Read `backend/src/test/java/…/chain/core/DeterministicNodeBudgetTest.java` and list its assertions under this task, so the replacement keeps each one
  - **Result**: DeterministicNodeBudgetTest has one assertion: after 50 warm-up iterations, one pass through PrepareRequest, RetrieveRecords, and ComputeIndicators takes under 100 ms (BUDGET_MS).
- [X] T029 [US3] Confirm by inspection that `ChatModel` is injected only into `FinancialChainFactory` and `ChatModelFactory`, and into no class under `node/`; record the result under this task
  - **Result**: ChatModel is injected into agent/FinancialChainFactory.summarizer and produced by model/ChatModelFactory; the only other holder is node/SummarizeNode, removed in T036. No class under node/ other than SummarizeNode receives it.
- [X] T030 [US3] Write `backend/src/test/java/…/chain/agent/ModelCallBudgetTest.java`: ten successful runs make exactly ten model calls on the counting `FakeChatModel`; a run failing in each of PrepareRequest, RetrieveRecords, and ComputeIndicators makes zero calls; plus every assertion listed in T028. Run it and confirm it passes
  - **Result**: 4 tests, green: ten successful runs make exactly ten calls; runs failing in PrepareRequest and RetrieveRecords make zero; a run failing in ComputeIndicators (test-only invalid-boundary replacement) makes zero; the 100 ms budget carried over from T028.

**Checkpoint**: FR-011 and SC-004 hold.

---

## Phase 6: User Story 4 - The summarizer is a declaration; no hand-written orchestration remains (Priority: P2)

**Goal**: remove the hand-written chain and its tests; update the reading path.

**Independent Test**: quickstart Scenario 6 finds nothing; Scenario 5 (`SummarizerTest`) passes; Scenario 8 live test passes or skips with a named reason.

**Depends on**: US1, US2, US3.

- [X] T031 [US4] Delete `backend/src/test/java/…/chain/core/ChainRunnerTest.java`, `backend/src/test/java/…/chain/node/SummarizeNodeTest.java`, and `backend/src/test/java/…/chain/core/DeterministicNodeBudgetTest.java`, confirming against the Test accounting table that each test method has its replacement
  - **Result**: deleted ChainRunnerTest (13), SummarizeNodeTest (14), DeterministicNodeBudgetTest (1). Each method's replacement per the Test accounting table exists and is green. Two of the files had carried the temporary Validations constructor change, so they were removed with rm rather than git rm.
- [X] T032 [US4] Rename `backend/src/liveTest/java/…/chain/live/SummarizeNodeLiveTest.java` to `SummarizerLiveTest.java` and change it to build the `Summarizer` with `AgenticServices.agentBuilder` over `new ChatModelFactory(properties).chatModel()`, keeping its skip messages and structural assertions (length, non-blank, no credential)
  - **Result**: renamed to SummarizerLiveTest; builds the Summarizer with AgenticServices.agentBuilder over ChatModelFactory, keeps the skip messages and structural assertions; its printed line no longer claims token counts, which a direct agent call does not return. Result of running it is under T045.
- [X] T033 [US4] In `backend/src/main/java/…/chain/node/PrepareRequestNode.java`, `RetrieveRecordsNode.java`, and `ComputeIndicatorsNode.java`, remove `implements ChainNode` and any `name()` method used only by the runner (keep a constant if `BoundaryValidation` or `@Agent` needs the name)
  - **Result**: implements ChainNode removed from the three steps; name() kept as a plain method (used for records and boundary messages).
- [X] T034 [US4] Delete `backend/src/main/java/…/chain/core/ChainRunner.java` and `backend/src/main/java/…/chain/core/ChainNode.java`
  - **Result**: ChainRunner.java and ChainNode.java deleted.
- [X] T035 [US4] Update the class Javadoc in `backend/src/main/java/…/chain/Application.java` to point at `agent/Summarizer`, `agent/FinancialChain`, and `agent/FinancialChainFactory` instead of `ChainRunner` and `SummarizeNode`
  - **Result**: Application Javadoc now points at agent/FinancialChain, agent/Summarizer, agent/FinancialChainFactory, agent/RunTraceListener.
- [X] T036 [US4] Delete `backend/src/main/java/…/chain/node/SummarizeNode.java`, and move any remaining helper it held (if any) to `agent/` or `domain/`; confirm nothing references it
  - **Result**: SummarizeNode.java deleted. Its only remaining user was IndicatorSetPromptTest, which now asserts the rendered table as a literal (the assertion had already passed against renderPrompt itself in T010). ChainRunService.finishUnexpected now uses FailureClassifier.UNKNOWN_STEP instead of the literal.
- [X] T037 [US4] Run quickstart Scenario 6 (`grep -rn "class ChainRunner\|interface ChainNode\|ModelExchangeHolder\|\.chat(" backend/src/main/java`) and confirm no match; run `./gradlew :backend:compileJava :backend:compileTestJava :backend:compileLiveTestJava` successfully
  - **Result**: Scenario 6 grep over backend/src/main/java: no match. compileJava, compileTestJava, compileLiveTestJava: BUILD SUCCESSFUL. Remaining mentions of ChainRunner or SummarizeNode are in comments, plus the deliberate stored value UNKNOWN_STEP = "ChainRunner".
- [X] T038 [US4] Update `README.md` "Where to look, in order": replace the `ChainRunner` and "only model call in the project" rows with `agent/FinancialChain.java` (the sequence), `agent/Summarizer.java` (the one AI agent, its instructions declared), `agent/FinancialChainFactory.java` (assembly), and `agent/RunTraceListener.java` (how each step becomes a record); rewrite the sentence "Nodes one through three never touch a model. Three tests assert that…" to name `ModelCallBudgetTest`; update the backend test count in "Test it" to the T043 figure
  - **Result**: reading path rewritten around FinancialChain, Summarizer, FinancialChainFactory, the node steps, RunTraceListener, and FailureClassifier; the model-call sentence names ModelCallBudgetTest; intro updated for the declarative design; backend count in Test it set to 145 (T043).

**Checkpoint**: SC-006 holds; the reading path describes the declarative design.

---

## Phase 7: User Story 5 - Traces come from the library's observation hooks (Priority: P3)

**Goal**: evidence that every stored field has the source the contract names.

**Independent Test**: T039 assertions pass; review T040 finds no undeclared hand-filled field.

**Depends on**: US1, US2.

- [X] T039 [US5] Add to `backend/src/test/java/…/chain/agent/FinancialChainTest.java` a test that, for a successful run, the Summarize record's request text equals the last user message of the `ChatRequest` the `FakeChatModel` received, the response text equals the fake's reply, and token counts equal the fake's `TokenUsage`; and that a deterministic step's record input equals the previous step's record output
  - **Result**: already present in FinancialChainTest from T017: theModelRequestTextIsTheUserMessageTheModelReceived asserts request text equals the last user message the FakeChatModel received and that each step's input equals the previous step's output; attachesTheModelExchangeToTheFourthNodeAndNoOther asserts response text and token counts 120/80. No additional test added, so T043's accounting counts no extra test for T039.
- [X] T040 [US5] Review `backend/src/main/java/…/chain/agent/RunTraceListener.java` against `contracts/trace-and-outcomes.md` "Record field sources", field by field, and confirm the only hand-filled values are the three named in plan Complexity Tracking 3; record the review result under this task
  - **Result**: field-by-field review of RunTraceListener against contracts/trace-and-outcomes.md: position (STEPS order), nodeName (agentName), input (inputs() by INPUT_KEYS), output (output(), or RunSummary.from for Summarize), succeeded/failureReason (event kind, FailureClassifier), startedAt/duration (before and after/error events), request text (chatRequest last user message; String.valueOf(indicators) on failure), response text (chatResponse), tokens (chatResponse.tokenUsage). Hand-filled: exactly the three named in Complexity Tracking 3. One addition outside the listener, recorded in R-005: the step:<Step> wrappers in FinancialChainFactory.

**Checkpoint**: Principle V compliance is evidenced.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T041 [P] Run `./gradlew :frontend:checkApi` and confirm `schema.d.ts` matches with no regeneration (FR-002, SC-002)
  - **Result**: `:frontend:checkApi`: schema.d.ts matches the backend API description; git diff on schema.d.ts is empty.
- [X] T042 [P] Update `specs/001-financial-agent-chain/research.md` R-003 and R-010 with a one-line note at the top of each: "Superseded by constitution v3.0.0 and feature 005 (research R-002, R-003)." Change nothing else in them
  - **Result**: one-line supersede note added at the top of R-003 and R-010 in specs/001-financial-agent-chain/research.md; nothing else changed there.
- [X] T043 Run `./gradlew check` from the root; confirm success and record backend and frontend counts under this task; confirm the backend change equals (tests added in T002, T005, T007, T009, T011, T013, T017, T024 including `ChainRunServiceTest`, T030, T039) minus 28 (T031), and that the frontend count is 126
  - **Result**: `./gradlew check` with both suites rerun: BUILD SUCCESSFUL. Backend 145 (0 failed, 0 skipped) = 121 baseline + 52 new (Golden 1, FailureClassifier 11, RunSummary 4, IndicatorSetPrompt 2, Summarizer 5, BoundaryValidation 3, FinancialChain 11, FinancialChainFailure 7, ChainRunService 4, ModelCallBudget 4) − 28 removed. Frontend 126.
- [X] T044 Run quickstart Scenario 7 with a configured provider (cloud with `OLLAMA_API_KEY`, or local with a pulled model): start a run through the application, confirm SUCCEEDED with four complete records, open a run recorded before the migration and confirm it displays unchanged, and paste the Summarize record's fields and the provider mode under this task (constitution workflow gate)
  - **Result**: provider mode verified: CLOUD (gpt-oss:120b, credential from the shell, not printed). Stack rebuilt with the migrated backend (compose.stack.yaml, 2 backends). Run 56b43678, stonebridge-paper 2025-Q4: SUCCEEDED; steps PrepareRequest 2 ms, RetrieveRecords 2 ms, ComputeIndicators 2 ms, Summarize 1665 ms; request text begins 'Company: Stonebridge Paper (fictional)\\nReporting period: 2025-Q4\\n\\nIndicators:\\n- revenueGrowth = -0.0500'; response text present (476-character summary); tokens 292 in, 229 out. A run recorded before the migration (44c468cc) opened as SUCCEEDED with 4 nodes, CLOUD, gpt-oss:120b. Stack stopped afterwards with data kept.
- [X] T045 Run `./gradlew :backend:liveTest` with the same provider and record pass or the named skip reason under this task
  - **Result**: `./gradlew :backend:liveTest` in cloud mode: SummarizerLiveTest 1 passed, 0 skipped; output 'Live test provider mode: CLOUD'.
- [X] T046 Update `specs/005-langchain4j-declarative-migration/spec.md` status to implemented with the date and provider mode verified, and confirm research R-005 and R-007 carry their verification notes
  - **Result**: spec status set; R-005 (T020 wrappers) and R-007 (T023 chains) carry their verification notes.

---

## Phase 9: Library-First Rework (added after review, 2026-09-13)

**Purpose**: the maintainer found that the implementation re-implemented `AgentMonitor`. These tasks replace the custom trace
bookkeeping with the library's observation and scope API and add the capability inventory the plan lacked (research R-011, R-012).

- [X] T047 Inventory every public capability of `langchain4j-agentic:1.18.0-beta28` and the relevant `langchain4j` / `langchain4j-core:1.18.0` APIs from the sources jars, with a decision per capability, in `specs/005-langchain4j-declarative-migration/research.md` R-011
  - **Result**: done. Newly adopted: `MonitoredAgent`/`AgentMonitor`, `@MemoryId`, `AgenticScopeAccess`, `ChatMessagesAccess`. Recorded as not adopted with reasons: `TypedKey`, `HtmlReportGenerator`, `ResultWithAgenticScope`, core `AiServiceListener` (not attachable through `AgentBuilder` in this version), `ChatModelListener` (no memory id), and the rest.
- [X] T048 Make `backend/src/main/java/…/chain/agent/FinancialChain.java` extend `MonitoredAgent` and `AgenticScopeAccess`, and take `@MemoryId UUID runId`
  - **Result**: done; the builder attaches an `AgentMonitor` automatically.
- [X] T049 Replace `agent/RunTraceListener.java` with `agent/RunProgressListener.java` (current step and failure instant only) and `agent/RunTraceAssembler.java` (records from `AgentMonitor` executions and the summarizer's `ChatMessagesAccess`), and update `agent/FinancialChainFactory.java`
  - **Result**: done. Hand-filled fields named in R-012 and the contract.
- [X] T050 Update `backend/src/main/java/…/chain/core/ChainRunService.java`: collect through `RunTraceAssembler`, read `indicators` from `getAgenticScope(runId)`, `evictAgenticScope(runId)`; `toResult(trace, indicators, thrown)`
  - **Result**: done.
- [X] T051 Update tests to the new API through a shared helper `backend/src/test/java/…/chain/support/ChainRuns.java`: `FinancialChainTest`, `ModelCallBudgetTest`, `ChainRunServiceTest`, `CredentialLeakTest`; change `FinancialChainFailureTest`'s empty-response assertion to the blank reply
  - **Result**: `./gradlew :backend:test`: 145 tests, 0 failed, including the golden snapshot (48 selections byte-identical). The only assertion that changed is the empty-response one: the blank reply `"   "` is now stored, exactly as before the migration, so the accepted deviation in FR-003 was removed.
- [X] T052 Update research R-005 (superseded note), R-007, R-011, R-012, `spec.md` FR-003, `plan.md`, `data-model.md`, `contracts/declarations.md`, `contracts/trace-and-outcomes.md`, and `quickstart.md`
  - **Result**: done.
- [X] T053 Amend the constitution so every plan must include a library capability inventory before design, in `.specify/memory/constitution.md`
  - **Result**: constitution amended to v3.1.0 (MINOR): Principle I now requires a capability inventory of the pinned LangChain4j modules, read from that version's sources, before any design; a new quality gate blocks task generation without it and requires review to reject hand-written code duplicating an inventoried capability.
- [X] T054 Run `./gradlew check`, a real cloud run through the stack, and `./gradlew :backend:liveTest`, and record the results here
  - **Result**: `./gradlew check` (suites rerun): backend 145, 0 failed; frontend 126; checkApi matches. Real run through the rebuilt stack in CLOUD mode (gpt-oss:120b), run 02b96b2f harbor-foods 2025-Q3: SUCCEEDED; step durations 3, 2, 2, 1512 ms with start instants from AgentMonitor; request text from ChatMessagesAccess begins 'Company: Harbor Foods (fictional)'; 386-character response; tokens 279/259 from the monitor's token usage. `./gradlew :backend:liveTest`: SummarizerLiveTest 1 passed. Stack stopped with data kept.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: none. T002 and T003 must run on unmodified production code
- **Foundational (Phase 2)**: after Setup; blocks all stories
- **US1 (Phase 3)**: after Foundational
- **US2 (Phase 4)**: after US1
- **US3 (Phase 5)**: after US2
- **US4 (Phase 6)**: after US3 (deletes the old chain only once every behavior has its new test)
- **US5 (Phase 7)**: after US2; may run alongside US3 and US4
- **Polish (Phase 8)**: after all stories

### Story Completion Order

```text
Setup → Foundational → US1 → US2 ─┬─► US3 → US4 ─┬─► Polish
                                  └─► US5 ────────┘
```

### Within Each Phase

- Setup: T001 → T002 → T003 → T004 (T003 before any production edit)
- Foundational: test before implementation in each pair: T005→T006, T007→T008, T009→T010, T011→T012, T013→T014. The five pairs touch different files and can run in parallel
- US1: T015, T016 → T017 (red) → T018 → T019 → T020 → T021 → T022
- US2: T023 → T024 (red) → T025 → T026 → T027
- US4: T031 → T032 → T033 → T034 → T035 → T036 → T037 → T038

### Parallel Opportunities

- T005, T007, T009, T011, T013 (tests) together, then their implementations T006, T008, T010, T012, T014 together
- T041 and T042
- US5 (T039, T040) alongside US3

## Parallel Example: Foundational tests

```bash
Task: "T005 FailureClassifierTest"
Task: "T007 RunSummaryTest"
Task: "T009 IndicatorSetPromptTest"
Task: "T011 SummarizerTest"
Task: "T013 BoundaryValidationTest"
```

---

## Implementation Strategy

### MVP First (User Stories 1 and 2)

1. Phase 1: snapshot the old behavior; add the dependency
2. Phase 2: deterministic pieces and the summarizer declaration
3. Phase 3 (US1): success path through the agentic sequence, matching the snapshot
4. Phase 4 (US2): failure paths. Both P1 stories are required before the chain can ship
5. **Stop and validate**: quickstart Scenarios 1 to 3

### Incremental Delivery

1. MVP as above; the old runner still exists but is unused
2. US3: model call budget
3. US4: remove the old chain and its tests; README
4. US5: trace evidence
5. Polish: full check, real run with provider mode recorded, live test

The old `ChainRunner` stays compilable until T034, so every step before it can be verified against the running code.
