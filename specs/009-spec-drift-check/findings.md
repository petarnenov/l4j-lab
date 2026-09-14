# Findings: drift found while planning the drift checker

**Feature**: `009-spec-drift-check` | **Phase**: 1

Recorded rather than fixed, on the reasoning the spec's Out of Scope gives: this feature may not edit
what a past feature decided. Each is exactly the kind of thing the check is meant to catch, which is
mildly embarrassing and extremely useful — they become the corpus SC-001 is measured against.

---

## G-001: three implemented features still declare themselves drafts

| Feature | `Status` says | Reality |
|---|---|---|
| 006-makefile-entrypoint | `Draft` | implemented, merged to `main` |
| 007-mcp-billing-server | `Draft` | implemented, merged to `main` |
| 008-mcp-console | `Draft` | implemented, merged to `main` |

Features 003, 004 and 005 say `Implemented` with a date and a note, so the convention exists and was
simply not applied to the last three.

**Why it matters here more than usual**: research R-001 makes `Status` the switch that arms every
other check. Three features that are implemented but say otherwise would be silently skipped — the
check would pass by not looking. That is the worst failure mode available to a tool like this, which
is why the status kind of claim exists in the grammar and why a complete task list beside a `Draft`
status is reported.

**Not fixed here.** Correcting them is a one-line edit per feature and someone should make it, but it
is an edit to the record of what those features decided, and this feature does not make those.

---

## G-002: feature 005's status line states a task count its own file contradicts

`005-langchain4j-declarative-migration/spec.md` says "All 46 tasks complete". `tasks.md` holds 54,
all of them complete.

Harmless in substance — the work is done either way — and a precise example of the class of claim
this feature checks: a number written in prose about a file, where the file is right there. It is in
the grammar as a status claim for that reason.

---

## G-003: the repository had no pull request until this week, so nothing had ever been scanned

Not drift, but the cause of two findings feature 008 recorded (its F-007 and F-008: a packaged image
unbuildable since feature 007, and a database password on `main` since before it). Both were caught
the first time a pull request existed, because the checks that catch them run on pull requests.

Recorded here because it bears on this feature's design: **a check that only runs where people
rarely look is a check that does not run.** FR-008 puts this one in ordinary verification rather than
in a workflow, and that is why.

---

## G-004: what the check found on its first real run

**T037.** 621 claims across four features that declare themselves implemented — 003, 004, 005 and
009 itself. 75 baseline entries, in three groups, each recorded with the reason it is there rather
than a blanket exclusion:

| Count | What | Why baselined |
|---|---|---|
| 62 | requirement identifiers cited by no task, in 003, 004 and 005 | Those features predate the convention of citing identifiers in task descriptions, which 007 onward follow. Their tasks cover the work; they do not name what they cover. |
| 7 | classes named in feature 005's migration tables | A migration document necessarily names what it replaced. `ChainRunner`, `ChainNode`, `SummarizeNode` and their tests were deleted by that very feature. |
| 6 | status fields | See G-001 and G-005. |

**The seven deleted classes are the finding worth keeping.** Feature 005 replaced the hand-written
chain with LangChain4j agentic orchestration and removed them, and features 001, 003 and 004 still
name them as though they exist. Nothing in this repository could previously have said so.

---

## G-005: the status decision this feature cannot make for you

Six features declare a status that does not match their state, and until that is corrected **their
documents are not held to their code at all**:

| Feature | Says | Is |
|---|---|---|
| 001-financial-agent-chain | `Approved` | delivered; 101 of 102 tasks, the last open on purpose |
| 002-corporate-ui-redesign | `Approved` | delivered; all 58 complete |
| 005-langchain4j-declarative-migration | `Implemented`, "All 46 tasks" | `tasks.md` holds 54 |
| 006-makefile-entrypoint | `Draft` | delivered and merged |
| 007-mcp-billing-server | `Draft` | delivered and merged |
| 008-mcp-console | `Draft` | delivered and merged |

**Why this feature did not simply fix them.** The spec's Out of Scope forbids editing what a past
feature decided. Whether a status line is such a decision or merely a fact about today is a genuine
question, and it is the maintainer's: correcting six lines would bring four more features under the
check, and leaving them means those four stay unchecked.

