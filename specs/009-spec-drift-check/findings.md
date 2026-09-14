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
