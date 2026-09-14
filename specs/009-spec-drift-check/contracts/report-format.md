# Contract: what the check prints

One run, one report. The shape is fixed here because FR-012 and FR-013 make the output part of what
the feature promises: a run that checked nothing must be distinguishable from a run that found
nothing wrong.

## Always printed

A line per claim kind with the number checked, and one line naming what is not checked:

```text
spec-drift: 1,847 claims checked across 6 implemented features
  paths         912 checked
  commands      118 checked
  requirements  246 checked
  references    544 checked
  status          6 checked
  not checked: prose, accuracy of descriptions, external links, code behaviour
```

The counts are not decoration. An extractor that silently stops matching is otherwise identical to a
clean repository, and this line is the only thing that would show it.

## On failure

Each broken claim, with enough to act on without re-deriving anything (FR-011):

```text
BROKEN  specs/008-mcp-console/plan.md:127
        path   frontend/src/mcp/liveFixture.ts
        the document names it; it does not exist
```

Exit code `1`.

## Excuses, always printed

```text
exempt (2)
  specs/009-spec-drift-check/contracts/claim-grammar.md:71
    reason: names a file deliberately, to show the failure

baselined (4)
  specs/003-monorepo-integration/plan.md → gradle/wrapper/gradle-wrapper.properties
    reason: predates this check; 003 is a historical record and may not be edited
    recorded: 2026-09-14
```

FR-015: an excuse nobody sees becomes permanent. Printing them on a passing run is the point — this
is the part of the output most likely to be deleted as noise, and the part that must not be.

## Excuses that have outlived their cause

An exemption on a line with no claim, or a baseline entry whose claim now holds:

```text
STALE   scripts/spec-drift/baseline.json
        specs/003-monorepo-integration/plan.md → gradle/wrapper/gradle-wrapper.properties
        this claim now holds; remove the baseline entry
```

Exit code `1`. The baseline can shrink by itself and cannot grow quietly.

## Exit codes

| Code | Meaning |
|---|---|
| `0` | Every verified claim holds; excuses listed |
| `1` | At least one claim broken, or an excuse has outlived its cause |
| `2` | The check could not run — a document it must read is unreadable, `Makefile` is missing |

`2` is distinct on purpose: a tool that cannot run must not be mistaken for a tool that found
nothing.

## What the report never does

- Suggest a fix. The check cannot tell whether the document or the code is the part that was right,
  and guessing would make the document the automatic loser of every disagreement.
- Modify anything. Reporting and repairing are different jobs, and the spec puts the second out of
  scope.
