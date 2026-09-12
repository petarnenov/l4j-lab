# Phase 0 Research: Financial Agent Chain

**Date**: 2026-09-12 | **Plan**: [plan.md](./plan.md)

All unknowns from Technical Context are resolved here. No NEEDS CLARIFICATION remains.

Revised 2026-09-12 for constitution v2.2.0, which locks Micronaut as the backend application
framework. R-001, R-002, R-006, and R-007 were re-decided. R-010 through R-013 are new. The
remaining entries were re-read and stand unchanged, because they concern the chain and the
arithmetic rather than the framework around them.

## R-001: Reaching Ollama Cloud through LangChain4j

**Decision**: Use `OllamaChatModel` from `dev.langchain4j:langchain4j-ollama`, with
`baseUrl("https://ollama.com")` and `customHeaders(Map.of("Authorization", "Bearer " + key))`.
Local mode uses the same class with `baseUrl("http://localhost:11434")` and no headers.

**Rationale**: Ollama Cloud serves the same native API as a local instance, at
`https://ollama.com/api/...`, authenticated with a bearer token read from `OLLAMA_API_KEY`.
The LangChain4j Ollama builder exposes `baseUrl`, `modelName`, `customHeaders`, `timeout`,
`temperature`, `responseFormat`, `logRequests`, and `logResponses`. That is enough to cover
both modes from one code path, which is what Principle II requires.

**Verified against 1.18.0**: the `OllamaChatModel` builder at the version the Micronaut
Platform BOM manages exposes `baseUrl`, `modelName`, `timeout`, `logRequests`, and two
`customHeaders` overloads, one taking a `Map` and one taking a `Supplier<Map>`. The decision
does not depend on anything added after 1.18.0. See R-012 for why the `Supplier` overload is
the one to use.

**Critical detail**: the base URL must be the bare host, `https://ollama.com`, with no `/api`
suffix. LangChain4j issue 1455 documents that a base URL carrying extra path segments is
silently dropped, because the client's own paths start with a slash. A base URL of
`https://ollama.com/api` therefore produces requests to `https://ollama.com/api/chat` only by
accident in some versions and to the wrong path in others. Configuration examples and the
quickstart must show the bare host.

**Alternatives considered**:

- The OpenAI-compatible endpoint at `https://ollama.com/v1` with `langchain4j-open-ai`. This
  works, but it names a second provider integration for what is one provider, and the
  constitution locks the inference runtime to Ollama. Rejected.
- A hand-written HTTP client against the Ollama API. Explicitly forbidden by the agent
  framework rules in the constitution, which require model access through a LangChain4j model
  interface. Rejected.
- Two different model classes, one per mode. Rejected because it puts provider knowledge in
  more than one place and makes the switch a code change rather than a configuration change.

## R-002: LangChain4j version

**Decision**: Take `dev.langchain4j:langchain4j-ollama` at the version the Micronaut Platform
BOM manages, which at platform 5.1.5 is 1.18.0. Declare no explicit version and no override.

**Rationale**: the constitution requires LangChain4j pinned to a single version declared in
the build file. Importing the platform BOM already declares one. Adding an override would
create two places that decide the version and one of them would eventually be wrong. The
Ollama builder API this feature depends on is present at 1.18.0, verified directly against the
published jar, so nothing is given up by aligning.

**Making the pin visible**: the BOM satisfies the rule's intent, and the version string would
never appear in the build file, so nobody could grep for it and a reviewer would reasonably
read the rule as unmet. The build therefore carries a Gradle `constraints` entry restating
1.18.0. It restates the BOM's choice rather than overriding it, so there is still exactly one
decision, and a version bump that the BOM makes without the constraint following it fails the
build loudly instead of drifting quietly.

**Superseded decision**: the previous revision pinned 1.20.0 as the newest release on Maven
Central. That was correct on its own terms and is now wrong in context. 1.20.0 remains the
newest standalone release, but the platform BOM at 5.1.5 manages 1.18.0, and the framework
choice decides this.

**Alternatives considered**:

- Override the BOM to 1.20.0. Possible, and it reintroduces the two-sources problem the
  constitution's single-version rule exists to prevent. It would also put the project on a
  LangChain4j the platform was not assembled against, for no capability this feature uses.
  Rejected.
- Skip the platform BOM and pin every module by hand. That discards the one artifact whose
  job is keeping the module versions consistent. Rejected.

## R-003: Keeping the agent loop visible

**Decision**: Define `ChainNode<I, O>` with a single `O run(I input)` method, and a
`ChainRunner` that walks an ordered `List<ChainNode<?, ?>>`, timing each node and writing one
`NodeExecution` record per step. The summarizing node calls `ChatModel.chat(ChatRequest)`
directly.

**Rationale**: Principle I requires prompt assembly, model call, and result handling to stay
readable in project code, and forbids a construct that collapses them into one opaque call.
A four-element loop with an interface a learner can read in ten seconds is the lowest
abstraction that still expresses the task.

**Alternatives considered**:

