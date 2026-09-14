# Quickstart: MCP Billing Server

**Feature**: `007-mcp-billing-server`

A run-and-verify guide. Contracts live in `contracts/`; entities in `data-model.md`;
decisions and their evidence in `research.md`. Nothing is duplicated here.

## Prerequisites

- Docker with Compose v2 (the repo already requires it)
- A JDK for the Gradle toolchain to resolve Java 25 against
- No credentials, no network beyond the container registry — the whole system runs offline
  (FR-025)

## Start everything with one command

```bash
make mcp-up
```

That is SC-001. It brings up, on one Compose network:

| Service | Published port | Role |
|---|---|---|
| `postgres` | none | `legacy_billing` and `mcp_ops` schemas, internal network only |
| `token-issuer` | none | mints and exchanges dev JWTs |
| `legacy-billing-api` | none | system of record, enforces entitlements |
| `mcp-a`, `mcp-b`, `mcp-c` | none | three identical MCP replicas |
| `mcp-proxy` | `${MCP_HTTP_PORT:-8877}` | nginx, round-robin over the three replicas |

**One published port, and it is deliberately not 8080.** `make dev` and `make dev-backend`
guard 8080, and `compose.yaml` already publishes 5432. Feature 004 promised its stack and the
development setup can run at the same time; a third stack has to honour that. The port follows
feature 006's pattern — a variable with a documented default — so a clash is fixable from the
shell: `MCP_HTTP_PORT=9001 make mcp-up`.

The proxy is the only way in, and it routes two paths: `POST /mcp` round-robin over the three
replicas, and `/dev/*` plus `/.well-known/jwks.json` to the token issuer, so a developer can
mint a token without a second published port. Nothing addresses a replica directly except the
tests that must — cross-replica scenarios target `mcp-a`/`mcp-b` by name to prove the hop.

```bash
make mcp-down     # stop and remove
make mcp-logs     # tail all six services
```

## Confirm it is up

```bash
export MCP=http://localhost:${MCP_HTTP_PORT:-8877}
curl -s $MCP/.well-known/jwks.json                       # issuer public key, via the proxy
docker compose -f compose.mcp.yaml ps                     # all six healthy
```

Then the protocol's own front door:

```bash
TOKEN=$(curl -s -X POST $MCP/dev/token \
  -H 'Content-Type: application/json' \
  -d '{"principal":"admin-alpha","audience":"mcp-billing-server"}' | jq -r .token)

curl -s -X POST $MCP/mcp \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H 'MCP-Protocol-Version: 2026-07-28' \
  -H 'Mcp-Method: server/discover' \
  -d '{"jsonrpc":"2.0","id":"d1","method":"server/discover","params":{"_meta":{
        "io.modelcontextprotocol/protocolVersion":"2026-07-28",
        "io.modelcontextprotocol/clientCapabilities":{}}}}' | jq
```

Expected: `supportedVersions: ["2026-07-28"]`, the tools capability, the tasks extension,
`ttlMs`, `cacheScope`, and `serverInfo` in `_meta`. Shape in
`contracts/mcp-protocol.md`.

Two one-line proofs the revision is really in force:

```bash
# Missing Mcp-Method → HTTP 400, JSON-RPC -32020
curl -s -o /dev/null -w '%{http_code}\n' -X POST $MCP/mcp \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H 'MCP-Protocol-Version: 2026-07-28' -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'

# GET on the endpoint → 405; sessions are gone
curl -s -o /dev/null -w '%{http_code}\n' $MCP/mcp
```

## Run the tests

Two suites, split the way the constitution already splits `liveTest` — by task, never by a
flag someone has to remember.

```bash
./gradlew :mcp-server:test :legacy-billing-api:test :token-issuer:test   # deterministic, no network, no containers
./gradlew topologyTest                                 # the acceptance scenarios, against the running stack
make mcp-verify                                        # mcp-up, topologyTest, mcp-down
```

`topologyTest` requires `make mcp-up` to have run; it fails with a message saying so rather
than starting containers behind your back.

**On timing.** A billing run takes 30–90 seconds by default, because FR-024 says so. `make
mcp-verify` therefore sets `LEGACY_RUN_DURATION_MS=3000` and finishes in well under a minute.
If you run `topologyTest` by hand against a stack started with a plain `make mcp-up`, the two
run-completion tests really will wait a full run each — that is the feature behaving correctly,
not a hang. Export `LEGACY_RUN_DURATION_MS=3000` before `make mcp-up` if you would rather not.

## Acceptance scenarios and where each is proved

Each row is one automated test (SC-002). Scenario numbering follows `spec.md`.

