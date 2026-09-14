# Feature Specification: MCP Billing Server (spec revision 2026-07-28)

**Feature Branch**: `007-mcp-billing-server`

**Created**: 2026-09-13

**Status**: Implemented, 2026-09-14. All 123 tasks complete. Six findings raised against this
feature by feature 008 remain open; see `specs/008-mcp-console/findings.md`. Status corrected
2026-09-14.

**Input**: User description supplied to `/speckit-plan`. This spec was written from that
description because no spec existed for the feature; it restates the description without
adding scope.

## Overview

A learning-grade Model Context Protocol server that targets specification revision
**2026-07-28** (the stateless core) and exposes a small billing domain to AI agents. The
point of the feature is to exercise, end to end, the protocol features that revision
introduced — stateless requests, server-minted handles, Multi Round-Trip Requests, the
Tasks extension, cacheable list results, header-based routing — on top of a realistic
identity model, so the code reads as a reference for how a production MCP server in a
regulated billing environment behaves.

The system has three parts, mirroring a real deployment topology:

1. **Legacy billing REST API** — the system of record. Owns the data and enforces
   entitlements. Deliberately simple, but its authorization semantics are real: it
   validates a bearer token issued for its own audience, derives the caller's scope from
   the token, and refuses requests outside that scope.
2. **MCP server** — a separate service with no database of its own for domain data. It
   validates its own bearer token, derives a principal, exchanges that token for one
   issued for the legacy API's audience, and calls the legacy API on behalf of the user.
   It never forwards the token it received.
3. **Local token issuer** — development only. Mints signed JWTs for both audiences from a
   static key so the whole system runs offline.

## Clarifications

### Session 2026-09-13

- Q: *Which do we give up — revision 2026-07-28, or the rule against hand-written protocol code?* (Какво жертваме — спецификация 2026-07-28, или правилото да не пишем протоколен код на ръка?) (FR-001) → A: Option C — hybrid. Keep revision 2026-07-28, and source everything the MCP Java SDK and LangChain4j already provide from those libraries; hand-write only the parts of 2026-07-28 that no library implements at any version.

**What this settles.** Verified against the libraries on 2026-09-13: MCP Java SDK is at
**v2.0.1** on the `2.0.x` line, tracking specification **2025-11-25** — there is no `3.x`
branch and no merged work for 2026-07-28, and its tracking issues for exactly the features
this feature needs are open (SEP-2575 stateless, SEP-2567 handles, SEP-2663 Tasks, SEP-2549
list TTL). `langchain4j-mcp` is client-side only and has no MCP server at any version.

Therefore, and as a requirement rather than a plan-level preference:

- The revision-independent machinery MUST come from `io.modelcontextprotocol.sdk:mcp` —
  the `McpSchema` wire types, JSON-RPC framing, the tool registry, the JSON Schema
  validation abstraction, and the stateless server handler and transport.
- Hand-written code is permitted ONLY for the 2026-07-28 deltas that no library implements,
  and each such piece MUST name the open upstream issue that would retire it.
- LangChain4j MUST be used wherever it can contribute. Established in the third
  clarification below: for an MCP server its only possible contribution is the FR-030
  compatibility test, because its client cannot speak this revision.

- Q: *How deep into the SDK's server side do we go, given its only HTTP transports are Jakarta Servlet and its version gate stops at 2025-11-25?* (Колко надълбоко влизаме в сървърната страна на SDK-то?) (FR-001, FR-007) → A: Option B — a Micronaut controller implements the SDK's `McpStatelessServerHandler`; only the servlet plumbing is replaced, never protocol logic.

**Where the boundary falls.** Verified against v2.0.1 on 2026-09-13: the SDK's only HTTP
server transports are Jakarta Servlet (`HttpServletStatelessServerTransport` and siblings)
plus stdio — there is no Netty or Micronaut transport. `ProtocolVersions` tops out at
`2025-11-25`, and `DefaultMcpStatelessServerHandler` requires an `initialize` handshake,
which FR-001 forbids. But `McpStatelessServerHandler` is a two-method interface
(`handleRequest(McpTransportContext, JSONRPCRequest) → Mono<JSONRPCResponse>` and
`handleNotification(...)`), so a Micronaut controller can satisfy it directly.

