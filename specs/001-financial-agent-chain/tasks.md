---

description: "Task list for Financial Agent Chain"
---

# Tasks: Financial Agent Chain

**Input**: Design documents from `/specs/001-financial-agent-chain/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/node-boundaries.md, contracts/rest-api.md

**Tests**: Test tasks ARE included. Constitution Principle IV (Test-First at Deterministic Boundaries) is
NON-NEGOTIABLE and the plan's Constitution Check commits to writing each node's test ahead of its
implementation. Every deterministic behaviour gets a failing test first.

**Organization**: Tasks are grouped by user story so each story can be implemented, tested, and demoed
independently.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Exact file paths are included in every task

## Framework Notes (read before Phase 1)

Regenerated against constitution v2.2.0 and the Micronaut plan. The previous list targeted Spring Boot
and is superseded. Four things about Micronaut change how these tasks are written, and each one has bitten
projects that assumed Spring habits carry over.

**Compile-time dependency injection.** Nothing scans the classpath at runtime. A bean that is not on the
annotation processor path simply does not exist, and the failure is a missing bean at startup rather than
a compile error. T004 configures that path, and it blocks every later task that declares a bean.

**Validation, serialization, data access, and OpenAPI each need their own processor.** Adding the runtime
dependency without the matching processor produces a build that compiles and an application that silently
ignores your annotations.

**OpenAPI is generated at compile time.** The description exists after `./gradlew :backend:build` with no
server running, which is why the drift check does not boot the backend.

**The model factory is a `@Context` bean**, so Micronaut instantiates it eagerly and its constructor
aborts startup when the credential is absent (FR-016). That is the desired behaviour in production and a
trap in tests: without the bean substitution in T035, the default suite would fail on any machine with no
`OLLAMA_API_KEY`, which Principle IV forbids.

**Build tool**: Gradle only, per constitution v2.1.0. `plan.md` and `quickstart.md` were corrected, so no
Maven remains anywhere in the feature.

## Path Conventions

Web application, two deployable parts, per the Project Structure section of `plan.md`:

- Backend: `backend/src/main/java/dev/l4jlab/chain/`, tests in `backend/src/test/java/dev/l4jlab/chain/`
- Live tests: `backend/src/liveTest/java/dev/l4jlab/chain/live/`
- Frontend: `frontend/src/`
- Repository root: `compose.yaml`, `settings.gradle.kts`, the Gradle wrapper

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Bring an empty repository to a state where both parts build and a database runs

- [X] T001 Create the repository skeleton: `backend/`, `frontend/`, and a root `.gitignore` covering `build/`, `.gradle/`, `node_modules/`, and `.env`
- [X] T002 Add the committed Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/`) and `settings.gradle.kts` declaring the `backend` project
- [X] T003 Write `backend/build.gradle.kts` applying the `io.micronaut.application` plugin, importing the Micronaut Platform BOM at 5.1.5, and declaring the Java toolchain at release 25
- [X] T004 Configure the annotation processor path in `backend/build.gradle.kts`: `micronaut-inject-java`, `micronaut-validation-processor`, `micronaut-serde-processor`, `micronaut-data-processor`, and `micronaut-openapi`. Pin the processor's `--release` to the same toolchain release 25 the `java` block declares, so compile-time wiring and emitted bytecode cannot disagree about the language level. Nothing wires without this
- [X] T005 Declare the runtime dependencies in `backend/build.gradle.kts`: `micronaut-http-server-netty`, `micronaut-serde-jackson`, `micronaut-validation`, `micronaut-data-jdbc`, `micronaut-jdbc-hikari`, `micronaut-flyway`, the PostgreSQL driver, and `dev.langchain4j:langchain4j-ollama` with no explicit version so the BOM decides it, plus a `constraints` entry naming `dev.langchain4j:langchain4j-core:1.18.0` that restates the BOM's choice rather than overriding it, so the pinned version is greppable in the build file (R-002, Principle Stack)
- [X] T006 Declare the test dependencies and a `liveTest` JVM test suite in `backend/build.gradle.kts`: `micronaut-test-junit5`, AssertJ, and Testcontainers for the default suite, with `liveTest` registered as its own task and excluded from `check`
- [X] T007 [P] Create `backend/src/main/resources/application.yml` binding `L4J_PROVIDER`, `L4J_MODEL_BASE_URL`, `L4J_MODEL_ID`, `OLLAMA_API_KEY`, and `L4J_MODEL_TIMEOUT_SECONDS` (default 45) as placeholders, plus the datasource and Flyway settings, with schema generation disabled
- [X] T008 [P] Create `compose.yaml` at the repository root with a PostgreSQL 17 service carrying the pgvector extension and an Ollama service for local provider mode
- [X] T009 Scaffold the frontend in `frontend/` with Vite, React 19, and TypeScript 5.x (`frontend/package.json`, `frontend/vite.config.ts`, `frontend/tsconfig.json`)
- [X] T010 [P] Add frontend dependencies in `frontend/package.json`: TanStack Query 5, Vitest, Testing Library, Mock Service Worker, and `openapi-typescript`
- [X] T011 [P] Configure the Vite dev proxy for `/api` to the backend in `frontend/vite.config.ts` and add the `dev`, `test`, `generate:api`, and `check:api` scripts to `frontend/package.json`
- [X] T012 [P] Configure formatting and linting: a Spotless or equivalent block in `backend/build.gradle.kts`, and ESLint plus Prettier config in `frontend/`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The contracts, persistence, model factory, and chain machinery that every user story sits on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Contracts first (Principle III: declared before any handler)

