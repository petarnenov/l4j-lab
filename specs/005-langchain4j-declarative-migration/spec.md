# Feature Specification: LangChain4j Declarative Migration

**Feature Branch**: `005-langchain4j-declarative-migration`

**Created**: 2026-09-13

**Status**: Implemented, 2026-09-13. All 46 tasks complete. Provider mode verified: cloud (gpt-oss:120b), through the containerized stack and the live model test. Local mode not exercised.

**Input**: User description: "Migrate the financial agent chain to LangChain4j declarative AI Services and agentic orchestration: the summarizer as an AI Service interface, the four steps as an agentic sequence with the three deterministic steps kept as code, traces captured through LangChain4j listeners, same API, persistence, and UI."

## Context

Constitution v3.0.0 made the declarative approach the first design and requires every capability
LangChain4j provides to be taken from the library (Principle I). Its Sync Impact Report lists the
chain as non-compliant:

- The four steps are sequenced by a hand-written runner that loops over them, times each one,
  validates each boundary, and converts failures into run outcomes.
- The summarizing step assembles its prompt by hand and calls the model directly.
- The trace of the model exchange is carried from the summarizing step to the runner by hand.

This feature brings the chain into compliance. It is a migration, not a new capability: a learner
using the application, a client calling the API, and the database holding past runs must not be able
to tell the difference, except that summaries remain whatever the model says each time.

The requirements of feature 001 (`specs/001-financial-agent-chain/spec.md`, FR-001 to FR-021 and its
success criteria) remain in force and are the acceptance baseline for this feature.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A run behaves exactly as before (Priority: P1)

A learner picks a company and period and starts a run. The four steps (PrepareRequest,
RetrieveRecords, ComputeIndicators, Summarize) run in that order. The screen names the step currently
running. The run finishes with the same indicator values as before and a summary built only from
them. The learner opens the run and steps through four records, each with its input, output, timing,
and outcome, and the summarizing step's record shows the text sent to the model, the text returned,
the model identifier, the provider mode, and the token counts.

**Why this priority**: It is the whole point of a migration: the orchestration changes and nothing a
learner or client depends on does. On its own it delivers a compliant, working chain.

**Independent Test**: Run the existing end-to-end chain tests and the API contract check unchanged
against the migrated chain, then start a run through the running application in one provider mode
and compare its detail with a run recorded before the migration for the same selection.

**Acceptance Scenarios**:

1. **Given** the migrated application, **When** a learner starts a run for any company and period in
   the sample dataset, **Then** the run passes through the same statuses as before and ends
   SUCCEEDED with exactly four step records in the order PrepareRequest, RetrieveRecords,
   ComputeIndicators, Summarize.
2. **Given** the same selection run before and after the migration, **When** the two runs' indicator
   values are compared, **Then** they are byte-identical.
3. **Given** a run in progress, **When** the learner watches it, **Then** the screen names the step
   currently running, as before.
4. **Given** a completed run, **When** the learner opens the Summarize record, **Then** it shows the
   text sent to the model, the text returned, the model identifier, the provider mode, and the token
   counts when the provider reports them.
5. **Given** two runs started at the same time, **When** both finish, **Then** each run's records
   contain only its own inputs, outputs, and model exchange.
6. **Given** runs recorded before the migration, **When** the learner opens them after it, **Then**
   they display exactly as before.

---

### User Story 2 - Failures still stop the chain and say why (Priority: P1)

When a step fails, the chain stops at that step, the run is marked failed or timed out, the step that
failed is named, and the learner reads the same kind of actionable reason as before. The steps that
completed before it keep their records.

**Why this priority**: Failure handling is where orchestration changes most often break behavior, and
feature 001's SC-006 (the learner can name the failed step and why from the screen alone) must hold.
It shares P1 because a migration that works only on the happy path is not shippable.

**Independent Test**: Induce each failure listed below with a fake model or bad configuration and
confirm the run status, failed step, reason, and preserved earlier records match the pre-migration
behavior.

**Acceptance Scenarios**:

1. **Given** the model call exceeds the configured time limit, **When** the run ends, **Then** its status
   is TIMED_OUT, the failed step is Summarize, and the reason says the indicators were computed and only
   the summary is missing.
2. **Given** the provider rejects the credential, **When** the run ends, **Then** its status is FAILED,
   the failed step is Summarize, and the reason names the credential and provider-mode settings to
   check, without containing the credential.
3. **Given** the model returns an empty response, **When** the run ends, **Then** its status is FAILED at
   Summarize with a reason saying the response was empty.
4. **Given** a deterministic step fails (for example, no records match the selection), **When** the run
   ends, **Then** the chain stops at that step, later steps do not run, and the model is never called.
5. **Given** a step produces output that violates its boundary contract, **When** the run continues,
   **Then** it fails at that step instead of passing invalid data to the next step.
6. **Given** any unexpected error inside a step, **When** the run ends, **Then** the learner sees a
   generic reason naming the step, never a stack trace.
7. **Given** any failure after at least one step succeeded, **When** the learner opens the run, **Then**
   the records of the steps that succeeded are present with their outputs.

---

### User Story 3 - Only the summarizing step reaches the model (Priority: P2)

The three deterministic steps never call the model, whatever the orchestration does internally, and
their results remain exact and repeatable.

**Why this priority**: Feature 001's FR-007 and the constitution's "declarative-first does not mean
model-first" rule. It is guarded by tests rather than visible on screen, so it follows the two P1
stories.

**Independent Test**: Run the chain with a fake model that counts calls and confirm exactly one call per
successful run, made by the summarizing step, and zero calls when a deterministic step fails.

**Acceptance Scenarios**:

1. **Given** a successful run against a counting fake model, **When** it finishes, **Then** the model was
   called exactly once.
2. **Given** a run that fails in any of the first three steps, **When** it ends, **Then** the model was
   called zero times.
3. **Given** the same selection run ten times, **When** the indicator values are compared, **Then** all
   ten are byte-identical (feature 001, SC-005).

---

### User Story 4 - The summarizer is a declaration, and its behavior is tested through it (Priority: P2)

A maintainer reads the summarizing agent as one declaration: its instructions, its input, and its
output. Tests exercise that declaration with a fake model and assert on the messages it produces and on
how the result is handled, never on exact model wording.

**Why this priority**: This is the constitutional change the feature exists for (Principle I and IV), and
it is what a maintainer sees. It follows the behavior-preserving stories because behavior is what must
not regress.

**Independent Test**: Read the summarizer's declaration and confirm the system instruction, the rendered
indicator table, and the result type are declared there. Run its tests with a fake model.

**Acceptance Scenarios**:

1. **Given** the summarizer declaration, **When** it is invoked with an indicator set against a fake model,
   **Then** the messages the model receives contain the fictional-data framing, the verbatim-values rule,
   the no-advice rule, and the rendered indicator table including not-applicable entries with reasons.
2. **Given** a model response longer than the stored limit, **When** the summary is recorded, **Then** it
   is truncated and marked exactly as before.
3. **Given** a response containing a figure that is not among the computed indicators, **When** the
   traceability check from feature 001 runs, **Then** it detects the figure.
4. **Given** the codebase after the migration, **When** a reviewer searches for hand-written orchestration
   of the steps or hand-assembled model calls, **Then** none remains, except any use recorded as a
   justified exception in the plan.

---

### User Story 5 - Traces come from the library's observation hooks (Priority: P3)

The per-step records and the model exchange are captured through the observation mechanisms LangChain4j
provides, and persisted in the same form as today, so the history and detail screens need no change.

**Why this priority**: Required by constitution Principle V as amended, and invisible to the learner as
long as User Story 1 holds.

**Independent Test**: Start a run and confirm its records are complete, then confirm by review that the
capture is attached to the library's hooks and that any hand-filled field is named in the plan.

**Acceptance Scenarios**:

1. **Given** a completed run, **When** its records are read, **Then** every field feature 001 requires is
   populated as before (FR-009, FR-010).
