# Findings: what closing the seven turned up

**Feature**: `010-close-mcp-findings`

Recorded as feature 008 and 009 recorded theirs: what was found, with a reproduction, whether it was
acted on, and why.

---

## H-001: three of the seven were recorded in the wrong direction

Each in the direction of being **smaller** than it is. Phase 0 read the code rather than the findings,
and that is the only reason it showed.

| Finding, as recorded | What it actually is |
|---|---|
| F-006: *a cross-advisor search returns HTTP 500* | **Any search whose result set is empty** returns 500. The advisor filter was one way to get an empty page; a date range with nothing in it is another, and it is an ordinary query. |
| F-001: *`tools/list` does not serve the contracts verbatim* | The contracts are **never read at runtime**. Declarations are generated from Java annotations and the JSON is a second hand-written copy — a Principle III violation, not a serialiser losing keywords. |
| F-004: *`start_billing_run` declares the wrong output shape* | The **server is right**: a tool's output schema describes what calling it returns, which is the handle. The committed contract is the wrong one. |

Recording a finding is not the same as understanding it, and a finding recorded from the outside
describes a symptom. All three were written by clients that could see the answer but not the cause.

---

## H-002: F-001 is 106 differences, not 18

The contract test written by this feature (T004) compares what the server declares against what the
repository commits, field by field:

| | |
|---|---|
| Output schemas | 71 |
| Input schemas | 23 |
| Annotations | 10 |
| Descriptions | 2 |
| **Total** | **106** |

Feature 008's live suite reported eighteen keywords and eighteen relaxed output fields because it
compared **what it knew to look for**. A test that compares everything finds six times as much. The
difference between the two numbers is the difference between a check written from a hypothesis and one
written from a definition.

---

## H-003: three modules' tests had never run in CI — and the first half of this finding was wrong

**Recorded, then corrected within the hour.** Both halves are kept, because the correction is the more
useful of the two.

### What was claimed, and was false

That four of feature 007's tests fail on a pristine `main`. They do not. `:mcp-server:test` was green
at the commit before this feature, verified by running it in a detached worktree at that commit.

**The failures were mine.** `UnexpectedFailureMapper` — added by this feature for FR-002 — declared
`canMap` true for every `RuntimeException` except `ToolFailure`. But `McpError extends
RuntimeException`, and the SDK raises it for things that are not failures at all: an elicitation the
tool is asking for, a task that does not exist, a request state that did not validate. The mapper
intercepted fifteen carefully written sentences and replaced them all with one generic one.

**How the wrong conclusion survived a check.** The check was `git stash`, and the mapper was a new
**untracked** file, which `git stash` leaves in place. The tests failed identically before and after,
so they read as pre-existing. `git stash -u`, or a worktree at the parent commit, would have said
otherwise in ten seconds.

The lesson is not about git. It is that **"I verified it" is only as good as what the verification
actually removed** — the same shape as everything else in this file, arriving from the inside this
time.

*A catch-all at a boundary catches the control flow too.* `canMap` now excludes `McpError`, and says
why in place.

### What was claimed, and is true

**No CI workflow runs any of the three modules.** `backend.yml` runs `:backend:check`; `contract.yml`
runs the frontend's API checks; nothing names `mcp-server`, `legacy-billing-api` or `token-issuer`.
Feature 007's entire deterministic suite — the one that was green, and that this feature briefly broke
without anything noticing — had never been executed by continuous integration.

**Fixed** by `.github/workflows/mcp.yml`. Had it existed, the regression above would have been caught
by a machine instead of by a developer looking for something else.

**This is still the third instance of one shape**, and the correction does not soften it:

| | What was silently unexamined | How it looked |
|---|---|---|
| 009 G-005 | six features whose documents were held to nothing | a status field said `Draft` |
| 009 G-007 | the drift check itself | green, because nothing invoked it |
| **010 H-003** | three modules' entire test suites | green, because nothing invoked them |

A check that is not run and a check that finds nothing are indistinguishable from outside.

---

## H-004: the empty page was three defects, and only one was in the finding

```
legacy API  →  {"totalCount":0}          items omitted from an empty page
mcp-server  →  page.items().stream()     dereferenced without a guard
boundary    →  message ""                an unexpected exception lost its message
caller      →  HTTP 500 "message must not be empty"
```

