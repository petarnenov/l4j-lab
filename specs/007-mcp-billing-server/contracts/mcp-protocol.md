# Contract: MCP surface, revision 2026-07-28

**This file is the single source for the protocol surface, including the error mapping table
below.** `spec.md` FR-010 states the rule; `research.md` R-010 records why it is shaped this
way; neither restates the table. If they disagree with this file, this file wins.

**Implementation note.** Everything here that the MCP Java SDK v2.0.1 already provides is
imported from it — `McpSchema.Tool`, `ToolAnnotations`, `CallToolResult`, the JSON-RPC records,
`McpError`, `ElicitFormRequest`/`ElicitResult`. The behaviours below that the SDK does not have
are the seven deltas in `research.md` R-003, each with an open upstream issue.

The MCP server exposes **one** HTTP endpoint: `POST /mcp`. No other method on that path is
served (`GET` and `DELETE` return `405`, per the revision's backward-compatibility rules).

## Methods

| Method | Supported | Notes |
|---|---|---|
| `server/discover` | Yes — required by the spec | |
| `tools/list` | Yes | Cacheable; deterministic order |
| `tools/call` | Yes | Five tools |
| `tasks/get` | Yes | Tasks extension |
| `tasks/cancel` | Yes | Accepted from any client holding the `taskId`, extension declared or not (see below). Cooperative, and it acts: it asks the legacy API to cancel the run, driving it to `CANCELED` and the task to `cancelled`. A run already terminal keeps its status and the request is still acknowledged (FR-031) |
| `tasks/update` | Yes | Accepted; no tool currently moves a task to `input_required` |
| `initialize` | **No** | Answered with a JSON-RPC error naming the supported versions, so a legacy client gets a diagnostic rather than silence |
| `subscriptions/listen` | No | Out of scope; `-32601` + HTTP 404 |
| `resources/*`, `prompts/*` | No | Out of scope; `-32601` + HTTP 404 |

## Required request headers

| Header | Mirrors | Required on |
|---|---|---|
| `MCP-Protocol-Version` | `params._meta["io.modelcontextprotocol/protocolVersion"]` | every request |
| `Mcp-Method` | `method` | every request |
| `Mcp-Name` | `params.name` | `tools/call` |
| `Content-Type: application/json` | — | every request |
| `Accept: application/json, text/event-stream` | — | every request |

`Mcp-Name` values may arrive in the sentinel form `=?base64?{base64}?=`; the server decodes
before comparing. Header **names** compare case-insensitively; header **values** compare
case-sensitively.

Any missing required header, or any header that disagrees with the body, is HTTP `400` with
JSON-RPC `-32020`.

## Required `_meta` on every request

```json
{
  "io.modelcontextprotocol/protocolVersion": "2026-07-28",
  "io.modelcontextprotocol/clientInfo": { "name": "…", "version": "…" },
  "io.modelcontextprotocol/clientCapabilities": {
    "elicitation": {},
    "extensions": { "io.modelcontextprotocol/tasks": {} }
  }
}
```

`protocolVersion` and `clientCapabilities` are required; a request missing either is
`-32602` with HTTP `400`. `traceparent` is read when present and propagated (FR-027).

Every result carries:

```json
{ "_meta": { "io.modelcontextprotocol/serverInfo": { "name": "mcp-billing-server", "version": "0.1.0" } } }
```

## `server/discover`

```json
{
  "resultType": "complete",
  "supportedVersions": ["2026-07-28"],
  "capabilities": {
    "tools": { "listChanged": false },
    "extensions": { "io.modelcontextprotocol/tasks": {} }
  },
  "instructions": "Billing runs and fee adjustments for wealth-management firms. Search runs before opening one. Fee adjustments require confirmation and a stable operation_id.",
  "ttlMs": 3600000,
  "cacheScope": "public",
  "_meta": { "io.modelcontextprotocol/serverInfo": { "name": "mcp-billing-server", "version": "0.1.0" } }
}
```

## `tools/list`

Returns all five tools in one page — there is no `nextCursor`, because five tools do not
need paging and a cursor the server never issues is a cursor it cannot get wrong.

```json
{ "resultType": "complete", "tools": [ … ], "ttlMs": 300000, "cacheScope": "public" }
```

`cacheScope` is `"public"`: the tool list is identical for every caller. It does **not**
vary by role — entitlements filter *results*, never the *tool set*. Were the set ever made
role-dependent, this would have to become `"private"`.

## `tools/call`

Three result shapes:

- **`complete`** — `content` (a text rendering) plus `structuredContent` (validated against
  the tool's `outputSchema`), `isError: false`.
- **`complete` with `isError: true`** — a tool execution failure. `content` carries one
  short, actionable sentence. No `structuredContent`.
- **`input_required`** — `post_fee_adjustment`'s first call only. Carries `inputRequests`
  with one `elicitation/create` and an opaque `requestState`.
- **`task`** — `start_billing_run` only, and only when the client declared the tasks
  extension.

### `post_fee_adjustment` first call

```json
{
  "resultType": "input_required",
  "inputRequests": {
    "confirm_adjustment": {
      "method": "elicitation/create",
      "params": {
        "mode": "form",
        "message": "Confirm: change account acc-9001 fee by +15 bps (0.90% → 1.05%), effective 2026-10-01.",
        "requestedSchema": {
          "type": "object",
          "properties": { "confirmed": { "type": "boolean" } },
          "required": ["confirmed"]
        }
      }
    }
  },
  "requestState": "<opaque>"
}
```

`requestState` is an AEAD-sealed payload holding the principal's user id, the operation id,
the request digest, and a short expiry. It is verified on the retry and rejected if the
principal differs, it has expired, or the digest does not match the retried arguments.

The retry carries `params.inputResponses.confirm_adjustment` and echoes
`params.requestState`, with a **different** JSON-RPC `id`. A `confirmed: false` response is
answered with a tool result saying the change was not applied — not an error.

### `start_billing_run` and the Tasks extension

Client declared `io.modelcontextprotocol/tasks`:

```json
{
  "resultType": "task",
  "taskId": "tsk_…",
  "status": "working",
  "statusMessage": "DATA_COLLECTION, 0/240 accounts",
  "createdAt": "2026-09-13T10:00:00Z",
  "lastUpdatedAt": "2026-09-13T10:00:00Z",
  "ttlMs": 900000,
  "pollIntervalMs": 2000
}
```

`tasks/get` returns the same shape refreshed, plus `result` on `completed` (the
`start_billing_run` `outputSchema` payload) or `error` on `failed`.

**The SDK gap, and the client fallback — two different things (FR-021).**

*The SDK gap*: no Java MCP SDK implements this extension (SEP-2663 / java-sdk#1013 is open), so
the server implements the wire shape directly on top of the SDK. This is recorded in
`research.md` R-003 and in the server's README, with the issue that would retire it.

*The client fallback*: separately, a **client** that does not declare
`io.modelcontextprotocol/tasks` must never be sent a `CreateTaskResult`. Such a client still
gets an immediate handle — an ordinary `complete` result whose `structuredContent` carries
`task_id`, `run_id`, and `poll_with: "get_billing_run_status"` — and polls
`get_billing_run_status` with that `run_id`. Same handle-and-poll shape, core protocol only.

Both are covered by the acceptance tests. They are named apart here because an earlier draft
of this file conflated them under one heading.

**A fallback client can still cancel.** `tasks/cancel` accepts any `taskId` the caller legitimately
holds, whether or not that caller declared `io.modelcontextprotocol/tasks`. The extension governs
what the server may *return* — never send a `CreateTaskResult` to a client that did not opt in —
not what it may *accept*. A fallback client was handed a `task_id` in `structuredContent`, so it
demonstrably has one, and refusing its cancellation would leave a run it started with no way to
stop it: there is no sixth tool to fall back to (FR-022). The principal check in FR-031 still
applies, and it is the check that matters.

## Error mapping

| Condition | JSON-RPC | HTTP | Kind |
|---|---|---|---|
| Header missing or mismatched | `-32020` | 400 | protocol |
| Client capability missing for what the server must send | `-32021` | 400 | protocol |
| Unsupported protocol version | `-32022` (`data.supported`, `data.requested`) | 400 | protocol |
| Required `_meta` field missing | `-32602` | 400 | protocol |
| Unknown method | `-32601` | 404 | protocol |
| Unknown tool name | `-32602` | 200 | protocol |
| Malformed JSON | `-32700` | 400 | protocol |
| Invalid/absent bearer token | — | 401 | transport |
| Legacy 403 | — | 200 | tool: `isError`, "no access" |
| Legacy 404 | — | 200 | tool: `isError` |
| Legacy 5xx / unreachable | — | 200 | tool: `isError`, "do not retry" |
| Arguments fail `inputSchema` | — | 200 | tool: `isError`, names the field |
| Cursor / requestState / taskId invalid or expired | — | 200 | tool: `isError` |

Tool error messages are built by one code path with a fixed vocabulary. No exception
message, SQL fragment, or hostname reaches a caller.
