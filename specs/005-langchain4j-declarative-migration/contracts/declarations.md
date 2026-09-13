# Contract: Agent and Sequence Declarations

**Feature**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md) | **Research**: R-002, R-003, R-004

The declarations a reader of the backend finds, and what each promises. Names are exact; changing one is a change to this
contract, because step names are stored in every run record.

## Summarizer (AI agent)

```text
interface Summarizer
  @Agent(name = "Summarize", outputKey = "summaryText",
         description = "Summarises pre-computed financial indicators for a learner")
  @SystemMessage(<SYSTEM_INSTRUCTION, unchanged text>)
  @UserMessage("{{indicators}}")
  String summarize(@V("indicators") IndicatorSet indicators)
```

| Aspect | Promise |
|--------|---------|
| System message | The six-rule instruction from `SummarizeNode.SYSTEM_INSTRUCTION`, character for character (FR-008) |
| User message | `IndicatorSet.toString()`, which renders exactly today's table: `Company: …`, `Reporting period: …`, blank line, `Indicators:`, one `- name = value` or `- name = not applicable (reason)` line per indicator |
| Output guardrail | `NonBlankSummaryGuardrail`: a blank response fails the call without retry (FR-009) |
| Model | the application's `ChatModel` bean, with its configured timeout |
| Created by | `AgenticServices.agentBuilder(Summarizer.class)` in `FinancialChainFactory`, exposed as a singleton bean |

## Deterministic steps (non-AI agents)

Each is an existing Micronaut singleton whose `run` method gains an `@Agent` annotation. Their logic does not change.

| Bean | Method | `@Agent.name` | Argument (`@V`) | `outputKey` |
|------|--------|---------------|-----------------|-------------|
| `PrepareRequestNode` | `ChainRequest run(Selection)` | `PrepareRequest` | `selection` | `request` |
| `RetrieveRecordsNode` | `RetrievedRecords run(ChainRequest)` | `RetrieveRecords` | `request` | `records` |
| `ComputeIndicatorsNode` | `IndicatorSet run(RetrievedRecords)` | `ComputeIndicators` | `records` | `indicators` |

Each validates its own output before returning and throws `BoundaryViolation` on a violation, which the learner sees as today's
generic "`<step>` failed unexpectedly" reason (R-006).

## FinancialChain (sequence)

```text
interface FinancialChain extends MonitoredAgent, AgenticScopeAccess
  String run(@MemoryId UUID runId, @V("selection") Selection selection)
```

| Aspect | Promise |
|--------|---------|
| Sub-agents, in order | `step:PrepareRequest`, `step:RetrieveRecords`, `step:ComputeIndicators` (each a one-step sequence around `PrepareRequestNode`, `RetrieveRecordsNode`, `ComputeIndicatorsNode`, so their events reach the listener; research R-005), then `Summarizer` |
| Output | the `summaryText` scope value |
| Observation | `AgentMonitor` (attached by the builder because of `MonitoredAgent`) and `RunProgressListener` (research R-012) |
| Error handling | library default: the first failure stops the sequence and is thrown as `AgentInvocationException` |
| Created by | `AgenticServices.sequenceBuilder(FinancialChain.class)` in `FinancialChainFactory`, exposed as a singleton bean; justified exception in plan (R-002) |
| Invoked by | `ChainRunService`, on its existing blocking executor, once per run |

## Removed

`ChainNode`, `ChainRunner` (with `ModelExchange` and `ModelExchangeHolder`), and `SummarizeNode`. No other class may sequence the
steps or call `ChatModel.chat` directly.
