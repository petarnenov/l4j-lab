# Phase 0 Research: LangChain4j Declarative Migration

**Date**: 2026-09-13 | **Plan**: [plan.md](./plan.md)

All unknowns are resolved here. Facts marked **Verified** were read on 2026-09-13 from Maven Central
artifacts (POMs, class files with `javap`, and sources jars) for the exact versions named. Nothing here was
taken from memory of the library.

## R-001: Is LangChain4j's agentic orchestration available at the pinned version?

**Decision**: Yes. Use `dev.langchain4j:langchain4j-agentic`, version `1.18.0-beta28`, managed by the BOMs the
build already imports. Add no version number to the build file.

**Verified**:

- The Micronaut Platform BOM 5.1.5 sets `langchain4j.version` to `1.18.0` and imports `langchain4j-bom`.
- `langchain4j-bom:1.18.0` manages `langchain4j-agentic` at `${langchain4j.beta.version}`, which it sets to
  `1.18.0-beta28`. `langchain4j-agentic:1.18.0` does not exist; the `-beta28` artifact is the one released with
  core 1.18.0, and its POM depends on `dev.langchain4j:langchain4j` (the AI Services module).
- The same platform BOM also manages `io.micronaut.langchain4j:micronaut-langchain4j-agentic:2.2.0`, built
  against `langchain4j-bom:1.18.0`.

**Constitution**: this resolves `TODO(AGENTIC_MODULE_AVAILABILITY)`. The single-version rule holds: every
LangChain4j module resolves from the one `langchain4j-bom:1.18.0`, and the `-beta28` suffix is the library's own
versioning for modules released in the same release train, not a second version.

**Risk accepted**: the module is labeled beta. Its API may change on the next LangChain4j upgrade. The plan records
this, and the tests written under FR-019 are the upgrade guard.

## R-002: The four steps as an agentic sequence

**Decision**: A typed sequence interface, `FinancialChain`, built with `AgenticServices.sequenceBuilder(FinancialChain.class)`
in a Micronaut `@Factory`, with the four steps passed as bean instances to `subAgents(...)` in order:
`PrepareRequestNode`, `RetrieveRecordsNode`, `ComputeIndicatorsNode`, and the `Summarizer` agent.

**Verified** (`langchain4j-agentic-1.18.0-beta28`):

- `AgenticServices` offers `sequenceBuilder(Class<T>)`, and its builder (`AbstractServiceBuilder`) offers
  `subAgents(Object...)`, `listener(AgentListener)`, `errorHandler(Function<ErrorContext, ErrorRecoveryResult>)`,
  `outputKey(String)`, `output(Function<AgenticScope, Object>)`, and `executor(Executor)`.
- `AgentUtil.agentsToExecutors` accepts plain object instances: an object that is not an `InternalAgent` is treated as
  a non-AI agent, and its method annotated `@Agent` is found by reflection on `agent.getClass()`
  (`nonAiAgentToExecutor`). Deterministic code can therefore be a step, as `NonAiAgentInstance` confirms.
- `@Agent` carries `name`, `description`, `outputKey`, `async`, `optional`, and `summarizedContext`.

**Rationale**: the steps depend on Micronaut beans (`SampleDatasetLoader`, `Clock`, the configured `ChatModel`), and
dependency injection must stay compile-time (constitution, Application framework). Passing the beans as instances is
the library's supported way to compose them.

**Justified exception to "declarative API MUST be used" (Principle I)**: LangChain4j also offers a purely declarative
form, `@SequenceAgent(subAgents = { A.class, B.class })` with `AgenticServices.createAgenticSystem(...)`. In that form the
library instantiates each sub-agent class itself and obtains models, listeners, and handlers from **static** supplier
methods (`@ChatModelSupplier`, `@AgentListenerSupplier`, `@ErrorHandler` are all documented as static). A static method
cannot receive a Micronaut bean without a static service locator, which the constitution's compile-time injection rule
forbids. The sequence's *contract* stays declared (the `FinancialChain` interface and each step's `@Agent`
annotation); only its assembly uses the builder.

**Alternatives considered**:

- `@SequenceAgent` with static suppliers reaching beans through a static holder. Rejected: a service locator beside
  compile-time injection.
