# Research: MCP Billing Server (spec revision 2026-07-28)

**Feature**: `007-mcp-billing-server` | **Date**: 2026-09-13

All protocol facts below were read from the published 2026-07-28 specification on
2026-09-13, not from memory. Every quoted field name and error code is copied from the
spec pages cited.

---

## R-001: Does specification revision 2026-07-28 exist, and what does it actually require?

**Decision**: Target `2026-07-28`. It is a published, final revision.

**Findings** (from `modelcontextprotocol.io/specification/2026-07-28/*`):

- **Statelessness is normative.** "The Model Context Protocol (MCP) is a **stateless
  protocol**: all the information needed to process a request is contained in the request
  itself." Servers "**MUST NOT** rely on prior requests over the same connection to
  establish context". State spanning requests "**MUST** be referenced by an explicit
  identifier the client passes on each request."
- **Per-request `_meta` fields**, all under the reserved `io.modelcontextprotocol/` prefix:

  | Key | Type | Required |
  |---|---|---|
  | `io.modelcontextprotocol/protocolVersion` | `string` | Yes |
  | `io.modelcontextprotocol/clientInfo` | `Implementation` | No (SHOULD) |
  | `io.modelcontextprotocol/clientCapabilities` | `ClientCapabilities` | Yes |
  | `io.modelcontextprotocol/logLevel` | `LoggingLevel` | No |

  A request missing a required field "is malformed; the server **MUST** reject it with
  JSON-RPC error code `-32602` (Invalid params)" and HTTP `400`.
- **Per-response**: servers **SHOULD** put `io.modelcontextprotocol/serverInfo` in every
  result's `_meta`.
- **Error codes** (new reserved sub-range `-32020`..`-32099`):

  | Code | Name |
  |---|---|
  | `-32020` | `HeaderMismatch` |
  | `-32021` | `MissingRequiredClientCapability` |
  | `-32022` | `UnsupportedProtocolVersion` |

  `UnsupportedProtocolVersionError` carries `data.supported` (array) and `data.requested`.
  `MissingRequiredClientCapabilityError` carries `data.requiredCapabilities`.
- **`resultType` is now mandatory on every result**: `"complete"`, `"input_required"`, or
  an extension value such as the Tasks extension's `"task"`. An absent `resultType` is read
  as `"complete"` only for backward compatibility with older servers.
- **Headers** (Streamable HTTP binding): `MCP-Protocol-Version` on every POST;
  `Mcp-Method` mirroring `method` on all requests; `Mcp-Name` mirroring `params.name` or
  `params.uri` for `tools/call`, `resources/read`, `prompts/get`. "These headers are
  **REQUIRED** for compliance." A missing required header, a header that disagrees with the
  body, or a header with invalid characters all get HTTP `400` plus JSON-RPC `-32020`.
  `Mcp-Name` values outside plain ASCII use the sentinel `=?base64?{value}?=`, which the
  server **MUST** decode before comparing.
- **Sessions are gone**: an `Mcp-Session-Id` header must be ignored and never minted or
  echoed; HTTP GET or DELETE on the endpoint gets `405`; `Last-Event-ID` is ignored.
- **Unknown method** gets HTTP `404` with JSON-RPC `-32601`, which is how a modern server is
  distinguished from a legacy one.
- **`server/discover`** — servers **MUST** implement it. Result fields: `supportedVersions`,
  `capabilities`, `instructions` (optional), `ttlMs`, `cacheScope`, and
  `_meta['io.modelcontextprotocol/serverInfo']`.
- **Caching**: servers **MUST** include `ttlMs` (integer ms, `>= 0`) and `cacheScope`
  (`"public"` or `"private"`) on `complete` results of `server/discover`, `tools/list`,
  `prompts/list`, `resources/list`, `resources/templates/list`, `resources/read`. Results
  produced by an MRTR retry — that is, carrying `inputResponses` or `requestState` —
  **MUST NOT** be cached.
- **Deterministic tool order**: servers **SHOULD** return tools in the same order across
  requests when the set has not changed.

**Alternatives considered**: targeting `2025-11-25`, which the Java SDK does implement.
Rejected: the whole point of the feature is the 2026-07-28 revision.

---

## R-002: Multi Round-Trip Requests — the exact shape of the confirmation round trip

**Decision**: `post_fee_adjustment` returns `resultType: "input_required"` with one
`elicitation/create` entry in `inputRequests` and an integrity-protected `requestState`.

**Findings** (from `.../basic/patterns/mrtr` and `.../server/tools`):

- `InputRequiredResult` has `inputRequests` (optional) — a map from server-assigned keys to
  `ElicitRequest` / `CreateMessageRequest` / `ListRootsRequest` — and `requestState`
  (optional), "an opaque string meaningful only to the server". At least one of the two
  **MUST** be present.
- The client retries by sending a **new request with a different JSON-RPC `id`**, carrying
  `params.inputResponses` (keyed to the `inputRequests` keys) and echoing `params.requestState`
  verbatim.
- The server **MUST** treat `requestState` as attacker-controlled. Where it influences
  authorization or business logic it **MUST** be integrity-protected (HMAC or AEAD) and
  invalid state **MUST** be rejected. To bound replay, the server **SHOULD** put inside the
  protected payload: the authenticated principal, a short TTL, and an identifier for the
  originating request (method name plus a digest of its salient parameters).
- The spec is explicit that these measures "do not by themselves guarantee single-use.
  Servers for which a given `requestState` must be consumed at most once **MUST** enforce
  that invariant server-side."