**What it costs to leave.** Declared implemented for one run, feature 008 produced eight findings —
seven shorthand paths of the form `007/contracts/token-issuer.md` <!-- drift-ok: the shorthand is the subject of this sentence, not a path this document promises -->
which a reader resolves instantly and the check cannot, and one elided path. All are in 008's own documents, written when
this check did not exist. None is serious. But nobody would have known.

**The recommendation**, offered rather than taken: correct 006, 007 and 008 to `Implemented`, fix
005's task count, and decide whether `Approved` is a state this flow still uses. Then baseline what
that surfaces, with reasons, as this feature did for 003 to 005.

### Decided, 2026-09-14

The maintainer took it. All six were corrected, and `Approved` was settled as a state this flow no
longer uses after delivery: 001 and 002 were approved at planning and never updated, so both are now
`Implemented` with the date the work landed. Each correction says in the status line itself that it
was a correction, and why.

**What it changed**, measured before and after:

| | Before | After |
|---|---|---|
| Features held to their code | 3 | **9** |
| Claims checked each run | 622 | **1,699** |
| Baseline entries | 75 | **201** |

The baseline nearly tripled, which is the honest price of looking at three times as much. Every entry
carries a reason and prints on every run; anything new fails.


---

## G-006: feature 007's reading guide points at classes that were never written

**CLOSED by feature 010** (2026-09-15). Eight of the twelve rows named files that were never written:
research R-014 superseded the controller and much of the protocol package was renamed during
implementation, and nothing carried that back. Every path in the table was opened before it was
written down; the two features with no single home — `server/discover` and the protocol-versus-tool
error split — now say so rather than naming a file arbitrarily.

**And the reason this check could not catch it.** The table wrote its paths as
`mcp-server/.../protocol/RequestEnvelope.java`. The ellipsis form is invisible to this check, which
resolves real paths and skips what it cannot — so a table of nonexistent files sat beside a check
designed to catch exactly that, and the check counted zero claims from it. The paths are written out
in full now: 11 more claims checked, and breaking one fails `make check-specs`. A check that cannot
see a document's claims is indistinguishable from a document with none.

**The largest single finding, and the most useful.** Feature 007's quickstart carries a table headed
*"Reading the code against the spec (SC-004)"* — the one place to look for each 2026-07-28 feature.
Of the twelve files it names, most do not exist:

```
Header-based routing and validation  →  protocol/HeaderValidationFilter.java
Dispatch on Micronaut                →  protocol/McpController.java
Stateless requests, per-request _meta →  protocol/RequestEnvelope.java
```

The delivered package holds `McpMethodHandler.java`, `HttpMethodGate.java`,
`BillingTransportContextExtractor.java` and others. The implementation went a different way —
`tasks.md` T039 says so outright: *"Superseded by R-014. No controller is written."* — and the
reading guide was never updated to follow.

Fifty-eight of feature 007's seventy-three baselined entries are this: planned class names that
survive in the documents and nowhere else.

**Why it matters more than a stale path usually would.** That table exists to be *followed*. It is
addressed at someone trying to read an unfamiliar protocol implementation against its specification,
which is the hardest moment to be sent to a file that is not there. A reader who checks two entries
and finds neither stops trusting the table, and then stops trusting the document.

**Not corrected here.** Rewriting a past feature's record is out of scope for this one, and the
correction needs someone who knows which delivered class took over which planned responsibility —
that is a reading of feature 007, not of its documents. Recorded so that whoever next opens 007 finds
it already written down.


---

## G-007: the check was green in CI because it never ran there

Found immediately after the first push, by asking a question the green tick did not answer.

All five workflows passed. None of them ran `specDrift`: every one invokes a module-scoped task —
`:backend:check`, `:frontend:checkApi` — and the drift check hangs off the **root** project's `check`,
which no workflow calls. Zero runs, reported as success.

FR-008 asks that the check run as part of ordinary verification rather than as a command someone must
remember. That was true locally and false where it matters, and a green tick is exactly the thing that
would have kept it false.

**Fixed** by `.github/workflows/specs.yml`, deliberately the only workflow here with no path filter.
Every other one narrows to the module it tests; this one cannot, because a rename anywhere breaks
claims written anywhere else. A path filter would have meant the check ran only when the thing it
checks was not what changed.

Worth recording next to G-005 for the shape they share: **a check that is not run and a check that
finds nothing look identical from outside.** G-005 was six features silently unexamined because a
status field said draft; this was a whole workflow silently absent because nothing invoked it. Both
were visible only by asking what the pass actually covered — which is the question FR-012's claim
counts exist to make askable.