| Scenario | Suite | What it asserts |
|---|---|---|
| US1-1,2,3 — ADVISOR vs FIRM_ADMIN vs other firm | topology | An ADVISOR's search returns only its own advisor's runs; FIRM_ADMIN's returns all of the firm's; neither returns `firm-beta`'s |
| US1-4 — cross-firm run | topology | A short "no access" tool error, `isError: true`, and no run data |
| US1-5 — cursor continuation | topology | Page 2 continues page 1; no overlap, no gap |
| US1-6 — cursor across replicas | topology | Cursor minted by `mcp-a` accepted by `mcp-b` |
| US1-7 — failed-run hint | unit + topology | A FAILED run's status carries `next_step_hint` naming `get_run_failures` |
| US1-8 — failures list | topology | Households with causes, capped, with `total_count` |
| US2-1 — first call | topology | `resultType: "input_required"`, an elicitation, a `requestState`, and **zero** writes at the legacy API |
| US2-2 — confirmed call | topology | Exactly one write in the legacy API's log |
| US2-3 — third call | topology | The original result returned, still exactly one write |
| US2-4 — retry on another replica | topology | First call `mcp-a`, retry `mcp-b`, executes correctly |
| US2-5 — audit record | topology | `confirmed_by_user_id` and `legacy_reference_id` present |
| US3-1 — returns fast | topology | Handle returned in < 1s, measured client-side (SC-003) |
| US3-2 — polls to completion | topology | Reaches `completed`; final status equals the legacy API's |
| US3-3 — polls across replicas | topology | Each poll on a different replica returns the same state |
| US3-4 — fallback path | topology | A client that does not declare the tasks extension still gets an immediate handle and can poll via `get_billing_run_status` |
| US3-5 — cancel a live run | unit + topology | `tasks/cancel` drives the run to `CANCELED` and the task to `cancelled` |
| US3-6 — cancel a finished run | unit + topology | Acknowledged, status unchanged, not an error — reuses the run T108 just cancelled |
| US4-1 — no routing headers | topology | Rejected |
| US4-2 — mismatched `Mcp-Name` | topology | `-32020`, HTTP 400 |
| US4-3 — bad protocol version | topology | `-32022` with `data.supported` |
| US4-4 — `server/discover` | unit + topology | Versions, capabilities, identity |
| US4-5 — `tools/list` | unit + topology | `ttlMs`, `cacheScope`, same order twice |
| US4-6 — bad tokens | topology | Wrong audience, expired, and bad signature each rejected, at both services |
| US4-7 — token never forwarded | topology | The inbound token string appears nowhere in `GET /test/received-requests` (SC-005) |
| US4-8 — legacy 500 | topology | `isError` telling the model not to retry; audit row with `outcome=TOOL_ERROR` |
| US4-9 — legacy client | topology | A real LangChain4j `DefaultMcpClient` sending `initialize` gets a JSON-RPC error naming the supported versions |
| SC-006 — leak check | topology | Every error message produced by the whole suite is scanned for stack traces, SQL, and internal hostnames |
| FR-027 — traceparent | unit + topology | The value sent in `_meta` reaches the legacy API unchanged |
| FR-003 — serverInfo | unit | Every result carries `resultType` and `serverInfo` in `_meta` |

## Reading the code against the spec (SC-004)

The one place to look for each 2026-07-28 feature:

Everything the MCP Java SDK provides is imported; the table below is the list of things it
does not. Each row is a 2026-07-28 delta with an open upstream issue (`research.md` R-003).

| Feature | Where |
|---|---|
| Stateless requests, per-request `_meta` | `mcp-server/.../protocol/RequestEnvelope.java` (#1011) |
| Header-based routing and validation | `mcp-server/.../protocol/HeaderValidationFilter.java` |
| `server/discover` | `mcp-server/.../protocol/DiscoverHandler.java` (#1011) |
| Cacheable list results | `mcp-server/.../protocol/ToolsListHandler.java` (#1009) |
| Server-minted handles (cursors) | `mcp-server/.../protocol/CursorCodec.java` |
| Multi Round-Trip Requests | `mcp-server/.../tools/PostFeeAdjustmentTool.java` + `protocol/RequestStateCodec.java` |
| Tasks extension | `mcp-server/.../tasks/` (#1013) |
| `resultType` on every result | `mcp-server/.../protocol/ResultEnvelope.java` (#1011) |
| Dispatch on Micronaut, not the SDK's servlet transport | `mcp-server/.../protocol/McpController.java` (FR-029) |
| Protocol vs tool error split | `mcp-server/.../protocol/ErrorBoundary.java` |
| Token exchange, never forwarding | `mcp-server/.../security/TokenExchangeClient.java` |
| Audit and trace propagation | `mcp-server/.../audit/` |

## Troubleshooting

| Symptom | Cause |
|---|---|
| Every call is HTTP 400 with `-32020` | A hand-built request is missing `Mcp-Method`, or `Mcp-Name` disagrees with `params.name` |
| Every call is HTTP 401 | The token was minted for the wrong audience — `/dev/token` needs `"audience":"mcp-billing-server"` |
| `-32021` on `post_fee_adjustment` | The client did not declare `elicitation` in `clientCapabilities`, so the server may not send the confirmation |
| `start_billing_run` returns `complete`, not `task` | The client did not declare `io.modelcontextprotocol/tasks`. This is the documented fallback, not a fault |
| `topologyTest` fails to connect | `make mcp-up` has not run, or `MCP_HTTP_PORT` is taken |
| `topologyTest` seems to hang for a minute | The stack was started without `LEGACY_RUN_DURATION_MS`, so a run takes its real 30–90s (FR-024). Not a fault |
