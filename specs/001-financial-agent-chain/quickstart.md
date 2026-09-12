# Quickstart: Financial Agent Chain

**Date**: 2026-09-12 | **Plan**: [plan.md](./plan.md)

How to start the system and prove the feature works end to end. Shapes referenced here are
defined in [contracts/rest-api.md](./contracts/rest-api.md) and
[contracts/node-boundaries.md](./contracts/node-boundaries.md).

## Prerequisites

- JDK 25. Gradle comes from the committed wrapper, so nothing else needs installing.
- Node.js 22 or newer
- Docker, for PostgreSQL and for Testcontainers
- For cloud mode, an Ollama API key. For local mode, a running Ollama with a pulled model.

## Configuration

Both provider modes read the same variables. Only `L4J_PROVIDER` changes.

| Variable | Cloud mode | Local mode |
|----------|-----------|------------|
| `L4J_PROVIDER` | `cloud` | `local` |
| `L4J_MODEL_BASE_URL` | `https://ollama.com` | `http://localhost:11434` |
| `L4J_MODEL_ID` | `gpt-oss:120b` | a model you have pulled |
| `OLLAMA_API_KEY` | required | unused |
| `L4J_MODEL_TIMEOUT_SECONDS` | optional, default 45 | optional, default 45 |

These bind to a Micronaut `@ConfigurationProperties` bean. `application.yml` references them
as placeholders with defaults, so the variable names above are the whole configuration surface.

The base URL is the bare host with no `/api` suffix. A path segment there is silently dropped
by the client and the requests go to the wrong place. See R-001.

Starting with `L4J_PROVIDER=cloud` and no `OLLAMA_API_KEY` must fail at startup with a message
naming the variable, not later at the first model call.

**Set `L4J_PROVIDER` explicitly.** It defaults to `local`. Exporting the key, `L4J_MODEL_BASE_URL=https://ollama.com`
and the model without it once started a backend that sent no credential to Ollama Cloud, failed every run
with `{"error":"Unauthorized"}`, and recorded the runs as `LOCAL`. The application now refuses to start on
that contradiction, naming `L4J_PROVIDER`. It likewise refuses `L4J_PROVIDER=cloud` against a localhost
base URL. A self-hosted Ollama on another machine in local mode is still accepted.

## Start

```bash
docker compose up -d                      # PostgreSQL with pgvector
./gradlew :backend:run                    # applies Flyway migrations on start
cd frontend && npm install && npm run dev
```

The application is at the address Vite prints, with API calls proxied to the backend.

## Scenario 1: a run produces a summary (US1, P1)

1. Open the launcher, pick a company and a period, and start the run.
2. The run appears immediately with status `PENDING`, then `RUNNING` with the current node
   named as it advances.
3. Within about a minute the status reaches `SUCCEEDED`.

**Expected**: the summary and the five indicators are on screen together. Every figure in the
summary appears in the indicator table. No figure appears that is not in the table (SC-003).

## Scenario 2: every node boundary is visible (US2, P2)

1. Open the completed run's detail view.
2. Step through the timeline.

**Expected**: exactly four entries in order, each showing what it received, what it produced,
and how long it took. The fourth additionally shows the full text sent to the model and the
full text returned, plus the model identifier and provider mode. No entry anywhere contains
the API key (SC-002, FR-018).

## Scenario 3: history survives a restart (US3, P3)

1. Complete two runs for different companies.
2. Stop and restart the backend, then reload the page.

**Expected**: both runs are in the history newest first, and opening either shows its original
summary and all four node records unchanged.

## Scenario 4: the model is unreachable

```bash
OLLAMA_API_KEY=invalid-key ./gradlew :backend:run
```

Start a run.

**Expected**: the first three nodes succeed and their records are visible with real
indicators. The run ends `FAILED` at `Summarize`, the reason is stated in plain language, and
the rejected key does not appear anywhere on screen or in the logs (SC-006).

## Scenario 5: an indicator does not apply

Pick the company whose record carries zero equity.

**Expected**: `debtToEquity` is shown as not applicable with its reason. The other four
indicators carry values, the run succeeds, and the summary does not invent a number for the
missing one (FR-005).

## Scenario 6: repeated runs agree

Run the same company and period ten times.

**Expected**: the indicator values are identical every time, to the last digit. The summaries
differ, which is the point being taught (SC-005).

## Tests

```bash
./gradlew :backend:test         # deterministic only, no credential, no network
./gradlew :backend:liveTest     # the live model test, its own source set and task
cd frontend && npm test         # Vitest
```

`./gradlew :backend:test` must pass with no `OLLAMA_API_KEY` set and no outbound network.
`liveTest` is a separate source set with its own task and is excluded from `check`, so
selecting it is a deliberate act rather than a flag someone has to remember. It skips with a
message naming `OLLAMA_API_KEY` when the credential is absent, rather than failing or passing
silently (Principle IV, R-007).

The default suite substitutes `FakeChatModel` for the real `ChatModel` bean. Without that
substitution the `@Context` factory would abort startup on a machine with no credential, which
is exactly what Principle IV forbids.

## Contract drift check

```bash
./gradlew :backend:build           # the annotation processor writes the OpenAPI description
cd frontend && npm run check:api   # fails if schema.d.ts differs from the generated types
```

A failure here means a Java DTO changed without the frontend types being regenerated. Run the
generator and commit the result rather than editing `schema.d.ts` by hand (Principle III).

Micronaut OpenAPI runs at compile time, so the description exists after a plain build with no
server started. Nothing needs to boot to refresh the frontend types.