- [X] T013 [P] Create the `ChainNode<I, O>` interface with `name()` and `run(I)` in `backend/src/main/java/dev/l4jlab/chain/core/ChainNode.java`
- [X] T014 [P] Create `ChainFailure` carrying a learner-facing message and the failing node name in `backend/src/main/java/dev/l4jlab/chain/core/ChainFailure.java`
- [X] T015 [P] Create the `ChainRequest` record, `@Serdeable` with `@NotBlank` and `@Pattern` constraints for `companyId` (`[a-z0-9-]{3,64}`) and `period` (`\d{4}-Q[1-4]`), in `backend/src/main/java/dev/l4jlab/chain/domain/ChainRequest.java`
- [X] T016 [P] Create the `FinancialRecord` record with the seven figure fields, `@Serdeable`, in `backend/src/main/java/dev/l4jlab/chain/domain/FinancialRecord.java`
- [X] T017 [P] Create the `RetrievedRecords` record, `@Serdeable`, with a non-null `current` and a nullable `prior` in `backend/src/main/java/dev/l4jlab/chain/domain/RetrievedRecords.java`
- [X] T018 [P] Create `Indicator`, `@Serdeable`, with a compact constructor enforcing that exactly one of `value` and `notApplicableReason` is present, in `backend/src/main/java/dev/l4jlab/chain/domain/Indicator.java`
- [X] T019 [P] Create `IndicatorSet`, `@Serdeable`, validating exactly five entries with unique names in `backend/src/main/java/dev/l4jlab/chain/domain/IndicatorSet.java`
- [X] T020 [P] Create the `RunSummary` record, `@Serdeable`, with `text` (max 4000 chars), `modelId`, `providerMode`, and nullable token counts in `backend/src/main/java/dev/l4jlab/chain/domain/RunSummary.java`
- [X] T021 [P] Create the `ProviderMode` enum with `LOCAL` and `CLOUD` in `backend/src/main/java/dev/l4jlab/chain/model/ProviderMode.java`

### Persistence

- [X] T022 [P] Write the Flyway migration `backend/src/main/resources/db/migration/V1__chain_run.sql` creating `chain_run` with every column from data-model.md, the `started_at DESC` index, and the check constraints on `failed_node`/`failure_reason` (both present for `FAILED` and `TIMED_OUT`, both absent otherwise) and on `ended_at` versus status
- [X] T023 [P] Write the Flyway migration `backend/src/main/resources/db/migration/V2__node_execution.sql` creating `node_execution` with JSONB payload columns, the unique `(run_id, position)` constraint, the `run_id` index, the allowed node-name check, and the cascade delete
- [X] T024 [P] Create `ChainRunEntity` as a `@MappedEntity` record mapping `chain_run` in `backend/src/main/java/dev/l4jlab/chain/persistence/ChainRunEntity.java`
- [X] T025 [P] Create `NodeExecutionEntity` as a `@MappedEntity` record mapping `node_execution`, with the two payload fields declared as Micronaut Data's JSON data type, in `backend/src/main/java/dev/l4jlab/chain/persistence/NodeExecutionEntity.java`
- [X] T026 Create `ChainRunRepository` and `NodeExecutionRepository` as `@JdbcRepository(dialect = Dialect.POSTGRES)` interfaces in `backend/src/main/java/dev/l4jlab/chain/persistence/`
- [X] T027 Configure the test datasource in `backend/src/test/resources/application-test.yml` against a Testcontainers PostgreSQL 17 instance, so no test depends on a database someone started by hand, then write a failing migration test asserting both tables, their constraints, the JSONB round trip, and the cascade delete in `backend/src/test/java/dev/l4jlab/chain/persistence/MigrationTest.java`, and make it pass

