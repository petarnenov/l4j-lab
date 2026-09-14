# Contracts: MCP Billing Server

Constitution Principle III: these are committed before any handler is written, and the
handlers are held to them.

| File | Contract |
|---|---|
| `mcp-protocol.md` | The MCP 2026-07-28 surface: methods, headers, `_meta`, errors, caching |
| `tools/*.json` | One MCP `Tool` definition per tool — `inputSchema`, `outputSchema`, `annotations` |
| `legacy-billing-api.md` | The legacy REST API the MCP server calls |
| `token-issuer.md` | The development token issuer |

The five files under `tools/` are loaded verbatim at runtime: `tools/list` serves them and
the tool handlers validate arguments against `inputSchema` and `structuredContent` against
`outputSchema`. There is no second, generated copy.

## Deterministic tool order

`tools/list` returns the five tools in this order, always:

1. `search_billing_runs`
2. `get_billing_run_status`
3. `get_run_failures`
4. `post_fee_adjustment`
5. `start_billing_run`

Reads before writes, and within reads the order an agent would naturally walk: find a run,
open it, then open its failures. The order is fixed in code, not derived from a directory
listing, because a directory listing is alphabetical on one filesystem and arbitrary on
another.

## Why each annotation is what it is (FR-011 says "honestly")

| Tool | readOnly | destructive | idempotent | openWorld | Reasoning |
|---|---|---|---|---|---|
| `search_billing_runs` | true | false | true | true | Reads only. Repeating it changes nothing. Data lives in a system beyond this server. |
| `get_billing_run_status` | true | false | true | true | Same. |
| `get_run_failures` | true | false | true | true | Same. |
| `post_fee_adjustment` | false | true | true | true | Writes, and changes an existing account's fee — that is a destructive update. Idempotent **because** of `operation_id`, not in spite of the write; the hint would be a lie without the idempotency record. |
| `start_billing_run` | false | false | false | true | Writes, but only adds a run; it destroys nothing. Not idempotent: two calls start two runs. |

`openWorldHint` is `true` on all five: every one of them reaches the legacy billing system,
whose contents this server does not control.
