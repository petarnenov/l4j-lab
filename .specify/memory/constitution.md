<!--
SYNC IMPACT REPORT
Version change: 2.2.0 → 3.0.0
Bump rationale: MAJOR. Principle I is redefined in a backward-incompatible way. Version 2.2.0
required LangChain4j at the lowest abstraction level, required the agent loop to stay visible in
hand-written project code, and forbade declarative AI service generation. Version 3.0.0 reverses
all three: the declarative approach is the first design, LangChain4j's own solutions (AI Services,
agentic orchestration, tools, memory, retrieval, MCP, guardrails, listeners) MUST be used wherever
the library provides them, and a hand-written replacement MUST NOT be written. The versioning
policy names a redefined principle as MAJOR.

Modified principles:
  I. Learning-First Transparency (NON-NEGOTIABLE)
    → I. Declarative-First, Library-First (NON-NEGOTIABLE)
  V. Observable Agent Runs: traces MUST now be captured through LangChain4j's observation hooks
    where the library provides them, rather than by hand instrumentation of a custom loop.
  II, III, IV: unchanged in obligation. IV gains one bullet stating that deterministic tests of
    declarative services run against a fake model implementing the LangChain4j model interface.

Added sections: none. Agent framework guidance is rewritten in place (see below).

Modified entries:
  Technology Stack Constraints > Application framework: new bullet stating that proxies created
    by LangChain4j AI Services and agentic services are library implementations of declared
    interfaces, not a competing wiring mechanism, and that their instances are exposed as
    Micronaut beans.
  Technology Stack Constraints > Agent framework: rewritten. The "declarative vs explicit, justify
    against Principle I" rule is inverted (the explicit API now needs the justification). The ban
    on Micronaut LangChain4j declarative AI service generation is lifted; it MAY be used, and one
    wiring style per service is required.
  Additional constraints: dependency additions are justified against the new Principle I.
  Development Workflow and Quality Gates: "a change touching the agent loop" becomes "a change
    touching an agent declaration or orchestration".

Removed rules:
  "The agent loop … MUST remain visible in project code; it MUST NOT be hidden behind a wrapper."
  "LangChain4j MUST be used at the lowest abstraction level that still expresses the task."
  "A dependency MUST NOT be added solely to save a few lines when it obscures the mechanism."
  "[Micronaut LangChain4j] declarative AI service generation MUST NOT be used."

