# Quickstart: Close the findings against the MCP billing server

**Feature**: `010-close-mcp-findings`

Every row here is a finding you can reproduce before the work and re-run after it. The decisions are
in [research.md](./research.md); the shapes in [data-model.md](./data-model.md); what must hold in
[contracts/](./contracts/).

## Prerequisites

```bash
make mcp-up        # or make mcp-up-topology for the replica-addressed rows
```

Everything below goes through the proxy on `${MCP_HTTP_PORT:-8877}`. A token for any seeded principal:

```bash
export MCP=http://localhost:${MCP_HTTP_PORT:-8877}
TOKEN=$(curl -s -X POST $MCP/dev/token -H 'Content-Type: application/json' \
  -d '{"principal":"admin-alpha","audience":"mcp-billing-server"}' | jq -r .token)
```

## Starting state, recorded 2026-09-14 (T001)

Measured against the running stack before any change, so every later claim is measured against this
rather than against memory.

| Finding | Before |
|---|---|
| **F-006** | `search_billing_runs` with `started_from: 2030-01-01` → **HTTP 500**, `{"code":-32603,"message":"message must not be empty"}` |
| **F-004** | `start_billing_run`'s served `outputSchema` declares `run_id`, `task_id`, `poll_with`, `next_step_hint` — **the handle**, which research R-005 concluded is correct. The committed contract declares the completed run. **The server is right and the contract is wrong**, so this closes when the contract is generated (T032). |
| **F-006, after** | `started_from: 2030-01-01` → **HTTP 200**, `isError: false`, `total_match_count: 0`. The crash is gone. **`runs` is still omitted from an empty result**: `jackson.serialization-inclusion: ALWAYS` fixed it at the legacy API but not at the MCP server, whose `structuredContent` the SDK serialises itself. US1-1 asks for an empty collection, not merely a count — that half is unfinished. |
| **F-001** | **106 differences** between what the server declares and what this repository commits, from `ToolDeclarationContractTest` (T005): 71 in output schemas, 23 in input schemas, 10 in annotations, 2 in descriptions. Feature 008's live suite could see 18 + 18, because it compared only what it knew to look for; this compares everything. |

## Reproduce each finding, then close it

| Finding | Reproduce it | After |
|---|---|---|
| **F-006** | `search_billing_runs` with `started_from: 2030-01-01` — a date range with nothing in it | an empty result: `runs: []`, `total_match_count: 0`, no `isError`, HTTP 200 |
| **F-006** | the same as an advisor, filtered by an advisor they may not act for | an entitlement refusal, identical in shape to one for a record that does not exist |
| **F-005** | send a confirmation retry built from the published contract — `inputResponses.<key>.confirmed`, flat | the contract now describes the envelope; a client following it applies the change |
| **F-005** | send an answer the server cannot interpret at all | an error saying the answer could not be read, not a silent decline |
| **F-005** | decline a confirmation | a result reporting nothing was applied, without `isError` |
| **F-001** | `tools/list`, and compare against `specs/007-mcp-billing-server/contracts/tools/` | every enumeration, format, bound, default and required output field present |
| **F-004** | read `start_billing_run`'s declared `outputSchema` | it describes the handle the call returns |
| **F-002** | apply a fee adjustment | the result carries `previous_fee_bps` |
| **F-003** | call through the proxy and read `_meta.serverInfo` | it names the replica that answered |
| **G-006** | open every file the quickstart's reading-guide table names | each exists, and holds what it is listed against |

**The first row is the one to try first.** It is an ordinary query — a date range that happens to
match nothing — and today it returns HTTP 500.

## The test that should have existed

```bash
./gradlew :mcp-server:test          # includes the new declaration contract test
```

It fetches what the server declares and compares it field by field to the committed files. Feature
007's build file says such a test exists; it did not, which is how eighteen keywords drifted unseen.

**Prove it can fail**: change one keyword in a committed contract, run the test, watch it fail, put it
back. A check never seen to fail is not evidence.

## Everything feature 007 already promised

```bash
make mcp-verify                     # 007's acceptance scenarios, start to finish
make test-console                   # feature 008's live suite, which pinned several of these
```

Both must pass. The console's suite pins some of today's behaviour deliberately — the decline that
arrives flagged as an error, the schema keywords that do not — so a row of it changes with each
finding closed, and that change is the evidence.

## Closing the record

```bash
make check-specs                    # feature 009's drift check
```

Each closed finding has a baseline entry in `scripts/spec-drift/baseline.json`. **Remove it** rather
than leaving it: an entry that no longer matches real drift is reported as stale and fails the check,
which is how that baseline is designed to shrink.

Then mark each finding closed where it was recorded, in
[`specs/008-mcp-console/findings.md`](../008-mcp-console/findings.md) and
[`specs/009-spec-drift-check/findings.md`](../009-spec-drift-check/findings.md), with what was done —
or leave it open, with why. None of the seven ends this feature undiscussed.
