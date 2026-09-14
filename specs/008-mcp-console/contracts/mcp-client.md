# Contract: what the console puts on the wire

The authority for every shape here is
`specs/007-mcp-billing-server/contracts/mcp-protocol.md`. This file records only what **this client**
chooses within it: which methods it drives, what it declares about itself, and what it does with each
result shape.

## Client identity and capabilities

Every request carries, in `params._meta`:

```json
{
  "io.modelcontextprotocol/protocolVersion": "2026-07-28",
  "io.modelcontextprotocol/clientInfo": { "name": "l4j-mcp-console", "version": "<app version>" },
  "io.modelcontextprotocol/clientCapabilities": {
    "elicitation": {},
    "extensions": { "io.modelcontextprotocol/tasks": {} }
  }
}
```

Both capabilities are declared, and both are declared because the console actually implements them:
`elicitation` because it drives the confirmation round trip (without it `post_fee_adjustment` is
answered `-32021`), and the tasks extension because it polls `tasks/get`. Declaring a capability a
client cannot honour is the one thing this contract forbids outright.

`clientInfo.version` is the application version, so a server-side audit row names the console rather
than an anonymous client.

## Headers

Built per request from the body, never from a stored template, so the mirroring the server checks is
a property of the code rather than of a copy-paste:

| Header | Value |
|---|---|
| `Authorization` | `Bearer <token for the chosen principal>` |
| `Content-Type` | `application/json` |
| `Accept` | `application/json, text/event-stream` |
| `MCP-Protocol-Version` | the same string as `params._meta[…/protocolVersion]` |
| `Mcp-Method` | the same string as `method` |
| `Mcp-Name` | on `tools/call` only, the same string as `params.name` |

The only code path that may break this mirroring is the malformed catalogue, which breaks it
deliberately and is marked as such.

## Methods driven

| Method | When | What the console does with it |
|---|---|---|
| `server/discover` | on opening, per reachable target | Confirms the stack answers; shows `supportedVersions`, capabilities, `instructions`, and `serverInfo` |
| `tools/list` | after discover | The five tools, in the server's order, with all four annotations; honours `ttlMs` / `cacheScope` |
| `tools/call` | on demand | The main flow; branches on `resultType` |
| `tasks/get` | while a handle is non-terminal | Polls at the server's `pollIntervalMs` |
| `tasks/cancel` | on demand | Cancels a handle; a terminal task is acknowledged, not an error |

`initialize` is never sent: the server answers it with an error naming its supported versions, by
design. The console is a 2026-07-28 client and says so in its headers.

## Branching on `resultType`

| `resultType` | Console behaviour |
|---|---|
| `complete`, `isError` false | Shows `structuredContent` as the result and `content` as the server's own rendering |
| `complete`, `isError: true` | Shows the server's sentence, marked a **tool** failure, alongside the raw exchange |
| `input_required` | Shows the elicitation's `message` verbatim, states nothing has been applied, offers confirm/decline, and retries in the shape below |
| `task` | Shows `taskId` and `status` at once, then polls |

### The confirmation retry, in full

Stated here in full because 007's own contract does not, and a client built from that contract alone
sends a confirmation the server reads as a **refusal** — silently, with a response that looks like
success (finding F-005). This console hit exactly that on its first live run. Repeating the omission
in this file would be indefensible.

```json
{
  "name": "post_fee_adjustment",
  "arguments": { "…the same arguments the first call sent…" },
  "inputResponses": {
    "confirm_adjustment": { "action": "accept", "content": { "confirmed": true } }
  },
  "requestState": "<echoed byte-for-byte>"
}
```

Three things carry weight, and each is load-bearing for a different reason:

- **`content` is not optional.** The answer goes inside the MCP `ElicitResult` envelope
  (`{ action, content }`), with the requested schema's fields inside `content`. The server reads
  `inputResponses.confirm_adjustment.content.confirmed` and treats anything else as a refusal. A flat
  `{ "confirmed": true }` — which is what 007's contract and the elicitation's own
  `requestedSchema` read as if they meant — is understood as "no".
- **The arguments are repeated.** The sealed `requestState` carries a digest of them, verified on
  the retry.
- **The JSON-RPC `id` differs** from the call it retries. 007's contract requires it, and the
  exchange log shows both ids so the difference is visible rather than asserted.

`action` is `"accept"` or `"decline"`. The server reads only `content.confirmed`, but sending the
action is what the protocol's own shape says, and a console demonstrating the protocol should send
the protocol.

**A declined confirmation comes back with `isError: true`**, though 007's contract says a declined
change is answered with a result and "not an error". The console shows the outcome for what it is —
nothing was applied — while the exchange log classifies it as the server sent it, and names the
divergence. Also F-005.

A JSON-RPC `error` at any point is a **protocol** failure and is shown with its code. The two are
never rendered by the same component, because not confusing them is the thing the console exists to
demonstrate.

## JSON-RPC ids

Monotonic within the tab, as strings. The confirmation retry uses a **new** id, which 007's contract
requires. Ids are shown in the exchange view so the retry's difference from the first call is
visible rather than asserted.

## What the console never does

- Construct, decode, modify, or display-as-meaningful an opaque value: `cursor`, `requestState`, and
  `taskId` are echoed exactly as received. The tool descriptions say "do not construct or modify it",
  and a console that did so while demonstrating the protocol would be teaching the opposite.
- Retry a call automatically. Every request on the screen is one a person asked for.
- Send a call it can already tell is incomplete (FR-009).
- Render a bearer token in full ([research.md R-009](../research.md)).
