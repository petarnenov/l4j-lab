# Feature Specification: Close the findings against the MCP billing server

**Feature Branch**: `010-close-mcp-findings`

**Created**: 2026-09-14

**Status**: Draft

**Input**: User description: "Close the seven findings recorded against the MCP billing server: the cross-advisor search that answers HTTP 500 with an internal message, the confirmation retry whose documented shape the server reads as a refusal, the tool declarations that are not served as the contracts claim, and the reading guide that points at classes never written."

## Overview

Seven observations were recorded against feature 007 and deliberately not acted on. Two features found
them and neither was allowed to fix them: feature 008's spec put that server out of scope, and feature
009 may not edit the record of what a past feature decided. Both filed instead, with reproductions.

This feature is the one that may act.

**They are not a list of small corrections.** One is a defect that returns an internal error message to
a caller. One makes a client built from the published contract do the opposite of what it asked. One
means the tool declarations a model reads are not the declarations this repository committed. The rest
are smaller, and the last is a reading guide that sends a newcomer to files that were never written.

### Why they were found at all, and why that matters here

None of them was visible from inside feature 007. Its own suite compares handler behaviour against the
committed contracts and never reads what the server puts on the wire; nothing in it reads a document.
They surfaced because a client was built against it (feature 008) and because a check was pointed at
its documents (feature 009).

That is the argument for closing them properly rather than patching the symptoms: the same blind spot
that hid them will hide their successors. Where a fix can come with something that would have noticed,
it should.

### The seven

| | Severity | What |
|---|---|---|
| **F-006** | defect | A cross-advisor search *inside the caller's own firm* answers **HTTP 500** with `-32603 "message must not be empty"`. The cross-firm refusal one line away is correct. |
| **F-005** | high | The published contract describes the confirmation retry one way; the server reads another. A client following the contract asks for a change and is silently understood to have declined it. Its sibling: a declined confirmation comes back flagged an error, against the same contract. |
| **F-001** | material | `tools/list` does not serve the committed tool declarations verbatim, though the contracts say it does. Eighteen schema keywords are dropped, eighteen required output fields arrive optional, and two descriptions have already drifted in wording. |
| **G-006** | material | The quickstart's "reading the code against the spec" table names twelve files; most were never written. |
| **F-004** | material | `start_billing_run` declares the immediate handle where its contract documents the completed run. |
| **F-002** | minor | A fee adjustment reports no previous fee, so every client must reconstruct it. |
| **F-003** | minor | Nothing identifies which replica answered. |

Full write-ups, with reproductions, in [`specs/008-mcp-console/findings.md`](../008-mcp-console/findings.md)
and [`specs/009-spec-drift-check/findings.md`](../009-spec-drift-check/findings.md).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A search that matches nothing is a search that matched nothing (Priority: P1)

Someone runs a query whose result set is empty — a date range with no runs in it, a filter that
matches none. They get an empty result. Nobody gets a crash.

**Why this priority**: it is the only defect among the seven, and it is larger than the finding that
reported it. Any search returning no rows answers **HTTP 500** with a JSON-RPC code the server's own
error table does not list and a message that is the server's validator complaining about itself. The
finding described one way to reach it — filtering by an advisor you may not act for, which yields an
empty page — and that framing hid the rest. A date range with nothing in it is an ordinary query, and
it crashes the tool.

**Independent Test**: search `firm_id: firm-alpha` with `started_from: 2030-01-01` and read the status.

**Acceptance Scenarios**:

1. **Given** a search whose result set is empty for any reason, **When** it runs, **Then** the answer
   is an ordinary successful result carrying an empty collection and a count of zero.
2. **Given** any query at all, **When** it is answered, **Then** the status is never 500 and the code
   is always one the server's error table lists.
3. **Given** an unexpected failure anywhere in a tool, **When** it reaches the caller, **Then** it
   carries a sentence someone can act on — never an empty message, never a stack frame, never a
   hostname.
4. **Given** an advisor searching for a different advisor's runs within their own firm, **When** the
   search runs, **Then** the answer is a refusal: a tool failure carried by a successful response,
   with a short sentence saying access was refused.
5. **Given** a search filtered by an advisor that does not exist at all, **When** it runs, **Then** it
   is answered identically to the one above — the caller cannot tell the two apart, and must not be
   able to.
6. **Given** a cross-firm search, **When** it runs, **Then** it behaves exactly as it does today:
   the path that was already correct does not move.

**On scenarios 4 to 6**: these describe behaviour that already works, and they are here as
regression protection rather than as the work. `LegacyErrorTranslator` already answers a refusal with
one code path and a fixed vocabulary, and its own comment already states the indistinguishability
rule. The defect is scenarios 1 to 3; keeping 4 to 6 written down is what stops the fix from moving
them.

---

### User Story 2 - A client built from the contract is understood correctly (Priority: P1)

Someone implements a client by reading the published contract, sends a confirmation, and the change is
applied.

