# Contracts: MCP console

**Feature**: `008-mcp-console` | **Phase**: 1

The console declares no MCP tool and no A2A message. It is a **consumer** of feature 007's
contracts, which remain the authority:

- `specs/007-mcp-billing-server/contracts/mcp-protocol.md` — the wire surface, headers, `_meta`,
  result shapes, and the error mapping table
- `specs/007-mcp-billing-server/contracts/tools/*.json` — the five tool declarations
- `specs/007-mcp-billing-server/contracts/token-issuer.md` — the development token issuer

**Where this directory disagrees with those files, those files win.** Nothing here restates them;
each file below cites what it depends on.

| File | Contract |
|---|---|
| `dev-proxy.md` | The Vite dev-server forwarder: paths, targets, rewrites, and failure shapes |
| `mcp-client.md` | What the console puts on the wire for each method it drives |
| `console-surface.md` | The user-facing contract: regions, controls, and what each must show |
| `malformed-requests.md` | The fixed catalogue of three deliberately wrong requests |

## The one thing declared twice, and the leash on it

`frontend/src/mcp/wire.ts` holds TypeScript types for the JSON-RPC envelope, because feature 007
publishes no machine-readable description of its HTTP surface — deliberately, as its own spec
records. This is the exception in the plan's Complexity Tracking. It is bounded two ways:

1. `wire.ts` describes only the **envelope**. A tool's arguments and results are never typed there;
   they stay derived from the served `inputSchema` / `outputSchema`.
2. The live suite asserts each declared envelope field against what the running server actually
   sends, so a field that moves fails a test instead of rendering blank.

Tool declarations are **not** declared twice: the deterministic suite serves them from the committed
`007/contracts/tools/*.json` themselves.