### Dataset

- [X] T028 [P] Author the seeded dataset `backend/src/main/resources/data/companies.json` with six fictional companies across eight consecutive quarters, every company labelled fictional, and at least one record carrying zero equity
- [X] T029 Write the failing `backend/src/test/java/dev/l4jlab/chain/dataset/SampleDatasetLoaderTest.java` covering `(companyId, period)` uniqueness, contiguous periods per company, prior-period lookup, and a named failure on a duplicate
- [X] T030 Implement `SampleDatasetLoader` as a singleton loading the file at startup with the validations above, in `backend/src/main/java/dev/l4jlab/chain/dataset/SampleDatasetLoader.java`

### Model access

- [X] T031 [P] Create `ModelProperties` as a Micronaut `@ConfigurationProperties` bean for provider mode, base URL, model identifier, credential, and timeout, in `backend/src/main/java/dev/l4jlab/chain/model/ModelProperties.java`
- [X] T032 Write the failing `backend/src/test/java/dev/l4jlab/chain/model/ChatModelFactoryTest.java` asserting startup failure naming the absent variable in cloud mode, rejection of a base URL carrying a path suffix (R-001), the timeout being applied, and the credential never appearing in `toString` or logs
- [X] T033 Implement `ChatModelFactory` as a `@Context` bean building `OllamaChatModel` for both modes from configuration alone, validating in the constructor, and passing the credential through `customHeaders(Supplier)` rather than a held map (R-012), in `backend/src/main/java/dev/l4jlab/chain/model/ChatModelFactory.java`
- [X] T034 [P] Create the `FakeChatModel` test fixture implementing LangChain4j `ChatModel` in `backend/src/test/java/dev/l4jlab/chain/support/FakeChatModel.java`, with scriptable responses, a configurable delay, empty-response behaviour, and a call counter exposing how many times `chat` was invoked
- [X] T035 Substitute `FakeChatModel` for the real `ChatModel` bean in the default test suite, through `@MockBean` or a `@Requires(env = "test")` factory, in `backend/src/test/java/dev/l4jlab/chain/support/TestChatModelFactory.java`. Without this the `@Context` factory in T033 aborts startup on a machine with no `OLLAMA_API_KEY`, breaking Principle IV

### The chain runner

- [X] T036 Write the failing `backend/src/test/java/dev/l4jlab/chain/core/ChainRunnerTest.java` asserting the fixed four-node order, one record written per node, the chain stopping at the first failure with earlier records retained, boundary validation between steps, any unexpected exception wrapped into a `ChainFailure` with no stack trace, and each node's `name()` returning the exact spelling the `node_execution` check constraint allows
- [X] T037 Implement `ChainRunner` looping over the ordered node list, timing each step, validating each boundary, and persisting one `node_execution` row per node, in `backend/src/main/java/dev/l4jlab/chain/core/ChainRunner.java`

### Cross-cutting infrastructure

- [X] T038 Create `Application.java` with the `Micronaut.run` entry point in `backend/src/main/java/dev/l4jlab/chain/Application.java`
- [X] T039 [P] Add an `ExceptionHandler` bean producing the RFC 9457 problem-detail shape with learner-facing detail text and no stack traces, in `backend/src/main/java/dev/l4jlab/chain/web/ApiExceptionHandler.java`
- [X] T040 [P] Configure structured logging carrying run and node identifiers only, never payloads or credentials, in `backend/src/main/resources/logback.xml`
- [X] T041 [P] Create the TanStack Query client provider and application shell in `frontend/src/main.tsx` and `frontend/src/App.tsx`
- [X] T042 [P] Create the thin typed fetch client in `frontend/src/api/client.ts`, reading shapes from the generated schema only
- [X] T043 [P] Set up Vitest and Mock Service Worker handlers in `frontend/src/test/setup.ts` and `frontend/src/test/handlers.ts`

