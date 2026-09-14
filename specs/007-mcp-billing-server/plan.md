# Implementation Plan: MCP Billing Server (spec revision 2026-07-28)

**Branch**: `007-mcp-billing-server` | **Date**: 2026-09-13 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/007-mcp-billing-server/spec.md`

## Summary

Build a Model Context Protocol server that targets specification revision **2026-07-28**,
the stateless core, and exposes five billing tools to AI agents — deployed as three identical
replicas behind a round-robin proxy, calling a separate legacy REST API that owns the data and
enforces every entitlement.

The technical approach is settled by four clarifications recorded in `spec.md` (session
2026-09-13) and by one verified fact behind them: **no Java library implements this revision**.
The official SDK is at v2.0.1, tracks `2025-11-25`, and its tracking issues for statelessness
(#1011), sessionless handles (#1010), Tasks (#1013), and list TTLs (#1009) are open.
`langchain4j-mcp` is client-side only.

So the approach is a **hybrid** (FR-028). Everything the MCP Java SDK already provides comes
from the SDK: `McpSchema.Tool` — which carries `outputSchema`, `annotations`, and `_meta`,
every field FR-011 and FR-012 need — `ToolAnnotations` with all four hints, `CallToolResult`
with `structuredContent` and `isError`, the JSON-RPC records, `McpError`, the elicitation
payloads, and the `JsonSchemaValidator` abstraction. Hand-written code is confined to seven
2026-07-28 deltas that exist nowhere, each naming the upstream issue that retires it
(`research.md` R-003). That is **seven deltas**, carried by roughly a dozen classes in
`protocol/` and `tasks/` — the count that matters is the seven, because each one has an
upstream issue that retires it. An SDK-free plan would have owned the whole surface.

The server is hosted by **`io.micronaut.mcp:micronaut-mcp-server-java-sdk:2.0.0`** (FR-029),
which the pinned Micronaut Platform BOM 5.1.5 already manages. That module was missed by the
first two inventories and found during implementation; `research.md` R-014 inventories it
properly, as the constitution's gate requires. It brings compile-time tool discovery, argument
binding, schema generation, a `JsonSchemaValidator`, protocol error mappers, and — decisively —
`@Tool` with `@Tool.ToolAnnotations` carrying all four behaviour hints, so FR-011 is a
declaration rather than code. No hand-written controller, no Jakarta Servlet runtime.

It does not rescue the revision: the module documents 2025-06-18 and its default transport
context extractor still assumes `2025-03-26` and reads `Mcp-Session-Id`. The seven deltas stand,
now written against Micronaut seams — a replacement `McpTransportContextExtractor` for headers
and per-request `_meta`, result decoration for `resultType`/`ttlMs`/`cacheScope`/`serverInfo`,
and extra method handlers for `server/discover` and the Tasks extension.

LangChain4j contributes exactly one thing (FR-030): a compatibility test driving a real
`DefaultMcpClient` — obtained from `micronaut-mcp-client-langchain4j`, a module that exists for
exactly this purpose — to prove a legacy client receives the documented diagnostic rather than
silence. Dual-era operation and wiring the existing backend
as a consumer are out of scope for this iteration.

Statelessness is honoured as the revision intends: cursors and MRTR request state are signed,
self-contained, and carried in the request; idempotency records, task state, and the audit log
live in shared PostgreSQL because all three replicas must see them identically. No session id
is minted or accepted anywhere.

## Technical Context

**Language/Version**: Java 25, declared once through the Gradle toolchain (constitution)

**Primary Dependencies**: Micronaut 5.1.5 (Platform BOM) — HTTP server, DI, config binding,
validation, Micronaut Data JDBC, Flyway, Micronaut OpenAPI (legacy API only). **MCP Java SDK
`io.modelcontextprotocol.sdk:mcp` v2.0.1** for all revision-independent machinery (FR-028).
**LangChain4j `langchain4j-mcp` 1.18.0, test scope only**, for the FR-030 compatibility test.
No direct schema-library dependency: validation goes through the SDK's `JsonSchemaValidator`
abstraction (`research.md` R-008). Transitive from `mcp-core`: `reactor-core`, `slf4j-api`,
`jackson-annotations`, `jakarta.servlet-api` — the servlet API lands on the compile path but
no servlet container is used.

**Storage**: PostgreSQL, two schemas — `legacy_billing` (domain data, owned by the legacy API)
and `mcp_ops` (idempotency, tasks, audit; no billing entity). Flyway migrations, ordered and
committed. No second datastore.

**Testing**: JUnit 5 with `micronaut-test-junit5` and AssertJ for the three deterministic
suites (`:mcp-server:test`, `:legacy-billing-api:test`, `:token-issuer:test`); Testcontainers
where a real PostgreSQL is needed. A separate `topologyTest` source set and Gradle task drives
the acceptance scenarios against the three-replica Compose stack through the proxy. Split by
task, never by a flag — the rule already applied to `liveTest`.

**Target Platform**: Linux containers via Docker Compose, on one network

**Project Type**: Multi-service backend. Three new Gradle subprojects plus one nginx config.
No frontend.

**Performance Goals**: `start_billing_run` returns a handle in under 1 second measured at the
client (SC-003). Nothing else has a stated target.

**Constraints**: Runs fully offline; no inference provider is contacted. No tool error may
contain a stack trace, SQL, or an internal hostname (SC-006). The MCP server's inbound token
must never reach the legacy API (SC-005). **One published port only** —
`${MCP_HTTP_PORT:-8877}` on the proxy — because 8080 and 5432 are already taken by the
repository's existing development and packaged stacks (`research.md` R-005). Full
configuration surface in `research.md` R-012.

**Scale/Scope**: Five tools, seven domain entities, three `mcp_ops` tables, three MCP
replicas, and 56 test tasks across the four suites. Seeded fixture data sized for the
tests — two firms, three advisors, a few dozen runs.

## Constitution Check

*Constitution v3.1.0. Evaluated before Phase 0, re-evaluated after Phase 1, and re-evaluated
again after the 2026-09-13 clarifications. All three results are recorded.*

| Principle / constraint | Verdict | How |
|---|---|---|
| **I. Declarative-First, Library-First** — capability inventory required before design | **PASS, after a recorded failure** | R-004 inventories LangChain4j 1.18.0 and R-003 the MCP Java SDK v2.0.1. Neither covered `io.micronaut.mcp`, which the pinned platform BOM manages — the gap surfaced during implementation, not review, and **R-014 now inventories it**. The miss is recorded rather than quietly patched: the gate's whole purpose is to catch this earlier than task T007. |
| **I.** — no hand-written replacement for a library capability | **PASS, no exception needed** | The pre-clarification plan claimed an exception for `ToolSpecification`. It is withdrawn: `McpSchema.Tool` carries `outputSchema`, `annotations`, and `_meta`, and `ToolAnnotations` carries all four hints, so the tool definition type comes from a library. Every remaining hand-written piece is a 2026-07-28 behaviour no library has at any version, listed in R-003 with its retiring issue. |
| **I.** — missing capability must be named, never silently replaced | **PASS** | Seven deltas, each mapped to open issue #1011, #1010, #1013, or #1009, and surfaced in the server's own README (FR-028). |
| **I.** — deterministic steps stay deterministic code | **PASS** | Every step is deterministic; nothing is delegated to a model. |
| **II. Provider-Agnostic Inference** | **N/A, stated** | No inference. No provider is selected, configured, or contacted. |
| **II.** — configuration fails at startup naming the absent setting | **PASS** | The two shared keys (`MCP_CURSOR_HMAC_KEY`, `MCP_REQUEST_STATE_KEY`) fail loudly at startup outside development (R-012). |
| **II.** — PostgreSQL + pgvector; no second datastore | **PASS** | Redis was considered for idempotency and task state and rejected on this rule (R-005). |
| **II.** — startable from a committed container definition | **PASS** | `make mcp-up`, one command (SC-001), on a port that does not collide with the two stacks already in the repository. |
| **III. Protocol Contracts Before Implementation** | **PASS** | `contracts/tools/*.json` are five complete tool definitions committed before any handler, and they load directly into `McpSchema.Tool` — the committed contract *is* the runtime artefact. |
| **III.** — contracts shared from a single source, never declared twice | **PASS** | The JSON files are the single source; no Java type re-declares a schema. |
| **Stack** — the OpenAPI description MUST be generated by Micronaut OpenAPI | **PASS, exemption stated** | The legacy REST API generates OpenAPI 3.1 from its Java types. **The MCP server declares no OpenAPI processor on purpose**: its external contract is the five JSON Schema 2020-12 tool definitions inside a JSON-RPC payload, not an HTTP API description. A generated description of `POST /mcp` would be a second, less useful contract for the same surface — the duplication the same constraint forbids. Stated here so review reads the omission as a decision (`research.md` R-008). |
| **IV. Test-First at deterministic boundaries** | **PASS** | Every acceptance scenario maps to a named test, and every implementation task in `tasks.md` is preceded by a test task. The earlier plan had four implementation tasks with no test (T028, T055, T084, T098); all four now have one. |
| **IV.** — deterministic tests run with no credential and no network | **PASS** | Three in-process suites; everything needing the topology is in `topologyTest`. |
| **IV.** — live model tests separately selectable | **N/A** | None. `topologyTest` follows the same by-task rule anyway. |
| **V. Observable Agent Runs** | **N/A, analogue held to the same bar** | No agent run occurs. FR-026's audit record is the analogue: structured, per-invocation, no secrets, no full payloads. |
| **Stack** — Java 25, Micronaut, Gradle, PostgreSQL, MCP | **PASS** | Three Micronaut subprojects on Java 25 built by the existing wrapper. |
| **Stack** — Micronaut is the sole framework and owns the HTTP server | **PASS, and now library-first** | FR-029. `micronaut-mcp-server-java-sdk` hosts the server on Micronaut itself. The SDK's servlet transports are not used and no controller is hand-written. |
| **Stack** — compile-time DI only | **PASS** | Micronaut annotation processing throughout. |
| **Stack** — LangChain4j is the sole agent framework | **PASS** | No competing agent framework. LangChain4j appears once, in test scope, for FR-030. |
| **Workflow** — spec and plan before implementation | **PASS** | Both exist; spec clarified 2026-09-13. |
| **Workflow** — new MCP tool needs a committed schema | **PASS** | Five, committed. |
| **Additional** — env-var config with documented defaults | **PASS** | Nine settings tabulated in R-012 and required in the README by task. |
| **Additional** — no committed credentials | **PASS, no exception** | No key material is tracked anywhere. The issuer generates its pair at startup and the verifiers read only the published public half (R-020); the tests generate their own. The issuer's controllers stay `@Requires(env = …)`-gated so they do not exist outside development (R-013). |
| **Additional** — migrations ordered and committed | **PASS** | Flyway for both schemas. |
| **Additional** — dependency additions justified against Principle I | **PASS** | Two additions, both library-first: the MCP Java SDK (the whole point of FR-028) and `langchain4j-mcp` in test scope. The direct `json-schema-validator` dependency the earlier plan needed is **gone** — validation goes through the SDK's abstraction. |

**Result — pre-Phase 0**: PASS with three justified items.
**Result — post-Phase 1**: PASS, unchanged.
**Result — post-clarification (2026-09-13)**: **PASS, and stronger.** The hybrid decision
removed one dependency and one Principle I exception, and closed the OpenAPI question that
had been left unstated.

## Project Structure

### Documentation (this feature)

```text
specs/007-mcp-billing-server/
├── plan.md                          # This file
├── spec.md                          # Written from the /speckit-plan input
├── research.md                      # Phase 0 — protocol findings, SDK gap, LangChain4j inventory
├── data-model.md                    # Phase 1 — legacy_billing and mcp_ops entities
├── quickstart.md                    # Phase 1 — run and verify
├── contracts/                       # Phase 1
│   ├── README.md
│   ├── mcp-protocol.md
│   ├── legacy-billing-api.md
│   ├── token-issuer.md
│   └── tools/
│       ├── search_billing_runs.json
│       ├── get_billing_run_status.json
│       ├── get_run_failures.json
│       ├── post_fee_adjustment.json
│       └── start_billing_run.json
└── tasks.md                         # Phase 2 — /speckit-tasks, NOT created here
```

### Source code (repository root)

```text
settings.gradle.kts                  # + include("mcp-server", "legacy-billing-api", "token-issuer")

mcp-server/
├── build.gradle.kts                 # + io.modelcontextprotocol.sdk:mcp:2.0.1
└── src/
    ├── main/java/dev/l4jlab/mcp/
    │   ├── Application.java
    │   ├── protocol/                # the 2026-07-28 deltas — one class per feature (SC-004)
    │   │   ├── McpController.java           # POST /mcp; implements McpStatelessServerHandler
    │   │   ├── HeaderValidationFilter.java  # Mcp-Method / Mcp-Name / MCP-Protocol-Version → -32020
    │   │   ├── RequestEnvelope.java         # per-request _meta: version, capabilities, traceparent
    │   │   ├── ProtocolVersionGate.java     # -32022 with data.supported
    │   │   ├── ClientCapabilities.java      # -32021 with data.requiredCapabilities
    │   │   ├── ResultEnvelope.java          # resultType + serverInfo on every result
    │   │   ├── DiscoverHandler.java         # server/discover
    │   │   ├── ToolsListHandler.java        # deterministic order + ttlMs + cacheScope
    │   │   ├── ToolsCallHandler.java        # dispatch, validation, result shaping, MRTR params
    │   │   ├── ToolCatalog.java             # loads the committed contracts into McpSchema.Tool
    │   │   ├── CursorCodec.java             # server-minted opaque cursors (HMAC)
    │   │   ├── RequestStateCodec.java       # MRTR requestState (AEAD)
    │   │   └── ErrorBoundary.java           # the only place protocol vs tool errors part
    │   ├── tools/                   # one class per tool, five of them
    │   ├── tasks/                   # TaskStore, TasksGet/Cancel/UpdateHandler
    │   ├── ops/                     # OperationRecordRepository (idempotency)
    │   ├── security/                # inbound validation, principal, token exchange
    │   ├── legacy/                  # LegacyBillingClient, LegacyErrorTranslator
    │   └── audit/                   # AuditWriter, ArgumentSummary
    ├── main/resources/
    │   ├── application.yml          # every setting from research.md R-012
    │   ├── tools/                   # the five contracts, copied in at build time
    │   └── db/migration/            # Flyway, mcp_ops
    ├── test/java/                   # deterministic; stubbed legacy client
    └── topologyTest/java/           # acceptance scenarios through the proxy

legacy-billing-api/
├── build.gradle.kts                 # + micronaut-openapi
└── src/
    ├── main/java/dev/l4jlab/legacy/
    │   ├── Application.java
    │   ├── api/                     # controllers; Micronaut OpenAPI describes these
    │   ├── security/                # audience validation, scope, entitlements
    │   ├── domain/                  # Micronaut Data JDBC repositories
    │   ├── runsim/                  # the 30–90s phase-advancing simulation, and cancellation
    │   └── testsupport/             # @Requires(env="test-capture") request log
    ├── main/resources/db/migration/ # Flyway, legacy_billing, incl. seed fixtures
    └── test/java/

token-issuer/
├── build.gradle.kts
└── src/main/
    ├── java/dev/l4jlab/issuer/      # all controllers @Requires(env=…)-gated — R-013
    └── resources/dev-keys/          # committed RSA pair — development only, and labelled so

deploy/mcp/nginx.conf                # round-robin over mcp-a, mcp-b, mcp-c
compose.mcp.yaml                     # postgres, issuer, legacy API, 3 replicas, proxy
Makefile                             # + mcp-up, mcp-down, mcp-logs, mcp-verify
```

**Structure Decision**: three Gradle subprojects beside the existing `backend` and `frontend`,
and a Compose file of their own. Three services because the topology is the lesson —
collapsing the legacy API into the MCP server would delete the token exchange, the
entitlement boundary, and the "never forwards the token" test with it. Three replicas because
FR-007's cross-replica scenarios cannot be faked.

Within `mcp-server`, `protocol/` is one class per 2026-07-28 delta rather than a single
dispatcher: SC-004 asks that a reader point at where each feature is implemented, and a file
name is the cheapest way to answer. Every class in that directory is something no library
provides; anything a library does provide is imported, not written.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| **Seven hand-written 2026-07-28 deltas** on top of the SDK (Principle I, library-first) | No Java library implements this revision. SDK v2.0.1 (2026-08-19) tracks 2025-11-25; #1011, #1010, #1013, #1009 are open; #1072 confirms `server/discover` 500s. Verified 2026-09-13. | Targeting 2025-11-25 deletes the feature's purpose (clarification 1). Forking the SDK is far more code to own. **Removal condition**: each delta names its upstream issue in `research.md` R-003 and in the server README; when an issue closes and ships, that delta goes. |
| **Schemas generated by `@Tool`, contracts committed separately** (Principle III: contract before handler) | The module generates `inputSchema`/`outputSchema` from Java types. Declaring tools any other way forfeits the four hints as declarations. | Resolved, not traded: `contracts/tools/*.json` stay committed and first, and a contract test asserts the generated schema matches the committed one. Drift fails the build, so the committed contract is still the authority — as a test oracle rather than a runtime resource (R-014). |
| **A replacement `McpTransportContextExtractor`** | The module's default assumes protocol version `2025-03-26` and reads `Mcp-Session-Id`, both removed by this revision. | The class is an advertised extension point, so replacing it is using the module as designed, not working around it. |
| **Development-only token issuer** (Additional: no committed credentials) | FR-025 requires the system to run offline from a fresh clone with no setup. | **No longer a violation.** The key is generated at startup rather than committed (R-020): only the issuer holds it, and the verifiers read the published public half, so nothing needed it to persist. The issuer's endpoints remain `@Requires(env = ...)`-gated, so they do not exist outside development. |
| **Three MCP replicas plus a proxy** rather than one instance | FR-007 and four acceptance scenarios cover behaviour that only exists across replicas. | One instance lets every one of those tests pass while the code is quietly stateful — passing for the wrong reason. |

**Withdrawn since the pre-clarification plan**: the `ToolSpecification` Principle I exception
(`McpSchema.Tool` carries every field it lacked) and the direct
`com.networknt:json-schema-validator` dependency (validation goes through the SDK's
`JsonSchemaValidator` abstraction). Two fewer things to justify.

## Phase status

- [x] Phase 0 — `research.md`: R-001…R-013, all NEEDS CLARIFICATION resolved, LangChain4j and
  MCP SDK capability inventories recorded
- [x] Phase 1 — `data-model.md`, `contracts/`, `quickstart.md`
- [x] Constitution Check re-evaluated post-design: PASS
- [x] **Clarification pass 2026-09-13** — four decisions recorded in `spec.md` (FR-028…FR-031);
  `research.md` R-003/R-004/R-005/R-008/R-011 revised, R-012/R-013 added; this plan's Summary,
  Technical Context, Constitution Check, structure, and Complexity Tracking rewritten
- [x] All 18 findings from the `/speckit-analyze` pass addressed
- [ ] Phase 2 — `tasks.md` regenerated against the revised plan