- The server **MUST NOT** send an `inputRequests` entry for a capability the client did not
  declare — so an `elicitation/create` requires `elicitation` in
  `io.modelcontextprotocol/clientCapabilities`, else `-32021`.

**Consequence for this feature**: the "exactly once" acceptance test is *not* satisfied by
`requestState` integrity alone. It needs the shared idempotency record keyed by the
client-supplied operation id (R-006).

---

## R-003: The Tasks extension, and exactly what the Java SDK does and does not give

**Decision**: build on MCP Java SDK **v2.0.1** for everything that is not revision-specific,
and hand-write only the 2026-07-28 deltas. Each hand-written piece names the upstream issue
that would retire it. (Clarification 1 and 2, `spec.md`; FR-028, FR-029.)

### Tasks extension wire shape

- Identifier `io.modelcontextprotocol/tasks`. The client opts in per request via
  `_meta.io.modelcontextprotocol/clientCapabilities.extensions`; the server advertises it in
  `server/discover` capabilities.
- Task creation returns `resultType: "task"` (`CreateTaskResult`). From the ext-tasks schema,
  `Task` is:

  ```
  taskId: string
  status: "working" | "input_required" | "completed" | "failed" | "cancelled"
  statusMessage?: string
  createdAt: string
  lastUpdatedAt: string
  ttlMs: number | null
  pollIntervalMs?: number
  ```

- Methods: `tasks/get` (`{taskId}`), `tasks/update` (`{taskId, inputResponses}`),
  `tasks/cancel` (`{taskId}`). No `tasks/list` exists.
- The task "must be durably created before sending the response", and the server **MUST**
  verify the client declared the extension before returning a `CreateTaskResult`.
- Cancellation is cooperative: the server acknowledges the intent but is not obliged to stop
  the work. FR-031 honours it where the run is still live and acknowledges without change
  where it is already terminal.

### SDK state, verified 2026-09-13

- Latest is **v2.0.1** (2026-08-19) on the `2.0.x` line. There is no `3.x` branch and no
  merged work for 2026-07-28; PR #1089 *MCP Spec 28-7-2026 Design* was **closed**, not merged.
- `ProtocolVersions` tops out at `2025-11-25`. Issue #1072 confirms `server/discover` returns
  HTTP 500.
- Open tracking issues: **#1011** (SEP-2575, stateless core), **#1010** (SEP-2567, sessionless
  handles), **#1013** (SEP-2663, Tasks), **#1009** (SEP-2549, TTL for list results).
- Naming trap: the SDK's `*Stateless*` classes mean "no per-connection session object" in a
  2025 sense. They still run `initialize` and speak `2025-11-25`.

### What we take from the SDK

| Taken | Why it is a real saving |
|---|---|
| `McpSchema.Tool` | Carries `name`, `title`, `description`, `inputSchema`, `outputSchema`, `annotations`, `_meta`, `icons` — every field FR-011 and FR-012 need |
| `McpSchema.ToolAnnotations` | All four hints: `readOnlyHint`, `destructiveHint`, `idempotentHint`, `openWorldHint` |
| `McpSchema.CallToolResult` | `content`, `isError`, `structuredContent`, `_meta` — the whole FR-012 shape |
| `McpSchema.JSONRPCRequest` / `JSONRPCResponse` / `JSONRPCError` / `McpError` | Framing and error envelopes |
| `McpSchema.ElicitFormRequest` / `ElicitResult` | The MRTR confirmation payloads |
| `McpSchema.Implementation`, `ClientCapabilities`, `ListToolsResult`, `PaginatedResult`, `JsonSchema` | Identity, capability, and list types |
| `io.modelcontextprotocol.json.schema.JsonSchemaValidator` | The schema-validation abstraction (R-008) |
| `McpStatelessServerHandler` | The two-method interface our controller satisfies (FR-029) |

### What must be hand-written, and what retires it

| Hand-written | Why no library has it | Retired by |
|---|---|---|
| Per-request `_meta` envelope + `serverInfo` on results | SDK carries version and capabilities in the `initialize` handshake | #1011 |
| Request dispatch on a Micronaut controller | `DefaultMcpStatelessServerHandler` mandates `initialize`, which FR-001 forbids | #1011 |
| `server/discover` | Not implemented; #1072 shows it 500s | #1011 |
| Header validation (`Mcp-Method`, `Mcp-Name`, `MCP-Protocol-Version`) and codes `-32020`/`-32021`/`-32022` | These codes and headers are new in this revision | #1011 |
| `resultType` on results, and `input_required` (MRTR) | `CallToolResult` has no `resultType` field at all | #1011 |
| `ttlMs` / `cacheScope` on list results | Absent from `ListToolsResult` | #1009 |
| Tasks extension (`CreateTaskResult`, `tasks/get`, `tasks/cancel`, `tasks/update`) | No type exists | #1013 |

Seven deltas, carried by roughly a dozen classes across `protocol/` and `tasks/`. The number
that matters is the seven: each one has an upstream issue that retires it. An SDK-free plan
would have owned the entire surface instead.

**Transitive additions to be aware of**: `mcp-core` pulls `reactor-core` (fine — Micronaut
Reactor is already on the platform), `slf4j-api`, `jackson-annotations`, and
`jakarta.servlet-api`. The servlet API arrives on the compile path but no servlet container
is used; FR-029 keeps Micronaut's Netty server the only HTTP runtime.

**Alternatives considered**:
- *Target 2025-11-25 and take the SDK whole.* Rejected by clarification 1 — it deletes the
  feature's purpose.