**Checkpoint**: Contracts declared, database migrated, model factory validated at startup, runner proven, test substitution in place. User story work can begin.

---

## Phase 3: User Story 1 - Run the chain and read the summary (Priority: P1) 🎯 MVP

**Goal**: A learner picks a company and period, four nodes run in order, and a plain-language summary
appears next to the five indicators it was built from.

**Independent Test**: Select a sample company and period, start the run, and confirm a summary appears
alongside the computed indicators, with every figure in the summary traceable to the indicator table.

### Tests for User Story 1 ⚠️ Write first, confirm they fail

- [X] T044 [P] [US1] Write `backend/src/test/java/dev/l4jlab/chain/node/PrepareRequestNodeTest.java` asserting a valid selection becomes a `ChainRequest`, a blank or malformed company or period fails here naming the offending field (FR-002), and the injected `FakeChatModel` records zero calls (FR-007)
- [X] T045 [P] [US1] Write `backend/src/test/java/dev/l4jlab/chain/node/RetrieveRecordsNodeTest.java` asserting a match returns current plus prior, a first-period company returns a null prior, no match fails with a message naming the missing company or period rather than passing empty data (FR-003), and the injected `FakeChatModel` records zero calls (FR-007)
- [X] T046 [P] [US1] Write `backend/src/test/java/dev/l4jlab/chain/node/ComputeIndicatorsNodeTest.java` asserting all five formulas, scale 4 with `HALF_UP`, byte-identical output across ten repetitions (SC-005), a not-applicable marker with its reason for each zero-denominator and absent-prior case (FR-005), and the injected `FakeChatModel` records zero calls (FR-007)
- [X] T047 [P] [US1] Write `backend/src/test/java/dev/l4jlab/chain/node/SummarizeNodeTest.java` using `FakeChatModel` to assert the prompt carries the system instruction and the rendered indicator table, non-empty text is returned, an empty model response fails the run with a named reason, an over-length response is truncated with the truncation marked, the credential appears nowhere in the output, every numeric token in the response matches a value in the supplied `IndicatorSet` so a fabricated figure fails the test (SC-003), and driving the fake past the configured limit reaches `TIMED_OUT` with a stated reason while the records of nodes one through three survive (FR-019)
- [X] T048 [P] [US1] Write `backend/src/test/java/dev/l4jlab/chain/web/RunControllerTest.java` asserting `POST /api/runs` returns 202 with a run identifier and a `Location` header, and 400 naming the offending field for an unknown or malformed pair
- [X] T049 [P] [US1] Write `backend/src/test/java/dev/l4jlab/chain/web/CatalogControllerTest.java` asserting `GET /api/catalog` lists every seeded company with its periods oldest first
- [X] T050 [P] [US1] Write `backend/src/test/java/dev/l4jlab/chain/integration/ChainEndToEndTest.java` with Micronaut Test, Testcontainers, and `FakeChatModel`, starting a run and polling to `SUCCEEDED`, asserting four persisted node records and a stored summary, plus two concurrent runs completing independently without overwriting each other
- [X] T051 [P] [US1] Write `frontend/src/components/RunLauncher.test.tsx` asserting the two dropdowns populate from the catalog and starting a run posts the selected pair
- [X] T052 [P] [US1] Write `frontend/src/components/SummaryPanel.test.tsx` and `frontend/src/components/IndicatorTable.test.tsx` asserting the five indicators render, a not-applicable indicator shows its reason, and the fictional-data note is present

### Implementation for User Story 1

