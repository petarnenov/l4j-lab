# Data Model: LangChain4j Declarative Migration

**Date**: 2026-09-13 | **Plan**: [plan.md](./plan.md)

No persisted data changes (FR-003). The run and step-record entities of feature 001
(`specs/001-financial-agent-chain/data-model.md`) are unchanged. What changes is how values move between steps and how records
are produced.

**Terminology**: the spec says *step*; stored fields and existing classes say *node* (`NodeRecord`, `node_execution`, `nodeName`,
`currentNode`). They name the same thing. Stored names do not change (FR-003).

## Agentic scope keys

The sequence passes values between steps through its `AgenticScope`. Each key is written by exactly one step.

| Key | Type | Written by | Read by |
|-----|------|-----------|---------|
| (memory id) | `UUID` | `@MemoryId` on `FinancialChain.run` | `AgentMonitor`, `ChatMessagesAccess`, `RunProgressListener`, `AgenticScopeAccess` (correlation; never a step input) |
| `selection` | `Selection` | sequence input | PrepareRequest |
| `request` | `ChainRequest` | PrepareRequest (`outputKey`) | RetrieveRecords |
| `records` | `RetrievedRecords` | RetrieveRecords (`outputKey`) | ComputeIndicators |
| `indicators` | `IndicatorSet` | ComputeIndicators (`outputKey`) | Summarize |
| `summaryText` | `String` | Summarize (`outputKey`) | sequence output |

Boundary types are those of feature 001's `contracts/node-boundaries.md`, unchanged.

## Steps

| Position | Step name (`@Agent.name`) | Kind | Implementation | Calls the model |
|----------|---------------------------|------|----------------|-----------------|
| 1 | `PrepareRequest` | non-AI | `PrepareRequestNode` bean | never |
| 2 | `RetrieveRecords` | non-AI | `RetrieveRecordsNode` bean | never |
| 3 | `ComputeIndicators` | non-AI | `ComputeIndicatorsNode` bean | never |
| 4 | `Summarize` | AI agent | `Summarizer` interface, built by `AgenticServices.agentBuilder` | exactly once per run |

Positions come from `subAgents(...)` order and are the `position` column of step records.

## Run trace (per run)

Built after the run by `RunTraceAssembler` from the library's records (research R-012): the `AgentMonitor` executions for the run's
memory id, the summarizer's `ChatMessagesAccess`, and `RunProgressListener`'s observations. After collection the scope is evicted and the
summarizer's last exchange removed. The monitor itself retains a bounded number of executions (100 per outcome by default).

## Summarize record fields

See [contracts/trace-and-outcomes.md](./contracts/trace-and-outcomes.md), "Record field sources".

## Run outcome state transitions

Unchanged from feature 001:

```text
PENDING ─► RUNNING ─┬─► SUCCEEDED
                    ├─► FAILED     (step failure, credential rejected, empty response, unexpected error)
                    └─► TIMED_OUT  (model time limit exceeded)
```

The current step shown while RUNNING is set on `beforeAgentInvocation` for each of the four steps.