- Micronaut's `@AgenticService` (`micronaut-langchain4j-agentic`). Its annotation exposes `value`, `named`, `tools`, and
  `outputKey`, and its factory resolves the chat model and tools from the bean context. It was not verified to assemble
  a sequence of bean-instance non-AI steps with a listener, so adopting it would rest on an assumption. Rejected for
  this feature; the plan does not add the module.
- Keeping `ChainRunner`. Forbidden by Principle I.

## R-003: The summarizer as an AI agent declaration

**Decision**: An interface `Summarizer` with one method, annotated `@Agent(name = "Summarize", outputKey = "summaryText")`,
`@SystemMessage` carrying today's instruction text, and `@UserMessage("{{indicators}}")`, taking
`@V("indicators") IndicatorSet indicators` and returning `String`. Built with
`AgenticServices.agentBuilder(Summarizer.class).chatModel(chatModel).outputGuardrails(...).build()` in the same factory.

**Verified**:

- `AgentBuilder` offers `chatModel(ChatModel)`, `outputGuardrails(...)`, `inputGuardrails(...)`, `tools(...)`, `chatMemory(...)`,
  `contentRetriever(...)`, and passes system and user message providers through to AI Services.
- LangChain4j core's `DefaultPromptTemplateFactory` renders a template variable with `value.toString()`.

**Prompt rendering**: the user message is exactly today's rendered indicator table. `IndicatorSet.toString()` is overridden
to return that table, produced by the existing deterministic rendering (moved from `SummarizeNode.renderPrompt` to
`IndicatorSet`), so `{{indicators}}` renders it. A comment on the override states that it is the prompt form. This keeps the
table in one tested function and adds no fifth step. The system instruction text is unchanged character for character.

**Alternatives considered**:

- `@UserMessageProviderSupplier` or `AgentBuilder.userMessageProvider`. **Verified**: both receive only the memory id, not
  the scope or arguments, so neither can see the indicators.
- A fifth step that renders the table into the scope. Rejected: it adds a recorded step (FR-005 requires four).
- Changing `ComputeIndicators` to also output the table. Rejected: it changes a stored step output (FR-003).

## R-004: Wiring style and the chat model bean

**Decision**: LangChain4j's own builders inside one Micronaut `@Factory` (`FinancialChainFactory`) create both the
`Summarizer` and the `FinancialChain`, each exposed as a `@Singleton` bean. The existing `ChatModelFactory` stays and supplies
the `ChatModel`.

**Rationale**: the constitution requires each AI Service to be created in exactly one way and the plan to say which (FR-023).
`ChatModelFactory` already enforces Principle II exactly as tested today: startup failure naming the absent variable, a bare base
URL check, and the credential sent through a header supplier that no logger or serializer can reach. Moving to Micronaut
LangChain4j's configuration-driven Ollama beans would need all of that re-verified for no capability this feature uses.

**Justified exception (Principle I, "every capability from the library")**: the model bean is built with LangChain4j's
`OllamaChatModel` builder, which is a LangChain4j API, inside a hand-written factory rather than through the Micronaut integration.
The constitution permits the integration ("MAY"); it does not require it.

## R-005: Traces through LangChain4j's observation hooks

> **Revised 2026-09-13 (R-012).** The design below built a custom `RunTraceListener` that tracked timing, inputs, outputs,
> tokens, and the first error itself. `AgentMonitor` / `MonitoredAgent` already do that, and `@MemoryId`,
> `AgenticScopeAccess`, and `ChatMessagesAccess` cover correlation, scope reads, and the model exchange. It was not considered
> here, which violated Principle I. R-012 records the replacement; this entry is kept as history. The T020 finding below
> (plain object steps get no inherited listener) still holds and still applies to `AgentMonitor`.

**Decision**: One `AgentListener` implementation, `RunTraceListener`, attached to the sequence with `listener(...)`, turns agentic
events into the existing step records for one run, collected per run and persisted by `ChainRunService` when the run ends.

**Verified** (`AgentListener`, `AgentRequest`, `AgentResponse`, `AgentInvocationError`):