- [X] T053 [P] [US1] Implement `PrepareRequest` in `backend/src/main/java/dev/l4jlab/chain/node/PrepareRequestNode.java`, with `name()` returning `PrepareRequest`
- [X] T054 [P] [US1] Implement `RetrieveRecords` reading through `SampleDatasetLoader` in `backend/src/main/java/dev/l4jlab/chain/node/RetrieveRecordsNode.java`, with `name()` returning `RetrieveRecords`
- [X] T055 [P] [US1] Implement `ComputeIndicators` with `BigDecimal` scale 4 `HALF_UP` and a guard on every denominator in `backend/src/main/java/dev/l4jlab/chain/node/ComputeIndicatorsNode.java`, with `name()` returning `ComputeIndicators`
- [X] T056 [US1] Implement `Summarize` in `backend/src/main/java/dev/l4jlab/chain/node/SummarizeNode.java`: assemble the system instruction (fictional figures, describe only the supplied indicators, reproduce every indicator value verbatim so the traceability assertion in T047 holds, give no recommendation) plus the rendered table, call `ChatModel.chat(ChatRequest)` at the single model call site, capture the request text, response text, model identifier, provider mode, and token counts, with `name()` returning `Summarize`
- [X] T057 [US1] Apply the configured model timeout and map an exceeded limit to the `TIMED_OUT` terminal state, populating `failedNode` as `Summarize`, in `backend/src/main/java/dev/l4jlab/chain/node/SummarizeNode.java` and `backend/src/main/java/dev/l4jlab/chain/core/ChainRunner.java` (FR-019)
- [X] T058 [US1] Implement `ChainRunService` persisting a `PENDING` run and submitting the chain to an `ExecutorService` injected from Micronaut's blocking executor, driving the status transitions, in `backend/src/main/java/dev/l4jlab/chain/core/ChainRunService.java` (R-013)
- [X] T059 [US1] Create the `@Serdeable` request and response DTO records for the catalog, run creation, and run detail in `backend/src/main/java/dev/l4jlab/chain/web/dto/`, as the single source for the generated frontend types
- [X] T060 [US1] Implement `POST /api/runs` returning 202 with `Location`, annotated `@ExecuteOn(TaskExecutors.BLOCKING)` so the chain never touches the event loop, in `backend/src/main/java/dev/l4jlab/chain/web/RunController.java`
- [X] T061 [US1] Implement `GET /api/runs/{runId}` returning status, current node, indicators, summary, provider mode, and model identifier in `backend/src/main/java/dev/l4jlab/chain/web/RunController.java`
- [X] T062 [US1] Implement `GET /api/catalog` in `backend/src/main/java/dev/l4jlab/chain/web/CatalogController.java`
- [X] T063 [US1] Confirm the Micronaut OpenAPI processor emits the description at compile time and record its output path, configuring it in `backend/src/main/resources/META-INF/openapi.properties` if a non-default location is wanted
- [X] T064 [US1] Generate `frontend/src/api/schema.d.ts` from the emitted OpenAPI description with `openapi-typescript` and commit it
- [X] T065 [P] [US1] Implement the `useCatalog` and `useStartRun` hooks in `frontend/src/hooks/useCatalog.ts` and `frontend/src/hooks/useStartRun.ts`
- [X] T066 [P] [US1] Implement the `useRun` hook polling at one-second intervals and stopping at a terminal status in `frontend/src/hooks/useRun.ts`
- [X] T067 [P] [US1] Implement `RunLauncher` with the company and period dropdowns in `frontend/src/components/RunLauncher.tsx`
- [X] T068 [P] [US1] Implement `RunProgress` naming the currently executing node while the run is in flight in `frontend/src/components/RunProgress.tsx` (FR-020)
- [X] T069 [P] [US1] Implement `IndicatorTable` rendering values and not-applicable reasons in `frontend/src/components/IndicatorTable.tsx`
- [X] T070 [P] [US1] Implement `SummaryPanel` with a scroll area capped at 24rem and the standing teaching-exercise, not-investment-advice note in `frontend/src/components/SummaryPanel.tsx` (R-009)
- [X] T071 [US1] Wire `NewRunPage` and `RunDetailPage` so the summary and the indicator table appear on one screen in `frontend/src/pages/NewRunPage.tsx` and `frontend/src/pages/RunDetailPage.tsx` (FR-012)
- [X] T072 [US1] Surface a failed or timed-out run's reason and failing node in plain language on the run screen in `frontend/src/pages/RunDetailPage.tsx` (FR-015, SC-006)

**Checkpoint**: A learner can run the chain end to end and read the summary next to its indicators. This is the MVP.

---

## Phase 4: User Story 2 - Inspect what each node did (Priority: P2)

