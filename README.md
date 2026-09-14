# Financial Agent Chain

A teaching project. Four steps run in a fixed order over fictional financial data. Three are
deterministic. The fourth asks a language model to summarise what the third computed.

The chain is declared with LangChain4j: the steps form an agentic sequence, and the summarizer is an AI
agent whose instructions are written on its interface. The declarations are short enough to read in one
sitting, and every run records what each step received and produced, including the exact text exchanged
with the model.

## Where to look, in order

| What | Where |
|------|-------|
| The chain, as an agentic sequence | `backend/src/main/java/dev/l4jlab/chain/agent/FinancialChain.java` |
| **The one AI agent**, its instructions declared | `agent/Summarizer.java` |
| How the sequence and the agent are assembled | `agent/FinancialChainFactory.java` |
| The three deterministic steps, one file each | `backend/src/main/java/dev/l4jlab/chain/node/` |
| How each step becomes a stored record, from LangChain4j's `AgentMonitor` | `agent/RunTraceAssembler.java` |
| What a learner reads when a run fails | `agent/FailureClassifier.java` |
| The provider seam, local against cloud | `model/ChatModelFactory.java` |
| The step boundaries | `domain/`, and `specs/001-financial-agent-chain/contracts/node-boundaries.md` |
| The seeded data | `backend/src/main/resources/data/companies.json` |

Steps one through three never touch a model. `ModelCallBudgetTest` asserts that by counting calls on a
fake model, because it is the claim the whole exercise rests on. The design decisions behind the
declarative chain are in `specs/005-langchain4j-declarative-migration/`.

## Run it with make

On macOS, Linux, and WSL, one Makefile at the root starts everything. Run `make` on its own for the full list
of targets, grouped by purpose. Every target runs one of the commands documented below, which stay the path
on native Windows. `./gradlew` is still the build.

```bash
make up                        # the packaged system on http://localhost:8866 (cloud or local, from your settings)
make up-local && make pull-model   # the same, with the local model runtime and its model
make dev                       # development: containers, backend, and frontend together; Ctrl+C stops both
make check                     # verify everything, the same as ./gradlew check
```

Settings still come from your shell or a `.env` file, and the Makefile defines none of them. Pass one for a
single command, for example `make scale BACKEND_REPLICAS=3`. The two targets that delete or overwrite data,
`make reset` (stored runs) and `make golden` (the committed snapshots), ask first. Without a terminal they
refuse unless you add `CONFIRM=yes`.

The self-test for the Makefile, with every tool stubbed, is `bash scripts/make/selftest.sh`.

## Run the packaged system

You need Docker and nothing else: no JDK, no Node.js. One command builds the images and starts the whole
application: PostgreSQL, two backend instances, two frontend instances, and an nginx load balancer in front
of both halves.

`make up` and `make up-local` run these. `make pull-model` pulls the model `L4J_MODEL_ID` names, `llama3.2`
by default, so unset any cloud settings before using local mode.

```bash
# Cloud mode: put these in your shell or in a .env file at the repository root (git-ignored).
export L4J_PROVIDER=cloud L4J_MODEL_BASE_URL=https://ollama.com L4J_MODEL_ID=gpt-oss:120b OLLAMA_API_KEY=...
docker compose -f compose.stack.yaml up -d --build --wait

# Local mode is the stack's default provider. Start the model runtime too, and pull a model once.
docker compose -f compose.stack.yaml --profile local up -d --build --wait
docker compose -f compose.stack.yaml --profile local exec ollama ollama pull llama3.2
```

Open **http://localhost:8866**. That is the only port the stack publishes. The backend, frontend, database,
and model runtime are reachable only on the stack's private network. If 8866 is taken, set another port
without editing anything: `L4J_HTTP_PORT=9000 docker compose -f compose.stack.yaml up -d`.

The stack reads the same variables as development (table below), with two differences: in local mode the
model runtime is reached as `http://ollama:11434`, which is the default, and the database credentials come
from `DATASOURCE_USER` and `DATASOURCE_PASSWORD`. Their `l4j` defaults are for local use only.

