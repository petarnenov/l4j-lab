# Feature Specification: Financial Agent Chain

**Feature Branch**: `001-financial-agent-chain`

**Created**: 2026-09-12

**Status**: Approved, 2026-09-12. Planning and task generation are complete against
constitution v2.2.0, and the quality checklist in `checklists/requirements.md` passes.

**Input**: User description: "Правим симпле агентик чейн с 4 възли и на последната използваме модела за съмъри на резултатите. Възлите нека да са учебни на финансова тематика. Целта да видят базовите концепции. Моделът ще е от клоуда на оллама гпт-оси-120 ще приложа ключ в енв проментлива"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Run the chain and read the summary (Priority: P1)

A learner opens the application, picks one of the sample companies and a reporting period,
and starts the chain. Four nodes run in order. The first prepares a structured request from
the learner's selection. The second retrieves the matching financial records. The third
computes a set of standard financial indicators from those records. The fourth asks the
language model to turn the computed indicators into a short plain-language summary. The
learner sees the summary and the indicators it was built from.

**Why this priority**: This is the whole point of the exercise. Without a run that produces
a visible result, there is nothing to learn from and no other story has anything to display.
It is the minimum viable slice on its own.

**Independent Test**: Select a sample company and period, start the run, and confirm a
summary appears alongside the computed indicators. Delivers the complete end-to-end concept
of a chain in a single interaction.

**Acceptance Scenarios**:

1. **Given** the sample dataset contains records for the selected company and period,
   **When** the learner starts a run, **Then** all four nodes complete in order and a
   plain-language summary is displayed together with the computed indicators.
2. **Given** a run has completed, **When** the learner reads the summary, **Then** the
   summary refers to indicators that appear in the displayed indicator set and introduces no
   figure that was not computed.
3. **Given** the learner starts a second run with a different company, **When** that run
   completes, **Then** the summary reflects the second company's figures and the first run's
   result remains unchanged.

---

### User Story 2 - Inspect what each node did (Priority: P2)

After a run, the learner opens the run's detail view and steps through the four nodes. For
each node they see what it received, what it produced, how long it took, and whether it
called the language model. The step where the model is involved shows the exact text that was
sent and the text that came back.

**Why this priority**: Seeing the data flow between nodes is what converts a working demo
into an understood concept. It is the difference between a black box and a lesson, but it
depends on a run existing first.

**Independent Test**: Complete one run, open its detail view, and confirm each of the four
node boundaries is listed with its input, its output, and its duration. Delivers the
explanatory value independently of any history or comparison features.

**Acceptance Scenarios**:

1. **Given** a completed run, **When** the learner opens its detail view, **Then** exactly
   four node entries are shown in execution order, each with its input, its output, and its
   duration.
2. **Given** a completed run, **When** the learner opens the summarizing node, **Then** the
   full text sent to the model and the full text returned are both visible.
3. **Given** a run where a node failed, **When** the learner opens its detail view, **Then**
   the failing node is clearly marked, the reason is shown, and the nodes that did complete
   still display their input and output.

---

### User Story 3 - Revisit earlier runs (Priority: P3)

The learner returns to the application later, sees a list of previous runs with their company,
period, timestamp, and outcome, and opens any one of them to read its summary and node detail
exactly as it was produced.

**Why this priority**: Persistence lets a learner compare how the same chain behaves across
inputs and across repeated runs of the same input, which is how the non-determinism of the
model becomes visible. Valuable but not required to teach the core concept.

**Independent Test**: Complete two runs, reload the application, and confirm both appear in
the history list and open with their original content intact.

**Acceptance Scenarios**:

1. **Given** several completed runs, **When** the learner opens the history list, **Then**
   every run is listed with its company, period, start time, and outcome, newest first.
2. **Given** the application has been restarted, **When** the learner opens a run from
   history, **Then** its summary and all four node records are unchanged from when the run
   completed.
3. **Given** the learner runs the same company and period twice, **When** they open both
   runs, **Then** the computed indicators are identical and the two summaries can be read
   side by side.