- *Adopt a Micronaut servlet runtime to use `HttpServletStatelessServerTransport`.* Rejected
  by clarification 2 — a second HTTP runtime in the repository to avoid writing one
  controller is a bad trade, and the constitution names Micronaut the HTTP server.
- *Fork the SDK.* Rejected — far more code to own than seven deltas.

---

## R-004: LangChain4j capability inventory (Constitution Principle I gate)

Pinned version: **LangChain4j 1.18.0** (`langchain4j-agentic` resolves to `1.18.0-beta28`
from `langchain4j-bom`, imported by the Micronaut Platform BOM 5.1.5). Modules read from the
1.18.0 sources on 2026-09-13.

| Capability | Module | Decision for this feature |
|---|---|---|
| AI Services (`@SystemMessage`, `@UserMessage`, typed returns) | `langchain4j-core` | **Not needed.** This feature makes no model call. It exposes tools *to* an agent; the agent is not in scope. |
| Agentic orchestration (sequential, parallel, conditional, loop, supervisor) | `langchain4j-agentic` | **Not needed.** No multi-step agent flow. The five tools are independent request handlers, not agent steps. |
| `AgentMonitor` / `MonitoredAgent` (timing, input/output, tokens, first error) | `langchain4j-agentic` | **Not needed** for the audit log: it monitors *agent invocations*, and there are none here. The FR-026 audit record is of an inbound MCP tool call, which no LangChain4j type models. |
| `@MemoryId`, `AgenticScopeAccess`, `ChatMessagesAccess` | `langchain4j-agentic` | **Not needed.** No agentic scope exists; MCP state lives in `requestState` and shared storage. |
| Chat memory (`ChatMemory`, `ChatMemoryStore`) | `langchain4j-core` | **Not needed.** No conversation. |
| Structured outputs / JSON schema from types | `langchain4j-core` | **Not needed.** MCP `inputSchema`/`outputSchema` are JSON Schema 2020-12 on the wire, generated for MCP tool definitions, not for model-constrained generation. Sourced from Micronaut/Jackson types instead (R-008). |
| Tools (`@Tool`, `ToolSpecification`, `ToolExecutor`) | `langchain4j-core` | **Not needed, and no exception required.** `ToolSpecification` is LangChain4j's shape for a tool *it* calls on a model's behalf, and it lacks `outputSchema`, the four hints, `_meta`, `structuredContent`, and `resultType`. But nothing is hand-written in its place: the MCP SDK's `McpSchema.Tool` and `ToolAnnotations` carry every one of those fields (R-003), so the tool definition type comes from a library either way. |
| MCP **client** (`McpClient`, `McpToolProvider`, `McpTransport`, registry client) | `langchain4j-mcp` | **Used, in exactly one place.** The tree is client-side only — `client/`, `protocol/`, `transport/`, `registryclient/`, `resourcesastools/`, plus `McpToolProvider` and `McpToolExecutor`; no server-side type exists. `DefaultMcpClient` defaults to `protocolVersion = "2025-11-25"` and always sends `initialize`; the version is builder-configurable, the handshake is not. So it cannot drive the modern path. Its one real contribution is the FR-030 compatibility test: a genuine legacy client must receive the diagnostic FR-001 mandates. |
| MCP **server** | — | **Does not exist in LangChain4j at any version.** Nothing to take from the library; nothing is being reimplemented. |
| A2A integration | `langchain4j-agentic-a2a` | **Not needed.** No inter-agent messaging in this feature. |
| Guardrails (input/output) | `langchain4j-core` | **Not needed.** No model output to guard. Argument validation is JSON Schema plus Micronaut validation. |
| Retrieval augmentation, embedding stores, document loading/splitting | `langchain4j-easy-rag`, `langchain4j-pgvector` | **Not needed.** RAG is explicitly out of scope. |
| Model and service listeners (`ChatModelListener`) | `langchain4j-core` | **Not needed.** No model call to listen to. |

**Conclusion**: this feature uses LangChain4j in exactly one place — the FR-030
compatibility test — because it contains no agent and no model call. It is the server side of
a protocol that agents speak, and LangChain4j has no server side. Every entry above is "not
needed" with its reason; **no justified exception under Principle I is required**, because
nothing LangChain4j provides is being reimplemented. What would otherwise have been
hand-written comes from the MCP Java SDK instead (R-003).

**Deferred, and now recorded in the spec's Out of Scope**: wiring the existing
`financial-agent-chain` backend to consume these five tools through `McpToolProvider`. It
cannot work against a modern-only server, so it travels with dual-era support in a later
feature (clarification 3).

---

## R-005: Deployment topology and where shared state lives

**Decision**: six containers in one Compose file — PostgreSQL, token issuer, legacy API,
three MCP replicas — behind an nginx round-robin proxy.

**Ports (finding F1 / A1).** The proxy publishes `${MCP_HTTP_PORT:-8877}`, and **nothing else
publishes anything**. An earlier draft put the proxy on 8080 and PostgreSQL on 5432; both
collide with what the repository already runs — `Makefile:36,43` guards 8080 for
`make dev`/`dev-backend`, and `compose.yaml:9` already publishes 5432. Feature 004 was
explicit that its stack and the development setup must run at the same time, and a third
setup has to honour the same property. Following feature 006's FR-011a, the port is a
variable with a documented default rather than a literal, so the collision is also fixable
from the shell. The MCP stack's PostgreSQL is reachable only on the internal network; nothing
outside Compose needs it.

**Findings / rationale**:

- FR-007 requires cursor continuation, confirmation retries, and task polling to work
  across replicas. Three kinds of state must therefore survive a request: pagination
  cursors, idempotency records, and task state.
- Cursors and `requestState` are carried *in the request* (signed, self-contained), per the
  spec's preferred model. Idempotency records, task state, and the audit log cannot be —
  they must be observed identically by all three replicas — so they live in shared storage.
- The constitution names PostgreSQL with pgvector as the datastore and forbids a second one
  without amendment. Redis is therefore excluded; PostgreSQL holds the shared tables. No
  amendment is needed and none is proposed.
- Two logical databases in the one PostgreSQL instance: `legacy_billing` (domain data, owned
  by the legacy API) and `mcp_ops` (idempotency, tasks, audit). This keeps FR's "the MCP
  server has no database of its own for domain data" true and visible: the MCP server's
  schema contains no billing entity.

**Alternatives considered**: in-memory state with sticky sessions at the proxy. Rejected —
it would make the cross-replica acceptance tests pass for the wrong reason and teach the
opposite of what the revision is about.

---

## R-006: Idempotency for `post_fee_adjustment`

**Decision**: a shared `operation_record` table keyed by `(principal_user_id, operation_id)`
holding a request digest, the terminal outcome, and the serialized original result. Insert
the row with the outcome in one transaction after the legacy write returns; a unique
constraint makes the third call a read.

**Rationale**: FR-020's three-call scenario spans replicas, so the record must be shared.
Keying by principal as well as operation id stops one principal replaying another's
operation id. Storing a digest of the salient arguments lets the server detect an operation
id reused with *different* arguments and refuse rather than silently return the wrong
result.

**Interaction with `requestState`**: `requestState` proves *this* confirmation belongs to
*this* proposal by *this* principal within its TTL. The `operation_record` provides the
at-most-once guarantee the MRTR spec says integrity protection does not give. Both are
required; neither substitutes for the other.

---

## R-007: Token exchange, and proving the inbound token never leaks

**Decision**: the MCP server validates the inbound token against audience
`mcp-billing-server`, derives the principal from its claims, then calls the token issuer's
exchange endpoint to mint a token with audience `legacy-billing-api` carrying the same
subject, firm, role, and advisor set. Only the minted token is attached to the legacy call.

**Findings**:
- Both services validate signature, expiry, and audience. Audience mismatch, expiry, and bad
  signature are three distinct rejection tests (FR-015).
- SC-005 needs positive proof, not absence of evidence. The legacy API records every inbound
  `Authorization` header value it receives into a request log the tests read; the test
  asserts the inbound MCP token string appears nowhere in it. This is a test-only capture,
  behind a profile, and it never logs to stdout.
- Signing key: a static RSA key pair committed under the token issuer's test resources, used
  only by the local issuer. It is development-only per FR-025 and the README must say so
  plainly, so nobody mistakes it for a secret worth protecting.

**Alternatives considered**: RFC 8693 token exchange semantics with `subject_token` and
`requested_token_type`. Deferred — the OAuth discovery flow is out of scope for this
iteration, and a plain internal mint keeps the exercise on the MCP revision. The exchange
endpoint's shape is nonetheless modelled on 8693 so the upgrade is mechanical.

---

## R-008: `inputSchema` / `outputSchema` — authoring and validation

**Decision**: hand-author the JSON Schema 2020-12 documents as committed resource files, one
per tool, load them into `McpSchema.Tool`, and validate through the SDK's
`io.modelcontextprotocol.json.schema.JsonSchemaValidator` abstraction rather than calling a
schema library directly.

**Rationale**:

- Constitution Principle III says the contract is committed *before* the handler. A
  hand-authored, committed schema is the contract; a schema derived from a Java class at
  build time describes the implementation, which inverts the ordering the principle demands.