Taken from the SDK: the `McpSchema` wire records, the tool registry, `JsonSchemaValidator`,
`McpError`, and the JSON-RPC request/response/notification types. Replaced: the servlet
transport only — plumbing, not protocol. Written by hand: the request dispatch, because the
SDK's default handler mandates a handshake this revision removed.

This keeps Micronaut the sole HTTP server, as the constitution requires, and keeps the whole
repository on one Netty runtime.

- Q: *Will the server also serve legacy clients (2025-11-25 with `initialize`), so LangChain4j's `McpToolProvider` and the existing backend can consume it?* (Ще обслужва ли сървърът и legacy клиенти?) (FR-001) → A: Option C — modern-only in this iteration, plus one compatibility test driving a real LangChain4j `McpClient`; dual-era is deferred to a separate feature.

**What this settles about LangChain4j.** Verified against 1.18.0 on 2026-09-13:
`DefaultMcpClient` defaults to `protocolVersion = "2025-11-25"` and always sends
`initialize`. The version is builder-configurable; the handshake is not. So LangChain4j's
client structurally cannot drive the modern path, and `langchain4j-mcp` has no server side
at all. Its contribution to this feature is therefore one thing only: a compatibility test
(FR-030) that proves a real-world legacy client receives the diagnostic FR-001 mandates
rather than silence. Serving legacy clients properly is deferred — see Out of Scope.

- Q: *What does `tasks/cancel` do — cancel the billing run in the legacy API, or only mark the task?* (Какво прави `tasks/cancel`?) (FR-021) → A: Option A — `tasks/cancel` requests cancellation of the legacy run, so `CANCELED` becomes reachable; cooperative, per the Tasks extension.

- Q: *A Micronaut MCP server integration exists and the pinned platform BOM already manages it — do we build on it?* (FR-028, FR-029) → A: Option A — build on `micronaut-mcp-server-java-sdk`, declare tools with `@Tool`, and write only the 2026-07-28 deltas on top.

**How this came to light, and what it costs.** Found on 2026-09-13 while resolving
`:mcp-server:dependencies`, six tasks into implementation: `io.micronaut.mcp:*:2.0.0` is managed
by Micronaut Platform BOM 5.1.5 and provides a full MCP server integration, `@Tool`/`@ToolArg`
annotations carrying all four behaviour hints, compile-time tool discovery, schema generation, a
JSON Schema validator, protocol error mappers, and a LangChain4j client built for testing MCP
servers. The earlier inventories (R-003, R-004) missed it, which is the omission the
constitution's v3.1.0 gate exists to catch. `research.md` R-014 now inventories it properly.

It does **not** rescue the target revision — the module documents 2025-06-18 and its transport
context extractor still defaults to `2025-03-26` and reads `Mcp-Session-Id`. The seven deltas in
R-003 survive; they are now written against Micronaut seams (a replacement
`McpTransportContextExtractor`, result decoration, extra method handlers) instead of raw SDK
interfaces, and the hand-written controller FR-029 previously required is gone.

- Q: *Should the signing key be committed so a clean clone runs offline, or generated so nothing secret is published?* (FR-025) → A: Generated at startup, in memory, and never written down.

**Why this changed.** The key was originally committed so `make mcp-up` needed no preparatory step,
and the trade seemed free because the key protects nothing. Preparing to push to a **public**
repository made the other side visible: a real RSA private key there trips secret scanning, cannot be
unpublished, and invites reuse somewhere it would matter.

The trade turned out to be unnecessary. Only the issuer ever holds the private key — the MCP server
and the legacy API verify against the public half they fetch from `/.well-known/jwks.json`, and never
see the other one. So a single container can generate a pair at startup and a fresh clone still runs
with one command, because there is no step to run first. The tests do the same rather than reading a
file.

**The cost, accepted:** restarting the issuer invalidates every token minted before it. For a
development stack that is fine, and arguably better — a token from a previous run of a system that no
longer exists should not still work.

This is recorded as FR-028 below, and FR-021's conditional clause is now resolved to fact.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - An agent finds and reads billing runs within its entitlements (Priority: P1)

An advisor's agent searches the firm's billing runs, pages through the matches, opens one
run's status, and — when the run failed — reads the per-household failure causes. What the
agent can see is decided by the legacy API from the principal's role and advisor set, not
by the agent's request.

