# Contract: Trace Capture and Run Outcomes

**Feature**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md) | **Research**: R-005, R-007

How library events become the step records and run outcomes feature 001 already defines. The stored formats are unchanged; this
contract fixes where each value comes from.

## Sources (research R-012)

| Source | Library API | Gives |
|--------|-------------|-------|
| Monitor | `FinancialChain` extends `MonitoredAgent`; `agentMonitor().allExecutionsFor(runId)` | per step: start, finish, duration, inputs, output, token usage; the run's first error |
| Summarizer message history | `ChatMessagesAccess.lastChatRequest(runId)`, `lastChatResponse(runId)`, then `removeLastResponseEvent(runId)` | the exact request and response of the model call, including a blank reply the guardrail rejected |
| Scope | `AgenticScopeAccess.getAgenticScope(runId).readState("indicators")`, then `evictAgenticScope(runId)` | the indicators, on success and failure |
| `RunProgressListener` (`AgentListener`) | `beforeAgentInvocation`, `onAgentInvocationError` | the current step (FR-014); the instant a failed step stopped |

The run id is the scope's memory id (`@MemoryId` on `FinancialChain.run`). The monitor's tree is walked depth first; the sequence and the
`step:<Step>` wrappers are skipped, and the four step invocations are recorded in invocation order.

## Record field sources

| Field | Deterministic steps | Summarize |
|-------|---------------------|-----------|
| `position` | order of the four step names | 4 |
| `nodeName` | invocation `agent().name()` | `Summarize` |
| `inputPayload` | the single data argument from the invocation's `inputs()` (hand-unwrapped) | `inputs().get("indicators")` |
| `outputPayload` | invocation `output()`; null when not finished | `RunSummary` built from the response text and the invocation's token usage (hand-filled) |
| `succeeded`, `failureReason` | invocation finished or not; reason from the classifier with the monitor's first error | same |
| `startedAt` | invocation `startTime()`, converted to `Instant` in the JVM zone | same |
| `durationMs` | invocation `duration()`; for the failed step, `RunProgressListener`'s failure instant minus start (hand-filled) | same |
| `modelRequestText` | null | last user message of `lastChatRequest(runId)`; when the call failed before a response, the rendered `indicators` (hand-filled) |
| `modelResponseText` | null | `lastChatResponse(runId).aiMessage().text()`, including a blank reply; null when no response arrived |
| `inputTokens`, `outputTokens` | null | invocation `tokenUsage()`, null when not reported |

## Outcome classification (`FailureClassifier`)

Applied by `ChainRunService` when `FinancialChain.run` throws. The failed step comes from the trace; the reason from the first
matching row, checked against the cause chain.

| Order | Failed step | Cause | Status | Reason (feature 001 wording) |
|-------|-------------|-------|--------|------------------------------|
| 0 | none recorded (the exception was raised before or outside any of the four steps) | any | FAILED | failed step stored as `ChainRunner`, reason "The run stopped unexpectedly before the chain completed." |
| 1 | any | `ChainFailure` | FAILED | the exception's message |
| 2 | Summarize | `OutputGuardrailException` from `NonBlankSummaryGuardrail` | FAILED | "The model returned an empty response. The indicators above were computed successfully; only the summary is missing." |
| 3 | Summarize | timeout (exception type or message contains "timeout" / "timed out") | TIMED_OUT | "The model did not answer within N seconds. The indicators above were computed successfully; only the summary is missing." |
| 4 | Summarize | 401, 403, "unauthorized", "forbidden" | FAILED | "The model provider rejected the credential, …Check that OLLAMA_API_KEY is valid, and that L4J_PROVIDER is set to cloud when using Ollama Cloud. Provider said: <redacted, capped>" |
| 5 | Summarize | any other exception | FAILED | "The model could not be reached or rejected the request. … Reason: <redacted, capped>" |
| 6 | PrepareRequest, RetrieveRecords, ComputeIndicators | any other exception, including `BoundaryViolation` | FAILED | "<step> failed unexpectedly. See the server log for detail." |

Row 0 keeps `ChainRunService.finishUnexpected`'s stored values exactly (`failed_node` is free text, but must be non-null for a failed run),
even though no class of that name remains; the value is part of stored data (FR-003, FR-005) and a comment at the constant says so.
Rows 2 to 5 apply only when the failed step is Summarize, because today only the summarizing step's model call produced those outcomes.
A `BoundaryViolation`'s detail is logged at error level with the run id and never becomes a reason (research R-006).

Every reason passes through credential redaction before it is stored or shown (FR-018).

## Success outcome

`FinancialChain.run` returns the summary text; `ChainRunService` takes indicators and the `RunSummary` from the trace's step 3 and step
4 records, validates the `RunSummary` with `BoundaryValidation`, and finishes the run as SUCCEEDED exactly as today, including the
denormalized summary text on the run row.

If that validation fails, the run finishes FAILED at Summarize with row 6's generic wording ("Summarize failed unexpectedly. See the
server log for detail."), and the Summarize record is replaced by a failure record keeping its request and response text, with no output
and no token counts, matching the runner's former `failureRecord`.
