---

description: "Task list for 007-mcp-billing-server"
---

# Tasks: MCP Billing Server (spec revision 2026-07-28)

**Input**: Design documents from `/specs/007-mcp-billing-server/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Revision**: regenerated 2026-09-13 after the clarification session (FR-028…FR-031) and the
`/speckit-analyze` pass. **Revised again during implementation** after `research.md` R-014 found
`io.micronaut.mcp` in the pinned platform BOM: T002/T003/T006/T042 now build on
`micronaut-mcp-server-java-sdk`, and tools are declared with `@Tool` rather than dispatched by a
hand-written controller. The protocol layer is now built **on MCP Java SDK v2.0.1**; only the
seven deltas in `research.md` R-003 are hand-written.

**Tests**: Included, and not optional. `spec.md` requires every acceptance scenario to be an
automated test, and Constitution Principle IV requires the test written and failing before the
implementation for every deterministic unit. **Every implementation task below is preceded by
a test task** — the earlier revision had four that were not (T033, T056, T089, T104 close
them). The one exception is T027, the Micronaut Data repositories: they carry no behaviour of
their own and are exercised through T025 and T067, which is the normal shape for a data layer.

**Organization**: Grouped by user story. Four suites: `:mcp-server:test`,
`:legacy-billing-api:test`, `:token-issuer:test` (all in-process, no network), and
`topologyTest` against the three-replica Compose stack. Split by Gradle task, never by a flag.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on incomplete work)
- **[Story]**: US1–US4, mapping to the user stories in `spec.md`

## Path Conventions

- `mcp-server/src/main/java/dev/l4jlab/mcp/…`, tests in `src/test/java/…` and `src/topologyTest/java/…`
- `legacy-billing-api/src/main/java/dev/l4jlab/legacy/…`
- `token-issuer/src/main/java/dev/l4jlab/issuer/…`

---

## Phase 1: Setup (Shared Infrastructure)

- [X] T001 Add `include("mcp-server", "legacy-billing-api", "token-issuer")` to `settings.gradle.kts`, leaving `backend` and `frontend` untouched
- [X] T002 [P] Create `mcp-server/build.gradle.kts`: Micronaut application plugin, Platform BOM 5.1.5, Java 25 toolchain, `options.release = 25`, annotation processors (http-validation, serde, data, validation), `micronaut-http-server-netty`, `micronaut-serde-jackson`, `micronaut-data-jdbc`, `micronaut-jdbc-hikari`, `micronaut-flyway`, `micronaut-management`, **`io.modelcontextprotocol.sdk:mcp:2.0.1`**, `micronaut-reactor`, `runtimeOnly` postgresql + flyway-database-postgresql + logback. **No `micronaut-openapi`** — see plan.md Constitution Check for why the MCP server declares none. **No direct schema-library dependency** — R-008
- [X] T003 [P] Resolve which SDK module supplies `io.modelcontextprotocol.json.schema.JsonSchemaValidator` and whether its default dialect is 2020-12, then bind it in `mcp-server/build.gradle.kts`. If it does not support 2020-12, add a direct schema library and record the justification in `plan.md` Complexity Tracking (open item from R-008)
- [X] T004 [P] Create `legacy-billing-api/build.gradle.kts`: same Micronaut/Java-25 shape plus `micronaut-openapi` processor, `micronaut-openapi-annotations`, and `-Amicronaut.openapi.filename=openapi-legacy`
- [X] T005 [P] Create `token-issuer/build.gradle.kts`: same shape plus a JWT library from the Platform BOM; no datasource
- [X] T006 Add `langchain4j-mcp:1.18.0` in **test scope only** to `mcp-server/build.gradle.kts`, for the FR-030 compatibility test (depends on T002; same file)
- [X] T007 Add the `topologyTest` source set and task to `mcp-server/build.gradle.kts`, modelled on the existing `liveTest` set in `backend/build.gradle.kts`, and a root-level `topologyTest` task depending on it (depends on T002; same file)
- [X] T008 [P] Create `deploy/mcp/nginx.conf`: round-robin `upstream` over `mcp-a:8080`, `mcp-b:8080`, `mcp-c:8080` for **every method on `/mcp`, not only POST** — `GET` and `DELETE` must reach the application so its own `405` is what the client sees (T053, T057); nginx answering first would test nginx. Plus a pass-through of `/dev/` and `/.well-known/jwks.json` to `token-issuer:8080`, so the stack needs one published port. No other path is served. Follow `deploy/load-balancer/nginx.conf`
- [X] T009 [P] Create `mcp-server/Dockerfile`, `legacy-billing-api/Dockerfile`, `token-issuer/Dockerfile`, following `backend/Dockerfile`
- [X] T010 Create `compose.mcp.yaml`: `postgres`, `token-issuer`, `legacy-billing-api`, replicas `mcp-a`/`mcp-b`/`mcp-c`, and `mcp-proxy`. **The proxy publishes `${MCP_HTTP_PORT:-8877}` and nothing else publishes any port** — 8080 is guarded by `make dev`/`dev-backend` and 5432 by `compose.yaml`, and feature 004's promise that the stacks coexist must hold for a third (R-005). Health checks on `/health/readiness`; the issuer starts before the two services that verify against it. Pass `LEGACY_RUN_DURATION_MS` through to the legacy API so `make mcp-verify` can compress the simulated run (depends on T008, T009)
- [X] T011 Add `mcp-up`, `mcp-down`, `mcp-logs`, `mcp-verify` to `Makefile` — `mcp-verify` sets `LEGACY_RUN_DURATION_MS=3000` so the suite finishes in seconds, while a plain `mcp-up` keeps FR-024's real timing — under a new `##@ MCP billing server` group, using `scripts/make/require.sh` and `scripts/make/port-free.sh` on `MCP_HTTP_PORT`, with `## ` descriptions so `make help` picks them up (depends on T010)
- [X] T012 [P] Write `mcp-server/src/main/resources/application.yml` and the two peer files binding every setting in `research.md` R-012 from environment variables with the documented defaults, and failing at startup outside development when `MCP_CURSOR_HMAC_KEY` or `MCP_REQUEST_STATE_KEY` is absent