**Why this priority**: it is the read half of the domain and it carries four of the
protocol features on its own: stateless request metadata, header-based routing,
server-minted cursors, and cacheable `tools/list`. It is a viable MVP without any write
tool.

**Independent Test**: run the three read-only tools against the legacy API with tokens for
an ADVISOR, a FIRM_ADMIN, and a principal of another firm, and assert on what each sees.

**Acceptance Scenarios**:

1. **Given** an ADVISOR principal, **When** it calls `search_billing_runs` for its firm,
   **Then** the result contains only runs executed by advisors in its allowed set.
2. **Given** a FIRM_ADMIN principal, **When** it calls `search_billing_runs` for its firm,
   **Then** the result contains every run of that firm.
3. **Given** any principal, **When** it searches runs of another firm, **Then** it sees
   none of them.
4. **Given** a run belonging to another firm, **When** the principal calls
   `get_billing_run_status` for it, **Then** the result is a short "no access" tool error
   and nothing else.
5. **Given** a search whose match count exceeds one page, **When** the client sends the
   returned cursor back as an ordinary argument, **Then** the next page continues the same
   result set.
6. **Given** a cursor minted by replica A, **When** the next call lands on replica B,
   **Then** replica B accepts it and continues the same result set.
7. **Given** a FAILED run, **When** the principal calls `get_billing_run_status`, **Then**
   the result carries a next-step hint pointing at `get_run_failures`.
8. **Given** a FAILED run, **When** the principal calls `get_run_failures`, **Then** it
   receives the affected households with a per-household cause, capped, with a total count.

---

### User Story 2 - An agent posts a fee adjustment, confirmed once and executed once (Priority: P2)

An operator's agent proposes a fee adjustment on one account. The server does not execute
on the first call: it returns an input-required result asking the user to confirm the exact
change. The client repeats the request with the confirmation and the server's request
state, and the adjustment executes against the legacy API exactly once. A repeat with the
same operation id returns the original result without touching the legacy API.

**Why this priority**: it is the write path and the only exercise of Multi Round-Trip
Requests, request-state integrity, and cross-replica idempotency.

**Independent Test**: call the tool three times against three different replicas and assert
on the legacy API's received-request log.

**Acceptance Scenarios**:

1. **Given** a first call with an operation id and no confirmation, **When** the tool runs,
   **Then** the result is `input_required` carrying an elicitation for the exact change and
   an opaque request state, and the legacy API was not called.
2. **Given** the confirmation and the request state echoed back, **When** the client
   repeats the request, **Then** the adjustment executes and the legacy API records exactly
   one write.
3. **Given** a third call with the same operation id, **When** the tool runs, **Then** it
   returns the original result and the legacy API is not called again.
4. **Given** the first call landed on replica A, **When** the confirmation retry lands on
   replica B, **Then** it still executes correctly.
5. **Given** an executed adjustment, **When** the audit log is read, **Then** it records who
   confirmed and the legacy reference id.

---

### User Story 3 - An agent starts a long billing run and polls it to completion (Priority: P3)

An agent starts a billing run for a firm. The legacy API simulates a run that takes 30–90
seconds, advancing through phases. The MCP server returns a handle immediately; the agent
polls until the run completes and then receives the final run status.

**Why this priority**: it is the only exercise of the Tasks extension, which no Java library
implements at any version, so every line of it is written against the published wire shape. It
also carries the only cancellation path in the feature.

**Independent Test**: call the tool, assert the response arrives within one second, poll to
a terminal state, and compare the final status with what the legacy API reports.

**Acceptance Scenarios**:

1. **Given** a firm the principal may act for, **When** it calls `start_billing_run`,
   **Then** a handle is returned within one second.
2. **Given** the handle, **When** the client polls, **Then** it eventually observes
   COMPLETED and the final status matches what the legacy API reports for that run.
3. **Given** polling requests spread across replicas, **When** each poll lands on a
   different replica, **Then** every poll returns the same task state.
4. **Given** a client that does not declare the `io.modelcontextprotocol/tasks` capability,
   **When** it calls the tool, **Then** it still receives a handle immediately, as an ordinary
   complete result carrying the run id and the tool to poll, and never a task result.
5. **Given** a run still in `PENDING` or `RUNNING`, **When** the client cancels the handle,
   **Then** the run reaches `CANCELED`, the task reaches `cancelled`, and a subsequent status
   read shows `CANCELED`.