Scale either half, with no file edited; the load balancer picks up the change within five seconds
(`make scale BACKEND_REPLICAS=3`, `make logs-lb`):

```bash
docker compose -f compose.stack.yaml up -d --scale backend=3       # or BACKEND_REPLICAS=3
docker compose -f compose.stack.yaml up -d --scale frontend=1      # or FRONTEND_REPLICAS=1
docker compose -f compose.stack.yaml logs --no-log-prefix load-balancer   # one JSON line per request, "upstream" names the instance
```

Stop it with `docker compose -f compose.stack.yaml down` (`make down`). Stored runs survive and are there on
the next start. `down -v` deletes them (`make reset`, which asks first). If you started the model runtime
with `--profile local`, add the same flag to `down`; `make down` and `make reset` always do.

Every instance of a half is interchangeable: runs live in PostgreSQL, so any backend answers for any run.
One limitation: if a backend instance stops while it is executing a run, that run stays in its last state.
Nothing reclaims it yet.

The load balancer's routing, timeouts, and failure responses are all in `deploy/load-balancer/nginx.conf`.

## Run it for development

You need Docker, and Node.js at the major version in `frontend/.nvmrc` (24; `nvm install` in
`frontend/` picks it up). The JDK is provisioned by the Gradle wrapper.

`make dev` runs all three at once with labeled output; `make deps-up`, `make dev-backend`, and
`make dev-frontend` run them one at a time.

```bash
docker compose up -d          # PostgreSQL with pgvector
./gradlew :backend:run        # Flyway applies the migrations on start
cd frontend && npm install && npm run dev
```

Configuration is environment variables only. Switching provider is a configuration change and never
a code edit.

| Variable | Cloud mode | Local mode |
|----------|-----------|------------|
| `L4J_PROVIDER` | `cloud` | `local` |
| `L4J_MODEL_BASE_URL` | `https://ollama.com` | `http://localhost:11434` |
| `L4J_MODEL_ID` | `gpt-oss:120b` | a model you have pulled |
| `OLLAMA_API_KEY` | required | unused |
| `L4J_MODEL_TIMEOUT_SECONDS` | optional, default 45 | optional, default 45 |

The base URL is the bare host with no `/api` suffix. A path segment there is silently dropped by the
client and requests land somewhere unintended. The application refuses to start rather than let that
happen quietly.

Starting in cloud mode with no `OLLAMA_API_KEY` fails at startup, naming the variable, rather than
forty-five seconds into the first run.

## Test it

```bash
./gradlew check               # everything: both suites and the contract check, no credential (make check)
```

That one command runs the backend suite, installs the frontend's dependencies from the lock file,
runs the frontend suite, and checks that the frontend's API types still match the backend. It fails
naming the step that failed. npm still builds the frontend; Gradle only calls it, and each call is one
line in `frontend/build.gradle.kts`.

Each half on its own:

```bash
./gradlew :backend:test       # 145 tests, no credential, no network        (make test-backend)
cd frontend && npm test       # 126 tests, including the contrast gate       (make test-frontend)
./gradlew :backend:liveTest   # reaches a real provider, never part of check (make test-live)
```

The default backend suite needs Docker for the database tests, which skip with a named message when
Docker is not running. It never needs a credential. The live test is a separate source set with its
own task, so running it is a deliberate act rather than a flag you have to remember; it skips with a
named message when no provider is configured.

## Interface

The frontend is built on Ant Design, in a restrained corporate style, with a light and a dark theme and a
layout that works from a 320 pixel phone to a wide desktop.

- **Themes** follow the operating system until you choose one with the control in the header. Your choice
  is remembered; choosing System again hands the decision back.
- **Colours** come from one place, `frontend/src/theme/tokens.ts`. Each theme has its own seed, because the
  obvious single navy seed measured 1.47 to 1 for links in dark mode.
- **Contrast is a test.** `frontend/src/theme/tokens.contrast.test.ts` measures every text and control pair
  against the WCAG floors, so a palette change that breaks accessibility fails `npm test`.
- **Indicator values are never formatted.** They are rendered as the exact strings the backend computed,
  `0.3400` and not `0.34`, in every theme and at every width.

