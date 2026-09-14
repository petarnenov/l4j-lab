# Contracts: MCP Billing Server

Constitution Principle III: these are committed before any handler is written, and the
handlers are held to them.

| File | Contract |
|---|---|
| `mcp-protocol.md` | The MCP 2026-07-28 surface: methods, headers, `_meta`, errors, caching |
| `tools/*.json` | One MCP `Tool` definition per tool — `inputSchema`, `outputSchema`, `annotations` |
| `legacy-billing-api.md` | The legacy REST API the MCP server calls |
| `token-issuer.md` | The development token issuer |

**The five files under `tools/` are generated, not loaded.** Corrected by feature 010 (finding
F-001); this paragraph previously claimed they were *"loaded verbatim at runtime"*. They never were —
nothing read them at runtime, and nothing read them at build time either.

What is true:

- The **Java annotations are the single source**. `@Tool` and `@ToolArg` on the five tool methods
  carry the name, title, description and annotations; `dev.l4jlab.mcp.tools.ToolArgumentConstraints`
  carries the argument keywords the Java signature cannot express — enumerations, date formats,
  bounds, defaults, and `integer` where the generator would emit `number`.
- **These files are written from the running server** by
  `./gradlew :mcp-server:generateToolContracts`. Edit them and the next regeneration overwrites you;
  edit the Java and regenerate.
- **`ToolDeclarationContractTest` compares the two** field by field and fails the build on any
  difference, which is what this repository's build file has promised since feature 007 and did not
  do. It also asserts the keywords by name, so a generator that quietly dropped one could not take
  both sides down with it.
- Arguments **are** validated against the declared constraints, by `McpRequestGate`, before the tool
  runs. `structuredContent` is validated against `outputSchema` by the SDK.

The two were maintained by hand and drifted to **106 differences** before anything compared them. One
source, one generator, one comparison.

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
