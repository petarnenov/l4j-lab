# Contract: where a tool declaration comes from

The constitution's rule, applied to the thing that broke it:

> Contract definitions MUST be shared between backend and frontend from a single source; the same
> shape MUST NOT be declared twice by hand.

## One source

**The Java declaration is the source.** `@Tool` and `@ToolArg` are what the server reads and what it
serves; the committed JSON under `specs/007-mcp-billing-server/contracts/tools/` is **generated** from
it.

```
@Tool / @ToolArg  ──generated──>  what tools/list serves
        │
        └─────────generated──>  specs/007-…/contracts/tools/*.json
```

Today both sides are hand-written and neither knows about the other. That is the arrangement
`specs/007-mcp-billing-server/contracts/README.md` describes as *"loaded verbatim at runtime"*, which was never true.

## What a declaration must carry

Everything the contract carried before it was reduced to what a Java type happens to express:

| Keyword | Where it goes |
|---|---|
| `enum` | on the argument that has a fixed set of values |
| `format` | on a date argument |
| `minimum`, `maximum` | on a bounded number |
| `minLength`, `maxLength` | on a constrained string |
| `default` | where the tool has one |
| `required` on outputs | as the contract declares, not relaxed |
| `integer` | as `integer`, not widened to `number` |

**A declared constraint is a validated constraint.** Where restoring one exposes an argument the
server does not check, the check follows. A declared enumeration the server ignores replaces a lie
about what is declared with a lie about what is enforced, which is not progress.

## What proves it

A test that fetches what the server declares and compares it to the committed files, field by field,
failing on any difference.

**Feature 007's build file already says this test exists.** It does not — the contracts are copied
into test resources and nothing reads them. That absence is why eighteen keywords, two descriptions
and one output schema could drift without anything noticing, and writing the test is the first thing
this feature does.

## `start_billing_run`, specifically

A tool's `outputSchema` describes **what calling the tool returns**: for this one, the handle —
`run_id`, `task_id`, `poll_with`. It does not describe the completed run, which arrives later through
`tasks/get` and belongs in the protocol contract where the tasks extension is described.

A client validating a call's result against a schema for something it will only receive later has
been handed the wrong instrument.
