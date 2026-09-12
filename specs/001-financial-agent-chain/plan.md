# Implementation Plan: Financial Agent Chain

**Branch**: `001-financial-agent-chain` | **Date**: 2026-09-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-financial-agent-chain/spec.md`

## Summary

A learner picks a fictional company and a reporting period, and a chain of four nodes runs in
a fixed order. Three deterministic nodes prepare the request, retrieve the seeded financial
records, and compute five indicators. The fourth node sends those indicators to a language
model and returns a plain-language summary. Every node boundary is recorded and displayed, so
the learner sees exactly what flowed between steps.

The technical approach keeps the chain explicit. A small `ChainNode<I, O>` interface and a
`ChainRunner` that loops over four node instances replace any declarative agent abstraction,
which is what Principle I of the constitution requires. The model is reached through the
LangChain4j `ChatModel` interface, built by a factory that selects between a locally served
Ollama instance and the hosted Ollama Cloud service from configuration alone. Runs execute
asynchronously and the browser polls for progress, which is the simplest mechanism that shows
a run in flight without adding a streaming transport.

The backend is Micronaut, per constitution v2.2.0. Compile-time dependency injection suits the
teaching goal: what is wired is decided by an annotation processor whose output a learner can
read, not by reflection at startup.

## Technical Context

**Language/Version**: Java 25 on the backend, TypeScript 5.x on the frontend

**Application framework**: Micronaut, pinned through the Micronaut Platform BOM at 5.1.5.
Modules used: `micronaut-http-server-netty`, `micronaut-serde-jackson`, `micronaut-validation`,
`micronaut-data-jdbc` with `micronaut-jdbc-hikari`, `micronaut-flyway`, `micronaut-openapi`,
and `micronaut-test-junit5`.

**Primary Dependencies**: LangChain4j `langchain4j-ollama`, version managed by the Micronaut
Platform BOM at 1.18.0 (see R-002); PostgreSQL JDBC driver; React 19 with Vite, TanStack Query 5

**Build**: Gradle with the Kotlin DSL, wrapper committed, Java toolchain release 25, the
Micronaut application plugin, and a `liveTest` JVM test suite as its own task

**Storage**: PostgreSQL 17. Two tables, `chain_run` and `node_execution`, with JSONB payload
columns mapped through Micronaut Data's JSON data type. The pgvector extension is part of the
project stack but is not used by this feature, which performs no embedding or retrieval.

**Testing**: JUnit 5 with Micronaut Test and AssertJ, Testcontainers for PostgreSQL on the
backend; Vitest with Testing Library and Mock Service Worker on the frontend

**Target Platform**: Local developer machine. Backend on the JVM, frontend in a modern
browser, PostgreSQL in a container.

**Project Type**: Web application, backend plus frontend

**Performance Goals**: Ninety-five percent of runs complete end to end within sixty seconds
(SC-004). The three deterministic nodes together must stay under one hundred milliseconds, so
effectively the whole budget belongs to the model call.

**Constraints**: The model call carries a configurable timeout defaulting to forty-five
seconds, leaving headroom inside the sixty-second run budget. Identical input must produce
byte-identical indicator values (SC-005), so the computing node uses `BigDecimal` with a
declared scale and rounding mode, never binary floating point. No credential may reach a log
line, a run record, or the screen.

**Scale/Scope**: Single learner, single local instance. Roughly six fictional companies
across eight quarters in the seeded dataset. Four nodes, five indicators, three read endpoints
and one write endpoint.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Checked against constitution v2.2.0.

| Principle | Gate | Status |
|-----------|------|--------|
| I. Learning-First Transparency | The agent loop is visible in project code | PASS. `ChainRunner` is an explicit loop over four `ChainNode` instances. No declarative agent construct. The model is called through `ChatModel.chat(...)` at exactly one call site. |
| I. Learning-First Transparency | Lowest useful LangChain4j abstraction | PASS. Only `ChatModel`, `ChatRequest`, and `ChatResponse` are used. Chat memory, tool binding, and RAG are not needed by this feature and are not pulled in. |
| I. Learning-First Transparency | No dependency added merely to save lines | PASS. The Micronaut LangChain4j integration is deliberately not used. See R-010, which weighs it against the explicit factory the provider seam needs. |
| II. Provider-Agnostic Inference | Endpoint, model, credential are configuration | PASS. `ChatModelFactory` reads provider mode, base URL, model name, and credential from a Micronaut `@ConfigurationProperties` bean bound to environment variables. |
| II. Provider-Agnostic Inference | Switching providers needs no code edit | PASS. `L4J_PROVIDER=local` or `cloud` selects the mode. Node code never sees the provider. |
| II. Provider-Agnostic Inference | Missing configuration fails at startup | PASS. `ChatModelFactory` is a `@Context` bean, so Micronaut instantiates it eagerly at startup and its constructor validation aborts the boot, naming the absent variable. |
| II. Provider-Agnostic Inference | Credential never logged, persisted, or shown | PASS. The credential reaches the builder through `customHeaders(Supplier)` and is never held in a field the serializer or logger can reach. The persisted model exchange stores prompt and response text only. |
| II. Provider-Agnostic Inference | Plan states what leaves the machine | PASS. See Data Egress below. |
| III. Protocol Contracts Before Implementation | Node boundaries are declared, validated structures | PASS. Each boundary is a Java record with Jakarta Validation constraints, defined in `contracts/node-boundaries.md` before implementation. |
| III. Protocol Contracts Before Implementation | Contracts shared from a single source | PASS. Java DTOs are the single source. The Micronaut OpenAPI annotation processor emits the description at compile time, and `openapi-typescript` generates the frontend types from it. The frontend declares no shape by hand. |
| III. Protocol Contracts Before Implementation | MCP and A2A schemas | NOT APPLICABLE. This feature exposes no MCP tool and sends no A2A message. |
| IV. Test-First | Failing tests written before implementation | PASS. Task ordering puts each node's test ahead of its implementation. |
| IV. Test-First | Model output not asserted against exact text | PASS. Summary tests assert non-empty text, absence of the credential, and that no numeric token appears that is absent from the indicator set. |
| IV. Test-First | Non-model tests run with no credential or network | PASS. Every node test except the live one uses a `FakeChatModel` implementing `ChatModel`. Testcontainers needs Docker but no credential and no outbound network. |
| IV. Test-First | Live model tests separately selectable, explicit skip | PASS. A `liveTest` JVM test suite with its own Gradle task, excluded from `check`, skipped with a named message when `OLLAMA_API_KEY` is absent. Selection is the task, not a flag. |
| IV. Test-First | Deterministic recomputation | PASS. `BigDecimal` with declared scale and `RoundingMode.HALF_UP` in the computing node. |
| V. Observable Agent Runs | Structured trace per run | PASS. One `node_execution` row per node holding input, output, start time, duration, and outcome. |
| V. Observable Agent Runs | Trace records model identifier, provider mode, tokens, latency | PASS. The summarizing node's row additionally holds the request text, response text, model identifier, provider mode, and token counts when reported. |
| V. Observable Agent Runs | Trace retrievable in the user interface | PASS. The run detail view renders all four node records including the model exchange. |
| V. Observable Agent Runs | No secrets or full document bodies in logs | PASS. Structured logging carries run and node identifiers, never payloads. |
| Stack | Locked entries respected | PASS. Java 25 with Micronaut and LangChain4j, Ollama in either mode, PostgreSQL with pgvector available, React on Vite, TanStack Query, Vitest. |
| Stack | Every model call carries a timeout | PASS. `L4J_MODEL_TIMEOUT_SECONDS`, default 45, applied through the Ollama builder's `timeout`. |
| Stack | LangChain4j pinned to one version | PASS. 1.18.0, decided by the Micronaut Platform BOM and restated as a Gradle `constraints` entry so the version is visible and greppable in the build file. One decision, one declaration. See R-002. |
| Stack | Java release declared once through the Gradle toolchain | PASS. `java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }` in `backend/build.gradle.kts`. Micronaut 5 publishes for JVM 25, so the toolchain and the framework agree. |
| Stack | Gradle is the only build tool, no parallel Maven | PASS. No `pom.xml` and no `mvnw` exists. Every documented command goes through `./gradlew`. |
| Stack | Build scripts use the Kotlin DSL and carry no application logic | PASS. `build.gradle.kts` and `settings.gradle.kts` declare plugins, dependencies, the toolchain, the annotation processor path, and the `liveTest` suite only. |
| Stack | The Gradle wrapper is committed | PASS. `gradlew`, `gradlew.bat`, and `gradle/wrapper/` are committed. |
| Stack | Micronaut is the sole application framework | PASS. Dependency injection, configuration, HTTP, data access, and validation all go through Micronaut. No second framework appears on the classpath. |
| Stack | Micronaut pinned once through the Platform BOM | PASS. 5.1.5, applied by the Micronaut Gradle plugin in `backend/build.gradle.kts`. |
| Stack | Annotation processor runs on the toolchain's Java release | PASS. The processor path is configured against the same toolchain, so compile-time wiring and bytecode agree on release 25. |
| Stack | Compile-time dependency injection, no reflective wiring added | PASS. Beans are discovered by the Micronaut processor. Nothing scans the classpath at runtime. |
| Stack | Configuration bound through Micronaut configuration properties | PASS. `ModelProperties` is a `@ConfigurationProperties` bean reading environment variables. |
| Stack | OpenAPI generated by Micronaut OpenAPI, no hand-maintained second description | PASS. The processor emits it at compile time. The frontend types are generated from that file. |
| Stack | Migrations remain Flyway, ordered and committed | PASS. `V1__chain_run.sql` and `V2__node_execution.sql`, run by `micronaut-flyway` at startup. Schema generation is disabled. |
| Agent framework | Micronaut LangChain4j declarative AI services not used | PASS. The integration is not on the classpath at all, so `@AiService` generation cannot appear by accident. See R-010. |
| Workflow | Spec and plan exist before implementation | PASS. |

**Result**: all applicable gates pass. No entry in Complexity Tracking.

### Data Egress

Required by Principle II before any data is sent to a hosted provider.

What leaves the machine in cloud mode is the prompt built by the summarizing node. It
contains the fictional company name, the reporting period, and the five computed indicator
values, plus fixed instruction text. Nothing else is sent. The companies and figures are
invented and committed to the repository, so no real financial data, no personal data, and no
credential other than the bearer token in the request header ever crosses the boundary. In
local mode nothing leaves the machine at all.

### Post-Design Re-Check

Re-evaluated after Phase 1, against the artifacts now on disk.

- **Principle I**: the design added no abstraction. `contracts/node-boundaries.md` defines a
  three-line interface and four records. The one model call site survives the design intact.
  R-010 records why the Micronaut LangChain4j integration was refused despite being available
  and supported: it would hide the provider seam that Principle II exists to teach.
- **Principle II**: `quickstart.md` documents the same variable set for both provider modes,
  and the Data Egress statement above is complete. R-001 surfaced a concrete trap, a base URL
  carrying a path suffix, which is called out in the quickstart so it cannot be configured
  wrong quietly. R-012 tightened credential handling to the `Supplier` overload.
- **Principle III**: both contracts were written before any handler exists, and R-006 fixes
  the single source as the Java DTOs with generated TypeScript, now through Micronaut OpenAPI.
  `quickstart.md` carries the drift check that enforces it.
- **Principle IV**: the split between `./gradlew test` and `./gradlew liveTest` is concrete in
  `quickstart.md`, including the skip message requirement.
- **Principle V**: `data-model.md` shows the trace is complete, carrying provider mode, model
  identifier, both texts of the model exchange, token counts, and per-node duration.

No gate changed status. Complexity Tracking stays empty.

## Project Structure

### Documentation (this feature)

```text
specs/001-financial-agent-chain/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── node-boundaries.md
│   └── rest-api.md
├── checklists/
│   └── requirements.md
└── tasks.md             # Created by /speckit-tasks, not by this command
```

### Source Code (repository root)

```text
backend/
├── build.gradle.kts
├── settings.gradle.kts          # at the repository root, declares this project
├── gradlew, gradlew.bat, gradle/wrapper/   # committed, every command goes through it
└── src/
    ├── main/
    │   ├── java/dev/l4jlab/chain/
    │   │   ├── Application.java      # Micronaut.run entry point
    │   │   ├── core/                 # ChainNode, ChainRunner, ChainRunService, ChainFailure
    │   │   ├── node/                 # the four nodes, one class each
    │   │   ├── domain/               # ChainRequest, FinancialRecord, IndicatorSet, RunSummary
    │   │   ├── dataset/              # SampleDatasetLoader, reads the committed JSON
    │   │   ├── model/                # ChatModelFactory, ModelProperties, ProviderMode
    │   │   ├── persistence/          # @MappedEntity records, @JdbcRepository interfaces
    │   │   └── web/                  # RunController, CatalogController, DTOs, ExceptionHandler
    │   └── resources/
    │       ├── application.yml
    │       ├── logback.xml
    │       ├── data/companies.json   # the seeded sample dataset
    │       └── db/migration/         # Flyway V1__chain_run.sql, V2__node_execution.sql
    ├── test/java/dev/l4jlab/chain/
    │   ├── core/                     # ChainRunner ordering and failure-stop tests
    │   ├── node/                     # one test class per node, FakeChatModel for the fourth
    │   ├── model/                    # ChatModelFactory configuration tests
    │   ├── web/                      # controller and contract tests
    │   ├── integration/              # Testcontainers end-to-end with FakeChatModel
    │   └── support/                  # FakeChatModel
    └── liveTest/java/dev/l4jlab/chain/live/   # SummarizeNodeLiveTest, own Gradle task

frontend/
├── package.json
├── vite.config.ts
└── src/
    ├── api/                          # generated schema.d.ts plus a thin fetch client
    ├── components/                   # RunLauncher, RunProgress, IndicatorTable,
    │                                 # SummaryPanel, NodeTimeline, NodeDetail, RunHistory
    ├── pages/                        # NewRunPage, RunDetailPage, HistoryPage
    ├── hooks/                        # useStartRun, useRun (polling), useRuns, useCatalog
    └── test/                         # Vitest setup and MSW handlers

compose.yaml                          # PostgreSQL with pgvector, and Ollama for local mode
```

**Structure Decision**: Web application, two deployable parts. The backend under `backend/`
owns the chain, the dataset, persistence, and the HTTP surface. The frontend under
`frontend/` owns presentation only and holds no chain logic. The split is forced by the
locked stack, which puts agent orchestration in Java and the interface in React. Node classes
live in their own package so a learner can open `node/` and see exactly four files, one per
step of the chain.

## Complexity Tracking

No constitutional violations. This section is intentionally empty.
