# Data Model: Hold the documents to the code

**Feature**: `009-spec-drift-check` | **Phase**: 1

Nothing here is a database. These are the shapes the check holds in memory for the length of one
run, plus two committed files. Types are described, not declared in a language.

---

## Claim

Something a document asserts about the repository that can be checked without judgement.

| Field | Type | Notes |
|---|---|---|
| `kind` | `path \| command \| requirement \| reference \| status` | The five kinds in [contracts/claim-grammar.md](./contracts/claim-grammar.md) |
| `document` | string | Repository-relative path of the file that makes the claim |
| `line` | integer | 1-based, so a failure can be opened where it is |
| `raw` | string | The text as it appears, for the report to quote |
| `subject` | string | What is claimed to exist: a path, a command, an identifier |
| `feature` | string | Which feature's record the document belongs to |

**Rules**

- A claim is extracted only from a form the grammar names. Prose is never a claim (FR-006).
- Claims are extracted whether or not the feature is in scope; scope decides what is *verified*, and
  counting extracted claims is what makes a silently-broken extractor visible (FR-012).
- `line` is mandatory. A failure that cannot be located is a failure someone will postpone.

---

## Verdict

The result of checking one claim.

| Value | Meaning |
|---|---|
| `holds` | The subject exists |
| `broken` | The subject does not exist, and nothing excuses it — the build fails |
| `exempt` | An inline marker at the claim states why it is not checked |
| `baselined` | Drift that predates this check, recorded with a reason |
| `unchecked` | The claim's kind is out of scope for this feature (its record is not implemented) |

**Rules**

- Only `broken` fails the build (FR-006 keeps prose out of this table entirely; it never becomes a
  claim, so it can never be a verdict).
- `exempt` and `baselined` are both reported every run (FR-015). Silence would let either become
  permanent.
- A `baselined` entry whose claim now `holds` is itself an error: the record outlived its cause.

---

## Feature record

The set of documents describing one feature, and whether they are binding.

| Field | Type | Source |
|---|---|---|
| `id` | string | Directory name under `specs/`, e.g. `008-mcp-console` |
| `documents` | string[] | The markdown files in that directory and its subdirectories |
| `declaredStatus` | string | The `**Status**:` line of `spec.md` |
| `implemented` | boolean | True when `declaredStatus` says so (research R-001) |
| `tasksTotal` / `tasksDone` | integer | Counted from `tasks.md` |

**Rules**

- `implemented` governs whether the record's claims are verified. Before it, a plan is *supposed* to
  describe files that do not exist.
- The record is cross-checked against itself: a feature whose tasks are all but complete while its
  status still says `Draft` is reported, and so is a status line that states a task count the file
  contradicts. This is the check watching its own arming switch (research R-001) — without it,
  `Status` would be a field nobody maintains, which is what it is today.
- `README.md` at the repository root belongs to no feature and is always in scope. It names more
  commands than any specification does.

---

## Exemption

A claim deliberately not checked, stated at the claim.

| Field | Type | Notes |
|---|---|---|
| `document` / `line` | string / integer | Where the marker sits |
| `reason` | string | Required. A marker without one is an error, not an exemption |

**Rules**

- Written as an HTML comment on the claim's line, so it is invisible in rendered markdown and
  unambiguous about which claim it covers.
- It covers one claim, never a file (FR-014): the rest of the document stays checked.
- An exemption on a line carrying no claim is an error — it has outlived whatever it excused.

---

## Baseline entry

Drift that existed before the check did.

| Field | Type | Notes |
|---|---|---|
| `document` / `subject` | string | Identifies the claim without depending on a line number, which moves |
| `reason` | string | Required |
| `recorded` | date | So an old entry reads as old |

**Rules**

- Lives in `scripts/spec-drift/baseline.json`, committed.
- Matched by document and subject rather than by line, so reformatting a document does not silently
  drop an entry or, worse, move it onto a different claim.
- An entry that matches no broken claim fails the run. The list can therefore shrink on its own and
  cannot grow quietly.

---

## Report

What one run emits. The shape is a contract: [contracts/report-format.md](./contracts/report-format.md).

| Field | Type | Notes |
|---|---|---|
| `checked` | count per kind | Printed always (FR-012) |
| `broken` | Claim + Verdict list | Each with document, line and the claim quoted |
| `exempt` / `baselined` | lists | Printed always (FR-015) |
| `notChecked` | string[] | The kinds of claim this tool does not attempt (FR-013) |
| `exitCode` | 0 or 1 | 1 when any verdict is `broken`, or when an exemption or baseline entry has outlived its cause |

---

## Relationships

```text
Feature record ──has──> Document ──yields──> Claim ──verified as──> Verdict
                                                │                      │
                                        Exemption (inline)      Baseline entry
                                                └──────── excuse ──────┘
                                                          │
                                                       Report
```

Every arrow into `Report` is deliberate: what was checked, what failed, and what was excused all
appear, because a check that hides its excuses is a check that stops being believed.
