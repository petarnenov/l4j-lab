<!--
SYNC IMPACT REPORT
Version change: 2.1.0 → 2.2.0
Bump rationale: MINOR. No principle is removed or redefined, and no locked stack entry is
replaced. The stack named no backend application framework at all, so locking Micronaut adds
a constraint rather than overturning one. Spring Boot appeared only in a feature plan, never
in this document, so the MAJOR trigger for replacing a locked entry is not met. The versioning
policy classifies this as materially expanded guidance.

Modified principles: none. All five principles are unchanged.

Added sections:
  Technology Stack Constraints > Application framework

Modified entries:
  Technology Stack Constraints, backend line now names Micronaut.
  Technology Stack Constraints > Agent framework, new bullet bounding the Micronaut
  LangChain4j integration against Principle I.

Removed sections: none

Artifacts this amendment renders non-compliant, with the plan for each:
  specs/001-financial-agent-chain/plan.md names Spring Boot 4.1.1, springdoc-openapi, and
  Spring Data JPA. Regenerate with /speckit-plan against this version.
  specs/001-financial-agent-chain/tasks.md carries Spring-specific tasks for the build file,
  configuration binding, entry point, persistence, error handling, logging, and the OpenAPI
  generator. Regenerate with /speckit-tasks after the plan.
  specs/001-financial-agent-chain/research.md R-006 fixes springdoc-openapi as the OpenAPI
  source. Revisit during the regenerated Phase 0.
  specs/001-financial-agent-chain/quickstart.md documents Maven commands and a Spring Boot
  run target. Both are superseded.
  No application source exists yet, so nothing written is invalidated.

Deferred TODOs: none
-->

# l4j AI Agent Lab Constitution

## Core Principles

### I. Learning-First Transparency (NON-NEGOTIABLE)

This project exists to teach how AI agents, the Model Context Protocol (MCP), and
agent-to-agent (A2A) communication actually work. Clarity outranks cleverness in every
trade-off.

- Every module MUST be readable end-to-end without stepping through a framework's internals.
- The agent loop (prompt assembly, model call, tool selection, tool execution, result
  folding) MUST remain visible in project code; it MUST NOT be hidden behind a wrapper that
  a reader cannot follow.
- Every non-obvious design decision MUST carry a short rationale comment or a note in the
  feature spec explaining why it was chosen.
- A dependency MUST NOT be added solely to save a few lines when it obscures the mechanism
  being taught.
- LangChain4j MUST be used at the lowest abstraction level that still expresses the task. A
  higher-level LangChain4j construct MUST NOT be used where it collapses prompt assembly,
  tool selection, or tool execution into a single opaque call.

Rationale: the deliverable of a learning project is understanding, not the smallest diff.
Abstractions that save typing but hide the lesson destroy the project's only real output.

### II. Provider-Agnostic Inference

The system MUST treat the inference backend as a swappable, configured dependency. Both a
locally served Ollama instance and the hosted Ollama Cloud service are supported, and neither
is privileged in code.

- The provider endpoint, the model identifier, and the credential MUST all be configuration.
  None of them MUST be hardcoded at a call site.
- Switching between a local instance and a hosted one MUST require only a configuration
  change, never an edit to agent or node code.
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

- Backend logic (tool handlers, retrieval, schema validation, persistence) MUST have failing
  tests written first, then made to pass.
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
  with its arguments and result, model identifier, token counts where available, and
  per-step latency.
- Traces MUST be persisted and MUST be retrievable in the user interface for any completed
  run.
- Errors MUST record which step failed and the input that reached it.
- Logs MUST be structured, and secrets or full document bodies MUST NOT be written into
  them.

Rationale: the failure modes worth learning from live between the steps. Without a trace,
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

Rationale for pinning both: a learning project is read far more often than it is configured.
One language, one release, and one build tool mean a learner never has to work out which of
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
- Configuration MUST be bound through Micronaut configuration properties reading environment
  variables, which is what makes the provider seam in Principle II a configuration change.
- The OpenAPI description required by Principle III MUST be generated by Micronaut OpenAPI
  from the Java types. A second, hand-maintained description MUST NOT exist.
- Database migrations remain Flyway, ordered and committed, per Additional constraints below.

Rationale: compile-time dependency injection suits a project whose stated output is
understanding. What is wired, and to what, is decided by a processor the learner can read the
output of, rather than by reflection at startup. Naming one framework also ends the recurring
question of which one a given feature plan happened to pick.

Inference providers:

- The supported provider modes are a locally served Ollama instance and the hosted Ollama
  Cloud service. A third inference provider MUST NOT be added without an amendment.
- The active provider mode MUST be resolvable at runtime and MUST be recorded in every agent
  run trace alongside the model identifier.
- A hosted provider MUST be reached over an encrypted connection, and the credential MUST be
  supplied through an environment variable.
- Every model call MUST carry a configurable timeout. An unbounded model call MUST NOT ship.

Agent framework:

- LangChain4j is the sole agent framework for this project. Every agent, tool binding,
  chat memory, embedding store integration, and retrieval pipeline MUST be built on its
  abstractions.
- A competing agent framework MUST NOT be introduced, and a bespoke in-house replacement for
  what LangChain4j already provides MUST NOT be written.
- Model access MUST go through a LangChain4j model interface rather than a hand-rolled HTTP
  client against Ollama.
- Vector storage MUST go through the LangChain4j pgvector embedding store rather than direct
  ad hoc SQL against the embedding tables.
- Where LangChain4j offers both a declarative and an explicit API for the same capability,
  the choice MUST be justified against Principle I in the feature plan.
- LangChain4j MUST be pinned to a single version declared in the build file, and that version
  MUST be recorded in the feature plan whenever an upgrade changes agent behavior.
- The Micronaut LangChain4j integration MAY be used to build and inject model beans from
  configuration, and MUST be pinned to a single declared version. Its declarative AI service
  generation MUST NOT be used, because it collapses prompt assembly, tool selection, and tool
  execution into one opaque call, which Principle I forbids. The convenience it offers is
  exactly the lesson this project exists to show.

Additional constraints:

- Configuration MUST come from environment variables with documented defaults. Credentials,
  endpoints, and model names MUST NOT be committed.
- Database schema changes MUST ship as ordered, committed migrations. Schema MUST NOT be
  mutated by hand or auto-generated at startup in a way that diverges from the migrations.
- Dependency additions MUST be justified in the pull request against Principle I.

## Development Workflow and Quality Gates

- Work follows the Spec Kit flow: specify, plan, tasks, implement. Implementation MUST NOT
  begin before a written spec and plan exist for the feature.
- Every feature MUST declare which principles it touches and how it satisfies them.
- A change MUST NOT merge while any test fails, while a new deterministic behavior lacks a
  test, or while a new MCP tool or A2A message lacks a committed schema.
- A change touching the agent loop MUST include at least one recorded example run in the
  pull request description showing the resulting trace, and that trace MUST name the provider
  mode it ran against.
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

**Version**: 2.2.0 | **Ratified**: 2026-09-12 | **Last Amended**: 2026-09-12
