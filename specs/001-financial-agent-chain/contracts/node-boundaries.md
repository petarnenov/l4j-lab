# Contract: Node Boundaries

**Date**: 2026-09-12 | **Plan**: [../plan.md](../plan.md)

Principle III requires every boundary to be a declared, validated structure written before the
handler. Each node receives only the previous node's output (FR-008). All four are Java records
in `dev.l4jlab.chain.domain`, carrying Jakarta Validation constraints that the runner checks
between every step.

## The node interface

```text
ChainNode<I, O>
    String name()
    O run(I input) throws ChainFailure
```

Every one of the four boundary records is annotated `@Serdeable`. Each is written verbatim
into a `node_execution` JSONB payload column, and Micronaut Serde refuses to serialize a type
it has no introspection for. The omission compiles and passes every unit test, then fails on
the first real run.

`name()` returns one of exactly four values, `PrepareRequest`, `RetrieveRecords`,
`ComputeIndicators`, or `Summarize`, never the implementing class name. These are the values
persisted into `node_execution.node_name` and enforced there by a check constraint, so a class
renamed without its `name()` breaks the insert rather than the compile.

`ChainFailure` carries a learner-facing message and the node name. The runner catches it, marks
the run failed at that node, and stops. Any other exception is wrapped into a `ChainFailure`
with a generic message so a stack trace never reaches the screen (FR-015).

## Boundary 1 → 2: ChainRequest

Produced by `PrepareRequest` from the learner's selection.

| Field | Type | Constraints |
|-------|------|-------------|
| companyId | String | not blank, matches `[a-z0-9-]{3,64}` |
| period | String | not blank, matches `\d{4}-Q[1-4]` |
| requestedAt | Instant | not null |

**Rejections**: a blank or malformed company identifier or period fails here, before any later
node runs (FR-002). The message names the offending field.

## Boundary 2 → 3: RetrievedRecords

Produced by `RetrieveRecords` from the dataset.

| Field | Type | Constraints |
|-------|------|-------------|
| companyId | String | not blank |
| companyName | String | not blank |
| period | String | matches `\d{4}-Q[1-4]` |
| current | FinancialRecord | not null |
| prior | FinancialRecord | nullable, absent for a company's first period |

**Rejections**: when no record matches, the node fails with a message naming the missing
company or period and passes nothing downstream (FR-003). An empty result is never a success.

## Boundary 3 → 4: IndicatorSet

Produced by `ComputeIndicators`. Field semantics and formulas are in
[../data-model.md](../data-model.md).

| Field | Type | Constraints |
|-------|------|-------------|
| companyName | String | not blank |
| period | String | matches `\d{4}-Q[1-4]` |
| indicators | List\<Indicator\> | exactly 5 entries, names unique |

`Indicator` holds `name`, `value` (BigDecimal, scale 4, nullable), `notApplicableReason`
(String, nullable), and `derivedFrom` (list of field names, at least one entry). Exactly one of
`value` and `notApplicableReason` is present, enforced by a record compact constructor.

**Rejections**: a set with a missing indicator, a duplicate name, or an entry carrying both a
value and a reason is a programming error and fails the run rather than reaching the model.

## Boundary 4 → out: RunSummary

Produced by `Summarize`, the only node that calls the model (FR-007).

| Field | Type | Constraints |
|-------|------|-------------|
| text | String | not blank, at most 4000 characters |
| modelId | String | not blank |
| providerMode | ProviderMode | `LOCAL` or `CLOUD` |
| inputTokens | Integer | nullable, present when the provider reports it |
| outputTokens | Integer | nullable, present when the provider reports it |

**Rejections**: an empty model response fails the run with a message saying the model returned
nothing. A response longer than the cap is truncated for storage with the truncation marked,
so an unbounded response cannot fill the record. On screen the same text is rendered inside a
scroll area capped at 24rem, which bounds the display independently of the storage cap.

## Model exchange, boundary 4 only

The prompt is assembled in `Summarize` from a system instruction and a rendered indicator
table. The system instruction states that the figures are fictional, that the model must
describe only the supplied indicators, and that it must give no recommendation (R-009).

The exact request text and the exact response text are both persisted on the node's record and
shown in the interface (FR-010, US2). The credential is never part of either; it lives only in
the request header built by the model factory.