Artifacts this amendment renders non-compliant, with the plan for each:
  backend/src/main/java/dev/l4jlab/chain/core/ChainRunner.java, ChainNode.java, and the
    sequencing in ChainRunService.java are a hand-written orchestration loop. Replace with a
    LangChain4j agentic workflow (a sequence of the four steps), keeping run state, per-step
    persistence, and asynchronous execution.
  backend/src/main/java/dev/l4jlab/chain/node/SummarizeNode.java assembles the prompt by hand and
    calls ChatModel.chat directly. Replace with an AI Service interface whose system and user
    messages are declared, returning the summary.
  backend/src/main/java/dev/l4jlab/chain/node/PrepareRequestNode.java, RetrieveRecordsNode.java,
    ComputeIndicatorsNode.java: deterministic steps. They stay deterministic code (Principle I) and
    are re-expressed as steps the orchestrator runs, without changing their arithmetic.
  backend/src/main/java/dev/l4jlab/chain/model/ChatModelFactory.java: review whether the Micronaut
    LangChain4j integration's configuration-driven model beans replace it while still meeting
    Principle II (startup failure naming the absent setting, credential never logged).
  Tracing in ChainRunner (NodeRecord, ModelExchangeHolder): move capture to LangChain4j listeners
    and orchestration hooks (Principle V), keeping the persisted node_execution records and the
    UI unchanged.
  Tests that assert the hand-written structure (ChainRunnerTest, DeterministicNodeBudgetTest,
    SummarizeNodeTest's inspection of the exact messages sent): rewrite against the declared
    services and the workflow, still with a fake model and still asserting structure, not text.
  specs/001-financial-agent-chain/research.md R-003 and R-010, and the Principle I rows of
    specs/001-financial-agent-chain/plan.md, record decisions this version reverses. Superseded
    by the migration feature's plan; left in place as history.
  README.md presents the hand-written loop and "the only model call in the project" as the
    reading path. Update with the migration feature.
  Plan: a new feature, "migrate the chain to LangChain4j declarative AI services and agentic
    orchestration", specified and planned against this version before any code changes. Until it
    merges, the code above is a recorded, known non-compliance, not a precedent.
  Features 003 (monorepo integration) and 004 (containerized deployment) are unaffected.

Deferred TODOs:
  TODO(AGENTIC_MODULE_AVAILABILITY): not verified whether langchain4j-agentic and its non-AI step
    support are managed by the Micronaut Platform BOM 5.1.5 (LangChain4j 1.18.0). The migration
    feature's research MUST confirm it before planning depends on it.
-->

# l4j AI Agent Lab Constitution

## Core Principles

### I. Declarative-First, Library-First (NON-NEGOTIABLE)

This project builds AI agents, Model Context Protocol (MCP) integrations, and agent-to-agent (A2A)
communication with LangChain4j, and it uses the library the way the library is designed to be
used: declaratively, and for everything it provides.

- The declarative approach is the first design. An agent MUST be declared as a LangChain4j AI
  Service interface, with its system and user messages, template variables, and return type
  declared on the interface or in committed prompt templates.
- A multi-step or multi-agent flow MUST be declared with LangChain4j's agentic orchestration
  (sequential, parallel, conditional, loop, or supervisor workflows, as the flow requires). A
  hand-written loop, runner, or scheduler that sequences agents MUST NOT be written.
- Every capability LangChain4j provides MUST be taken from LangChain4j rather than reimplemented:
  AI Services, agentic orchestration, tools, chat memory, structured outputs, retrieval
  augmentation, embedding stores, document loading and splitting, MCP clients and tool providers,
  A2A integration, guardrails, and model and service listeners.
- The explicit, programmatic API (for example, calling a chat model directly with hand-assembled
  messages) MAY be used only where LangChain4j offers no declarative equivalent for the need. Each
  such use MUST be recorded as a justified exception in the feature plan, naming the missing
  declarative capability.
- Declarative-first does not mean model-first. Arithmetic, data retrieval, validation, and any
  step whose result must be exact and repeatable MUST remain deterministic code. Such steps MUST be
  expressed as steps the LangChain4j orchestrator runs, or as tools an agent calls, never delegated
  to a model.
- Every non-obvious design decision MUST carry a short rationale comment or a note in the feature
  spec. Where a declaration hides behavior a reader needs to know (a retry, a memory window, a
  guardrail), the declaration MUST carry a comment saying what it does.
- Where a needed LangChain4j capability is missing, experimental, or unavailable at the pinned
  version, the feature plan MUST say so and choose between a version change under the
  single-version rule and a justified exception. It MUST NOT silently fall back to a hand-written
  replacement.

Rationale: the library already encodes how prompts, tools, memory, retrieval, and orchestration fit
together, and it will keep improving them. Declaring agents and flows states intent in a form the
library can validate, observe, and evolve, while hand-written machinery around a model is code this
project would have to own, test, and keep in step with the library by hand. Understanding is kept
through declarations a reader can scan and through the traces required by Principle V, not
through reimplementation.

### II. Provider-Agnostic Inference

The system MUST treat the inference backend as a swappable, configured dependency. Both a
locally served Ollama instance and the hosted Ollama Cloud service are supported, and neither
is privileged in code.

- The provider endpoint, the model identifier, and the credential MUST all be configuration.
  None of them MUST be hardcoded at a call site.
- Switching between a local instance and a hosted one MUST require only a configuration
  change, never an edit to agent or orchestration code.
- Credentials MUST be read from the environment. A credential MUST NOT be committed, written
  into a log line, persisted into a run record, or shown on screen.
- Missing or malformed configuration MUST fail at startup with a message naming the absent
  setting, never at the moment the model is first called and never as a silent fallback to a
  different provider.
- Before data is sent to a hosted provider, the feature plan MUST state what leaves the
  machine. Secrets, credentials, and personal data MUST NOT be sent to a hosted provider.
- Persistence and vector search MUST use PostgreSQL with the pgvector extension. A second
  datastore MUST NOT be introduced without an amendment to this constitution.
- PostgreSQL MUST be startable through a committed container or script definition, and a
  fresh clone MUST reach a running system using only documented commands plus the credentials
  the documentation names.
- The documentation MUST state, for each supported provider mode, exactly which environment
  variables are required.

Rationale: the lesson is the agent, not the host of the weights. Pinning the project to one
backend would make the exercise depend on the learner's hardware, while pinning it to a
hosted one would make it depend on their wallet. Keeping the seam explicit teaches the more
useful habit and keeps both doors open.

### III. Protocol Contracts Before Implementation

MCP and A2A are protocol exercises, so the contract is the artifact that comes first.

- Each MCP tool MUST have a declared name, description, and typed input and output schema
  committed before its handler is implemented.
- Each A2A message type MUST have a versioned schema, and agents MUST reject messages that
  fail schema validation rather than coercing them.
- A change that alters an existing tool or message schema in a backward-incompatible way
  MUST introduce a new version rather than mutate the old one.
- Contract definitions MUST be shared between backend and frontend from a single source; the
  same shape MUST NOT be declared twice by hand.

Rationale: protocols are the subject matter here. Implementation-first work produces
accidental contracts that teach the wrong habits and break across agent boundaries.

### IV. Test-First at Deterministic Boundaries, Live Model Tests Permitted (NON-NEGOTIABLE)

Tests are written before the implementation for every deterministic unit of behavior.

- Backend logic (tool handlers, deterministic workflow steps, retrieval, schema validation,
  persistence) MUST have failing tests written first, then made to pass.
- Declared AI Services and agentic workflows MUST be tested with a fake model that implements
  the LangChain4j model interface, asserting on the messages the declaration produces, the tools
  selected, the structured result, the order of workflow steps, and error handling.
- Frontend logic MUST be tested with Vitest under the same order: test, red, implement,
  green.
- Model output MUST NOT be asserted against exact text. Tests covering model interaction
  MUST assert on structure, tool-call selection, schema conformance, or error handling.
- Tests that do not exercise model interaction MUST run with no credential and no network
  access, and MUST NOT be made to depend on a reachable provider.
- Tests that do exercise model interaction MAY require a live provider, a valid credential,
  and network access. They MUST be separately selectable so the deterministic tests can still
  be run alone.
- A live model test MUST state its provider mode and skip with an explicit message, never
  fail silently or pass vacuously, when its credential is absent.
- The consequence is accepted deliberately: continuous integration and a fresh clone cannot
  run the complete suite without a credential. Any change that widens this dependency further
  MUST say so in the feature plan.
- Retrieval correctness MUST be tested against a fixed seeded corpus with deterministic
  embeddings or recorded vectors.

Rationale: non-determinism is confined to one layer. Everything around it is ordinary
software and MUST be held to ordinary standards, or agent bugs become unattributable.

### V. Observable Agent Runs

An agent run that cannot be inspected after the fact is not finished work.

- Every agent run MUST emit a structured trace recording the resolved prompt, each tool call
  with its arguments and result, each workflow step with its input and output, model
  identifier, token counts where available, and per-step latency.
- Trace data MUST be captured through LangChain4j's observation mechanisms (model listeners,
  AI Service and agent listeners, orchestration scope or monitoring hooks) wherever the library
  provides them. Hand instrumentation MAY fill only what those mechanisms do not expose, and the
  feature plan MUST name what it fills.