---

### Edge Cases

- The model service is unreachable or the credential is missing or rejected. The first three
  nodes have already produced valid indicators, so the run must be reported as failed at the
  summarizing node while still showing everything the earlier nodes produced.
- The selected company and period combination has no record in the sample dataset. The
  retrieval node must fail with a message naming what was missing rather than passing empty
  data down the chain.
- A record is present but a field needed for an indicator is absent, for example equity of
  zero when computing a debt-to-equity ratio. The computing node must report the indicator as
  not applicable rather than producing an infinite or invented value.
- The model returns an empty response, or a response so long it would fill the screen. The
  run must still complete with the raw response preserved and displayed within a bounded area.
- The model takes an unusually long time. The learner must see that the summarizing node is
  still running rather than a frozen screen, and the run must end with a clear timeout outcome
  if the wait exceeds the configured limit.
- The learner starts a second run while the first is still running. Both runs must complete
  independently and neither may overwrite the other's records.
- The model's summary contradicts the computed figures. The indicators remain the displayed
  source of truth and are shown next to the summary so the discrepancy is visible.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST execute a chain of exactly four named nodes in a fixed order for
  every run, and the order MUST be the same for every run.
- **FR-002**: The first node MUST turn the learner's selection into a structured request
  containing the company identifier and the reporting period, and MUST reject a selection that
  is incomplete before any later node runs.
- **FR-003**: The second node MUST retrieve the financial records matching the structured
  request from the seeded sample dataset, and MUST fail with a message naming the missing
  company or period when no record matches.
- **FR-004**: The third node MUST compute a fixed set of financial indicators from the
  retrieved records using deterministic arithmetic, producing identical output for identical
  input on every run.
- **FR-005**: The indicator set MUST include revenue growth against the prior period, gross
  margin, net margin, current ratio, and debt-to-equity ratio, and each indicator MUST be
  labelled as not applicable when its inputs do not permit a valid result.
- **FR-006**: The fourth node MUST send the computed indicators to the language model and MUST
  return the model's plain-language summary of them.
- **FR-007**: The fourth node MUST be the only node that calls the language model. Nodes one
  through three MUST produce their results without any model call.
- **FR-008**: Each node MUST receive only the output of the node before it, and the boundary
  between nodes MUST be a declared, validated structure rather than free-form text.
- **FR-009**: The system MUST record, for every run, an ordered entry per node holding that
  node's input, its output, its start time, its duration, and whether it succeeded.
- **FR-010**: The record for the summarizing node MUST additionally hold the exact text sent to
  the model, the exact text returned, the model identifier used, and the token counts when the
  model service reports them.
- **FR-011**: The records required by FR-009 and FR-010 MUST survive an application restart,
  unchanged and completely. This requirement is about durability only; what those records hold
  is defined once, in FR-009 and FR-010, and is not restated here.
- **FR-012**: Learners MUST be able to view a completed run's summary together with the
  indicators the summary was built from, on the same screen.
- **FR-013**: Learners MUST be able to step through all four node records of any run and read
  each node's input and output.
- **FR-014**: Learners MUST be able to list previous runs, newest first, with company, period,
  start time, and outcome, and open any of them.
- **FR-015**: When any node fails, the system MUST stop the chain, mark the run as failed at
  that node, state the reason in language a learner can act on, and retain the records of the
  nodes that already completed.
- **FR-016**: The system MUST read the model credential from an environment variable at
  startup and MUST fail with an explicit, named message when that variable is absent, rather
  than failing at the moment the fourth node runs.
- **FR-017**: The model identifier MUST be configurable without a code change.
- **FR-018**: The system MUST NOT write the model credential, in whole or in part, into any
  run record, log line, or screen.
- **FR-019**: The system MUST enforce a configurable time limit on the model call and MUST end
  the run with a stated timeout outcome when the limit is exceeded.