6. **Given** a run that has already completed, failed, or been canceled, **When** the client
   cancels the handle, **Then** the request is acknowledged, the run's status is unchanged, and
   nothing is reported as an error.

---

### User Story 4 - The protocol layer rejects malformed and unauthorized traffic correctly (Priority: P1)

Every request carries its own protocol version, client capabilities, and routing headers.
The server rejects anything inconsistent, and it never confuses a protocol failure with a
tool failure.

**Why this priority**: it is the substance of the 2026-07-28 revision and it gates every
other story. It is testable with no domain data at all.

**Independent Test**: drive the endpoint directly with hand-built HTTP requests.

**Acceptance Scenarios**:

1. **Given** a request with no `Mcp-Method` or `Mcp-Name` header, **When** it reaches the
   server, **Then** it is rejected.
2. **Given** a request whose `Mcp-Name` header disagrees with the body, **When** it reaches
   the server, **Then** it is rejected with the HeaderMismatch error.
3. **Given** a request declaring an unsupported protocol version, **When** it reaches the
   server, **Then** it is rejected with the UnsupportedProtocolVersion error listing the
   versions the server supports.
4. **Given** any request, **When** `server/discover` is called, **Then** the result carries
   the supported versions, the server capabilities, and the server identity.
5. **Given** two `tools/list` calls with the same underlying tool set, **When** both are
   served, **Then** both carry `ttlMs` and `cacheScope` and list the tools in the same
   order.
6. **Given** a token whose audience is not the MCP server, **When** it is presented,
   **Then** the MCP server rejects it; the same holds for an expired token and one with a
   bad signature.
7. **Given** any tool call, **When** the MCP server calls the legacy API, **Then** the token
   the MCP server received never appears in that request.
8. **Given** the legacy API returns 500, **When** the tool completes, **Then** the result is
   a tool error telling the model not to retry, it contains no stack trace, SQL, or internal
   host name, and the audit log records the failure.
9. **Given** a client built for an earlier protocol revision, which opens with an `initialize`
   handshake, **When** it connects, **Then** it receives a JSON-RPC error naming the protocol
   versions this server supports — never silence, and never a response that would let it
   proceed as though the handshake had succeeded.

### Edge Cases

- A cursor that is expired, tampered with, or minted for a different principal must be
  refused rather than served.
- A request state presented by a different principal, or after its expiry, must be refused.
- A confirmation that does not match the change the server proposed must not execute.
- A poll for an unknown or expired handle must say so in a way the model can act on.
- A page size argument above the cap must be clamped to the cap, not rejected silently.
- The legacy API being unreachable must produce a tool error, not a protocol error.
- Cancelling a run that has already completed, failed, or been canceled must be acknowledged
  rather than refused, and must leave the run's status untouched.
- Cancelling a task whose principal differs from the caller's must be refused.

## Requirements *(mandatory)*

### Functional Requirements

#### Protocol

- **FR-001**: The MCP server MUST target protocol revision `2026-07-28` and MUST NOT
  implement or require an `initialize` handshake.
- **FR-002**: The server MUST read the protocol version and client capabilities from each
  request's `_meta` and MUST NOT infer them from any previous request.
- **FR-003**: Every result the server returns MUST carry server identity in the result's
  `_meta`.
- **FR-004**: A request declaring a protocol version the server does not support MUST be
  rejected with the UnsupportedProtocolVersion error, listing the supported versions.
- **FR-005**: The server MUST implement `server/discover`, returning supported versions,
  capabilities, and server identity.
- **FR-006**: The server MUST NOT issue or accept a session identifier.
- **FR-007**: Three replicas behind a round-robin proxy MUST serve any sequence of requests
  correctly, including cursor continuation, confirmation retries, and task polling. State
  that must survive between requests MUST live in the request itself or in shared storage.
- **FR-008**: `Mcp-Method` and `Mcp-Name` headers MUST be required on every request that
  the transport binding requires them for, and a request whose headers are missing or
  inconsistent with the body MUST be rejected with the HeaderMismatch error.
- **FR-009**: `tools/list` MUST return `ttlMs` and `cacheScope`, and MUST return tools in a
  deterministic order.
- **FR-010**: Tool execution failures MUST be returned as results with `isError` set,
  carrying a short, actionable message and never a stack trace, SQL, or internal host name.
  Protocol failures MUST use JSON-RPC error responses. The two MUST NOT be confused.