- Traces MUST be persisted and MUST be retrievable in the user interface for any completed
  run.
- Errors MUST record which step failed and the input that reached it.
- Logs MUST be structured, and secrets or full document bodies MUST NOT be written into
  them.

Rationale: a declarative agent hides its mechanics by design, so the trace is where they become
visible. The failure modes worth learning from live between the steps. Without a trace,
debugging an agent degrades into guessing at the prompt.

## Technology Stack Constraints

The stack is fixed for the life of this project. Replacing any entry below requires a MAJOR
amendment.

- Backend, agent orchestration, and AI assistant runtime: Java 25 with Micronaut and
  LangChain4j, built with Gradle.
- Inference runtime: Ollama, either locally served or the hosted Ollama Cloud service,
  selected by configuration.
- Datastore and vector search: PostgreSQL with pgvector.
- Frontend: React built with Vite.
- Server state and data fetching: TanStack Query (React Query). Fetched server state MUST
  NOT be duplicated into a separate global store.
- Frontend testing: Vitest.
- Interoperability: MCP for tool exposure and consumption, A2A for inter-agent messaging.

Language and build:

- The backend source language is Java at release 25. A second JVM language MUST NOT be
  introduced into production or test source.
- The Java release MUST be declared once through the Gradle toolchain, so the build compiles
  against 25 regardless of which JDK runs Gradle.