- `McpSchema.Tool` takes `inputSchema` and `outputSchema` as `Map<String, Object>`, so the
  committed JSON files load straight into the SDK's own type. No parallel representation
  exists (finding: this also satisfies Principle III's "MUST NOT be declared twice by hand").
- The SDK exposes `JsonSchemaValidator` as an interface with a `validate(Map, Object)`
  contract. Note the older `io.modelcontextprotocol.spec.JsonSchemaValidator` is
  `@Deprecated` in v2.0.1 and points at `io.modelcontextprotocol.json.schema.JsonSchemaValidator`;
  the new location is the one to bind against.
- Because the validator is an SDK abstraction, the concrete schema library arrives behind it
  as a bound implementation. **No direct dependency on `com.networknt:json-schema-validator`
  is declared** — which removes a dependency the pre-clarification plan had to justify
  against Principle I.

**Open build detail, to settle when the build file is written (T002)**: which SDK module
supplies the `JsonSchemaValidator` implementation, and whether 2020-12 is its default
dialect. `mcp-core`'s own dependencies are only `slf4j-api`, `jackson-annotations`,
`reactor-core`, and `jakarta.servlet-api`, so the implementation is in a sibling module. If
the bound implementation does not support 2020-12, a direct schema-library dependency comes
back and must be justified in `plan.md` at that point.

**Micronaut OpenAPI does not overlap** (finding C1): it generates OpenAPI 3.1 from the
*legacy REST API*'s Java types, and that stays. MCP tool schemas are JSON Schema 2020-12
documents inside a JSON-RPC payload, not an HTTP API description. The MCP server therefore
declares no OpenAPI processor at all — its external contract is the five committed tool
definitions, and adding a second description of `POST /mcp` would be the duplication the
constitution forbids. This is stated explicitly in `plan.md`'s Constitution Check so review
does not read the omission as an oversight.

**Alternatives considered**: Jackson's `jsonSchema` module (draft-04 only — rejected);
Micronaut JSON Schema (generates from types, wrong direction — rejected).

---

## R-009: Cursor design

**Decision**: an opaque, HMAC-signed, URL-safe Base64 token holding the search predicate
digest, the offset, the principal's user id, and an expiry.

**Rationale**: FR-007 requires replica B to accept replica A's cursor, and every replica
shares one HMAC key from configuration, so no shared storage is needed. Binding the
predicate digest stops a cursor being replayed against a different search; binding the
principal stops it being replayed by a different caller; the expiry bounds both. The spec
calls handles "a name, not a capability" and says the server should validate authorization
against the handle on every call — signing plus a fresh entitlement check on the legacy call
does that.

---

## R-010: Errors — keeping protocol failures and tool failures apart

**Decision**: one boundary class turns exceptions into responses, and it has exactly two
exits.

| Condition | Exit |
|---|---|
| Missing/mismatched header | JSON-RPC `-32020`, HTTP 400 |
| Unsupported protocol version | JSON-RPC `-32022`, HTTP 400 |
| Missing required client capability | JSON-RPC `-32021`, HTTP 400 |
| Missing required `_meta` field | JSON-RPC `-32602`, HTTP 400 |
| Unknown method | JSON-RPC `-32601`, HTTP 404 |
| Unknown tool name | JSON-RPC `-32602` |
| Invalid token (audience, expiry, signature) | HTTP 401, no JSON-RPC result |
| Legacy 403 | tool result, `isError: true`, "no access" |
| Legacy 404 | tool result, `isError: true` |
| Legacy 5xx or unreachable | tool result, `isError: true`, "do not retry" |
| Argument fails `inputSchema` | tool result, `isError: true`, names the field |

The "never a stack trace, SQL, or internal host name" rule (FR-010, SC-006) is enforced by a
single message-construction path plus a test that asserts on a deny-list of substrings
across every error the acceptance suite produces.

---

## R-011: Test strategy against three replicas

**Decision**: two kinds of suite. Deterministic tests run in-process, per Constitution
Principle IV — `:mcp-server:test` against a stubbed legacy client, plus
`:legacy-billing-api:test` and `:token-issuer:test`. A `topologyTest` Gradle task runs the
cross-replica acceptance scenarios against the real Compose stack through the proxy.

All three deterministic suites must be named wherever the commands are documented; an earlier
draft of `quickstart.md` listed only two and silently dropped `:token-issuer:test` (finding
I1).

**Rationale**: the constitution requires deterministic tests to run with no credential and
no network; the cross-replica scenarios by definition need the topology. Separating them by
source set and task, not by a flag, matches the rule already applied to `liveTest`. The
scripted MCP client the spec allows is a small test fixture shared by both suites, so a
scenario can be written once and pointed at either target.

**Note on Principle V**: this feature performs no agent run, so there is no LangChain4j
trace to emit. The FR-026 audit record is the analogue and is held to the same bar —
structured, no secrets, no full payloads, records which step failed.

---

## R-012: Configuration surface (finding U2)

**Decision**: every setting below is a Micronaut configuration property bound from an
environment variable with a documented default, and the full list ships in the root README
alongside the ones features 004 and 006 already document.

| Variable | Default | Used by | Notes |
|---|---|---|---|
| `MCP_HTTP_PORT` | `8877` | proxy | The only published port of the MCP stack (R-005) |

Every service listens on Micronaut's default **8080 inside its container**; only the proxy
maps a host port. An earlier draft carried 8085 and 8086 from the days when the issuer and the
legacy API published their own — those numbers no longer exist anywhere.
| `MCP_CURSOR_HMAC_KEY` | dev value in `application.yml` | all replicas | Must be identical across replicas or a cursor minted by A fails on B (R-009) |
| `MCP_REQUEST_STATE_KEY` | dev value in `application.yml` | all replicas | AEAD key for MRTR `requestState`; same sharing requirement (R-002) |
| `MCP_ISSUER_URL` | `http://token-issuer:8080` | MCP server, legacy API | JWKS and exchange endpoint |
| `MCP_LEGACY_URL` | `http://legacy-billing-api:8080` | MCP server | System of record |
| `MCP_LEGACY_TIMEOUT_MS` | `5000` | MCP server | Bounded, so an unreachable legacy API becomes a tool error rather than a hang (R-010) |
| `LEGACY_RUN_DURATION_MS` | unset | legacy API | Overrides the simulated run length. Unset means a random duration in `[30000, 90000]`, which is what FR-024 specifies; the topology suite sets it to `3000` so `make mcp-verify` does not spend minutes waiting |
| `MCP_TASK_TTL_MS` | `900000` | MCP server | `ttlMs` on `CreateTaskResult` |
| `MCP_TOOLS_TTL_MS` | `300000` | MCP server | `ttlMs` on `tools/list` |
| `DATASOURCE_URL` / `_USER` / `_PASSWORD` | as `compose.yaml` already sets | both services | Reuses the repository's existing names rather than inventing new ones |

`LEGACY_RUN_DURATION_MS` exists because FR-024's 30–90 seconds is a property of the *domain
simulation*, not of the test harness. T099 asserts the real band against an injected clock; the
containerised legacy API has no clock to inject, so without this variable T106 and T108 each
wait a full run and `make mcp-verify` costs minutes with no lever. The default preserves
FR-024 exactly; only the tests compress it, and they say so.

The two keys are the only settings that *must* match across replicas. Both fail loudly at
startup when absent in a non-development environment, per Principle II's rule that missing
configuration fails at startup naming the absent setting, never at first use.

---

## R-013: Keeping the token issuer out of anything but development (finding U3)

**Decision**: two controls, not one. The issuer's controllers are annotated
`@Requires(env = ...)` for development and the `test-capture` environment the tests use, so in any
other environment the beans do not exist and the endpoints 404. **And the signing key is generated at
startup rather than committed** — see R-020, which supersedes the committed-key decision this entry
originally recorded.

**Rationale**: FR-025 says "development only", and a committed RSA key pair makes that a
property worth enforcing rather than asserting. Documentation alone (the earlier plan's only
control) is a comment, not a constraint. The three test levers — wrong audience, expired,
wrong key — are gated the same way, so a production profile cannot mint a deliberately
broken token even by accident.

**Cost**: a test that wants the levers must run under the `test-capture` environment. That is
already true of the legacy API's request log, so the two share one switch.

---

## R-014: Micronaut MCP capability inventory (Constitution Principle I gate)

**Why this exists.** R-004 inventoried LangChain4j and R-003 inventoried the raw MCP Java SDK.
Neither covered **`io.micronaut.mcp`**, which the pinned Micronaut Platform BOM 5.1.5 already
manages — discovered on 2026-09-13 while resolving `:mcp-server:dependencies`, six tasks into
implementation. That is exactly the omission the constitution's v3.1.0 amendment added the
inventory gate to prevent, so the gate is honoured here before any further design.

Pinned version: **`io.micronaut.mcp:*:2.0.0`**, read from the v2.0.0 sources.

### Modules

| Module | Contents |
|---|---|
| `micronaut-mcp-annotations` | `@Tool`, `@ToolArg`, `@Prompt`, `@PromptArg`, `@Resource`, `@ResourceTemplate`, `@McpPrimitive`, completions |
| `micronaut-mcp` | Configuration binding: `McpServerConfigurationProperties`, `ToolsConfiguration`, `McpServerInfoConfiguration`, `Transport` |
| `micronaut-mcp-server-java-sdk` | 80 classes: hosting, compile-time primitive discovery, argument binding, schema generation, error mapping |
| `micronaut-mcp-client-java-sdk` | Client beans |
| `micronaut-mcp-client-langchain4j` | `McpClientFactory`, `ToolProviderFactory`, `StreamableHttpMcpTransportFactory` — injects a LangChain4j `McpClient`, documented as *for testing MCP servers* |

### Capability decisions

| Capability | Where | Decision |
|---|---|---|
| Declarative tool definition | `@Tool` on a method of a `@Singleton` | **Used.** Carries `name`, `title`, `description`, and `@Tool.ToolAnnotations` with **all four hints** — `readOnlyHint`, `destructiveHint`, `idempotentHint`, `openWorldHint`. FR-011 becomes a declaration, not code. |
| Argument declaration | `@ToolArg(name, description)` | **Used.** |
| `inputSchema` / `outputSchema` generation | `JsonSchemaUtils`, `micronaut-json-schema-utils` | **Used, with a guard.** Generated from parameter and return types. See "Principle III" below. |
| Compile-time tool discovery | `McpExecutableMethodProcessor` | **Used.** Compile-time, which is what the constitution requires of wiring. |
| Argument binding | `registry/*ArgumentBinder*` | **Used.** |
| HTTP hosting | `McpHttpServer`, `McpServerBootstrap` | **Used.** Replaces the hand-written controller FR-029 previously mandated. |
| Transport context | `McpTransportContextExtractor<HttpRequest<?>>` | **Used as the seam for a delta.** The default reads `MCP-Protocol-Version` (defaulting to `2025-03-26`) and `Mcp-Session-Id`. A replacement bean carries `Mcp-Method`/`Mcp-Name` and this revision's rules. |
| Protocol error mapping | `exceptions/McpErrorExceptionMapper` and five siblings | **Used** for the JSON-RPC side of R-010's table. The tool-error side stays ours, because which failures are tool-shaped is a domain decision. |
| JSON Schema validation | `MicronautJsonSchemaValidator` | **Used.** Implements the SDK's `JsonSchemaValidator`. Supersedes R-008's plan to bind `mcp-json-jackson2`. |
| Server identity and capabilities config | `McpServerInfoConfigurationProperties`, `ServerCapabilitiesFactory` | **Used** for `serverInfo`; extended for `capabilities.extensions`. |
| Prompts, resources, resource templates, completions | `@Prompt`, `@Resource`, … | **Not needed.** Out of scope; only tools. |
| LangChain4j client bean | `micronaut-mcp-client-langchain4j` | **Used for FR-030**, replacing the raw `langchain4j-mcp` dependency T006 first added. The module exists for precisely this purpose. |

### What this module does *not* give, and still must be written

`DefaultMcpTransportContextExtractor` defaults the protocol version to `2025-03-26` and reads
`Mcp-Session-Id`; the module's own documentation cites **2025-06-18**. It is therefore two
revisions behind the target, and the seven deltas from R-003 survive unchanged — now written
against Micronaut seams rather than raw SDK interfaces:

| Delta | Seam it is written against | Retired by |
|---|---|---|
| Per-request `_meta` envelope, `serverInfo` on results | custom `McpTransportContextExtractor` + result decoration | java-sdk#1011 |
| `server/discover` | an additional MCP method handler | java-sdk#1011 |
| Header validation, `-32020`/`-32021`/`-32022` | custom `McpTransportContextExtractor` + an `McpErrorExceptionMapper` | java-sdk#1011 |
| `resultType`, `input_required` (MRTR) | result decoration | java-sdk#1011 |
| `ttlMs` / `cacheScope` on list results | result decoration | java-sdk#1009 |
| Tasks extension | additional method handlers + `TaskStore` | java-sdk#1013 |
| No `initialize`, no session id | replacement extractor; the default reads a session header | java-sdk#1011 |

### Principle III: generated schemas versus committed contracts

`@Tool` generates `inputSchema` and `outputSchema` from Java types. Principle III requires the
contract committed **before** the handler. The two are reconciled without weakening either:

- `contracts/tools/*.json` stay the authority and stay committed first.
- The handlers are declared with `@Tool`, so the hints and descriptions are declarations.
- A contract test asserts that the schema Micronaut generates for each tool is **equivalent to
  the committed JSON**. Drift fails the build.

The committed contract becomes the test oracle rather than a runtime resource. This is a change
from R-008, which had the JSON loaded at runtime, and it is strictly better: a generated schema
that must match a committed one cannot silently diverge from the code either.

**Consequence for T042/T044**: the build-time `Copy` of the contracts moves from `main` to
`test` resources, and `ToolCatalog` is no longer needed — the registry is the module's.

---

## R-015: How `@JsonSchema` decides whether `outputSchema` exists at all

**Found while implementing T043 on 2026-09-13, by comparing the generated schema with the committed
contract.** Recorded because the failure mode is silent and the next person will otherwise lose an
hour to it.

`micronaut-json-schema` writes one file per annotated type under `META-INF/schemas/`, and
`micronaut-mcp-server-java-sdk` looks that file up by a name it derives the same way. Both
`@JsonSchema(title = ...)` and `@JsonSchema(uri = ...)` change the written name. If the two
derivations disagree, the module does not fail — it emits the tool definition **without
`outputSchema`**, and `tools/list` looks almost right.

Observed:

| `@JsonSchema` members | Generated file | `outputSchema` in `tools/list` |
|---|---|---|
| none | `billing-run-status.schema.json` | present |
| `title = "Billing run status"` | `billing run status.schema.json` | absent |
| `title` + `uri = ".../billing-run-status.json"` | `billing-run-status.json` | absent |
| `title` + `uri = ".../billing-run-status.schema.json"` | correct name | absent |
| `description` only | `billing-run-status.schema.json` | **present** |

**Rule adopted**: `@JsonSchema` carries **only `description`** on any type used as a tool result. The
tool's display title comes from `@Tool(title = ...)`, which is where a client looks for it anyway,
and the `$id` keeps its generated default. T043's contract test is the guard: it compares the served
schema against the committed contract, so a reintroduced `title` fails the build instead of quietly
shipping a tool with no declared output.

**Also recorded**: Javadoc on the type and on any enum it references becomes the `description` in the
generated schema, verbatim, markup and all. These types are read by models, so their documentation is
caller-facing prose — internal notes belong on the tool class, not on the schema-bearing record.

**Result after applying both rules** — the generated schema now matches the committed contract on
names and constraints:

```
properties: run_id, status (enum of 5), phase (enum of 4), accounts_processed,
            accounts_total, failure_reason, next_step_hint
```

---

## R-016: Where a 2026-07-28 delta can actually be applied to a response

**Found while implementing the result-shaping deltas on 2026-09-13.** Three seams were tried; two
were rejected on evidence rather than on taste, and both are the obvious first guess.

| Seam | Result |
|---|---|
| Micronaut `@ResponseFilter` on `/mcp` | **No.** The module streams the body, so `response.body()` is `null` by the time a response filter runs. Instrumented and confirmed. |
| Replacing the `McpJsonMapper` bean (`@Replaces`) | **No.** Instrumenting `writeValueAsString` showed it is never called on the response path. `McpJsonMapper` serves tool argument and result conversion; the module hands the `JSONRPCResponse` to Micronaut Serde, which writes it. |
| A Micronaut Serde `Serializer<McpSchema.JSONRPCResponse>` | **Yes.** This is where the bytes are produced. |

`JsonRpcResponseSerializer` therefore carries four deltas at once, because they all want the same
place and four half-parsers of the same message would be worse than one:

- `resultType` on every result (java-sdk#1011)
- `_meta.io.modelcontextprotocol/serverInfo` on every result (FR-003)
- `ttlMs` and `cacheScope` on `tools/list` and `server/discover` (FR-009, java-sdk#1009)
- a `ToolFailure` converted from a JSON-RPC error into a result with `isError: true` (FR-010)

**On that last one.** The module maps every thrown exception to an `McpError`, and so to a JSON-RPC
error; there is no path from a tool method to an `isError` result. That is an **eighth delta**, not
in R-003's original seven. It is handled by throwing `ToolFailure`, mapping it to an error with an
application-defined code (`1001`, deliberately outside the JSON-RPC reserved range the specification
forbids emitting undefined codes from), and converting it here. The code never reaches a client.

Verified against a live `tools/list`: `resultType`, `ttlMs`, `cacheScope`, and `serverInfo` all
present on a response the module produced.

---

## R-017: Any Micronaut filter that does I/O must say so

**Found three times on 2026-09-13, in three different places, before the pattern was obvious.**

Micronaut server filters run on the Netty event loop. A blocking HTTP client called from one throws
outright — `"You are trying to run a BlockingHttpClient operation on a netty event loop thread"` —
and rightly: blocking an event loop starves every other request sharing it. The fix is one
annotation, `@ExecuteOn(TaskExecutors.BLOCKING)`, but the failures do not look alike:

| Where | Symptom | Why it hid |
|---|---|---|
| `McpMethodHandler` (`tasks/*`) | A tool error saying the billing system was unavailable | `tools/call` never hits it — the module dispatches tools on its own blocking executor |
| `InboundTokenFilter` (JWKS fetch) | Every request 401, with no explanation | The deterministic suite replaces `JwksSource` with a local file, so it never fetches |
| `BearerTokenFilter` in the legacy API | The MCP server reporting the billing system unavailable, while the exchange plainly worked | Same substitution, same blind spot |

**The general rule adopted**: a filter that touches the network or a database carries
`@ExecuteOn(TaskExecutors.BLOCKING)`, and the annotation carries a comment saying what it blocks on.
`AuditFilter` and the two token filters all have one.

**The wider lesson, which is about the tests rather than the code.** All three were invisible to a
suite that runs correctly and proves real properties. Substituting `JwksSource` was the right call —
it keeps the deterministic tests free of a network, as Principle IV requires — but the substitution
removed the only I/O on that path and with it the only thing that could fail. This is exactly what
the `topologyTest` layer is for, and it is why SC-002 asks for the scenarios to run against the real
stack rather than a faithful-looking imitation of it.

---

## R-018: Transactions do not follow a scheduled task the way they look like they do

**Found on 2026-09-13, while the simulated billing run refused to advance past PENDING.** Three
separate mistakes, each producing the same symptom — a run that simply never moved — and none of
them visible from reading the code.

1. **`@Transactional` is proxy-applied, so self-invocation bypasses it.** `RunSimulator` scheduled
   lambdas calling its own `advance(...)`. The annotation was there; the interceptor was not.
   Failure: `Expected an existing connection, but none was found`. Fixed by moving the writes to an
   injected collaborator, `RunProgressWriter`, so the call goes through the proxy.

2. **A scheduled task inherits the propagated context of the request that scheduled it**, including
   its connection — which is long closed by the time a phase advances seconds later. Failure:
   `Connection is closed`. Fixed with `Propagation.REQUIRES_NEW`, which says what it means better
   than clearing the context by hand would.

3. **A `ScheduledFuture` nobody holds discards its exception silently.** Both failures above were
   invisible until the scheduled work was wrapped so a failure is logged. That wrapper stays: a
   background task that swallows its own errors is a generator of bugs exactly like this one.

Controllers that write through `JdbcOperations` also need `@Transactional` — `BillingRunController`'s
`start` and `cancel` did not have it, and produced the same "Expected an existing connection".

**The pattern across R-017 and this entry**: every one of these is a threading or lifecycle
assumption that a single-process test cannot falsify. They are the reason SC-002 asks for the
scenarios to run against the real stack.

---

## R-019: One request body, one reader

**Found on 2026-09-13 while adding a test that sends `_meta` on `tools/list`.**

Four filters in this server need the JSON-RPC body: the handshake gate, the audit filter, the
protocol gate, and the method handler. Each of them originally bound `@Body String` for itself.
Micronaut binds the body per filter, and subscribing the request body more than once fails inside
`BaseSharedBuffer` with a bare `java.lang.AssertionError` and a 500 that names nothing.

It is intermittent by construction: the failure only appears when enough of the filters actually run
for a given request, so most of the suite passed and one new test did not.

**Fixed by `ParsedBody`**, a filter at order 1 that binds the body once, parses it once, and leaves
the result in a request attribute. Every filter after it reads the attribute. That also removed three
duplicate parse-and-swallow helpers.

**The rule**: on this endpoint, exactly one class binds `@Body`. Anything else that needs the message
calls `ParsedBody.of(request)`.


---

## R-020: Generate the signing key, do not commit it

**Decided on 2026-09-14, while preparing to push to a public repository.** Supersedes the
committed-key part of R-013.

The key was committed so that `make mcp-up` needed no preparatory step (FR-025), and the trade looked
free: the key protects nothing — it signs tokens for seeded fixtures on a stack that publishes one
port on localhost. That reasoning holds for a local repository and stops holding at a public one. A
real RSA private key on GitHub trips secret scanning, cannot be unpublished once indexed, and invites
reuse somewhere it would matter.

**The trade was unnecessary.** Tracing who actually reads the private key found three places — the
issuer's `KeyProvider` and the two `TestKeys` fixtures. Everything else already went through
`/.well-known/jwks.json`:

| Reads the private key | Reads the public key over HTTP |
|---|---|
| `token-issuer` `KeyProvider` | `mcp-server` `HttpJwksSource` → `InboundTokenFilter` |
| `mcp-server` test `TestKeys` | `legacy-billing-api` `HttpJwksSource` → `BearerTokenFilter` |
| `legacy-billing-api` test `TestKeys` | every topology test, via the issuer |

So one container generates a pair at startup and holds it in memory; the verifiers fetch the public
half as they already did. **A fresh clone still runs with one command, because there is no step to
run first.** Generation costs about a tenth of a second. The tests generate their own pair in a
static initialiser rather than reading a file, and still verify for real — `jwks()` publishes the
public half and the filter under test checks signature, expiry and audience against it.

**Cost, accepted and stated in FR-025:** restarting the issuer invalidates every token minted before
it. For a development stack that is fine, and arguably more honest than a key outliving its process.

**Result:** no `.pem` file is tracked anywhere in the repository. 104 deterministic tests and 35
acceptance scenarios pass unchanged.