| Event | Carries | Becomes |
|-------|---------|---------|
| `beforeAgentInvocation(AgentRequest)` | `agentName()`, `inputs()`, `agenticScope()` | step start time; current step on the run (FR-014) |
| `afterAgentInvocation(AgentResponse)` | `agentName()`, `inputs()`, `output()`, `chatRequest()`, `chatResponse()` | success record: input, output, duration; for Summarize, request text, response text, token counts |
| `onAgentInvocationError(AgentInvocationError)` | `agentName()`, `inputs()`, `error()` | failure record with the classified reason |

The listener also sees the sequence itself as an agent; events whose `agentName()` is not one of the four step names are ignored.

**Correlation across concurrent runs**: the run id enters the sequence as an input (`@V("runId")`), so it is in each invocation's
`AgenticScope`. The listener reads it from the scope and appends to that run's collector. No thread-local is used.

**Verify during implementation**: whether a listener attached to the sequence receives sub-agent events (`inheritedBySubagents()`
defaults to its interface default). The listener MUST be registered through exactly one path, so every event arrives once: either the
sequence alone, or the sequence with `inheritedBySubagents()` returning `true`. It is never also attached to the `Summarizer` builder in
addition to an inheriting sequence listener. The tests assert exactly four records per run, with no duplicate step names.

**Verified during implementation (T020, 2026-09-13)**: with the listener on the sequence and
`inheritedBySubagents()` returning `true`, the `Summarizer` reported its events but the three deterministic steps
reported none. Reading the 1.18.0-beta28 sources: a workflow agent's `setParent` calls
`registerInheritedParentListener`, and so does an AI agent's, but `NonAiAgentInstance.setParent` only stores the
parent. A plain object step placed directly in a sequence therefore never receives an inherited listener. A
workflow agent does push the inherited listener down to its own sub-agents, including plain objects. **Resolution,
public API only**: each deterministic step is wrapped in a one-step sequence named `step:<StepName>` in
`FinancialChainFactory`. The listener ignores the wrapper's events (its name is not a step name) and records the
inner step's, whose `inputs()` hold exactly that step's argument. The listener is still attached in exactly one
place, the outer sequence. A debug run showed one before and one after event per step, and `FinancialChainTest`
asserts four records with no duplicates. The wrappers carry a comment to remove them when the library registers
inherited listeners for plain object steps.

**Hand-filled fields, named per Principle V**:

1. **Model request text on a failed Summarize call.** `AgentInvocationError` carries no `ChatRequest`. Today's behavior records the
   prompt even when the call fails (feature 001 test `recordsTheExactPromptEvenWhenTheCallFails`). The listener fills it with
   `String.valueOf(inputs.get("indicators"))`, which is exactly what the `{{indicators}}` template rendered.
2. **The Summarize step's recorded output.** The agent returns the raw text; the record's output is the existing `RunSummary`
   (truncated text with marker, model identifier, provider mode, token counts), built by a deterministic factory from the response
   event and configuration.
3. **Input payload shape.** `inputs()` is a map of argument names to values. Each step has one data argument, so the record's input
   is that single value, matching the stored format today. The run id argument is excluded.

**Critical detail**: `ListenerNotifierUtil` catches and logs every exception a listener throws. A listener therefore cannot fail a
run. Anything that must stop a run (boundary validation) is not placed in the listener.

## R-006: Boundary validation between steps

**Decision**: Each deterministic step validates its own output with the injected Micronaut `Validator` before returning, through one
shared helper, `BoundaryValidation.requireValid(step, output)`. A violation throws `BoundaryViolation`, an unchecked exception that is
**not** a `ChainFailure`, carrying today's detail ("`<step>` produced an invalid `<Type>`: `<violations>`", or "`<step>` produced no
output"). The classifier treats it as an unexpected error, so the learner sees exactly today's reason, "`<step>` failed unexpectedly. See
the server log for detail.", and the detail goes to the server log only.

**Why not `ChainFailure`** (found in analysis, 2026-09-13): `ChainRunner.validateBoundary` throws `IllegalStateException` inside the
runner's `try`, where `catch (RuntimeException e)` turns it into the generic reason. A `ChainFailure` would put the validation detail on
screen, which FR-005 forbids. No existing test covers this path, so `FinancialChainFailureTest` adds one.