- Gradle is the only build tool for the backend. A parallel Maven build MUST NOT be added,
  and no committed script or documented command may invoke one.
- Build scripts use the Gradle Kotlin DSL. This is the single exception to the Java-only rule
  above, and build scripts MUST NOT carry application logic.
- The Gradle wrapper MUST be committed, and every documented command MUST go through it, so
  the build never depends on what happens to be installed on the machine.
- The separately selectable live model tests required by Principle IV MUST be expressed as a
  distinct Gradle task or test suite, never as a flag a developer has to remember to pass.

Rationale for pinning both: a project is read far more often than it is configured.
One language, one release, and one build tool mean a reader never has to work out which of
two toolchains produced the artifact in front of them.

Application framework:

- Micronaut is the sole backend application framework. Dependency injection, configuration
  binding, the HTTP server, data access, and validation MUST all go through it.
- A competing application framework MUST NOT be introduced. Spring Boot, Quarkus, and Helidon
  are each excluded by this rule, and mixing one in alongside Micronaut is excluded whether or
  not it replaces Micronaut anywhere.
- Micronaut MUST be pinned to a single version declared once through the Micronaut Platform
  BOM in the Gradle build. The pinned version is 5.1.5, the current platform release.
- The Micronaut annotation processor MUST run against the same Java release the toolchain
  declares, so compile-time wiring and compiled bytecode never disagree about the language
  level.
- Dependency injection MUST use Micronaut's compile-time processing. A runtime
  reflection-based or classpath-scanning wiring mechanism MUST NOT be added on top of it.
- Proxies that LangChain4j creates for declared AI Service interfaces and agentic workflows are
  library implementations of those interfaces, not a wiring mechanism, and are permitted. Their
  instances MUST be exposed to the application as Micronaut beans, through a Micronaut factory or
  the Micronaut LangChain4j integration, so injection itself stays compile-time.
- Configuration MUST be bound through Micronaut configuration properties reading environment
  variables, which is what makes the provider seam in Principle II a configuration change.
- The OpenAPI description required by Principle III MUST be generated by Micronaut OpenAPI
  from the Java types. A second, hand-maintained description MUST NOT exist.
- Database migrations remain Flyway, ordered and committed, per Additional constraints below.

Rationale: compile-time dependency injection keeps what is wired, and to what, decided by a
processor whose output can be read, rather than by reflection at startup. Naming one framework
also ends the recurring question of which one a given feature plan happened to pick.

Inference providers:

- The supported provider modes are a locally served Ollama instance and the hosted Ollama
  Cloud service. A third inference provider MUST NOT be added without an amendment.
- The active provider mode MUST be resolvable at runtime and MUST be recorded in every agent
  run trace alongside the model identifier.