**Goal**: The learner steps through all four node boundaries of a run and reads each node's input, output,
duration, and, for the summarizing node, the exact model exchange.

**Independent Test**: Complete one run, open its detail view, and confirm four node entries appear in order
with input, output, and duration, and that the fourth shows the text sent to the model and the text returned.

### Tests for User Story 2 ⚠️ Write first, confirm they fail

- [X] T073 [P] [US2] Extend `backend/src/test/java/dev/l4jlab/chain/web/RunControllerTest.java` to assert `nodes` contains only started nodes in ascending position, that positions 1 through 3 carry null model columns, that position 4 carries both texts, the model identifier, and the token counts, and that no field anywhere carries the credential (FR-018)
- [X] T074 [P] [US2] Write `backend/src/test/java/dev/l4jlab/chain/integration/FailedRunTraceTest.java` asserting that a run failing at `Summarize` still returns the completed records of nodes one through three, with the failing node and its reason named (FR-015)
- [X] T075 [P] [US2] Write `frontend/src/components/NodeTimeline.test.tsx` and `frontend/src/components/NodeDetail.test.tsx` asserting four ordered entries, a marked failing node, and the model exchange rendering for position 4

### Implementation for User Story 2

- [X] T076 [US2] Extend the run detail DTO with the `nodes` array carrying position, node name, outcome, failure reason, input and output payloads, start time, duration, and the four model fields, in `backend/src/main/java/dev/l4jlab/chain/web/dto/RunDetailResponse.java`
- [X] T077 [US2] Map `NodeExecutionEntity` rows onto that array, ordered by position and limited to started nodes, in `backend/src/main/java/dev/l4jlab/chain/web/RunController.java`
- [X] T078 [US2] Persist `model_request_text`, `model_response_text`, `input_tokens`, and `output_tokens` onto the summarizing node's row in `backend/src/main/java/dev/l4jlab/chain/core/ChainRunner.java` (FR-010)
- [X] T079 [US2] Regenerate `frontend/src/api/schema.d.ts` from the updated OpenAPI description and commit it
- [X] T080 [P] [US2] Implement `NodeTimeline` listing the four nodes in execution order with duration and outcome in `frontend/src/components/NodeTimeline.tsx`
- [X] T081 [P] [US2] Implement `NodeDetail` rendering the boundary payloads and, for position 4, the full model exchange with the model identifier and provider mode, each inside a scroll area capped at 24rem so an unbounded model response cannot fill the screen, in `frontend/src/components/NodeDetail.tsx`
- [X] T082 [US2] Wire the timeline and detail into `frontend/src/pages/RunDetailPage.tsx` with the failing node visually marked (FR-013)

**Checkpoint**: Both User Story 1 and User Story 2 work independently.

---

## Phase 5: User Story 3 - Revisit earlier runs (Priority: P3)

**Goal**: The learner lists previous runs newest first and opens any of them to read its summary and node
detail exactly as produced.

**Independent Test**: Complete two runs, restart the backend, reload the page, and confirm both appear in
history and open with their original content intact.

### Tests for User Story 3 ⚠️ Write first, confirm they fail

- [X] T083 [P] [US3] Write `backend/src/test/java/dev/l4jlab/chain/persistence/ChainRunRepositoryTest.java` asserting newest-first ordering, the default limit of 50, the maximum of 200, and stable cursor paging with no duplicates or gaps
- [X] T084 [P] [US3] Write `backend/src/test/java/dev/l4jlab/chain/web/RunHistoryControllerTest.java` asserting `GET /api/runs` returns company, period, status, `summaryPreview` capped at 160 characters, timestamps, and `nextCursor`, and carries no node payloads
- [X] T085 [P] [US3] Write `backend/src/test/java/dev/l4jlab/chain/integration/RestartPersistenceTest.java` asserting a completed run and all four node records read back unchanged after the application context is rebuilt against the same database
- [X] T086 [P] [US3] Write `frontend/src/components/RunHistory.test.tsx` asserting the list renders newest first and each row opens its run

### Implementation for User Story 3

