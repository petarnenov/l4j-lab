# Quickstart: Hold the documents to the code

**Feature**: `009-spec-drift-check`

A run-and-verify guide. The claim grammar is in [contracts/claim-grammar.md](./contracts/claim-grammar.md),
the output shape in [contracts/report-format.md](./contracts/report-format.md), the entities in
[data-model.md](./data-model.md), and the decisions in [research.md](./research.md).

## Prerequisites

- Node 24, the version `frontend/.nvmrc` already pins
- Nothing else. No network, no credentials, no containers, no `npm install`

## Run it

```bash
make check-specs        # the launcher
./gradlew specDrift     # the task it runs
./gradlew check         # runs it among everything else, which is the point
```

In CI it is `.github/workflows/specs.yml`, and it is the only workflow with no path filter. Every
other one narrows to the module it tests, which is right for them and wrong for this: a rename
anywhere can break a claim written anywhere else. A filter would mean the check ran only when the
thing it checks was not what changed.

A passing run prints what it checked and what it did not:

```text
spec-drift: N claims checked across M implemented features
  paths / commands / requirements / references / status …
  not checked: prose, accuracy of descriptions, external links, code behaviour
```

## Verify it, one claim kind at a time

Each row is a thing to break and what must happen. Put it back afterwards.

| Do this | Expect |
|---|---|
| Rename a file that an implemented feature's plan names | `BROKEN`, naming the document, the line and the path (US1-1, FR-011) |
| Add a file under a directory marked `[complete]` and mention it nowhere | `BROKEN`, naming the file (US1-2) |
| Add a file under a directory **not** marked `[complete]` | passes — completeness is claimed, never inferred (R-009) |
| Rename a `make` target a quickstart names | `BROKEN`, naming the document and the command (US2-1) |
| Rename a target and update every document that names it | passes (US2-1) |
| Point a link at a moved file | `BROKEN`, naming both (US4-1) |
| Delete a task that is the only one citing a requirement | `BROKEN`, naming the requirement (US3-1) |
| Change a document's prose to say something false | **passes** — and this is correct (FR-006) |

That last row is the one to try deliberately. A check that appeared to validate prose would be worse
than no check, and seeing it pass is how you learn what this tool is.

## Verify what it refuses to do

| Do this | Expect |
|---|---|
| Run with no network | passes; nothing external is fetched (FR-009, SC-006) |
| Watch for side effects | nothing is built, started, or written (FR-010) |
| Name `curl` or `docker` in a document | passes; those are not this project's promises (R-004) |
| Set a feature's `Status` back to `Draft` | its claims stop being verified, and the complete task list beside a draft status is reported (R-001) |

## Verify the excuses

```bash
./gradlew specDrift     # exempt and baselined entries print on every run, including passing ones
```

| Do this | Expect |
|---|---|
| Add `<!-- drift-ok: reason -->` to a broken claim's line | it becomes `exempt` and is listed (FR-014) |
| Add the marker without a reason | error — an exemption with no reason is not one |
| Add the marker to a line carrying no claim | `STALE`, exit 1 — it has outlived what it excused |
| Fix drift that is in the baseline | `STALE`, exit 1 — remove the entry (report-format) |

The point of the last two rows: excuses in this tool can only shrink by themselves.

## What it will find on its first run

Three things are already known, recorded in [findings.md](./findings.md), and are the reason the
baseline exists:

- Features 006, 007 and 008 declare themselves `Draft` while being implemented and merged (G-001)
- Feature 005's status line states 46 tasks where its file holds 54 (G-002)
- Whatever else features 001 through 006 have accumulated, none of which this feature may edit

Expect the first run to be a conversation about the baseline rather than a clean pass. That is the
intended outcome: the check's first job is to say what is true today.

## Run the tests

```bash
node --test scripts/spec-drift/     # the checker's own suite
```

No network, no credential, no containers — the same constraint the check itself is held to.