**Checkpoint**: `make mcp-up` starts six containers on one published port that collides with nothing.

---

## Phase 2: Foundational (Blocking Prerequisites)

**⚠️ CRITICAL**: no user story phase begins until this phase completes.

### Database schemas

- [X] T013 [P] Flyway migration `legacy-billing-api/src/main/resources/db/migration/V1__legacy_billing_schema.sql` (FR-023): schema `legacy_billing` with `firm`, `advisor`, `household`, `account`, `billing_run`, `run_failure`, `fee_adjustment` per `data-model.md` Part 1, including CHECK constraints on `status`, `phase`, `accounts_processed <= accounts_total`, `current_fee_bps >= 0`
- [X] T014 [P] Flyway migration `mcp-server/src/main/resources/db/migration/V1__mcp_ops_schema.sql`: schema `mcp_ops` with `operation_record` (PK `(principal_user_id, operation_id)`), `task_record`, `audit_record` per `data-model.md` Part 2 — and no billing entity
- [X] T015 [P] Flyway migration `legacy-billing-api/src/main/resources/db/migration/V2__seed_fixtures.sql`: `firm-alpha` (advisors `adv-101`, `adv-102`) and `firm-beta` (`adv-201`), households and accounts under each, enough runs to exceed one 20-row page for `firm-alpha`, at least one FAILED run with `run_failure` rows, and at least one long-running run for cancellation

### Token issuer

- [X] T016 [P] Failing test `token-issuer/src/test/java/dev/l4jlab/issuer/TokenMintingTest.java`: a minted token carries `sub`, `firm_id`, `role`, `advisor_ids`, and the requested `aud`, and verifies against the published JWKS
- [X] T017 Implement `token-issuer/src/main/java/dev/l4jlab/issuer/KeyProvider.java` loading the committed RSA pair from `src/main/resources/dev-keys/`, and `JwksController.java` serving `GET /.well-known/jwks.json` (makes T016 pass)
- [X] T018 Implement `token-issuer/src/main/java/dev/l4jlab/issuer/TokenController.java` — `POST /dev/token` — with the six fixture principals from `contracts/token-issuer.md` (depends on T017)
- [X] T019 [P] Failing test `token-issuer/src/test/java/dev/l4jlab/issuer/TokenExchangeTest.java`: `/dev/exchange` rejects a subject token whose `aud` is not `mcp-billing-server`; the minted token carries the same `sub`/`firm_id`/`role`/`advisor_ids` and does not embed the subject token
- [X] T020 Implement `token-issuer/src/main/java/dev/l4jlab/issuer/ExchangeController.java` — `POST /dev/exchange` per `contracts/token-issuer.md` (makes T019 pass)
- [X] T021 [P] Failing test `token-issuer/src/test/java/dev/l4jlab/issuer/EnvironmentGatingTest.java`: under a non-development, non-`test-capture` environment, `/dev/token`, `/dev/exchange`, and the test levers do not exist and return 404 (FR-025, R-013)
- [X] T022 Gate every issuer controller with `@Requires(env = …)` for development and `test-capture` only, and add the three test levers to `TokenController` — wrong `aud`, already expired, signed by a second key (makes T021 pass)

### Legacy API core

- [X] T023 [P] Failing test `legacy-billing-api/src/test/java/dev/l4jlab/legacy/security/AudienceValidationTest.java`: wrong `aud`, expired, and bad signature each yield 401 (FR-015)
- [X] T024 Implement `legacy-billing-api/src/main/java/dev/l4jlab/legacy/security/BearerTokenFilter.java` validating signature against the issuer JWKS, `exp`, and `aud == "legacy-billing-api"` (makes T023 pass)
- [X] T025 [P] Failing test `legacy-billing-api/src/test/java/dev/l4jlab/legacy/security/EntitlementTest.java`: ADVISOR sees only its `advisor_ids`; **FIRM_ADMIN and OPS each** see the whole firm; a cross-firm id is 403 checked before existence, so a foreign id and a nonexistent one are indistinguishable; READ_ONLY is 403 on writes
- [X] T026 Implement `legacy-billing-api/src/main/java/dev/l4jlab/legacy/security/CallerScope.java` and `EntitlementGuard.java` (makes T025 pass)
- [X] T027 [P] Implement the Micronaut Data JDBC entities and repositories under `legacy-billing-api/src/main/java/dev/l4jlab/legacy/domain/` for the seven entities in `data-model.md` Part 1
- [X] T028 [P] Implement `legacy-billing-api/src/main/java/dev/l4jlab/legacy/testsupport/ReceivedRequestLog.java` and `GET /test/received-requests`, `@Requires(env = "test-capture")`, in-memory only, never writing to stdout. **Captures the `Authorization` and `traceparent` headers of every inbound request** — needed by SC-005 and FR-027. *(Moved here from the old Polish phase, where tasks in User Story 2 already depended on it.)*

### MCP server: SDK wiring and shared plumbing