- LangChain4j `AiServices` with a declarative interface. It would remove most of the code, and
  with it the entire lesson. This is exactly the case Principle I's abstraction-level rule was
  written to prevent. Rejected.
- A generic typed pipeline with compile-time-checked node chaining. More elegant, and the
  generics obscure the flow for the audience this project is written for. Rejected under
  Principle I.

## R-004: Showing a run in progress

**Decision**: `POST /api/runs` validates the request, persists a run in `PENDING`, returns
`202 Accepted` with the run identifier, and executes the chain on a bounded task executor. The
frontend polls `GET /api/runs/{id}` through TanStack Query with a one-second refetch interval
that stops once the run reaches a terminal state.

**Rationale**: FR-020 requires the learner to see which node is currently executing, and the
edge cases require two concurrent runs to stay independent. Polling delivers both with no new
transport, and TanStack Query already owns the refetch behaviour, which honours the stack
rule against duplicating server state into another store.

**Alternatives considered**:

- Server-sent events. Better live behaviour, but it adds a streaming transport and a second
  state path in the frontend for a feature whose point is the chain, not the plumbing.
  Rejected as premature.
- A synchronous request that blocks for the model call. Simplest to write, but a
  forty-five-second blocked request cannot show progress and fails FR-020. Rejected.

## R-005: Deterministic indicator arithmetic

**Decision**: Compute every indicator with `BigDecimal`, scale 4, `RoundingMode.HALF_UP`, and
store the values as `numeric(19,4)`. Guard each denominator and emit an explicit
not-applicable marker instead of a value when it is zero or absent.

**Rationale**: SC-005 demands byte-identical values across repeated runs and FR-005 demands an
explicit not-applicable result rather than an invented one. Binary floating point satisfies
neither reliably, and a division guard is the only way to keep an infinity or a NaN out of the
record.

**Alternatives considered**: `double` with formatting at the edge. Rejected because the stored
value, not the rendering, is what SC-005 compares.

## R-006: Contract single-sourcing between Java and TypeScript

**Decision**: The Java DTO records are the single source. The Micronaut OpenAPI annotation
processor emits the description at compile time into `META-INF/swagger/`, and
`openapi-typescript` generates `frontend/src/api/schema.d.ts` from that file. The generated
file is committed, and a build check fails when it drifts.

**Changed by the framework**: springdoc-openapi is a Spring library and is gone. Micronaut
OpenAPI replaces it and differs in one way that matters: it runs as an annotation processor at
compile time rather than by inspecting a running application, so the description exists after
`./gradlew build` with no server started. That makes the drift check cheaper and removes the
need to boot the backend in continuous integration to regenerate types. The output is YAML
rather than JSON, which `openapi-typescript` reads natively.

**Rationale**: Principle III forbids declaring the same shape twice by hand. Generation from
the running specification is the only arrangement here where the frontend cannot silently
diverge.

**Alternatives considered**:

- Hand-written TypeScript interfaces kept in sync by review. A direct violation of Principle
  III. Rejected.
- A shared schema language with generators for both sides. Correct for a larger system, and
  more machinery than a five-endpoint feature can justify. Rejected under Principle I.

## R-007: Testing the model boundary

**Decision**: A `FakeChatModel` implementing LangChain4j's `ChatModel` interface backs every
default test, injected as a bean replacing the real one. One live test class lives in a
separate `liveTest` source set with its own Gradle task, excluded from `check`, and calls
`Assumptions.abort` with a message naming `OLLAMA_API_KEY` when the credential is absent.

**How the fake is substituted**: Micronaut Test's `@MockBean`, or a `@Requires(env = "test")`
factory, supplies the fake without the production factory ever being constructed. This matters
for more than tidiness. `ChatModelFactory` is a `@Context` bean that aborts startup when the
credential is absent, so if the real factory were constructed during a default test run, the
suite would fail on a machine with no `OLLAMA_API_KEY`, which Principle IV forbids.

**Rationale**: Constitution v2.0.0 permits live model tests to require a credential and a
network, and requires them to be separately selectable and to skip with an explicit message.
It also still requires that tests not touching the model run without either.

**Alternatives considered**: Recorded HTTP fixtures replayed against the real client.
Reproducible without a credential, but the recording goes stale silently and teaches nothing
about provider behaviour. Rejected.

## R-008: Sample dataset shape and size

**Decision**: One committed `companies.json` holding six fictional companies across eight
consecutive quarters, each record carrying revenue, cost of goods sold, net income, current
assets, current liabilities, total debt, and equity. The file is loaded into memory at
startup. At least one record is deliberately constructed with zero equity so the
not-applicable path is exercised by real data.

**Rationale**: FR-021 requires a committed dataset sufficient for every scenario in the
specification, including the edge cases. Eight quarters gives the prior-period comparison that
revenue growth needs, with room for a learner to browse.

**Alternatives considered**: Seeding the dataset into PostgreSQL through a migration.
Rejected because it makes the teaching data harder to read and edit than a single JSON file
sitting next to the code.