**Why this priority**: today that client is silently understood to have declined. There is no
diagnostic, and the response looks like success. The console built in feature 008 hit this on its first
live run, and only found it because it could compare what it sent against what came back.

**Independent Test**: build the retry exactly as the contract describes, send it, and see whether the
fee changed.

**Acceptance Scenarios**:

1. **Given** a confirmation retry built from the published contract, **When** it is sent, **Then** the
   change is applied.
2. **Given** a retry whose answer cannot be understood at all, **When** it is sent, **Then** the
   caller is told the answer could not be read — not silently treated as a refusal.
3. **Given** a declined confirmation, **When** the answer comes back, **Then** it agrees with what the
   contract says a decline is: a result reporting that nothing was applied.
4. **Given** the contract and the server, **When** both are read, **Then** they describe the same
   exchange — whichever of the two is corrected.

---

### User Story 3 - What the server declares is what the repository committed (Priority: P2)

A model, or a person, reads the tool declarations the server serves and gets what the contracts in
this repository say.

**Why this priority**: the declarations are the whole contract of an MCP server. What is being lost —
every enumeration, every format, every bound, every required output field — is precisely the part that
tells a model which values are legal. It is not a cosmetic difference, and it is invisible to every
test feature 007 has.

**Independent Test**: fetch the declarations from a running server and compare them to the committed
files.

**Acceptance Scenarios**:

1. **Given** a running server, **When** its tool declarations are fetched, **Then** each declares the
   same enumerations, formats, bounds and defaults as the committed contract for that tool.
2. **Given** the same declarations, **When** their output shapes are compared, **Then** a field the
   contract requires is required on the wire.
3. **Given** a tool whose contract is edited, **When** the server is restarted, **Then** what it serves
   changes with it, without a second edit anywhere.
4. **Given** the long-running tool, **When** its declared output shape is read, **Then** it describes
   what a caller actually receives from the call.

---

### User Story 4 - The reading guide leads somewhere (Priority: P2)

Someone reading this protocol implementation for the first time follows the quickstart's table from a
protocol feature to the code that implements it, and arrives.

**Why this priority**: that table exists to be followed, by someone at the hardest moment to be sent to
a file that is not there. A reader who checks two entries and finds neither stops trusting the table,
and shortly afterwards the document.

**Independent Test**: open every file the table names.

**Acceptance Scenarios**:

1. **Given** the reading guide, **When** each file it names is opened, **Then** each exists.
2. **Given** a protocol feature in the guide, **When** the file it names is read, **Then** that file is
   where the feature is implemented.
3. **Given** the corrected guide, **When** the repository's own drift check runs, **Then** the entries
   it had recorded for this table are gone rather than silenced.

---

### User Story 5 - A result carries what a caller needs to report it (Priority: P3)

Someone showing the outcome of a fee change, or of a call through the proxy, can say what happened
without reconstructing it.

**Why this priority**: the smallest two. Neither is wrong today; both make every client do arithmetic
or guesswork that the server could have saved them.

**Independent Test**: read an applied fee adjustment and a result obtained through the proxy.

**Acceptance Scenarios**:

1. **Given** an applied fee adjustment, **When** its result is read, **Then** the fee before the change
   is in it, not merely derivable from it.
2. **Given** a call answered through the shared entry point, **When** the answer is read, **Then** it
   says which replica produced it.

### Edge Cases

- A refusal must not become a way of learning what exists. Refusing a real advisor and refusing an
  invented one must be indistinguishable to the caller.
- Correcting the confirmation exchange changes what existing clients must send. Whether that is a
  breaking change, and what the protocol's own versioning rules require of it, must be settled rather
  than assumed.
- Making the served declarations match the committed ones may expose arguments the server does not in
  fact validate. A declaration the server does not honour is a new lie in place of an old one.
- Correcting the reading guide requires knowing which delivered class took over which planned
  responsibility. Where no single file owns a protocol feature, the guide must say so rather than pick
  one.
- Several of these are recorded in the drift check's baseline. A fix that leaves its baseline entry
  behind fails that check, which is the intended behaviour and must be followed through.

## Requirements *(mandatory)*

### Functional Requirements

#### The defect

- **FR-001**: A search whose result set is empty MUST be answered as a successful result carrying an
  empty collection, whatever made it empty.
- **FR-001a**: No query MUST be answered with a 500, and no answer MUST carry a code outside the set
  the server's error table lists.
- **FR-002**: No answer MUST carry an internal message, a validation complaint, or an empty message.
  An unexpected failure MUST reach the caller as a sentence they can act on.
- **FR-003**: An entitlement refusal MUST continue to be answered as a tool failure carried by a
  successful response, and MUST continue not to distinguish a subject the caller may not see from one
  that does not exist. This already holds; it is stated so the fix cannot quietly move it.
- **FR-004**: The system MUST gain a test that fails for the defect as it stands today, so the fix is
  demonstrated rather than asserted.

#### The confirmation exchange

- **FR-005**: A confirmation retry built from the published contract MUST apply the change.
- **FR-006**: The contract and the server MUST describe the same exchange. Which of the two is
  corrected is a design decision for the plan; that they agree is not.