- A hosted provider MUST be reached over an encrypted connection, and the credential MUST be
  supplied through an environment variable.
- Every model call MUST carry a configurable timeout. An unbounded model call MUST NOT ship.

Agent framework:

- LangChain4j is the sole agent framework for this project. Every agent, workflow, tool
  binding, chat memory, embedding store integration, retrieval pipeline, MCP integration, and
  A2A integration MUST be built on its abstractions.
- A competing agent framework MUST NOT be introduced, and a bespoke in-house replacement for
  what LangChain4j already provides MUST NOT be written.
- Where LangChain4j offers both a declarative and an explicit API for the same capability, the
  declarative API MUST be used. Choosing the explicit API requires a justified exception in the
  feature plan under Principle I.
- Model access MUST go through a LangChain4j model interface rather than a hand-rolled HTTP
  client against Ollama.
- Vector storage MUST go through the LangChain4j pgvector embedding store rather than direct
  ad hoc SQL against the embedding tables.
- LangChain4j MUST be pinned to a single version declared in the build file, and that version
  MUST be recorded in the feature plan whenever an upgrade changes agent behavior. Every
  LangChain4j module MUST resolve to that one version.
- The Micronaut LangChain4j integration MAY be used to build model beans from configuration and
  to generate AI Service implementations at compile time, and MUST be pinned to a single declared
  version. Each AI Service MUST be created in exactly one way, either through that integration or
  through LangChain4j's own AI Services builder in a Micronaut factory, never both, and the
  feature plan MUST state which.

Additional constraints:

- Configuration MUST come from environment variables with documented defaults. Credentials,
  endpoints, and model names MUST NOT be committed.
- Database schema changes MUST ship as ordered, committed migrations. Schema MUST NOT be
  mutated by hand or auto-generated at startup in a way that diverges from the migrations.
- Dependency additions MUST be justified in the pull request against Principle I: a LangChain4j
  module that provides the capability MUST be preferred over any third-party or hand-written
  alternative.

## Development Workflow and Quality Gates

- Work follows the Spec Kit flow: specify, plan, tasks, implement. Implementation MUST NOT
  begin before a written spec and plan exist for the feature.
- Every feature MUST declare which principles it touches and how it satisfies them.
- A change MUST NOT merge while any test fails, while a new deterministic behavior lacks a
  test, or while a new MCP tool or A2A message lacks a committed schema.
- A change touching an agent declaration or orchestration MUST include at least one recorded
  example run in the pull request description showing the resulting trace, and that trace MUST
  name the provider mode it ran against.
- A change that alters model configuration, provider selection, or credential handling MUST
  state which provider modes it was verified against before it merges.
- Review MUST verify constitutional compliance explicitly, not only correctness.
- Complexity that violates a principle MUST be either removed or recorded as a justified
  exception in the feature plan, naming the principle and the reason.

## Governance

This constitution supersedes ad hoc practice and informal convention. Where a tool default,
a habit, or a suggestion conflicts with a principle stated here, the principle wins.

Amendment procedure:

- Amendments MUST be proposed as a written change to this file, stating the motivation and
  the migration impact on existing code.
- An amendment takes effect when merged, and the Sync Impact Report at the top of this file
  MUST be updated in the same change.
- Code that an amendment renders non-compliant MUST be listed in the amendment with a plan
  for bringing it into compliance.

Versioning policy follows semantic versioning:

- MAJOR: a principle is removed or redefined in a backward-incompatible way, or a locked
  stack entry is replaced.
- MINOR: a principle or governance section is added, or existing guidance is materially
  expanded.
- PATCH: clarification, wording, or typo fixes that do not change obligations.

Compliance review:

- Every pull request MUST be checked against these principles before merge.
- The constitution MUST be re-read at the start of each planning cycle, and drift between
  stated principles and actual practice MUST be resolved by amending the document or fixing
  the code, never by ignoring the gap.

**Version**: 3.0.0 | **Ratified**: 2026-09-12 | **Last Amended**: 2026-09-13
