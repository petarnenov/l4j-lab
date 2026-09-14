# MCP billing server — protocol revision 2026-07-28

An MCP server that exposes five billing tools to AI agents, built to demonstrate the stateless core
introduced in specification revision **2026-07-28**. Three identical replicas run behind a
round-robin proxy and call a separate legacy REST API that owns the data and decides every
entitlement.

Specification, plan, and the research behind each decision: [`specs/007-mcp-billing-server/`](../specs/007-mcp-billing-server/).

```bash
make mcp-up       # the whole system, one command
make mcp-verify   # start it, run the acceptance scenarios, stop it
make mcp-down
```

No key material is committed: the token issuer generates its signing pair at startup, and this
server only ever reads the public half from the issuer's JWKS (`research.md` R-020).

The stack publishes one port, `${MCP_HTTP_PORT:-8877}`. Deliberately not 8080 or 5432, which
`make dev` and `compose.yaml` already use — all three setups are meant to run at once.

## Where each 2026-07-28 feature lives

This is the table SC-004 asks for: a reader should be able to point at each one.

| Feature | File |
|---|---|
| Stateless requests, per-request `_meta` | `protocol/RequestEnvelope.java`, `protocol/BillingTransportContextExtractor.java` |
| Header-based routing, `-32020` | `protocol/McpRequestGate.java` |
| Version negotiation, `-32022` | `protocol/McpRequestGate.java`, `protocol/ProtocolVersions.java` |
| `server/discover` | `protocol/McpMethodHandler.java` |
| No `initialize`, answered with a diagnostic | `protocol/LegacyHandshakeGate.java` |
| No session, `405` on `GET`/`DELETE` | `protocol/HttpMethodGate.java`, `protocol/McpRequestGate.java` |
| `resultType` on every result | `protocol/JsonRpcResponseSerializer.java` |
| `_meta` server identity on every result | `protocol/JsonRpcResponseSerializer.java` |
| Cacheable list results (`ttlMs`, `cacheScope`) | `protocol/JsonRpcResponseSerializer.java` |
| Server-minted handles (cursors) | `protocol/CursorCodec.java` |
| Multi Round-Trip Requests | `protocol/InputRequired.java`, `protocol/RequestStateCodec.java`, `tools/FeeAdjustmentTool.java` |
| Tasks extension | `tasks/`, `protocol/TaskCreated.java` |
| Tool errors vs protocol errors | `protocol/ToolFailure.java`, `protocol/JsonRpcResponseSerializer.java` |
| Token exchange, never forwarding | `security/TokenExchangeClient.java`, `legacy/LegacyBillingClient.java` |
| Audit and trace propagation | `audit/` |

The filter chain is ordered, and the order is a decision rather than an accident:

| Order | Filter | Why there |
|---|---|---|
| 5 | `HttpMethodGate` | Whether `GET` is allowed has nothing to do with who is asking |
| 6 | `LegacyHandshakeGate` | A legacy client has no credentials to offer and must still learn which version this server speaks |
| 10 | `InboundTokenFilter` | Everything below needs a caller |
| 15 | `AuditFilter` | Before the gate, so a rejected request is still recorded |
| 20 | `McpRequestGate` | Headers, version |
| 30 | `McpMethodHandler` | `server/discover`, `tasks/*` — these act on a caller's data |

## What is written here, and what is not

Everything the **Micronaut MCP integration** (`io.micronaut.mcp:micronaut-mcp-server-java-sdk:2.0.0`)
provides comes from it: hosting, compile-time `@Tool` discovery, argument binding, schema generation,
JSON Schema validation, protocol error mappers. Tools are declared, not dispatched — the four
behaviour hints MCP requires are annotation members, not code.

That module targets **2025-06-18**, and the MCP Java SDK beneath it targets **2025-11-25**. Neither
implements this revision. The gap below is what is written by hand, and each row names the upstream
issue that would delete it.

| Hand-written | Why | Retired by |
|---|---|---|
| Per-request `_meta` envelope, `serverInfo` on results | Version and capabilities arrive per request now, not in a handshake | [java-sdk#1011](https://github.com/modelcontextprotocol/java-sdk/issues/1011) |
| `server/discover` | Not implemented; [#1072](https://github.com/modelcontextprotocol/java-sdk/issues/1072) reports it returning 500 | java-sdk#1011 |
| Header validation and codes `-32020`/`-32021`/`-32022` | New in this revision | java-sdk#1011 |
| `resultType`, and `input_required` for MRTR | `CallToolResult` has no `resultType` field | java-sdk#1011 |
| `ttlMs` / `cacheScope` on list results | Absent from `ListToolsResult` | [java-sdk#1009](https://github.com/modelcontextprotocol/java-sdk/issues/1009) |
| Tasks extension | No type exists | [java-sdk#1013](https://github.com/modelcontextprotocol/java-sdk/issues/1013) |
| Refusing `initialize`, no session id | The module's transport context still defaults to `2025-03-26` and reads `Mcp-Session-Id` | java-sdk#1011 |
| Tool failures as `isError` results | The module maps every thrown exception to a JSON-RPC error; there is no path to an `isError` result | — |

The last row is the one with no upstream issue behind it. It is handled by throwing `ToolFailure`,
carrying it through the module with an application-defined code, and converting it in
`JsonRpcResponseSerializer` — because the specification is explicit that a business-logic failure and
a malformed request must never look alike.

**When an issue above closes and ships, delete the corresponding file.** That is the point of the
table.

## Tests

```bash
./gradlew :mcp-server:test          # deterministic: no network, no credential
./gradlew :mcp-server:topologyTest  # the acceptance scenarios; needs `make mcp-up` first
```

The split is not tidiness. Four properties do not exist in a single process — a cursor minted by one
replica read by another, a confirmation retry landing elsewhere, task polls spread across three, and
the inbound token never reaching the legacy API — and a single-instance test would pass for all of
them while the code was quietly stateful.

It earns its keep: three separate bugs were invisible to a deterministic suite that proves real
properties, because substituting a key source removed the only I/O on that path and with it the only
thing that could fail. See `research.md` R-017 and R-018.
