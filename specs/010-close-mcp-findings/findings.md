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

## H-003: feature 007's test suite is failing on `main`, and has never run in CI

Found while running `:mcp-server:test` for the first time in this feature. On a pristine checkout of
`main`, four tests fail:

```
AuditTest > anExecutedAdjustmentNamesWhoConfirmedItAndWhatTheRecordCalledIt
RequestEnvelopeTest > capabilitiesDeclaredOnOneRequestDoNotCarryToTheNext
TasksTest > cancellingAFinishedRunIsAcknowledgedAndChangesNothing
TasksTest > pollingReportsProgressAndThenTheFinalResult
```

**No CI workflow runs them.** `backend.yml` runs `:backend:check`; `contract.yml` runs
`:frontend:checkApi :frontend:checkVersion`. Nothing names `mcp-server`, `legacy-billing-api` or
`token-issuer` — three modules, an entire feature's deterministic suite, never once executed by
continuous integration.

**This is the third instance of one shape in this repository**, and the pattern is now worth stating
as a rule rather than as three coincidences:

| | What was silently unexamined | How it looked |
|---|---|---|
| 009 G-005 | six features whose documents were held to nothing | a status field said `Draft` |
| 009 G-007 | the drift check itself | green, because nothing invoked it |
| **010 H-003** | three modules' entire test suites | green, because nothing invoked them |

**A check that is not run and a check that finds nothing are indistinguishable from outside.** Every
time this repository has looked, it has found another. The only defence that has worked is asking what
a pass actually covered — which is why feature 009 prints its claim counts, and why this finding exists
at all.

**Not fixed here.** Adding the three modules to CI is a workflow change, not one of the seven findings,
and the four failures need diagnosing before they are made visible — a CI job that starts red teaches
people to ignore it. Both belong in their own change, and this is the record that it is needed.

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
