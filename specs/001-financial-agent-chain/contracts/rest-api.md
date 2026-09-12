# Contract: REST API

**Date**: 2026-09-12 | **Plan**: [../plan.md](../plan.md)

The Java DTO records are the single source for these shapes. The Micronaut OpenAPI annotation
processor emits the description from them at compile time, and `openapi-typescript` generates
`frontend/src/api/schema.d.ts` from that file. The frontend declares no shape by hand
(Principle III, R-006).

All payloads are JSON. All timestamps are RFC 3339 in UTC. Errors use RFC 9457 problem details
with a learner-facing `detail`, never a stack trace.

**Standing of this file.** It is the pre-implementation design contract Principle III requires,
written before any handler exists, and it is prose rather than a machine-readable description.
It is not the second hand-maintained OpenAPI description the stack rules forbid: nothing
generates from it and nothing validates against it. Once handlers exist, the description the
Micronaut OpenAPI processor emits from the Java types is authoritative, and a disagreement
between the two is a defect in this file. T095 checks the generated description against the
frontend types; T102 re-reads this file against the generated one.

## GET /api/catalog

The companies and periods a learner may select. Backs the launcher's two dropdowns.

**200** — `{ companies: [ { companyId, companyName, periods: [string] } ] }`

Periods are ordered oldest first. The list comes from the committed dataset, so it is stable
across restarts.

## POST /api/runs

Starts a run. Returns immediately; the chain executes asynchronously (R-004).

**Request** — `{ companyId: string, period: string }`

**202 Accepted** — `{ runId: uuid, status: "PENDING" }`, with `Location: /api/runs/{runId}`

**400** — the pair is absent from the catalog, or either field is malformed. The problem detail
names the offending field.

Two concurrent calls produce two independent runs. Neither observes the other.

## GET /api/runs/{runId}

The full record of one run, including every node. Polled at one-second intervals while the run
is not terminal, then left alone.

**200** —

```text
{
  runId, companyId, companyName, period,
  status,            # PENDING | RUNNING | SUCCEEDED | FAILED | TIMED_OUT
  currentNode,       # null once terminal
  failedNode,        # null unless FAILED or TIMED_OUT
  failureReason,     # null unless FAILED or TIMED_OUT
  providerMode, modelId,
  summary,           # null until the fourth node succeeds
  indicators,        # null until the third node succeeds
  startedAt, endedAt,
  nodes: [ {
    position, nodeName, succeeded, failureReason,
    inputPayload, outputPayload,      # the boundary structures, verbatim
    startedAt, durationMs,
    modelRequestText, modelResponseText, inputTokens, outputTokens   # position 4 only
  } ]
}
```

`nodes` contains only the nodes that have started, in ascending position, so a running chain
shows its progress. A failed run still returns the records of the nodes that completed
(FR-015). No field ever carries the credential (FR-018).

`failedNode` is populated for `TIMED_OUT` as well as `FAILED`. A timeout can only occur at
`Summarize`, and SC-006 requires the learner to name the failing node from the screen alone,
which a null would prevent.

**404** — unknown run identifier.

## GET /api/runs

The history list, newest first.

**Query** — `limit` (default 50, maximum 200), `cursor` (opaque, from a previous response)

**200** — `{ runs: [ { runId, companyId, companyName, period, status, summaryPreview, startedAt, endedAt } ], nextCursor }`

`summaryPreview` is the first 160 characters of the summary, or null. Node payloads are not
included; the detail endpoint serves those. Satisfies FR-014 and US3.

## Error shapes

| Condition | Status | Detail names |
|-----------|--------|--------------|
| Malformed or unknown company or period | 400 | The offending field and its allowed form |
| Unknown run identifier | 404 | The identifier |
| Model configuration invalid at startup | n/a | The application fails to start, naming the absent environment variable. `ChatModelFactory` is a `@Context` bean, so this happens during context initialisation, before the server binds a port (Principle II) |

A model outage does not produce an HTTP error. The run reaches `FAILED` or `TIMED_OUT` and the
detail endpoint reports which node failed and why, which is what the learner needs to see.