- **FR-007**: An answer the server cannot interpret MUST be reported as uninterpretable, never treated
  as a refusal by default.
- **FR-008**: A declined confirmation MUST be answered as the contract describes a decline.
- **FR-009**: If the corrected exchange is not what existing clients send, the system MUST say so where
  the protocol's own versioning rules require, rather than changing behaviour silently. *Planning found
  this engages nothing: no client sends the flat form, because a client that sent it never worked
  (research R-004). The requirement stays as the check that was made, not as work to do.*

#### The declarations

- **FR-010**: What the server serves as a tool declaration MUST carry the same enumerations, formats,
  bounds, defaults and required fields as the committed contract for that tool.
- **FR-011**: A change to a committed tool contract MUST reach what the server serves without a second
  edit anywhere — one source, as the contracts already claim.
- **FR-012**: A tool's declared output shape MUST describe what a caller receives from calling it.
- **FR-013**: The system MUST NOT declare a constraint it does not enforce. Where a restored constraint
  is not validated, either the validation or the declaration MUST follow, and which MUST be recorded.
- **FR-014**: The system MUST gain a test that compares what is served against what is committed, so
  this cannot drift again unnoticed.

#### The reading guide

- **FR-015**: Every file the reading guide names MUST exist, and MUST be where the feature it is listed
  against is implemented.
- **FR-016**: Where no single file implements a listed feature, the guide MUST say so rather than name
  one arbitrarily.

#### What results carry

- **FR-017**: An applied fee adjustment MUST report the fee before the change.
- **FR-018**: An answer MUST identify which replica produced it, including when it came through the
  shared entry point.

#### Closing the record

- **FR-019**: Each finding closed MUST be marked as closed where it was recorded, with what was done.
- **FR-020**: Each baseline entry the repository's drift check holds for a closed finding MUST be
  removed, and the check MUST pass afterwards.
- **FR-021**: A finding that is **not** closed MUST remain recorded, with why it was left.

### Key Entities

- **Finding**: an observation recorded against feature 007, with a reproduction, a severity, and — after
  this feature — a disposition.
- **Tool declaration**: what the server serves for a tool, and what the repository commits for it. The
  same thing, said twice, which is the problem.
- **Confirmation exchange**: the three calls that apply a fee change, and the shape of the answer that
  carries the decision.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: No query in feature 007's acceptance suite, feature 008's live suite, or this feature's
  own, is answered with a 500 — including every search whose result set is empty. An advisor filtering
  by another advisor receives a refusal indistinguishable from one for a record that does not exist,
  and no answer anywhere carries an internal message.
- **SC-002**: A client implemented from the published contract alone, by someone who has not read the
  server, applies a fee change on its first attempt.
- **SC-003**: Every enumeration, format, bound, default and required output field in the committed tool
  contracts is present in what a running server serves — checked by comparison rather than by reading.
- **SC-004**: Editing a committed tool contract changes what the server serves, with no other edit.
- **SC-005**: Every file the reading guide names can be opened, and a reader following an entry arrives
  at the code implementing it.
- **SC-006**: The repository's drift check passes with every baseline entry belonging to a closed
  finding removed rather than silenced.
- **SC-007**: Each of the seven findings ends this feature either closed, with what was done, or open,
  with why — and none ends it undiscussed.
- **SC-008**: Feature 007's existing acceptance scenarios all still pass, and the behaviour this
  feature did not set out to change is unchanged.

## Out of Scope

- Any new capability. Nothing here adds a tool, a method, or a protocol feature; every item is a
  disagreement between what exists and what was promised.
- New capability in the MCP console. It reads whatever the server declares, so it gains from these
  fixes on its own — its argument fields become choosers and date pickers the day the declarations are
  restored. It *is* edited here, in one direction only: removing the workarounds these fixes make
  unnecessary. Its live suite pins some of today's behaviour deliberately, and the explanation it
  renders about a divergence disappears with the divergence.
- Rewriting feature 007's specification to match what was built. Its plan and research record decisions
  taken at the time and stay as they are; only claims that are false about the *code today* are in
  scope.
- The protocol revision itself. Nothing here changes what 2026-07-28 requires.
- Any finding recorded against another feature.

## Assumptions

- The findings are accurate as recorded. Each carries a reproduction; those are the starting point, and
  a finding that no longer reproduces is closed as such rather than acted on.
- Correcting the confirmation exchange may be a breaking change for any client that already sends what
  the server currently requires. The only such client in this repository is the console from feature
  008, which is development-only and changes with it.
- The reading guide can be corrected by someone reading feature 007's delivered code. Where it cannot,
  that is itself worth recording.
- Restoring the declarations does not require changing what any tool does with its arguments. If it
  does, that is a finding to raise here, not to absorb silently.
- The drift check from feature 009 is the mechanism for confirming the documentation half of this work.
  Its baseline is expected to shrink; that is how the work shows up.