- **FR-020**: The system MUST show a run in progress, including which node is currently
  executing, while the chain is still running.
- **FR-021**: The seeded sample dataset MUST be committed with the project and MUST be
  sufficient to run every scenario in this specification without any network call beyond the
  model service.

### Key Entities *(include if feature involves data)*

- **Chain Run**: One execution of the four-node chain. Holds the learner's original selection,
  the start and end time, the overall outcome, and the node at which it stopped if it failed.
- **Node Execution**: One node's participation in a run. Holds the node's name, its position
  in the chain, the input it received, the output it produced, its duration, and its success or
  failure with reason. The summarizing node's entry additionally holds the model exchange.
- **Financial Record**: One company's reported figures for one period in the sample dataset.
  Holds revenue, cost of goods sold, net income, current assets, current liabilities, total
  debt, and equity.
- **Indicator Set**: The computed output of the third node for one run. Holds each indicator's
  name, its value or its not-applicable marker, and the record fields it was derived from.
- **Run Summary**: The plain-language text produced by the fourth node for one run, held
  alongside the model identifier that produced it.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A learner who has never seen the project completes their first full run within
  five minutes of opening the application, without reading any source code.
- **SC-002**: Every completed run exposes all four node boundaries with input and output
  visible, in one hundred percent of runs.
- **SC-003**: Across ten runs over the sample dataset, every figure appearing in the summary
  is traceable to a computed indicator, with no invented numbers.
- **SC-004**: Ninety-five percent of runs against the sample dataset finish end to end within
  sixty seconds.
- **SC-005**: Repeating the same run produces byte-identical indicator values every time,
  across at least ten repetitions.
- **SC-006**: In one hundred percent of induced failures, the learner can name which node
  failed and why from the screen alone.
- **SC-007**: Eight of ten learners can describe, in their own words, what each of the four
  nodes did after a single run and one look at the node detail view. This one is observational,
  confirmed by watching learners rather than by an automated check, and it is deliberately
  outside the test suite. It is the criterion the whole feature exists to satisfy, so it is
  stated here rather than dropped for being unautomatable.
- **SC-008**: A reviewer can point to the single place where the model is called, within
  thirty seconds of opening the project.

## Assumptions

- Only the fourth node calls the language model. The description assigns the model to the last
  node for summarization, and keeping the first three deterministic makes the chain's plumbing
  visible and testable. Adding model calls to earlier nodes is a separate feature.
- The financial data is a small, fixed sample dataset committed with the project, covering a
  handful of fictional companies across several periods. No live market data feed or external
  financial API is in scope.
- The companies and figures are fictional and labelled as such. Nothing in this feature is
  investment advice, and the summary carries a visible note to that effect.
- The learner selects a company and period from the seeded options. Free-text financial
  questions are out of scope for this feature.
- The set of four nodes and their order are fixed in this feature. Configuring, reordering, or
  adding nodes is out of scope.
- The indicator set is fixed at the five indicators named in FR-005. Learner-defined
  indicators are out of scope.
- A single learner uses a local instance. Accounts, authentication, and multi-user separation
  are out of scope.
- The chain runs to completion or fails. Retrying a failed node, resuming a partial run, and
  branching or looping between nodes are out of scope and belong to later exercises.

## Dependencies

- **Language model service**: the summarizing node depends on the hosted Ollama Cloud service
  and the model `gpt-oss:120b`. The colon form is the tag Ollama actually resolves; the original
  request wrote it without one. The service credential is supplied by the operator through an
  environment variable and is never committed.
- **Constitutional standing, resolved**: this feature originally conflicted with Principle II
  of the project constitution, which required local-first execution with no cloud model
  provider and no outbound API key. Constitution v2.0.0 replaced that principle with
  Provider-Agnostic Inference, which permits the hosted service alongside a locally served
  one and sets the credential and data-egress rules this feature must follow. The conflict no
  longer blocks planning.
- **Persistence**: run and node records depend on the project datastore being available at
  startup, per the locked stack in the constitution.