2. **Given** the capture mechanism, **When** a reviewer inspects it, **Then** model request, response, and
   token data come from the library's model observation hook, and step start, end, input, and output come
   from the orchestration's observation hooks, with any remaining gap listed in the plan.

---

### Edge Cases

- **Orchestrator and deterministic steps**: if the orchestration cannot run plain deterministic code as a
  step at the pinned library version, the plan must choose a compliant alternative (a version change under
  the single-version rule, or a justified exception) rather than delegating computation to a model.
- **Boundary validation**: the orchestrator passes values between steps through its own shared state. The
  boundary contracts from feature 001 (`contracts/node-boundaries.md`) must still be enforced at every
  step, not only at the ends.
- **Concurrent runs**: library observation hooks may be shared across runs. Records must never mix data
  from two runs executing at the same time.
- **Retries inside the library**: if the library retries a model call, the recorded exchange and token
  counts must describe what the learner's run actually used, and the time limit must still bound the whole
  summarizing step.
- **Credential in library errors**: errors raised by the library may echo request details. The existing
  guarantee that no credential reaches a record, a log line, or the screen must still hold (feature 001,
  FR-018).
- **Runs in flight during upgrade**: a run started before the migrated application is deployed and still
  running when it stops is the existing orphaned-run limitation (feature 004); this feature does not
  change it.
- **Live provider**: the separately selectable live model test must still pass in at least one provider
  mode and still skip with a named reason when no provider is configured.

## Requirements *(mandatory)*

### Functional Requirements

**Behavior preserved**

- **FR-001**: All functional requirements of feature 001 (FR-001 to FR-021) MUST continue to hold.
- **FR-002**: The HTTP API MUST be unchanged: the same endpoints, request and response shapes, status codes,
  and error shapes. The generated API description MUST be unchanged, so the frontend's committed types still
  match without regeneration.
- **FR-003**: Stored data MUST be unchanged: no database migration, and every field of a run and of a step
  record MUST be populated with the same meaning and format as before.
- **FR-004**: The user interface MUST be unchanged.
- **FR-005**: Step names, their order, run statuses (PENDING, RUNNING, SUCCEEDED, FAILED, TIMED_OUT), and
  the learner-facing failure reasons MUST be the same as before for the same conditions.
- **FR-006**: Indicator values MUST remain byte-identical for the same selection before and after the
  migration.

**Declarative summarizer**

- **FR-007**: The summarizing step MUST be a declared AI agent whose instructions, input, and output are
  declared in one place, with no hand-assembled model request.
- **FR-008**: The summarizer's instructions MUST carry the same rules as today: fictional data, only the
  supplied indicators, values reproduced exactly, not-applicable indicators stated with their reason, no
  recommendation or advice, four to six plain sentences.
- **FR-009**: Empty-response detection, truncation with its marker, time-limit handling, credential-rejection
  handling, and credential redaction MUST behave as before.

**Declarative orchestration**

- **FR-010**: The four steps MUST be sequenced by LangChain4j's agentic orchestration. No hand-written loop,
  runner, or scheduler may sequence them.
- **FR-011**: PrepareRequest, RetrieveRecords, and ComputeIndicators MUST remain deterministic code with
  unchanged logic, and MUST NOT call the model.
- **FR-012**: The boundary contract between each pair of steps MUST be validated at each step, and a
  violation MUST fail the run at that step.
- **FR-013**: A failure in any step MUST stop the sequence, and the outcome MUST carry the failed step's
  name and reason.
- **FR-014**: The currently running step MUST be observable while a run is in progress, so the screen can
  name it.
- **FR-015**: Runs MUST continue to execute off the request thread, and two concurrent runs MUST remain
  independent.

**Traces**

- **FR-016**: Step timing, inputs, outputs, outcomes, the model request and response text, and token counts
  MUST be captured through LangChain4j's observation mechanisms wherever the library provides them. Any field
  filled by hand MUST be named, with the reason, in the plan.
- **FR-017**: The captured trace MUST be persisted in the existing step-record structure, and the provider
  mode and model identifier MUST still be recorded on every run.