## Contracts

The Java records are the single source. Micronaut OpenAPI emits the description at compile time, and
`openapi-typescript` generates the frontend types from it. The frontend declares no shape by hand.

```bash
./gradlew :frontend:checkApi  # also part of ./gradlew check (make check-api)
```

The check compiles the backend first, so the description is always current. It is written to
`backend/build/classes/java/main/META-INF/swagger/openapi.yml`, a name with no version in it.

A failure there means a Java record changed without the frontend types being regenerated. Regenerate
and commit the result; never edit `schema.d.ts`:

```bash
cd frontend && npm run generate:api   # make generate-api compiles the backend first, then runs this
```

The application version is declared once, in `backend/build.gradle.kts`. The API description reads it
from there, and `./gradlew check` fails if `frontend/package.json` carries a different one.

Continuous integration runs the same commands on GitHub Actions, in workflows under `.github/workflows/`:
the backend suite when `backend/` changes, the frontend suite when `frontend/` changes, and the contract
check when either does. `stack` builds and smoke-tests the packaged system when its files change, and
`make` runs the Makefile's self-test on macOS and Linux when the Makefile or `scripts/make/` changes. No
workflow goes through make. A change to documentation or specifications alone
runs neither suite. No workflow is given a model credential.

## What this is not

The companies and every figure are invented and committed to this repository. Nothing here is real
financial data, and nothing the model writes is investment advice. Where the summary and the
indicator table disagree, the table is correct: it is computed with `BigDecimal` at a fixed scale and
is identical on every run, while the summary is whatever the model said this time.

Specifications, plans, and task lists live in `specs/`, one directory per feature. The project
constitution is in `.specify/memory/constitution.md`.

## MCP billing server (feature 007)

A second, independent system in this repository: an MCP server targeting protocol revision
**2026-07-28**, three replicas behind a proxy, a legacy billing API that owns the data, and a
development token issuer. It shares the repository and the build, and nothing else — it has its own
Compose file, its own schemas, and its own published port.

```bash
make mcp-up       # the whole system, one command
make mcp-verify   # start it, run the acceptance scenarios, stop it
make mcp-down
make mcp-logs
```

Served at `http://localhost:8877/mcp` unless `MCP_HTTP_PORT` says otherwise. Deliberately not 8080
or 5432, which `make dev` and `compose.yaml` already use: all three setups are meant to run at once.

Details in [`mcp-server/README.md`](mcp-server/README.md); specification and research in
[`specs/007-mcp-billing-server/`](specs/007-mcp-billing-server/).

### Configuration

Every setting is an environment variable with a documented default.

| Variable | Default | Meaning |
|---|---|---|
| `MCP_HTTP_PORT` | `8877` | The only published port of the MCP stack |
| `MCP_CURSOR_HMAC_KEY` | a development value | Signs pagination cursors. **Must be identical on every replica**, or a cursor minted by one fails on another |
| `MCP_REQUEST_STATE_KEY` | a development value | Seals the confirmation state of a fee adjustment. Same sharing requirement |
| `MCP_ISSUER_URL` | `http://token-issuer:8080` | Where the JWKS and the token exchange live |
| `MCP_LEGACY_URL` | `http://legacy-billing-api:8080` | The system of record |
| `MCP_LEGACY_TIMEOUT_MS` | `5000` | Bounds every legacy call, so an unreachable system of record becomes a tool error rather than a hang |
| `MCP_TASK_TTL_MS` | `900000` | `ttlMs` on a task handle |
| `MCP_TOOLS_TTL_MS` | `300000` | `ttlMs` on `tools/list` and `server/discover` |
| `LEGACY_RUN_DURATION_MS` | unset | Unset means a random 30–90s, which is what the requirement specifies. `make mcp-verify` sets it low so the acceptance suite does not wait a run out |
| `DATASOURCE_URL`, `DATASOURCE_USER`, `DATASOURCE_PASSWORD` | as `compose.yaml` | Reused rather than renamed |

The two keys are the only settings that must match across replicas, and both fail at startup outside
development rather than at first use.