- [X] T029 [P] Failing test `mcp-server/src/test/java/dev/l4jlab/mcp/security/InboundTokenTest.java`: the MCP server rejects wrong-audience, expired, and bad-signature tokens with 401 and never reaches a handler
- [X] T030 Implement `mcp-server/src/main/java/dev/l4jlab/mcp/security/InboundTokenFilter.java` (`aud == "mcp-billing-server"`, `exp`, signature) and `Principal.java` (FR-013), derived per request and retained nowhere between requests (makes T029 pass)
- [X] T031 [P] Failing test `mcp-server/src/test/java/dev/l4jlab/mcp/security/TokenExchangeClientTest.java` (FR-016): the exchanged token is attached to the outbound call and the inbound token string appears in no outbound header
- [X] T032 Implement `mcp-server/src/main/java/dev/l4jlab/mcp/security/TokenExchangeClient.java` (FR-016) with a bounded timeout and a cache that never outlives the request (makes T031 pass)
- [X] T033 [P] Failing test `mcp-server/src/test/java/dev/l4jlab/mcp/legacy/TraceparentPropagationTest.java`: a `traceparent` present in the request `_meta` is attached, byte-for-byte, to the outbound legacy request; when absent, no `traceparent` header is sent (**FR-027's second half — previously untested**)
- [X] T034 Implement `mcp-server/src/main/java/dev/l4jlab/mcp/legacy/LegacyBillingClient.java` — a declarative Micronaut HTTP client for `contracts/legacy-billing-api.md`, attaching the exchanged token and forwarding `traceparent` (makes T033 pass; depends on T032)
- [X] T035 [P] Failing test `mcp-server/src/test/java/dev/l4jlab/mcp/protocol/RequestEnvelopeTest.java` (FR-002): a request missing `io.modelcontextprotocol/protocolVersion` or `io.modelcontextprotocol/clientCapabilities` is `-32602` + HTTP 400; `traceparent` is read when present; `clientInfo` is optional
- [X] T036 Implement `mcp-server/src/main/java/dev/l4jlab/mcp/protocol/RequestEnvelope.java` parsing the per-request `_meta` (FR-002), using the SDK's `McpSchema.ClientCapabilities` and `Implementation` records rather than new types (makes T035 pass)
- [X] T037 [P] Failing test `mcp-server/src/test/java/dev/l4jlab/mcp/protocol/ResultEnvelopeTest.java`: **every** result the server can produce carries `resultType` and `io.modelcontextprotocol/serverInfo` in `_meta` — asserted by enumerating handlers, not by spot-checking one (FR-003; **previously only `server/discover` was checked**)
- [X] T038 Implement `mcp-server/src/main/java/dev/l4jlab/mcp/protocol/ResultEnvelope.java`, the single place `resultType` and `serverInfo` are attached, wrapping the SDK's `Result` types which carry neither (makes T037 pass)
- [X] T039 **Superseded by R-014.** No controller is written: `micronaut-mcp-server-java-sdk` hosts the endpoint (FR-029). What remains hand-written is the filter chain around it. ~~Implement `mcp-server/src/main/java/dev/l4jlab/mcp/protocol/McpController.java`~~: `POST /mcp` on Micronaut Netty, implementing the SDK's `McpStatelessServerHandler` (`handleRequest(McpTransportContext, JSONRPCRequest) → Mono<JSONRPCResponse>`), dispatching by `method`. The SDK's `DefaultMcpStatelessServerHandler` is deliberately **not** used — it mandates `initialize` (FR-029, plan.md Complexity Tracking) (depends on T036, T038)
- [X] T040 **Covered by `ReadToolsTest` and `ProtocolSurfaceTest`**, which assert the same mapping table across both halves. ~~Failing test `mcp-server/src/test/java/dev/l4jlab/mcp/protocol/ErrorBoundaryTest.java`~~: the full mapping table in `contracts/mcp-protocol.md` — every protocol condition to a JSON-RPC error with its HTTP status, every tool condition to an `isError` result with HTTP 200, no overlap
- [X] T041 **Superseded by R-016.** The boundary is two classes, not one: `ToolFailureMapper` carries a tool failure through the module, and `JsonRpcResponseSerializer` turns it into an `isError` result. ~~Implement `mcp-server/src/main/java/dev/l4jlab/mcp/protocol/ErrorBoundary.java`~~ on top of the SDK's `McpError` and `JSONRPCError` records, with a fixed message vocabulary and no exception text, SQL, or hostname reaching a caller (makes T040 pass)
- [X] T042 [P] Add a Gradle `Copy` task to `mcp-server/build.gradle.kts` putting `specs/007-mcp-billing-server/contracts/tools/*.json` into `src/main/resources/tools/` at build time, so the committed contract and the runtime artefact cannot drift
- [X] T043 [P] Failing test `mcp-server/src/test/java/dev/l4jlab/mcp/protocol/ToolCatalogTest.java`: all five contracts load into `McpSchema.Tool` with `inputSchema`, `outputSchema`, and `ToolAnnotations` populated; arguments validate against `inputSchema` and `structuredContent` against `outputSchema` through the SDK validator
- [X] T044 Implement `mcp-server/src/main/java/dev/l4jlab/mcp/protocol/ToolCatalog.java`, deserializing the five files straight into `McpSchema.Tool` and validating through `io.modelcontextprotocol.json.schema.JsonSchemaValidator` (makes T043 pass; depends on T003, T042)
- [X] T045 [P] Failing test `mcp-server/src/test/java/dev/l4jlab/mcp/audit/AuditWriterTest.java`: an audit row carries principal, tool, outcome, duration, and `traceparent`; `argument_summary` contains only allow-listed identifier keys; a full payload cannot reach it
- [X] T046 Implement `mcp-server/src/main/java/dev/l4jlab/mcp/audit/AuditWriter.java` and `ArgumentSummary.java` with a per-tool allow-list of key names — an allow-list, not a redaction pass, so a new field is invisible by default (makes T045 pass)
- [X] T047 [P] Implement the scripted MCP client fixture `mcp-server/src/test/java/dev/l4jlab/mcp/testclient/ScriptedMcpClient.java`: builds conformant requests (headers mirrored from the body, `_meta` populated, declarable capabilities), targets an in-process server or a URL, asserts `resultType` and `serverInfo` on every response it receives, and is shared by all suites. It is hand-rolled because LangChain4j's `DefaultMcpClient` always sends `initialize` and cannot speak this revision (`research.md` R-004)
- [X] T048 Make `ScriptedMcpClient` available to `topologyTest` and add `mcp-server/src/topologyTest/java/dev/l4jlab/mcp/topology/TopologyFixture.java`, reading the proxy and per-replica URLs from the environment and failing with "run `make mcp-up` first" rather than starting containers itself (depends on T007, T047)

**Checkpoint**: SDK wired, identity, plumbing, audit and the test client all work. No tool answers yet.

---

## Phase 3: User Story 4 - Protocol layer rejects malformed and unauthorized traffic (Priority: P1) 🎯 MVP substrate

**Goal**: the 2026-07-28 surface — per-request metadata, header routing, version negotiation,
discovery, cacheable and deterministically ordered `tools/list`, and the strict separation of
protocol errors from tool errors.

**Independent Test**: hand-built HTTP against `POST /mcp`. No domain data, no tool implementation.

**Why first, though US1 is also P1**: every other story's requests go through this layer.

### Tests for User Story 4 ⚠️ write first, confirm they fail

- [X] T049 [P] [US4] `mcp-server/src/test/java/dev/l4jlab/mcp/protocol/HeaderValidationTest.java` (FR-008): no `Mcp-Method` is rejected; no `Mcp-Name` on `tools/call` is rejected; `Mcp-Name` disagreeing with `params.name` is `-32020` + HTTP 400; `=?base64?…?=` is decoded before comparison; header names compare case-insensitively, values case-sensitively (US4-1, US4-2)
- [X] T050 [P] [US4] `mcp-server/src/test/java/dev/l4jlab/mcp/protocol/ProtocolVersionTest.java` (FR-004): `MCP-Protocol-Version` disagreeing with `_meta` is `-32020`; an unsupported version is `-32022` + HTTP 400 with `data.supported` and `data.requested` (US4-3)
- [X] T051 [P] [US4] `mcp-server/src/test/java/dev/l4jlab/mcp/protocol/DiscoverHandlerTest.java` (FR-005): `server/discover` returns `supportedVersions`, `capabilities.tools`, `capabilities.extensions["io.modelcontextprotocol/tasks"]`, `instructions`, `ttlMs`, `cacheScope`, and `serverInfo` in `_meta` (US4-4)
- [X] T052 [P] [US4] `mcp-server/src/test/java/dev/l4jlab/mcp/protocol/ToolsListTest.java` (FR-009, FR-022): exactly five tools; `ttlMs` and `cacheScope: "public"` present; two consecutive calls return identical order; each tool carries `inputSchema`, `outputSchema`, and all four hints with the values in `contracts/README.md` (US4-5, FR-011, FR-022)
- [X] T053 [P] [US4] `mcp-server/src/test/java/dev/l4jlab/mcp/protocol/NoSessionTest.java`: `GET /mcp` and `DELETE /mcp` return 405; an `Mcp-Session-Id` request header is ignored and never echoed; `Last-Event-ID` is ignored (FR-006)
- [X] T054 [P] [US4] `mcp-server/src/test/java/dev/l4jlab/mcp/protocol/UnknownMethodTest.java`: an unknown method is `-32601` + HTTP 404; `initialize` is answered with a JSON-RPC error naming the supported versions rather than silence (FR-001)
- [X] T055 [P] [US4] `mcp-server/src/test/java/dev/l4jlab/mcp/protocol/CapabilityGateTest.java`: when the server must send an `elicitation/create` but the client did not declare `elicitation`, the response is `-32021` with `data.requiredCapabilities`
- [X] T056 [P] [US4] `mcp-server/src/test/java/dev/l4jlab/mcp/audit/ProtocolErrorAuditTest.java`: a request rejected at the protocol layer still writes an audit row with `outcome = PROTOCOL_ERROR` and its `error_code` (**previously implemented without a test**)
- [X] T057 [P] [US4] `mcp-server/src/topologyTest/java/dev/l4jlab/mcp/topology/ProtocolConformanceTopologyTest.java`: the same header, version, discovery and `tools/list` assertions through the proxy, and `tools/list` from all three replicas returns byte-identical definitions in identical order
- [X] T058 [P] [US4] `mcp-server/src/topologyTest/java/dev/l4jlab/mcp/topology/TokenRejectionTopologyTest.java`: wrong-audience, expired and bad-signature tokens are each rejected by the MCP server, and separately by the legacy API when presented directly (US4-6, FR-015)
- [X] T059 [P] [US4] `mcp-server/src/topologyTest/java/dev/l4jlab/mcp/topology/LangChain4jCompatibilityTest.java`: build a real `dev.langchain4j.mcp.client.DefaultMcpClient` against the proxy and assert its `initialize` receives a JSON-RPC error naming the supported protocol versions — never silence, never a legacy-era response (**FR-030**)

### Implementation for User Story 4

- [X] T060 [US4] Implement `mcp-server/src/main/java/dev/l4jlab/mcp/protocol/HeaderValidationFilter.java` (FR-008): requires `MCP-Protocol-Version`, `Mcp-Method`, and on `tools/call` `Mcp-Name`; decodes the Base64 sentinel; compares against the body; emits `-32020` + HTTP 400 through `ErrorBoundary` (makes T049 pass)
- [X] T061 [US4] Implement `mcp-server/src/main/java/dev/l4jlab/mcp/protocol/ProtocolVersionGate.java` (FR-004) holding the single supported version `2026-07-28` and emitting `-32022` with `data.supported`/`data.requested`. Does **not** use the SDK's `ProtocolVersions`, which stops at `2025-11-25` (makes T050 pass)
- [X] T062 [P] [US4] Implement `mcp-server/src/main/java/dev/l4jlab/mcp/protocol/DiscoverHandler.java` (FR-005) per `contracts/mcp-protocol.md`, `ttlMs: ${MCP_TOOLS_TTL_MS}`-independent at `3600000`, `cacheScope: "public"` (makes T051 pass)
- [X] T063 [P] [US4] Implement `mcp-server/src/main/java/dev/l4jlab/mcp/protocol/ToolsListHandler.java` (FR-009) returning the five `McpSchema.Tool` values in the fixed order in `contracts/README.md` — a constant in code, never a directory listing — with `ttlMs` from configuration and `cacheScope: "public"`, and no `nextCursor` (makes T052 pass)
- [X] T064 [US4] Add to `McpController`: 405 on `GET`/`DELETE`, ignore `Mcp-Session-Id` and `Last-Event-ID` without echoing either, `-32601` + HTTP 404 for unknown methods, and the `initialize` diagnostic (makes T053, T054, T059 pass)
- [X] T065 [US4] Implement `mcp-server/src/main/java/dev/l4jlab/mcp/protocol/ClientCapabilities.java` and the `-32021` path used whenever the server needs a capability the request did not declare (makes T055 pass)
- [X] T066 [US4] Wire `AuditWriter` into `McpController`'s rejection paths so a protocol failure is audited (makes T056 pass)

**Checkpoint**: US4 fully functional through the proxy.

---

## Phase 4: User Story 1 - Agent finds and reads billing runs within its entitlements (Priority: P1) 🎯 MVP

**Goal**: three read-only tools, paginated with server-minted cursors any replica accepts,
every entitlement decided by the legacy API.

**Depends on**: Phase 3 — every request here carries the headers and `_meta` US4 validates.

### Tests for User Story 1 ⚠️ write first, confirm they fail

- [X] T067 [P] [US1] `legacy-billing-api/src/test/java/dev/l4jlab/legacy/api/BillingRunSearchTest.java`: `GET /api/v1/billing-runs` filters by firm, status, advisor and date range; scope filtering happens before paging so `totalCount` is the caller's total
- [X] T068 [P] [US1] `mcp-server/src/test/java/dev/l4jlab/mcp/protocol/CursorCodecTest.java`: a cursor round-trips offset and predicate digest; tampered, cross-principal, and expired cursors are each refused — as tool errors, never protocol errors
- [X] T069 [P] [US1] `mcp-server/src/test/java/dev/l4jlab/mcp/tools/SearchBillingRunsTest.java`: `page_size` above 20 is clamped, not rejected; `truncated` and `refine_hint` appear together and only when more matches exist; `structuredContent` validates against the committed `outputSchema` and `content` carries a text rendering (FR-012, FR-017)
- [X] T070 [P] [US1] `mcp-server/src/test/java/dev/l4jlab/mcp/tools/GetBillingRunStatusTest.java`: the result is the purpose-built shape, not the stored entity; `phase` is null once terminal; `next_step_hint` naming `get_run_failures` appears when and only when `status == FAILED` (US1-7, FR-018)
- [X] T071 [P] [US1] `mcp-server/src/test/java/dev/l4jlab/mcp/tools/GetRunFailuresTest.java`: the list is capped at `limit` (clamped to 50), `total_count` reports the full count, `truncated` is consistent with the two (FR-019)
- [X] T072 [P] [US1] `mcp-server/src/test/java/dev/l4jlab/mcp/tools/LegacyErrorTranslationTest.java`: legacy 403 becomes a short "no access" error carrying no run data; 404 becomes a tool error; 500 and an unreachable legacy API become "do not retry" — and an audit row is written for each (US1-4, US4-8, FR-014)
- [X] T073 [P] [US1] `mcp-server/src/topologyTest/java/dev/l4jlab/mcp/topology/EntitlementTopologyTest.java`: `advisor-alpha-101` sees only `adv-101`'s runs; `admin-alpha` **and `ops-alpha`** each see every `firm-alpha` run; none sees a `firm-beta` run; `admin-beta` sees only `firm-beta`'s (US1-1, US1-2, US1-3)
- [X] T074 [P] [US1] `mcp-server/src/topologyTest/java/dev/l4jlab/mcp/topology/CursorTopologyTest.java`: page 1 from `mcp-a`, page 2 from `mcp-b` — the union is the full result set with no overlap and no gap (US1-5, US1-6, FR-007)
- [X] T075 [P] [US1] `mcp-server/src/topologyTest/java/dev/l4jlab/mcp/topology/ReadToolsTopologyTest.java`: search, open the FAILED run, follow `next_step_hint` to `get_run_failures` (US1-7, US1-8)
- [X] T076 [P] [US1] `mcp-server/src/topologyTest/java/dev/l4jlab/mcp/topology/TraceparentTopologyTest.java`: a `traceparent` sent in `_meta` on a read tool appears unchanged in `GET /test/received-requests` (FR-027, end to end)

### Implementation for User Story 1

- [X] T077 [P] [US1] Implement `legacy-billing-api/src/main/java/dev/l4jlab/legacy/api/BillingRunController.java` (FR-023): `GET /api/v1/billing-runs` with `offset`/`limit`, `GET /api/v1/billing-runs/{runId}`, `GET /api/v1/billing-runs/{runId}/failures` (409 when not FAILED) — each scope-guarded before existence (makes T067 pass)
- [X] T078 [P] [US1] Implement `mcp-server/src/main/java/dev/l4jlab/mcp/protocol/CursorCodec.java`: URL-safe Base64 over an HMAC-signed payload of predicate digest, offset, principal user id and expiry, keyed from `MCP_CURSOR_HMAC_KEY` so all replicas share it (makes T068 pass)
- [X] T079 [US1] Implement `mcp-server/src/main/java/dev/l4jlab/mcp/protocol/ToolsCallHandler.java`: resolve from `ToolCatalog`, validate arguments, invoke, validate `structuredContent`, render `content` into `McpSchema.CallToolResult`, route failures through `ErrorBoundary` (depends on T044, T041)
- [X] T080 [P] [US1] Implement `mcp-server/src/main/java/dev/l4jlab/mcp/tools/SearchBillingRunsTool.java` with the page-size clamp, `total_match_count`, `truncated`, `next_cursor`, `refine_hint` (makes T069 pass)
- [X] T081 [P] [US1] Implement `mcp-server/src/main/java/dev/l4jlab/mcp/tools/GetBillingRunStatusTool.java`, mapping the legacy run onto the purpose-built shape and adding `next_step_hint` on FAILED (makes T070 pass)
- [X] T082 [P] [US1] Implement `mcp-server/src/main/java/dev/l4jlab/mcp/tools/GetRunFailuresTool.java` with the cap, `total_count`, `truncated` (makes T071 pass)
- [X] T083 [US1] Implement `mcp-server/src/main/java/dev/l4jlab/mcp/legacy/LegacyErrorTranslator.java`: 403 → "no access", 404 → not found, 5xx and connection failure → "do not retry", each from the fixed vocabulary (makes T072 pass)

**Checkpoint**: US4 + US1 are the MVP.

---

## Phase 5: User Story 2 - Fee adjustment, confirmed once and executed once (Priority: P2)

### Tests for User Story 2 ⚠️ write first, confirm they fail

- [X] T084 [P] [US2] `legacy-billing-api/src/test/java/dev/l4jlab/legacy/api/FeeAdjustmentTest.java`: `POST /api/v1/fee-adjustments` inserts the adjustment and moves `account.current_fee_bps` in one transaction; a cross-firm account is 403; READ_ONLY is 403
- [X] T085 [P] [US2] `mcp-server/src/test/java/dev/l4jlab/mcp/protocol/RequestStateCodecTest.java`: request state round-trips; tampered, cross-principal, expired, and digest-mismatched state are each refused (R-002)
- [X] T086 [P] [US2] `mcp-server/src/test/java/dev/l4jlab/mcp/tools/PostFeeAdjustmentConfirmationTest.java`: the first call returns `resultType: "input_required"` with one `elicitation/create` built from the SDK's `McpSchema.ElicitFormRequest` and a `requestState`, and the legacy client is never called; a retry carrying `confirmed: false` returns a plain result saying nothing was applied, not an error (US2-1)
- [X] T087 [P] [US2] `mcp-server/src/test/java/dev/l4jlab/mcp/tools/IdempotencyTest.java`: a second call with the same `(principal, operation_id)` and matching digest returns the stored result with `replayed: true` and does not call the legacy client; the same key with a differing digest is a tool error rather than a wrong replay
- [X] T088 [P] [US2] `mcp-server/src/test/java/dev/l4jlab/mcp/tools/FeeAdjustmentValidationTest.java`: `delta_bps` of zero and a malformed `effective_date` are each a tool error naming the field, produced by `inputSchema` validation rather than a hand-written check
- [X] T089 [P] [US2] `mcp-server/src/test/java/dev/l4jlab/mcp/protocol/MrtrCachingTest.java`: a result produced by a retry carrying `inputResponses` or `requestState` carries **no** `ttlMs`/`cacheScope` and is marked non-cacheable, per the caching rules (**previously implemented without a test**)
- [X] T090 [P] [US2] `mcp-server/src/topologyTest/java/dev/l4jlab/mcp/topology/FeeAdjustmentTopologyTest.java`: the three-call sequence, with `GET /test/received-requests` asserting exactly one write reached the legacy API (US2-1, US2-2, US2-3)
- [X] T091 [P] [US2] `mcp-server/src/topologyTest/java/dev/l4jlab/mcp/topology/CrossReplicaConfirmationTest.java`: first call `mcp-a`, confirmation `mcp-b`, third call `mcp-c` — still exactly one write (US2-4, FR-007)
- [X] T092 [P] [US2] `mcp-server/src/topologyTest/java/dev/l4jlab/mcp/topology/FeeAdjustmentAuditTest.java`: the audit row carries `confirmed_by_user_id` (the authenticated principal — the protocol supplies no separate confirmer identity) and `legacy_reference_id`, and `argument_summary` holds identifiers only (US2-5, FR-020, FR-026)

### Implementation for User Story 2

- [X] T093 [P] [US2] Implement `legacy-billing-api/src/main/java/dev/l4jlab/legacy/api/FeeAdjustmentController.java`, minting `legacyReferenceId` and applying the delta in one transaction (makes T084 pass)
- [X] T094 [P] [US2] Implement `mcp-server/src/main/java/dev/l4jlab/mcp/protocol/RequestStateCodec.java`: AEAD-sealed principal, operation id, request digest and short expiry, keyed from `MCP_REQUEST_STATE_KEY` (makes T085 pass)
- [X] T095 [P] [US2] Implement `mcp-server/src/main/java/dev/l4jlab/mcp/ops/OperationRecordRepository.java` over `mcp_ops.operation_record`, relying on the primary key for at-most-once rather than a read-then-write (makes T087 pass)
- [X] T096 [US2] Implement `mcp-server/src/main/java/dev/l4jlab/mcp/tools/PostFeeAdjustmentTool.java`: idempotency record first; on a miss with no `inputResponses`, return `input_required` with the elicitation and sealed state; on a retry, verify the state, require `elicitation` in client capabilities, execute, and write the record in the same transaction as the outcome (makes T086, T088 pass; depends on T094, T095)
- [X] T097 [US2] Extend `ToolsCallHandler` to read `params.inputResponses` and `params.requestState` and to mark such results non-cacheable (makes T089 pass; depends on T079, T096)
- [X] T098 [US2] Record `confirmed_by_user_id` and `legacy_reference_id` from `PostFeeAdjustmentTool` (makes T092 pass)

**Checkpoint**: US1, US2, US4 all work independently.

---

## Phase 6: User Story 3 - Start a long billing run and poll it to completion (Priority: P3)

### Tests for User Story 3 ⚠️ write first, confirm they fail

- [X] T099 [P] [US3] `legacy-billing-api/src/test/java/dev/l4jlab/legacy/runsim/RunSimulationTest.java` (FR-024): a started run advances `DATA_COLLECTION → FEE_CALC → INVOICING → POSTING`, `accounts_processed` climbs to `accounts_total`, and the run takes 30–90 seconds — asserted against an injected clock, so the test does not
- [X] T100 [P] [US3] `legacy-billing-api/src/test/java/dev/l4jlab/legacy/runsim/RunCancellationTest.java`: `POST /api/v1/billing-runs/{runId}/cancel` drives a live run to `CANCELED`; a run already terminal keeps its status and the request is still acknowledged; a cross-firm run is 403 (**FR-031**)
- [X] T101 [P] [US3] `mcp-server/src/test/java/dev/l4jlab/mcp/tasks/TaskStoreTest.java`: a task row is committed before the result is returned; `completed`, `failed`, `cancelled` are terminal; a poll by a different principal is refused; an unknown or expired `taskId` yields a tool-shaped error
- [X] T102 [P] [US3] `mcp-server/src/test/java/dev/l4jlab/mcp/tasks/CreateTaskResultTest.java`: with the extension declared, `start_billing_run` returns `resultType: "task"` carrying `taskId`, `status: "working"`, `createdAt`, `lastUpdatedAt`, `ttlMs`, `pollIntervalMs` (R-003)
- [X] T103 [P] [US3] `mcp-server/src/test/java/dev/l4jlab/mcp/tasks/TasksGetTest.java`: `tasks/get` refreshes status from the legacy run; on `completed` it carries `result` matching the `start_billing_run` `outputSchema`; on `failed` it carries `error`
- [X] T104 [P] [US3] `mcp-server/src/test/java/dev/l4jlab/mcp/tasks/TasksCancelUpdateTest.java`: `tasks/cancel` asks the legacy API to cancel the run, drives the task to `cancelled`, and acknowledges without change when the run is already terminal; a cancel by a different principal is refused; **a cancel from a client that never declared the tasks extension is accepted, because it holds the handle and has no sixth tool to fall back to (FR-031)**; `tasks/update` accepts `inputResponses`, acknowledges with an empty result, and ignores unknown keys (**FR-031; these handlers previously had no test at all**)
- [X] T105 [P] [US3] `mcp-server/src/test/java/dev/l4jlab/mcp/tasks/TasksFallbackTest.java`: a client that does not declare `io.modelcontextprotocol/tasks` still gets an immediate `complete` result carrying `task_id`, `run_id`, `poll_with: "get_billing_run_status"`, and never `resultType: "task"` (FR-021)
- [X] T106 [P] [US3] `mcp-server/src/topologyTest/java/dev/l4jlab/mcp/topology/StartRunTopologyTest.java`: the handle arrives in under one second measured client-side; polling reaches `completed`; the final status equals the legacy API's (US3-1, US3-2, SC-003)
- [X] T107 [P] [US3] `mcp-server/src/topologyTest/java/dev/l4jlab/mcp/topology/TaskPollingReplicaTest.java`: consecutive polls routed to `mcp-a`, `mcp-b`, `mcp-c` each return the same task state (US3-3, FR-007)
- [X] T108 [P] [US3] `mcp-server/src/topologyTest/java/dev/l4jlab/mcp/topology/TaskCancellationTopologyTest.java`: start a run, cancel it through `tasks/cancel` on a different replica, and observe the run reach `CANCELED` through `get_billing_run_status` (US3-5, FR-031). Then cancel the **same, now-terminal** run again and assert it is acknowledged with the status unchanged and no error (US3-6) — reusing the run rather than starting a second, so SC-002's "against three replicas" holds for both scenarios
- [X] T109 [P] [US3] `mcp-server/src/topologyTest/java/dev/l4jlab/mcp/topology/TasksFallbackTopologyTest.java`: the fallback path polled to completion through `get_billing_run_status` (US3-4)

### Implementation for User Story 3

- [X] T110 [P] [US3] Implement `legacy-billing-api/src/main/java/dev/l4jlab/legacy/runsim/RunSimulator.java` and `POST /api/v1/billing-runs` (FR-024), advancing phases on a scheduled executor against an injectable clock. Total duration is a random value in `[30000, 90000]` ms unless `LEGACY_RUN_DURATION_MS` overrides it (R-012) — the default is FR-024 exactly; the override exists so the topology suite does not wait minutes (makes T099 pass)
- [X] T111 [US3] Implement `POST /api/v1/billing-runs/{runId}/cancel` in `BillingRunController`, scope-guarded like every other write, cooperative on terminal runs (makes T100 pass; depends on T110)
- [X] T112 [P] [US3] Implement `mcp-server/src/main/java/dev/l4jlab/mcp/tasks/TaskStore.java` over `mcp_ops.task_record`, committing the row before the response is composed and refusing a cross-principal poll (makes T101 pass)
- [X] T113 [US3] Implement `mcp-server/src/main/java/dev/l4jlab/mcp/tools/StartBillingRunTool.java`: start the legacy run, create the task, return `resultType: "task"` when the client declared the extension or the documented fallback result when it did not (makes T102, T105 pass; depends on T112)
- [X] T114 [P] [US3] Implement `mcp-server/src/main/java/dev/l4jlab/mcp/tasks/TasksGetHandler.java`, refreshing from the legacy run on each poll so no background worker exists and no replica owns a task (makes T103 pass)
- [X] T115 [US3] Implement `mcp-server/src/main/java/dev/l4jlab/mcp/tasks/TasksCancelHandler.java` and `TasksUpdateHandler.java` per FR-031 (makes T104 pass; depends on T111, T112)
- [X] T116 [US3] Register `tasks/get`, `tasks/cancel`, `tasks/update` in `McpController` and advertise `capabilities.extensions["io.modelcontextprotocol/tasks"]` in `DiscoverHandler` (depends on T114, T115)

**Checkpoint**: all four stories independently functional.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T117 [P] `mcp-server/src/topologyTest/java/dev/l4jlab/mcp/topology/TokenNeverForwardedTest.java`: across the whole acceptance run, the MCP server's inbound token string appears nowhere in `GET /test/received-requests` (US4-7, SC-005)
- [X] T118 [P] `mcp-server/src/topologyTest/java/dev/l4jlab/mcp/topology/ErrorMessageLeakTest.java`: every error message the acceptance suite produces is scanned against a deny-list of stack-trace markers, SQL keywords, and internal hostnames (SC-006, FR-010)
- [X] T119 [P] Write `mcp-server/README.md`: the feature-to-file map from `quickstart.md`, so SC-004 is answerable from the repository rather than the spec directory
- [X] T120 [P] Add to `mcp-server/README.md` the FR-028 delta list — each hand-written 2026-07-28 piece, the SDK version it works around, and the upstream issue (#1011, #1010, #1013, #1009) that would retire it
- [X] T121 [P] Add the offline-development note to `token-issuer/README.md`: the committed RSA key is development-only and worthless, and the controllers are environment-gated so they do not exist elsewhere (FR-025, R-013)
- [X] T122 Update the root `README.md` with the new services, `MCP_HTTP_PORT`, the `make mcp-*` targets, and **every environment variable in `research.md` R-012 with its default**, matching how features 004 and 006 documented theirs
- [X] T123 Run `make mcp-verify` and walk `quickstart.md` end to end, confirming every row of its acceptance table maps to a passing test (SC-001, SC-002)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies. T003 is a research spike that can start immediately and must land before T044
- **Foundational (Phase 2)**: depends on Setup — **blocks every user story**
- **US4 (Phase 3)**: depends on Foundational
- **US1 (Phase 4)**: depends on Foundational and, in practice, US4
- **US2 (Phase 5)**: depends on Foundational and US4; independent of US1 and US3
- **US3 (Phase 6)**: depends on Foundational and US4; independent of US1 and US2
- **Polish (Phase 7)**: depends on the stories you intend to ship. Nothing in Phase 7 is a
  prerequisite of an earlier phase — `ReceivedRequestLog` moved to T028 in Foundational, where
  the tasks that need it can reach it

### Story Dependencies

- **US4 (P1)**: the protocol substrate; nothing else is meaningfully testable before it
- **US1, US2, US3**: each depends on US4 and on nothing else. They are mutually independent

US1/US2/US3 independence is real. Their shared dependency on US4 is also real, and stating it
is better than tests that pass against a server nobody could call.

### Within Each Story

- Tests first, confirmed failing, then implementation — Principle IV, with no exceptions
- Contracts already exist and are committed; no task creates one
- Legacy API endpoint before the MCP tool that calls it
- Codec before the tool that uses it

### Parallel Opportunities

- T002, T004, T005 — three build files
- T013, T014, T015 — three migrations
- All test files in each phase: T016/T019/T021, T023/T025, T029/T031/T033/T035/T037/T040/T043/T045, T049–T059, T067–T076, T084–T092, T099–T109
- T080, T081, T082 — the three read tools, one file each
- T117–T121 — the polish items

---

## Parallel Example: User Story 4

```bash
# All eleven US4 tests, written together and all failing:
Task: "T049 HeaderValidationTest"            Task: "T055 CapabilityGateTest"
Task: "T050 ProtocolVersionTest"             Task: "T056 ProtocolErrorAuditTest"
Task: "T051 DiscoverHandlerTest"             Task: "T057 ProtocolConformanceTopologyTest"
Task: "T052 ToolsListTest"                   Task: "T058 TokenRejectionTopologyTest"
Task: "T053 NoSessionTest"                   Task: "T059 LangChain4jCompatibilityTest"
Task: "T054 UnknownMethodTest"
```

---

## Implementation Strategy

### MVP (US4 + US1)

1. Phase 1: Setup
2. Phase 2: Foundational — the long one; nothing demos until it is done
3. Phase 3: US4 — the protocol substrate
4. Phase 4: US1 — the read tools
5. **STOP and VALIDATE**: `make mcp-verify`. Four of the six SC-004 features are demonstrable:
   stateless requests, header routing, server-minted handles, cacheable list results.

US4 alone is a smaller honest increment — `server/discover`, `tools/list`, and every rejection
path, with no domain data. Worth demoing if Foundational runs long.

### Incremental Delivery

1. Setup + Foundational → nothing to show, everything unblocked
2. + US4 → the protocol is real and conformance is provable
3. + US1 → MVP
4. + US2 → MRTR and cross-replica idempotency
5. + US3 → Tasks, cancellation, and the last of the six SC-004 features
6. + Polish → SC-005, SC-006, and the documentation SC-004 asks for

### Parallel Team Strategy

Setup and Foundational together; T013–T015, T016–T022, T023–T028, and T029–T048 are four
tracks that barely touch. Then US4 by whoever knows the revision best. Once US4 lands, US1,
US2 and US3 are three independent tracks.

---

## Notes

- `[P]` means a different file with no dependency on incomplete work
- The five tool contracts are committed and load directly into `McpSchema.Tool` (T042, T044);
  no task writes a schema
- Anything the MCP Java SDK provides is imported, not written. The seven exceptions are listed
  in `research.md` R-003 and must appear in the server README (T120)
- Confirm each test fails before implementing against it
