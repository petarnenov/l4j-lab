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
| **F-006, after** | `started_from: 2030-01-01` → **HTTP 200**, `isError: false`, `structuredContent: {"runs": [], "total_match_count": 0, "truncated": false}`. US1-1 in full: the collection is present and empty, not absent. |
| **F-001** | **106 differences** between what the server declares and what this repository commits, from `ToolDeclarationContractTest` (T005): 71 in output schemas, 23 in input schemas, 10 in annotations, 2 in descriptions. Feature 008's live suite could see 18 + 18, because it compared only what it knew to look for; this compares everything. |

## Reproduce each finding, then close it

| Finding | Reproduce it | After |
|---|---|---|
| **F-006** | `search_billing_runs` with `started_from: 2030-01-01` — a date range with nothing in it | an empty result: `runs: []`, `total_match_count: 0`, no `isError`, HTTP 200 |
| **F-006** | the same as an advisor, filtered by an advisor they may not act for | an empty result, byte-for-byte identical to the one for an advisor that does not exist |
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

**Corrected at T046: none of the seven findings had a baseline entry.** This paragraph used to say
each of them did, and it was written from an assumption rather than from the file. The two records
track different things — `scripts/spec-drift/baseline.json` holds *path claims in specification
documents*, while these findings are about *server behaviour*, which that check does not look at. The
201 entries belong to features 001 through 007 naming classes their implementations no longer have.

So FR-020 resolves to an empty set, and that is reported rather than quietly skipped. What was
verified instead is that the mechanism works: a deliberately stale entry was added, `make check-specs`
**failed** with it and passed when it was removed. An obligation with nothing to do and an obligation
nobody checked look identical in a report; this one was checked.

The one thing that did change here went the other way — writing feature 007's reading-guide paths out
in full added **11 claims** the check had never seen, because the abbreviated `mcp-server/.../` form
is invisible to it. The baseline did not shrink; the coverage grew.

Then mark each finding closed where it was recorded, in
[`specs/008-mcp-console/findings.md`](../008-mcp-console/findings.md) and
[`specs/009-spec-drift-check/findings.md`](../009-spec-drift-check/findings.md), with what was done —
or leave it open, with why. None of the seven ends this feature undiscussed.

## After, confirmed by hand against the running stack on 2026-09-15 (T016)

Each row was run through the proxy with `make mcp-up-topology` up, and each is covered by a test that
now fails if it regresses.

| Scenario | Answer | Held by |
|---|---|---|
| US1-1: `search_billing_runs` with `started_from: 2030-01-01` | HTTP 200, `runs: []`, `total_match_count: 0`, no `isError` | `EmptyResultTopologyTest`, `ReadToolsTest`, `BillingRunSearchTest` |
| US1-2: any query at all | no 500 anywhere in the suites above | `UnexpectedFailureMapper` + `ToolErrorStatusFilterTest` |
| US1-4/5: an unentitled advisor and an unknown one | the same empty result, compared field by field | `EmptyResultTopologyTest`, `entitlement.live.test.ts` |
| US1-6: a cross-firm search | still a refusal, still says nothing about `firm-beta` | `EmptyResultTopologyTest` |
| US2-1: a retry built only from the corrected contract | the change is applied | `ConfirmationTopologyTest` |
| US2-2: an answer present but unreadable | `isError: true`, naming `content.confirmed` | `ConfirmationTopologyTest`, `FeeAdjustmentTest` |
| US2-3: a decline | `resultType: complete`, no `isError`, no `structuredContent` | `ConfirmationTopologyTest`, `FeeAdjustmentTest`, `confirmation.live.test.ts` |

**Two things found on the way, both recorded because they were the same mistake in a new place:**

