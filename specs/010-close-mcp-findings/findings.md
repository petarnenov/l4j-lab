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

**Fixed after all, and not by rewriting the assertions to agree.** The paragraph above said this was
007's to fix, on the grounds that making a red test green by editing it is what this feature argues
against. That reasoning was half right: editing a test to *accept different behaviour* destroys its
claim, but both of these tests were asserting something narrower than the property they were named
for, and the narrower thing happened to be true only on a fresh volume.

| test | asserted | the property it is named for |
|---|---|---|
| `aFirmAdminAndOpsBothSeeTheWholeFirm` | both advisors appear on **the first page** | a firm administrator reaches both advisors |
| `aCursorFromOneReplicaContinuesTheSameSearchOnAnother` | the search fits in **exactly two pages**, which sum to the total | a cursor minted by one replica continues the search on another |

Page composition was never the subject of either. The first now asks for each advisor directly; the
second walks the whole search, taking every page from a different replica, and makes the stronger
claim the old arithmetic was reaching for — no run seen twice, and the pages sum to the caller's own
total, however many there are. Both now pass on a used volume, which was checked by running the suite
twice in a row and letting the first pass create the data the second ran against.

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

---

## H-007: restoring a contract without reading what it contradicted

Feature 010's own mistake, recorded because it is the feature's subject matter committed by the
feature itself.

F-001 said the server had lost keywords the committed contracts carried — among them
`page_size: {minimum: 1, maximum: 20}`. They were restored to the declaration and, per FR-013,
enforced: a declared constraint is a validated constraint. So `page_size: 100` became a tool error.

Feature 007's specification says, in its edge cases:

> A page size argument above the cap must be clamped to the cap, not rejected silently.

`data-model.md` lists the same thing as a requirement, and `tasks.md` T069 tested for it. The
committed contract's `maximum: 20` had been wrong since 007 — it was one more thing declared twice by
hand and disagreeing with itself, which is F-001's actual subject. Restoring it faithfully restored
the disagreement.

**The rule has two directions, and only one of them is obvious.** *Do not declare what you do not
enforce* is the half everyone states. *Do not enforce what you have not declared* is the half that
bites when you are repairing a contract you did not write: the keyword looked like evidence of what
the server should do, and it was evidence of what a past feature had got wrong.

Caught by feature 007's own test, which failed the moment the enforcement was added — the test
feature 010 then edited to match the new behaviour before checking which of the two was right. The
edit was reverted. The lesson is not about page sizes: **a test that fails when you change behaviour
is making a claim, and rewriting it to agree with you destroys the claim.**

---

## H-008: a business rule enforced only by a CHECK constraint

Found by the third instance of H-005, and worth more than the test that found it.

`legacy_billing.account` declares `CHECK (current_fee_bps >= 0)`. Nothing in the API checked it. An
adjustment that would take a fee below zero inserted its row, failed on the `UPDATE`, and left the
service as **HTTP 500** — which `LegacyErrorTranslator` renders, correctly for a 500, as:

> The billing system is unavailable. Do not retry; report this and stop.

The billing system was available. The request was not applicable. A caller that followed that
instruction would escalate a mistyped delta as an outage, and the one thing the message tells a model
to do — stop and report — is the wrong thing to do about a typo.

**This is finding F-006's shape in a different place**: an internal failure wearing a message about
something else, because nobody chose what the answer should be and the default chose for them. F-006
was a `NullPointerException` arriving as *"message must not be empty"*; this is a constraint violation
arriving as *"the system is unavailable"*.

Now a `409`, which the MCP server already renders as *"that request does not apply to this record in
its current state"* — a sentence a caller can act on. The insert and the balance still move together
or not at all.

**How it stayed hidden**: the fee only reaches zero after the topology suite has taken 10 bps off the
same account a dozen times. The scenario that found it now raises the fee before lowering it, so it
nets to zero and leaves the account as it found it — a test that changes the world it measures
eventually measures its own history.