**The Summarize output** (`RunSummary`) was also validated by the runner. The listener cannot fail a run (R-005), so `ChainRunService`
validates the `RunSummary` taken from the trace after `FinancialChain.run` returns, with the same helper. A violation fails the run at
Summarize with the generic reason, and the Summarize record is replaced by a failure record that keeps its request and response text
and has no output, exactly as the runner's `failureRecord` did.

**Reachability** (found in analysis): through the running application this branch cannot be reached. `RunSummary`'s constraints are
`@NotBlank text` (a blank reply is stopped earlier by the guardrail), `@Size(max = 4000) text` (prevented by truncation in
`RunSummary.from`), and `@NotBlank modelId` (an absent `L4J_MODEL_ID` stops startup in `ChatModelFactory`). The check stays as defence in
depth, matching the runner, and is tested at the `ChainRunService` level with a stubbed trace rather than through the chain.

**Rationale**: the runner did this between steps. Listener exceptions are swallowed (R-005), so validation must happen where an
exception reaches the orchestrator: inside the step. Declarative `@Valid` on the return type would need a Micronaut AOP proxy, and
the orchestrator finds `@Agent` by reflection on the instance's runtime class, where a proxy subclass's overriding method does not
carry the annotation. That combination was not verified and is not relied on. Validation is Micronaut's capability, not
LangChain4j's, so Principle I's library-first rule is not engaged.

The Summarize step's output is validated by `ChainRunService` after the sequence returns, as described above.

## R-007: Failure outcomes

**Decision**:

- Deterministic steps keep throwing `ChainFailure` with their current messages.
- An empty model response is rejected by a LangChain4j `OutputGuardrail` on the `Summarizer` (`NonBlankSummaryGuardrail`), which fails
  without retry. **Verified**: `OutputGuardrail`, `OutputGuardrailResult`, and `OutputGuardrailException` exist in core 1.18.0.
- The sequence throws `AgentInvocationException` wrapping the cause. `ChainRunService` catches it and asks a deterministic
  `FailureClassifier` for the outcome, using the failed step name recorded by the listener and the cause chain:

| Failed step | Cause | Status | Reason |
|-------------|-------|--------|--------|
| any | `ChainFailure` | FAILED | the step's message, unchanged |
| Summarize | `OutputGuardrailException` from `NonBlankSummaryGuardrail` | FAILED | today's empty-response message |
| Summarize | timeout in the cause chain | TIMED_OUT | today's timeout message naming the configured seconds |
| Summarize | 401, 403, "unauthorized", "forbidden" in the cause chain | FAILED | today's credential message naming `OLLAMA_API_KEY` and `L4J_PROVIDER`, redacted |
| Summarize | any other exception | FAILED | today's "could not be reached or rejected the request" message, redacted and capped |
| PrepareRequest, RetrieveRecords, ComputeIndicators | any other exception, including `BoundaryViolation` | FAILED | "`<step>` failed unexpectedly. See the server log for detail." |

**Scoping to Summarize** (found in analysis): today only `SummarizeNode` produces the timeout, credential, and "could not be reached"
outcomes, because only its `try` wraps the model call, and it turns **every** runtime exception from the call into the "could not be
reached" reason. A deterministic step whose exception merely mentions "timeout" or "401" produced the generic reason. The table keeps
both facts.

**Empty response record** (found in analysis; resolved by R-012): with the first design, the failure event carried no
`ChatResponse`, so the blank reply would have been stored as `null`, and this was recorded as an accepted deviation. With R-012 the
response text comes from the summarizer's `ChatMessagesAccess`, which the library fills when the response arrives, before the
output guardrail rejects it. The blank reply is stored exactly as before the migration, and the deviation no longer exists.

`isTimeout`, `isRejectedCredential`, `safeReason`, and `redactCredential` move from `SummarizeNode` to `FailureClassifier` unchanged,
with their tests.

**Verified during implementation (T023, 2026-09-13)**, with a temporary probe test (deleted afterwards):

- Model failure at Summarize: `AgentInvocationException` ← `InvocationTargetException` ← `UndeclaredThrowableException` ←
  `InvocationTargetException` ← the model's exception (here `RuntimeException("timed out")` ← `SocketTimeoutException`). The
  trace recorded three successes and a Summarize failure with the timeout reason. One model call: no retry.