- [X] T087 [US3] Implement the newest-first cursor query on `started_at DESC` in `backend/src/main/java/dev/l4jlab/chain/persistence/ChainRunRepository.java`
- [X] T088 [US3] Write `summary_text` onto `chain_run` once at the end of a successful run and derive `summaryPreview` from it, in `backend/src/main/java/dev/l4jlab/chain/core/ChainRunService.java`
- [X] T089 [US3] Implement `GET /api/runs` with `limit` and `cursor` in `backend/src/main/java/dev/l4jlab/chain/web/RunController.java` (FR-014)
- [X] T090 [US3] Regenerate `frontend/src/api/schema.d.ts` and implement the `useRuns` hook in `frontend/src/hooks/useRuns.ts`
- [X] T091 [P] [US3] Implement `RunHistory` listing company, period, start time, and outcome in `frontend/src/components/RunHistory.tsx`
- [X] T092 [US3] Add `HistoryPage` and navigation to it in `frontend/src/pages/HistoryPage.tsx` and `frontend/src/App.tsx`

**Checkpoint**: All three user stories are independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T093 Write the live model test in `backend/src/liveTest/java/dev/l4jlab/chain/live/SummarizeNodeLiveTest.java`, stating its provider mode and calling `Assumptions.abort` with a message naming `OLLAMA_API_KEY` when the credential is absent (Principle IV, R-007)
- [X] T094 [P] Add a credential-leak test asserting that no run record, node record, API response, or log line contains the configured credential, in `backend/src/test/java/dev/l4jlab/chain/security/CredentialLeakTest.java` (FR-018, SC-002)
- [X] T095 [P] Add the contract drift check wiring `npm run check:api` against the compile-time OpenAPI description so a changed Java DTO fails the build, in `frontend/package.json` and `backend/build.gradle.kts` (Principle III, R-006)
- [X] T096 [P] Assert the three deterministic nodes complete together in under one hundred milliseconds in `backend/src/test/java/dev/l4jlab/chain/core/DeterministicNodeBudgetTest.java`
- [X] T097 [P] Measure end-to-end run duration across the sample dataset and assert the sixty-second budget in `backend/src/test/java/dev/l4jlab/chain/integration/RunDurationTest.java` (SC-004)
- [X] T098 [P] Write `README.md` at the repository root pointing at the four node classes and the single model call site, so a reviewer finds it within thirty seconds (SC-008)
- [X] T099 [P] Review every learner-facing failure message for plain language naming the failing node and the reason, across `backend/src/main/java/dev/l4jlab/chain/node/` and `backend/src/main/java/dev/l4jlab/chain/web/ApiExceptionHandler.java` (SC-006)
- [X] T100 [P] Add keyboard navigation and accessible labels to the launcher, timeline, and history in `frontend/src/components/`
- [ ] T101 Walk all six scenarios in `specs/001-financial-agent-chain/quickstart.md` end to end against both provider modes and record one trace per mode for the pull request, as the constitution's agent-loop rule requires
- [X] T102 Re-check the feature against constitution v2.2.0, confirming Micronaut as the sole framework, the platform BOM pin, the LangChain4j constraint restating it, the compile-time processor path and its release matching the toolchain, the absence of any `io.micronaut.langchain4j` module, the Gradle toolchain, the distinct live test task, the single model call site, the complete run trace, and `contracts/rest-api.md` still agreeing with the generated OpenAPI description

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies, starts immediately
- **Foundational (Phase 2)**: Depends on Setup. BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Foundational. No dependency on US2 or US3
- **User Story 2 (Phase 4)**: Depends on Foundational. Needs a completed run to inspect, so in practice it follows US1, but its own code touches only the node array and the timeline components
- **User Story 3 (Phase 5)**: Depends on Foundational. Needs completed runs to list, so in practice it follows US1. Independent of US2
- **Polish (Phase 6)**: Depends on the user stories you intend to ship

### Critical path inside Setup

T003 and T004 gate everything on the backend. A bean declared before the annotation processor path exists
will not be discovered, and the symptom is a missing bean at startup rather than a compile error, which
costs more time to diagnose than it does to configure.

### Critical path inside Foundational

T013 through T021 (contracts) unblock everything. T022 and T023 (migrations) unblock T024 through T027.
T028 unblocks T029 and T030. T031 and T032 unblock T033. T033 and T034 unblock T035, and T035 unblocks
every default-suite test from Phase 3 onward. T036 unblocks T037, which needs T026 and the contracts.