- **FR-011**: Each tool MUST declare `readOnlyHint`, `destructiveHint`, `idempotentHint`,
  and `openWorldHint` honestly.
- **FR-012**: Tool results MUST carry `structuredContent` validated against a declared
  `outputSchema`, plus a text rendering in `content`.

#### Identity and authorization

- **FR-013**: A principal MUST carry a user id, a firm id, a role (FIRM_ADMIN, ADVISOR,
  OPS, READ_ONLY), and the set of advisor ids it may act for. FIRM_ADMIN and OPS see all
  advisors in their firm; ADVISOR sees only itself.
- **FR-014**: Entitlements MUST be enforced in the legacy API only. The MCP server MUST NOT
  duplicate them; it MUST translate a legacy 403 into a short tool error.
- **FR-015**: The MCP server MUST reject a token whose audience is not itself, and the
  legacy API MUST reject a token whose audience is not itself. Both MUST reject on audience
  mismatch, expiry, and bad signature.
- **FR-016**: The MCP server MUST exchange the token it received for one issued for the
  legacy API's audience, and MUST NOT forward the received token to the legacy API.

#### Tools — exactly five, no more

- **FR-017**: `search_billing_runs` (read-only) MUST search runs by firm, status, advisor,
  and date range; MUST paginate with a server-minted opaque cursor returned in the result
  and accepted as an ordinary argument on the next call; MUST carry the page, the total
  match count, a truncated flag, and a refine hint when truncated; and MUST cap page size
  at 20.
- **FR-018**: `get_billing_run_status` (read-only, idempotent) MUST return status, phase,
  accounts processed and total, failure reason if any, and a next-step hint pointing at
  `get_run_failures` when the run has failed. The result MUST be a purpose-built shape, not
  the stored entity.
- **FR-019**: `get_run_failures` (read-only, idempotent) MUST, for a failed run, return the
  affected households with the failure cause per household, as a capped list with a total
  count.
- **FR-020**: `post_fee_adjustment` (write, destructive, idempotent) MUST adjust the fee on
  one account; MUST require a client-supplied operation id for idempotency such that a
  repeated call with the same operation id returns the original result without
  re-executing; MUST return an input-required result on the first call asking the user to
  confirm the exact change; MUST execute only when the client repeats the request with the
  confirmation and the request state; MUST execute only against the legacy API, on behalf
  of the user; and MUST record who confirmed and the legacy reference id in an audit log.
  "Who confirmed" is the authenticated principal: an `ElicitResult` carries no identity of its
  own, so the protocol offers no separate confirmer, and the audit record MUST NOT imply one.
- **FR-021**: `start_billing_run` (write, long-running) MUST start a run for a firm and
  return a task handle immediately using the Tasks extension; the client polls task status
  until completion and then receives the final run status. The available SDK does not
  implement the Tasks extension (established 2026-09-13; SEP-2663 is open upstream), so the
  extension MUST be implemented against its published wire shape on top of the SDK, and that
  gap MUST be recorded explicitly with the upstream issue that would retire it. Separately,
  a client that does not declare the extension MUST still receive a handle immediately, with
  progress exposed through a documented core-protocol fallback.
- **FR-031**: `tasks/cancel` MUST request cancellation of the underlying billing run through
  the legacy API, driving the run to `CANCELED` and the task to `cancelled`. Cancellation is
  cooperative: a run already in a terminal status MUST have the cancellation acknowledged
  without any state change, and the task MUST reflect the status the run actually reached.
  Entitlement for the cancellation is decided by the legacy API, as for every other write.
  `tasks/cancel` MUST be accepted from any client holding the task handle, whether or not it
  declared the Tasks extension: a client on the FR-021 fallback path was given a handle, and
  there is no sixth tool it could use instead (FR-022).
- **FR-022**: The server MUST expose exactly these five tools and no more.

#### Legacy API and token issuer

- **FR-023**: The legacy billing REST API MUST own the domain data and MUST be the only
  place entitlements are enforced.
- **FR-024**: The legacy API MUST simulate a billing run taking 30–90 seconds, advancing
  through phases.