- Failure in a deterministic step (RetrieveRecords, no record for 1999-Q1): `AgentInvocationException` (outer sequence) ←
  `InvocationTargetException` ← `AgentInvocationException` (the `step:RetrieveRecords` wrapper) ← `InvocationTargetException` ←
  `ChainFailure`. Trace: PrepareRequest success, RetrieveRecords failure with the step's own message. No model call.
- Blank reply: the same Summarize chain ending in `OutputGuardrailException("The guardrail … NonBlankSummaryGuardrail failed with
  this message: The model returned an empty response")`. One model call: `fatal` does not retry.

Every cause the classifier looks for is present in the chain, so it walks the chain rather than inspecting the top exception.

**Verify during implementation**: the exact exception type and cause chain the orchestrator raises for a failure inside a non-AI step
and inside the AI agent, and that no retry happens by default (`errorHandler` is not set, so the builder's default applies).

## R-008: Model call count and determinism

**Decision**: Keep a counting `FakeChatModel` as the model bean in tests. The three deterministic steps receive no model.

**Rationale**: FR-011 and User Story 3. With the model injected only into the `Summarizer` builder, a non-AI step has no path to it,
and the count test proves the runtime behavior.

## R-009: What is removed

`ChainRunner`, `ChainNode`, `ChainRunner.ModelExchange`, `ChainRunner.ModelExchangeHolder`, and `SummarizeNode` are deleted (FR-021).
`NodeRecord`, `ChainResult`, `RunStatus`, `ChainFailure`, and `ChainTimeoutException` stay: they are the run service's and
persistence's vocabulary, not orchestration.

## R-010: Proving behavior is preserved

**Decision**: Before any change, record a golden snapshot: for each company and period in the sample dataset, run the chain with the
counting fake model and serialize each run's detail response and step records, excluding identifiers, timestamps, and durations. After
the migration, the same test compares against the snapshot byte for byte. The fake model's fixed response makes summaries comparable
too.

**Rationale**: SC-003 and FR-003 ask for byte-identical indicators and unchanged record formats. A committed snapshot taken from the
pre-migration code is the only evidence that cannot drift with the implementation.

## R-011: LangChain4j capability inventory (added after review, 2026-09-13)

**Why this entry exists**: the first plan did not inventory what the pinned library offers before designing, and missed
`AgentMonitor`. Every public capability of `langchain4j-agentic:1.18.0-beta28` and the relevant parts of `langchain4j` /
`langchain4j-core:1.18.0` was checked against the chain, reading the sources jars, and is listed with its decision. Constitution
v3.1.0 makes this inventory a required part of every plan.

| Capability (package) | Decision | Where / why |
|----------------------|----------|-------------|
| `AgenticServices.sequenceBuilder` (typed) | **used** | `FinancialChainFactory` |
| `@SequenceAgent` declarative workflow | not used, justified exception 1 | sub-agents and suppliers are static; steps are Micronaut beans (R-002) |
| `parallelBuilder`, `loopBuilder`, `conditionalBuilder`, `supervisorBuilder`, `plannerBuilder`, `HumanInTheLoop` | not needed | the chain is a fixed four-step order |
| `AgenticServices.agentBuilder`, `@Agent`, `@SystemMessage`, `@UserMessage`, `@V` | **used** | `Summarizer`, the three steps |
| `@MemoryId` on the sequence | **used** (R-012) | run id is the scope's memory id |
| `outputKey` | **used** | every step and the sequence |
| `TypedKey` / `@K` typed scope keys | not adopted | the scope keys are the `@V` parameter names already declared on every step; adopting typed keys changes no behavior and is a candidate for a later clean-up |
| `@Output`, `output(Function)`, `@BeforeCall` | not needed | the output is one scope value; nothing to prepare before the run |
| `errorHandler` / `@ErrorHandler` / `ErrorRecoveryResult` | library default **used** (throw) | a failure must stop the chain without retry, which is the default |
| `AgentInvocationException`, `MissingArgumentException` | **used** | the classifier walks the library's exception chain (R-007) |
| `AgentListener`, `ComposedAgentListener` | **used** | `RunProgressListener`, composed with the monitor by the builder |
| `AgentMonitor`, `MonitoredAgent`, `MonitoredExecution`, `observability.AgentInvocation` | **used** (R-012) | timing, inputs, outputs, tokens, first error per run |
| `HtmlReportGenerator` | not used in 005 | the feature must not change UI or API (FR-004); a report endpoint or page is a candidate feature |
| `BeforeAgentToolExecution`, `AfterAgentToolExecution` | not needed | no tools in the chain |
| `AgenticScope.readState` | **used** | `ChainRunService` reads `indicators` |
| `AgenticScopeAccess.getAgenticScope` / `evictAgenticScope` | **used** (R-012) | read after a run; evicted once stored |
| `ResultWithAgenticScope` | not used | only available on success; `AgenticScopeAccess` serves success and failure alike |
| `AgenticScopeStore`, `AgenticScopePersister`, `AgenticScopeJsonCodec` | not needed | runs are persisted in the existing schema (FR-003) |
| `ChatMessagesAccess` (`lastChatRequest`, `lastChatResponse`, `removeLastResponseEvent`) | **used** (R-012) | the model exchange text |
| `OutputGuardrail` | **used** | `NonBlankSummaryGuardrail` |
| `InputGuardrail` | not needed | inputs are deterministic, validated steps |
| chat memory (`ChatMemory`, providers, suppliers) | not needed | single-turn summarization |
| tools, `ToolProvider`, MCP (`McpClientAgent`), A2A (`A2AClientAgent`), RAG (`ContentRetriever`, `RetrievalAugmentor`) | not needed | out of scope for 005 |
| `AiServiceListener` events (`AiServiceRequestIssuedEvent`, ...) in core observability | not available here | `AgentBuilder` in 1.18.0-beta28 registers only its internal response listener and exposes no way to add one |
| `ChatModelListener` on the model | not used | its context carries no memory id, so it cannot tell concurrent runs apart; `ChatMessagesAccess` is keyed by it |
| Micronaut LangChain4j integration (`micronaut-langchain4j-agentic`, Ollama config beans) | not used | R-002, R-004 |

## R-012: Observation through AgentMonitor, memory id, scope access, and the summarizer's message history

**Decision**: replace the custom `RunTraceListener` with the library's observation and scope API:

- `FinancialChain extends MonitoredAgent, AgenticScopeAccess`, and its method takes `@MemoryId UUID runId`. The builder attaches an
  `AgentMonitor` automatically (verified in `AbstractServiceBuilder.build`), and the scope's memory id is the run id (verified in
  `PlannerBasedInvocationHandler.memoryId`).
