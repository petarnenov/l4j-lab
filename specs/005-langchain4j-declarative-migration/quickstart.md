# Quickstart: Validating the Declarative Migration

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

Declarations are in [contracts/declarations.md](./contracts/declarations.md); event mapping and outcomes in
[contracts/trace-and-outcomes.md](./contracts/trace-and-outcomes.md).

## Prerequisites

- Docker running (database tests), Node.js 24, no credential for Scenarios 1 to 6.
- For Scenario 7: `OLLAMA_API_KEY` with cloud settings, or a local Ollama with a pulled model.

## Scenario 0: Golden snapshot, before any production change (R-010)

Run the snapshot test on the unmodified code so it writes `backend/src/test/resources/golden/` for every company and period in the
sample dataset. Commit the snapshot.

**Expect**: one file per selection with the run detail and four step records, identifiers, timestamps, and durations removed.

## Scenario 1: Behavior matches the snapshot (US1, SC-003, FR-003)

```bash
./gradlew :backend:test --tests '*GoldenRunSnapshotTest'
```

**Expect**: every selection's serialized detail and step records match the committed snapshot byte for byte.

## Scenario 2: The whole project still verifies (SC-002, SC-008)

```bash
./gradlew check
```

**Expect**: success; `:frontend:checkApi` reports `schema.d.ts` matches; frontend 126 tests pass unchanged; backend count differs only
by the tests listed as replaced or added in `tasks.md`.

## Scenario 3: Failures keep their outcomes (US2, SC-005)

```bash
./gradlew :backend:test --tests '*FinancialChainFailureTest' --tests '*ChainRunServiceTest' --tests '*FailedRunTraceTest'
```

**Expect**: timeout → TIMED_OUT at Summarize; rejected credential → FAILED naming `OLLAMA_API_KEY` and `L4J_PROVIDER`, no credential in
the reason; empty response → FAILED with the empty-response reason, request text recorded, and the blank reply stored as the response
text, as before the migration; no matching records → FAILED at RetrieveRecords with zero model calls; invalid boundary → FAILED at the producing step with the
generic "`<step>` failed unexpectedly. See the server log for detail." reason and no validation detail on screen; a deterministic step
error mentioning "timed out" → FAILED, not TIMED_OUT; an error before any step → FAILED at `ChainRunner` with "The run stopped
unexpectedly before the chain completed."; earlier steps' records present.

## Scenario 4: One model call per successful run (US3, SC-004)

```bash
./gradlew :backend:test --tests '*ModelCallBudgetTest'
```

**Expect**: ten successful runs make exactly ten calls; runs failing in each of the first three steps make zero.

## Scenario 5: The summarizer declaration produces today's messages (US4)

```bash
./gradlew :backend:test --tests '*SummarizerTest'
```

**Expect**: the fake model receives a system message with the six rules unchanged and a user message equal to today's rendered table,
including not-applicable entries with reasons; truncation and the fabricated-figure check behave as before.

## Scenario 6: No hand-written orchestration remains (US4, SC-006)

```bash
grep -rn "class ChainRunner\|interface ChainNode\|ModelExchangeHolder\|\.chat(" backend/src/main/java
```

**Expect**: no match.

## Scenario 7: A real run end to end (SC-007, constitution workflow gate)

Start the application (development workflow or `compose.stack.yaml`) with a configured provider, start a run in the browser, and open
it.

**Expect**: SUCCEEDED; four step records; the Summarize record shows request text, response text, model identifier, provider mode, and
token counts. A run recorded before the migration still opens unchanged. Record the provider mode and paste the trace into the pull request.

## Scenario 8: Live model test

```bash
./gradlew :backend:liveTest
```

**Expect**: passes against the configured provider using the `Summarizer` declaration; skips with a named reason when no provider is set.