- `jackson.serialization-inclusion: ALWAYS` was added to both services' `application.yml` as the fix
  for the empty collection, and **it does not cover this case**. The legacy API still wrote
  `{"totalCount":0}` with it in place; what actually stopped the 500 was the null-guard one layer up.
  The setting has been removed from both files and replaced with `@JsonInclude(ALWAYS)` on the two
  components that need it, where a test can see it. A setting that reads like a fix and is not one is
  worse than none.
- `compose.mcp.yaml` passed `LEGACY_RUN_DURATION_MS: ${LEGACY_RUN_DURATION_MS:-}` — an empty *string*,
  which is not the same as unset. Micronaut resolved the property to nothing, `RunSimulator`'s `long`
  had nothing to bind, and **every billing-run route answered HTTP 500**. `make mcp-verify` sets the
  variable and was green; `make mcp-up` does not, and served a broken stack. The default is now
  written out (`:--1`). This is F-006's shape exactly — a failure that looked like the code and was
  the configuration — found only because the live suite was run against a stack brought up by hand.

## F-001 closed, and how it was proved (T034, SC-003/SC-004)

| | |
|---|---|
| Before | **106 differences**: 71 output-schema, 23 input-schema, 10 annotation, 2 description |
| After | `ToolDeclarationContractTest` passes; the committed contracts are written by `./gradlew :mcp-server:generateToolContracts` |

**The propagation was tested rather than assumed.** `get_run_failures.limit`'s maximum was changed
from 50 to 49 in `ToolArgumentConstraints`, regenerated, and the committed file changed to 49 — a
single source, seen to propagate.

**And the test that would have missed it.** With the contracts generated from the declarations, the
field-by-field comparison **stayed green** on that change: both sides moved together, which is exactly
the tautology a generated oracle invites. What caught it was
`theDeclarationsCarryTheKeywordsTheContractsAlwaysClaimed`, which names `maximum: 50` outright and
reads only what the server serves. A generated contract needs a check that does not come from the
generator, or it proves nothing. Both were reverted after the demonstration.

## SC-002, and what is honestly unverified (T052)

SC-002 asks that someone who has not read the server implement the confirmation retry from
`specs/007-mcp-billing-server/contracts/mcp-protocol.md` alone and apply a fee change on the first
attempt. **That has not been done, and it is recorded as unverified rather than marked done.**

The whole of finding F-005 is that the contract read convincingly and was wrong. The people who could
run this check — the ones who wrote the correction — are exactly the ones whose reading proves
nothing, because they know the answer. A self-administered comprehension test is not a test.

What *was* done, and what it is worth:

- `ConfirmationTopologyTest.aRetryBuiltOnlyFromTheContractAppliesTheChange` builds the retry from the
  corrected contract's example and nothing else, against the running stack, and the change is applied.
  It proves the example is correct. It does not prove the prose around it is clear, which is the half
  that failed last time.
- The flat shape the old contract implied is now answered with a diagnostic naming
  `content.confirmed`, so a client that reads the document wrongly finds out immediately instead of
  believing it declined. This is the safety net under SC-002 rather than a substitute for it.

**What changed after this was first written.** The contract had no example of the *request* at all —
it showed the response to the first call, then a retry whose `arguments` were an ellipsis reading
*"the same arguments the first call sent"*. An implementer had to assemble both calls from the tool's
`inputSchema` and hope. Both are now written out in full, with real values, and marked
`<!-- executable: … -->`. `ConfirmationTopologyTest` **reads the markdown**, lifts the JSON out of
those two fences, substitutes only `<OPERATION_ID>` and `<REQUEST_STATE>`, and sends it at the
running server.

So the document is executed rather than paraphrased, and an edit to it that stops working fails the
build. That closes the half of F-005 that was mechanical — the example is now known to be correct
rather than believed to be. It does not close SC-002, which is about whether the **prose** is clear
to someone reading it cold, and no test can answer that.

**To close it**: hand someone `contracts/mcp-protocol.md`, nothing else, and ask them to apply a fee
change. One attempt. Record the result here.