Each was fixed at its own level rather than the cheapest one. The serialiser alone leaves the server
one null from the same crash from any source; the guard alone leaves the system of record violating its
own shape; the boundary alone leaves the crash and makes it quieter, which is worse.

The third is the one worth having anyway: an unexpected exception now reaches a caller as a sentence
they can act on, with the detail in the log. It was one bad exception away from leaking a stack trace
instead of an empty string.

---

## H-005: two green suites that depended on state nobody could see

Found while running feature 007's own suites to check this feature's work. Neither is one of the
seven findings; both are the same shape as F-006, which is why they are recorded rather than quietly
patched.

**An empty string is not an unset variable.** `compose.mcp.yaml` passed
`LEGACY_RUN_DURATION_MS: ${LEGACY_RUN_DURATION_MS:-}`, whose comment read "unset means FR-024's real
30–90s". It does not mean that. Compose substituted an empty *string*, Micronaut resolved the
property to nothing, `RunSimulator`'s `long` parameter had nothing to bind, and **every billing-run
route answered HTTP 500** — a dependency-injection failure escaping as a server error, arriving at
the MCP server as an unusable page.

`make mcp-verify` sets the variable and was green. `make mcp-up` does not, and served a stack where
the main tool did not work. The two commands disagreed and nothing said so. Fixed by writing the
default out: `${LEGACY_RUN_DURATION_MS:--1}`.

**A suite that is green once.** `make mcp-verify` ends with `down`, not `down -v`, so the volume
outlives the run. Every pass of the topology suite starts billing runs, all under `adv-101`, and they
sort ahead of the seeded rows:

| | seeded | created by test runs |
|---|---|---|
| `adv-101` | 13 | 88 |
| `adv-102` | 13 | 0 |
| `adv-201` | 4 | 0 |

`EntitlementTopologyTest.aFirmAdminAndOpsBothSeeTheWholeFirm` asserts that a firm administrator's
**first page** holds both advisors. After enough runs it does not, and
`CrossReplicaTest.aCursorFromOneReplicaContinuesTheSameSearchOnAnother` drifts for the same reason.
Both pass on a fresh volume and fail on a used one, so the suite's verdict depends on how many times
it has been run before — which is not a property of the code it tests.

Left as feature 007's to fix, and recorded here rather than repaired in passing: making a red test
green by rewriting its assertion is the move this whole feature exists to argue against, and the
right repair is to give the suite its own data or reset the volume, which is a change to how 007 is
verified rather than a correction of a claim.

**Run `make mcp-reset` before `make mcp-verify` if the two rows above are the failures you see.**

**Confirmed at T049.** The 120 accumulated runs were deleted — only rows created by test runs, dated
after the seeded fixtures, leaving 13/13/4 exactly as seeded — and the whole topology suite passed:
**43 of 43**. So the two failures were the accumulated data and nothing this feature changed, which is
what SC-008 asks. The suite is green the first time it is run against a clean volume, and drifts from
there. That is the defect, and it is feature 007's to fix.

---

## H-006: what this feature found that the seven findings did not name

Recorded because each was invisible from where the original finding was written, and each is the same
mistake: a claim nobody could check.

**F-006 was not about entitlement.** It was recorded as *"a cross-advisor search returns HTTP 500"*.
Every search whose result set was empty returned 500 — a date range with no runs in it, a status
nobody used, anything. The cross-advisor case was simply the first one someone tried. The finding
named the symptom it happened to meet, and a repair scoped to it would have fixed one query and left
the rest crashing. See H-004 for the three levels it turned out to be.

**F-001 was 106 differences, not 18.** Feature 008 compared what it knew to look for, from a console
that could only see what it rendered. The contract test compares everything, and the number it
produced was the first thing this feature delivered.

**The contract test feature 007's build file describes was never written.** Its comment reads *"a
contract test asserts the generated schema matches the committed JSON, so Principle III's ordering
holds and drift fails the build."* The contracts were copied into `build/resources/test/contracts/`
and nothing read them. That is the third instance of the pattern in H-003's table, and the most
expensive: it is what let 106 differences accumulate unremarked.

**Feature 009's check could not see feature 007's reading guide.** The guide wrote its paths with an
ellipsis, which the check skips. Writing them out added 11 checked claims and turned G-006 from a
thing someone had to notice into a thing the build fails on. Recorded under G-006 as well, because
that is where someone will look.