- **FR-018**: No credential, in whole or in part, may appear in any captured trace, log line, or error shown
  to the learner.

**Tests and compliance**

- **FR-019**: Tests written against the hand-written runner and the hand-assembled summarizer MUST be replaced
  by tests of the same behaviors against the declared summarizer and the orchestrated sequence, using a fake
  model and asserting structure, not model wording. No behavior covered today may lose its test.
- **FR-020**: The live model test MUST exercise the declared summarizer.
- **FR-021**: Code that exists only to support the hand-written orchestration or the hand-carried model
  exchange MUST be removed.
- **FR-022**: The README's reading path MUST describe the declared summarizer and the orchestrated sequence
  instead of the hand-written loop and the single hand-written model call.
- **FR-023**: The feature plan MUST record the LangChain4j version and modules used, whether the Micronaut
  LangChain4j integration or LangChain4j's own AI Services builder creates the summarizer, and every
  justified exception to Principle I.

### Key Entities

- **Run**: unchanged. Selection, status, current step, failed step and reason, provider mode, model
  identifier, summary text, start and end times.
- **Step record**: unchanged. Position, step name, input, output, success, failure reason, start time,
  duration, and for Summarize the model request text, response text, and token counts.
- **Summarizer declaration**: the declared AI agent that receives an indicator set and returns a summary,
  carrying the instructions from FR-008.
- **Sequence declaration**: the declared ordering of the four steps and the values passed between them.
- **Trace capture**: the observation hooks that turn library events into step records for one run.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100 percent of the backend tests that assert externally visible behavior (API responses,
  stored records, statuses, failure reasons, credential safety, indicator values) pass against the migrated
  chain, whether unchanged or rewritten under FR-019, and no behavior tested before the migration is left
  untested.
- **SC-002**: The API contract check passes with the frontend's committed types unchanged, and the frontend
  test suite passes unchanged.
- **SC-003**: For every company and period in the sample dataset, indicator values from a run after the
  migration are byte-identical to those from a run before it.
- **SC-004**: Across ten successful runs against a counting fake model, the model is called exactly ten
  times; across runs failing in each of the first three steps, zero times.
- **SC-005**: Each induced failure listed in User Story 2 produces the same status, failed step, and reason
  category as before, in 100 percent of trials.
- **SC-006**: A review of the backend finds zero hand-written sequencing of the steps and zero hand-assembled
  model requests outside justified exceptions recorded in the plan.
- **SC-007**: A run completed after the migration in at least one provider mode shows all four step records
  with every field feature 001 requires, and runs recorded before the migration still open and display
  unchanged.
- **SC-008**: The root verification command passes, with backend test counts changing only by tests replaced
  or added under FR-019, each accounted for in the tasks.

## Assumptions

- **Model request text**: today the stored "text sent to the model" is the rendered user message (the
  indicator table); the system instruction is a fixed declaration. The migration keeps that meaning so old
  and new runs are comparable. Recording the full message list instead would change the meaning of a stored
  field and is out of scope.
- **Library availability**: resolved in planning (research R-001, 2026-09-13). LangChain4j's agentic module is
  available as `1.18.0-beta28`, managed by the same `langchain4j-bom:1.18.0` the Micronaut Platform BOM pins,
  and supports deterministic (non-AI) steps. It is labeled beta.
- **Wiring choice**: whether the summarizer is created by the Micronaut LangChain4j integration or by
  LangChain4j's own AI Services builder in a Micronaut factory is a plan decision (constitution, Agent
  framework).
- **Model bean**: the configuration-driven model setup may move to the Micronaut LangChain4j integration only
  if it still fails at startup naming the absent setting and never logs the credential (Principle II);
  otherwise the existing factory stays.
- **Persistence timing**: step records are written when the run ends, as today. Writing them incrementally is
  a behavior change and out of scope.
- **Out of scope**: new agents, tools, chat memory, retrieval, MCP, A2A, streaming, and any UI or API change.
  Those become possible after this migration and belong to later features.
- **Historical documents**: feature 001's research decisions R-003 and R-010 stay as history; this feature's
  plan supersedes them.
