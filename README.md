# Financial Agent Chain

A teaching project. Four nodes run in a fixed order over fictional financial data. Three are
deterministic. The fourth asks a language model to summarise what the third computed.

The point is that you can read the whole thing.

## Where to look, in order

| What | Where |
|------|-------|
| The agent loop | `backend/src/main/java/dev/l4jlab/chain/core/ChainRunner.java` |
| The four nodes, one file each | `backend/src/main/java/dev/l4jlab/chain/node/` |
| **The only model call in the project** | `SummarizeNode.java`, the single `chatModel.chat(request)` |
| The provider seam, local against cloud | `model/ChatModelFactory.java` |
| The node boundaries | `domain/`, and `specs/001-financial-agent-chain/contracts/node-boundaries.md` |
| The seeded data | `backend/src/main/resources/data/companies.json` |

Nodes one through three never touch a model. Three tests assert that by counting calls on a fake,
because it is the claim the whole exercise rests on.

## Run the packaged system

You need Docker and nothing else: no JDK, no Node.js. One command builds the images and starts the whole
application: PostgreSQL, two backend instances, two frontend instances, and an nginx load balancer in front
of both halves.

```bash
# Cloud mode: put these in your shell or in a .env file at the repository root (git-ignored).
export L4J_PROVIDER=cloud L4J_MODEL_BASE_URL=https://ollama.com L4J_MODEL_ID=gpt-oss:120b OLLAMA_API_KEY=...
docker compose -f compose.stack.yaml up -d --build --wait

# Local mode is the stack's default provider. Start the model runtime too, and pull a model once.
docker compose -f compose.stack.yaml --profile local up -d --build --wait
docker compose -f compose.stack.yaml --profile local exec ollama ollama pull llama3.2
```

Open **http://localhost:8000**. That is the only port the stack publishes. The backend, frontend, database,
and model runtime are reachable only on the stack's private network. If 8000 is taken, set another port
without editing anything: `L4J_HTTP_PORT=9000 docker compose -f compose.stack.yaml up -d`.

The stack reads the same variables as development (table below), with two differences: in local mode the
model runtime is reached as `http://ollama:11434`, which is the default, and the database credentials come
from `DATASOURCE_USER` and `DATASOURCE_PASSWORD`. Their `l4j` defaults are for local use only.

Scale either half, with no file edited; the load balancer picks up the change within five seconds:

```bash
docker compose -f compose.stack.yaml up -d --scale backend=3       # or BACKEND_REPLICAS=3
docker compose -f compose.stack.yaml up -d --scale frontend=1      # or FRONTEND_REPLICAS=1
docker compose -f compose.stack.yaml logs --no-log-prefix load-balancer   # one JSON line per request, "upstream" names the instance
```

Stop it with `docker compose -f compose.stack.yaml down`. Stored runs survive and are there on the next
start. `down -v` deletes them.

Every instance of a half is interchangeable: runs live in PostgreSQL, so any backend answers for any run.
One limitation: if a backend instance stops while it is executing a run, that run stays in its last state.
Nothing reclaims it yet.

The load balancer's routing, timeouts, and failure responses are all in `deploy/load-balancer/nginx.conf`.

## Run it for development

You need Docker, and Node.js at the major version in `frontend/.nvmrc` (24; `nvm install` in
`frontend/` picks it up). The JDK is provisioned by the Gradle wrapper.

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
./gradlew check               # everything: both suites and the contract check, no credential
```

That one command runs the backend suite, installs the frontend's dependencies from the lock file,
runs the frontend suite, and checks that the frontend's API types still match the backend. It fails
naming the step that failed. npm still builds the frontend; Gradle only calls it, and each call is one
line in `frontend/build.gradle.kts`.

Each half on its own:

```bash
./gradlew :backend:test       # 121 tests, no credential, no network
cd frontend && npm test       # 126 tests, including the contrast gate
./gradlew :backend:liveTest   # reaches a real provider, its own task, never part of check
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
./gradlew :frontend:checkApi  # also part of ./gradlew check
```

The check compiles the backend first, so the description is always current. It is written to
`backend/build/classes/java/main/META-INF/swagger/openapi.yml`, a name with no version in it.

A failure there means a Java record changed without the frontend types being regenerated. Regenerate
and commit the result; never edit `schema.d.ts`:

```bash
cd frontend && npm run generate:api
```

The application version is declared once, in `backend/build.gradle.kts`. The API description reads it
from there, and `./gradlew check` fails if `frontend/package.json` carries a different one.

Continuous integration runs the same commands on GitHub Actions, in three workflows under
`.github/workflows/`: the backend suite when `backend/` changes, the frontend suite when `frontend/`
changes, and the contract check when either does. A change to documentation or specifications alone
runs neither suite. No workflow is given a model credential.

## What this is not

The companies and every figure are invented and committed to this repository. Nothing here is real
financial data, and nothing the model writes is investment advice. Where the summary and the
indicator table disagree, the table is correct: it is computed with `BigDecimal` at a fixed scale and
is identical on every run, while the summary is whatever the model said this time.

Specifications, plans, and task lists live in `specs/`, one directory per feature. The project
constitution is in `.specify/memory/constitution.md`.