- `RunTraceAssembler` reads `chain.agentMonitor().allExecutionsFor(runId)` for each step's start, finish, duration, inputs, output,
  token usage, and the run's first error; reads `((ChatMessagesAccess) summarizer).lastChatRequest(runId)` and `lastChatResponse(runId)`
  for the model exchange and then calls `removeLastResponseEvent(runId)`.
- `ChainRunService` reads `indicators` from `chain.getAgenticScope(runId)` and calls `chain.evictAgenticScope(runId)` after collecting.
- `RunProgressListener`, a thin `AgentListener`, keeps only what the monitor does not expose: the current-step callback (FR-014) and the
  instant a failed step stopped (the monitor never finishes a failed invocation, so it has no duration).

**Hand-filled, named per Principle V**: the request text of a model call that failed before any response arrived (the input rendered as
the template renders it); the Summarize record's `RunSummary`; each step's single input unwrapped from the argument map; the failed step's
duration (from `RunProgressListener`); conversion of the monitor's `LocalDateTime` to the stored `Instant` in the JVM's zone.

**Verified during implementation**: all 145 backend tests pass, including the golden snapshot (48 selections byte-identical), concurrent
runs, and no duplicated steps. The empty-response test now asserts the blank reply `"   "` is stored, as before the migration.

**Accepted consequences**: the monitor retains up to 100 successful and 100 failed executions in memory (`setMaxRetainedSessions`
default) and offers no per-run removal; memory stays bounded. The step:<Step> wrappers from T020 remain, because the monitor is a
listener and has the same limitation.