### Within each user story

- Tests are written and confirmed failing before the implementation they cover
- Domain and node classes before services, services before controllers, controllers before frontend hooks
- The generated schema (T064, T079, T090) must be regenerated after any DTO change and before the frontend consumes it

### Parallel Opportunities

- Setup: T007, T008, T010, T011, T012 run together once T001 through T006 and T009 land
- Foundational: all nine contract tasks T013 through T021 run together; T022 and T023 run together; T028, T031, T034, T039, T040, T041, T042, T043 are independent of each other
- US1: the nine test tasks T044 through T052 all run together; the three deterministic node implementations T053, T054, T055 run together; the frontend components T065 through T070 run together
- US2: T073, T074, T075 run together, then T080 and T081
- US3: T083 through T086 run together
- Polish: T094 through T100 run together
- With three developers, US1, US2, and US3 can be staffed in parallel once Phase 2 closes, provided the US2 and US3 developers use seeded run rows rather than waiting on the US1 user interface

---

## Parallel Example: User Story 1

```bash
# Write all nine failing tests for User Story 1 together:
Task: "PrepareRequestNodeTest in backend/src/test/java/dev/l4jlab/chain/node/PrepareRequestNodeTest.java"
Task: "RetrieveRecordsNodeTest in backend/src/test/java/dev/l4jlab/chain/node/RetrieveRecordsNodeTest.java"
Task: "ComputeIndicatorsNodeTest in backend/src/test/java/dev/l4jlab/chain/node/ComputeIndicatorsNodeTest.java"
Task: "SummarizeNodeTest in backend/src/test/java/dev/l4jlab/chain/node/SummarizeNodeTest.java"
Task: "RunControllerTest in backend/src/test/java/dev/l4jlab/chain/web/RunControllerTest.java"
Task: "CatalogControllerTest in backend/src/test/java/dev/l4jlab/chain/web/CatalogControllerTest.java"
Task: "ChainEndToEndTest in backend/src/test/java/dev/l4jlab/chain/integration/ChainEndToEndTest.java"
Task: "RunLauncher.test.tsx in frontend/src/components/RunLauncher.test.tsx"
Task: "SummaryPanel and IndicatorTable tests in frontend/src/components/"

# Then implement the three deterministic nodes together:
Task: "PrepareRequestNode in backend/src/main/java/dev/l4jlab/chain/node/PrepareRequestNode.java"
Task: "RetrieveRecordsNode in backend/src/main/java/dev/l4jlab/chain/node/RetrieveRecordsNode.java"
Task: "ComputeIndicatorsNode in backend/src/main/java/dev/l4jlab/chain/node/ComputeIndicatorsNode.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational, which blocks everything
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: run quickstart Scenario 1, then Scenarios 4, 5, and 6
5. Demo: a learner picks a company, starts a run, and reads the summary next to the indicators

### Incremental Delivery

1. Setup plus Foundational, the foundation is ready
2. Add User Story 1, validate with quickstart Scenario 1, ship the MVP
3. Add User Story 2, validate with quickstart Scenario 2
4. Add User Story 3, validate with quickstart Scenario 3
5. Polish, then run all six scenarios against both provider modes

### Parallel Team Strategy

1. The team completes Setup and Foundational together, since the contracts in T013 through T021 gate everything
2. Then Developer A takes User Story 1, Developer B takes User Story 2 against seeded run rows, Developer C takes User Story 3 against seeded run rows
3. Stories integrate at `RunDetailPage` and at the run detail DTO, so agree the `nodes` array shape before splitting

---

## Notes

- [P] means different files with no dependency on an incomplete task
- Every deterministic behaviour gets a failing test first, per Principle IV, which is non-negotiable
- The default `./gradlew :backend:test` must pass with no `OLLAMA_API_KEY` and no outbound network. Only `./gradlew :backend:liveTest` may reach a provider
- Never assert model output against exact text. Assert structure, non-emptiness, absence of the credential, and that no figure appears outside the indicator set
- The indicator values are the source of truth on screen. The summary sits beside them so any contradiction is visible
- No `io.micronaut.langchain4j` module belongs on the classpath. R-010 records why, and T102 checks it
- Commit after each task or logical group, and stop at any checkpoint to validate a story independently