- **FR-025**: The local token issuer MUST mint signed JWTs for both audiences, and MUST be
  development-only, so the whole system runs offline and a fresh clone starts with one command. The
  signing key MUST be generated at startup and MUST NOT be committed: the services that verify
  tokens read only the published public key, so nothing requires the private key to outlive the
  process. Tokens minted before an issuer restart are expected to stop working.

#### Observability

- **FR-026**: Every tool invocation MUST be audited with principal, tool name, argument
  summary (identifiers only, never full payloads), outcome, and duration.
- **FR-027**: Trace context MUST be accepted from `_meta` (`traceparent`) and propagated to
  the legacy API call.

#### Library sourcing

- **FR-028**: Every capability the **Micronaut MCP integration** (`io.micronaut.mcp`), the MCP
  Java SDK, or LangChain4j already provides MUST be taken from those libraries rather than
  written by hand. Hand-written code is permitted only for
  a 2026-07-28 behaviour that no released version of either library implements. Each such
  piece MUST be listed, with the upstream issue that would retire it, and the list MUST be
  reachable from the server's own README, not only from this spec directory.
- **FR-029**: The MCP server MUST be hosted by the `io.micronaut.mcp` server integration
  (`micronaut-mcp-server-java-sdk`), not by a hand-written controller and not by the SDK's
  Jakarta Servlet transports. Tools MUST be declared with `@Tool` and `@ToolArg`, so the four
  behaviour hints of FR-011 are declarations rather than code. A second HTTP runtime MUST NOT
  be introduced.
- **FR-030**: A compatibility test MUST drive a real LangChain4j `McpClient`, obtained from
  `micronaut-mcp-client-langchain4j` rather than wired by hand, against the MCP
  server and assert that the resulting `initialize` request receives a JSON-RPC error naming
  the protocol versions the server supports, never silence and never a legacy-era response.

### Key Entities

- **Firm**: the top of the hierarchy. Owns advisors and billing runs.
- **Advisor**: belongs to a firm; executes billing runs; owns households.
- **Household**: belongs to an advisor; owns accounts.
- **Account**: belongs to a household; carries the fee a fee adjustment changes.
- **Billing run**: belongs to a firm, executed by an advisor. Has a status (PENDING,
  RUNNING, COMPLETED, FAILED, CANCELED), a phase (DATA_COLLECTION, FEE_CALC, INVOICING,
  POSTING), a count of accounts processed versus total, and an optional failure reason.
  `CANCELED` is reached only through `tasks/cancel` (FR-031), which any client holding the
  handle may call.
- **Fee adjustment**: a signed change to an account's fee with an effective date and a
  legacy reference id.
- **Principal**: user id, firm id, role, and the set of advisor ids it may act for.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The whole system starts with one command.
- **SC-002**: Every acceptance scenario above is an automated test, and all of them pass
  against three MCP replicas behind a round-robin proxy.
- **SC-003**: `start_billing_run` returns a handle in under one second, measured at the
  client.
- **SC-004**: An engineer reading the code can point to where each 2026-07-28 feature is
  implemented — stateless requests, server-minted handles, Multi Round-Trip Requests, the
  Tasks extension, cacheable list results, header-based routing.
- **SC-005**: No test observes the MCP server's own inbound token in any request reaching
  the legacy API.
- **SC-006**: No tool error message emitted by the server contains a stack trace, SQL, or
  an internal host name.

## Out of Scope

- OAuth discovery flow: protected resource metadata, authorization server metadata, Client
  ID Metadata Documents. Tokens are minted by the local issuer directly.
- `subscriptions/listen` and list-changed notifications.
- Resources and prompts primitives; only tools.
- Any RAG or retrieval tool.
- A user interface. A minimal scripted MCP client used by the tests is sufficient.
- Dual-era operation. This server is modern-only: it does not serve clients that expect an
  `initialize` handshake, even though the SDK would make 2025-11-25 nearly free. Folding it
  in would double the acceptance matrix while the protocol layer is still unproven, so it is
  deferred to a separate feature, along with wiring the existing `backend` to consume these
  five tools through LangChain4j's `McpToolProvider`.

## Assumptions

- The system runs offline; no hosted inference provider and no external identity provider
  is contacted.
- This feature contains no LLM call of its own. It exposes tools that an agent elsewhere
  calls; the agent is not part of the deliverable.
- "Three replicas behind a round-robin proxy" is a local composition, not a production
  deployment target.
- The legacy API's data is seeded fixture data, sized for the acceptance tests.