## R-009: Financial content and safety framing

**Decision**: Companies, figures, and names are invented and labelled as fictional in the
dataset and in the interface. The summarizing node's system prompt instructs the model to
describe only the supplied indicators and to give no recommendation, and the summary panel
carries a standing note that the content is a teaching exercise and not investment advice.

**Rationale**: The specification's assumptions require it, and a model asked to summarise
financial figures will otherwise drift toward advice, which would misteach the audience.

## R-010: Whether to use the Micronaut LangChain4j integration

**Decision**: Do not use it. Keep `langchain4j-ollama` as a plain dependency and build the
`ChatModel` in the project's own `ChatModelFactory`. The `io.micronaut.langchain4j` modules are
not placed on the classpath at all.

**Rationale**: the integration exists, is maintained, and is managed by the platform BOM at
2.2.0. It would build an Ollama `ChatModel` from configuration and inject it, replacing the
factory with a few lines of YAML. That is exactly the trade Principle I forbids: "A dependency
MUST NOT be added solely to save a few lines when it obscures the mechanism being taught."

The mechanism being taught here is specifically the provider seam. Principle II is an entire
principle about endpoint, model identifier, and credential being configuration, and about the
switch between local and hosted costing no code edit. A learner who opens `ChatModelFactory`
sees that seam as twenty readable lines. A learner who opens an empty classpath and a YAML
block sees nothing and has to trust it.

The integration also ships `micronaut-langchain4j-processor`, which generates declarative AI
services from annotated interfaces. Constitution v2.2.0 permits the integration for model bean
wiring but forbids that generation. Keeping the modules off the classpath entirely means the
forbidden construct cannot appear by accident, which is a stronger guarantee than a review
convention.

**Alternatives considered**:

- Use the integration for the bean and forbid `@AiService` by review. Fewer lines, and it
  leaves a loaded gun on the table for a later exercise. Rejected.
- Use the integration and write a second explanatory document describing what it does. That
  trades readable code for prose about code. Rejected under Principle I.

**Cost accepted**: the project re-implements roughly twenty lines the integration would have
provided, and does not get `micronaut-langchain4j-ollama-testresource`, which would start a
local Ollama for tests automatically. Neither is needed, because the default suite uses
`FakeChatModel` and never reaches a provider.

## R-011: Persistence with Micronaut Data and JSONB

**Decision**: Micronaut Data JDBC with `micronaut-jdbc-hikari`, entities as `@MappedEntity`
Java records, repositories as `@JdbcRepository(dialect = Dialect.POSTGRES)` interfaces. The two
JSONB payload columns map through Micronaut Data's JSON data type, serialized by
`micronaut-serde-jackson`. Flyway runs the committed migrations at startup through
`micronaut-flyway`, and schema generation is disabled.

**Rationale**: Micronaut Data JDBC compiles queries at build time, so a malformed query fails
the build rather than the first request, which suits a project whose failures are meant to be
legible. It also keeps the object graph flat, which is what this schema is: a run and its four
rows, never lazily loaded, never traversed.

**Alternatives considered**:

- Micronaut Data JPA with Hibernate. Supported, and it brings a persistence context, lazy
  loading, and a dirty-checking flush model that this feature never needs and that a learner
  would have to understand before reading the repository. Rejected under Principle I.
- Storing the node payloads as `text` and parsing on read. Simpler mapping, and it gives up
  the ability to query into a payload later and makes the column's contents opaque to anyone
  inspecting the database directly. Rejected.

## R-012: Holding the credential

**Decision**: Pass the credential to the Ollama builder through `customHeaders(Supplier)`,
reading it from the `ModelProperties` bean at call time, rather than through
`customHeaders(Map)` built once at startup.

**Rationale**: Principle II requires the credential never be logged, persisted, or shown. A
`Map` field on a built model object is reachable by anything that serializes or dumps that
object, including a debugger session projected on a screen during a lesson, which is a real
exposure for this project specifically. A supplier holds no header value between calls.

**Alternatives considered**: the `Map` overload with a redacting `toString`. It depends on
every future printer honouring the redaction. Rejected.

## R-013: Running the chain asynchronously under Micronaut

**Decision**: `POST /api/runs` validates, persists a `PENDING` run, returns `202 Accepted`, and
submits the chain to an `ExecutorService` injected by name from Micronaut's blocking executor.
The controller method carries `@ExecuteOn(TaskExecutors.BLOCKING)` because the chain performs
blocking JDBC and HTTP work and must not run on the Netty event loop.

**Rationale**: FR-020 requires the learner to see which node is currently executing, and the
edge cases require two concurrent runs to stay independent. An explicit `ExecutorService`
submission is visible in the code that does it, which an annotation-driven alternative is not.

**Alternatives considered**:

- Micronaut's `@Async` annotation on a service method. Fewer lines, and the hand-off becomes
  invisible at the call site, which is the same trade R-010 refused. Rejected.
- Running the chain on the event loop. It would stall Netty for the length of a model call and
  break every concurrent run. Rejected.
