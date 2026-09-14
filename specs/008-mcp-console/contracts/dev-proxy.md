# Contract: the development forwarder

The browser never addresses the MCP stack. `vite.config.ts` forwards, and this file is the contract
for that forwarding. Rationale and rejected alternatives are in [research.md R-001](../research.md).

## Targets

| Console path prefix | Target | Default | Environment variable |
|---|---|---|---|
| `/mcp-dev/proxy` | the nginx proxy (`mcp-proxy`) | `http://localhost:8877` | `MCP_HTTP_PORT` |
| `/mcp-dev/a` | `mcp-a` | `http://localhost:8881` | `MCP_REPLICA_A_PORT` |
| `/mcp-dev/b` | `mcp-b` | `http://localhost:8882` | `MCP_REPLICA_B_PORT` |
| `/mcp-dev/c` | `mcp-c` | `http://localhost:8883` | `MCP_REPLICA_C_PORT` |

Variable names and defaults are **the same ones `compose.mcp.yaml` and `compose.mcp.topology.yaml`
already read**. A stack started with `MCP_HTTP_PORT=9001 make mcp-up` needs the same variable
exported for `npm run dev`, and nothing else changes.

## Rewrite

The prefix is stripped; the remainder is passed through unchanged.

| Sent by the console | Arrives at the target as |
|---|---|
| `POST /mcp-dev/proxy/mcp` | `POST /mcp` |
| `POST /mcp-dev/a/mcp` | `POST /mcp` on `mcp-a` |
| `POST /mcp-dev/proxy/dev/token` | `POST /dev/token` on the issuer, via nginx |
| `GET /mcp-dev/proxy/lb-health` | `GET /lb-health` on nginx |
| `GET /mcp-dev/a/health/readiness` | `GET /health/readiness` on `mcp-a` |

Request headers and body are forwarded verbatim. The forwarder adds, removes, and rewrites nothing
else — if it did, FR-010's promise that the console shows what travelled would be false.

## What each target serves

Established by `deploy/mcp/nginx.conf`, not by this feature:

| Path | `proxy` | `a` / `b` / `c` |
|---|---|---|
| `/mcp` | yes, round-robin | yes, that replica |
| `/dev/*`, `/.well-known/*` | yes → token issuer | **no** |
| `/lb-health` | yes | no |
| `/health/readiness` | no | yes |

**Consequence**: every token is minted through `proxy`, whatever target the subsequent call uses.

## Failure shapes the console must handle

| Condition | What the browser sees | Console behaviour |
|---|---|---|
| Proxy target refuses the connection | `502`/`504` from the dev server, or a network error | The offline state: says so, names `make mcp-up` (FR-003) |
| A replica target refuses the connection | same | That replica marked unreachable with its reason, the rest of the console unaffected (FR-017) |
| Dev server not running at all | the page does not load | Out of scope — there is no console to show a message |

A connection refused by a target and an HTTP error from a target are different things, and the
console does not conflate them: the first is "not published", the second is the server speaking.

## Non-requirements

- No caching, retrying, or response rewriting in the forwarder. The console is a window; a forwarder
  that retried would show a call that did not happen the way it is displayed.
- No path is forwarded that is not under `/mcp-dev/`. The existing `/api` proxy to the application
  backend on 8080 is untouched.
- The forwarder exists only in `vite dev`. There is no equivalent in `vite build` output or in
  `nginx.conf` for the packaged frontend, which is most of how FR-001a is satisfied.
