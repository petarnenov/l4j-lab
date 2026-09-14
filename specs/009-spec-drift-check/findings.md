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
