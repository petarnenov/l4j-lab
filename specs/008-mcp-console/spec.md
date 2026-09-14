# Feature Specification: MCP console

**Feature Branch**: `008-mcp-console`

**Created**: 2026-09-14

**Status**: Implemented, 2026-09-14. 70 of 71 tasks complete; T071 is left open deliberately — SC-001
asks that someone who has never seen the console reach a result in two minutes, and the implementer is
not that person. Status corrected 2026-09-14.

**Input**: User description: "Направи лек УИ към съществуващият по аналогичен начин за да тестваме функционалността от 007"

## Overview

A small console added to the existing web application, in the same manner as the pages already
there, for exercising the MCP billing server from feature 007 by hand.

Feature 007 shipped with no interface on purpose — one was explicitly out of scope, and a scripted
client was sufficient for its tests. What it left behind is a system that can only be operated with
`curl` and a careful reading of `quickstart.md`: four headers that must agree with the body, a token
that must be minted first, and results whose shape differs depending on which of three paths the
server took. That is a poor way to look at something, and a worse way to show it to someone else.

**This console is not a client that hides the protocol. It is a window onto it.** The existing pages
abstract the agent chain away, because a user of that feature wants the answer and not the
mechanism. Here the mechanism *is* the subject: someone opening this console wants to see that a
request carried its own protocol version, that a cursor minted by one replica was honoured by
another, that a fee change asked before it acted. A console that showed only the tidy result would
hide the very thing feature 007 exists to demonstrate.

## Clarifications

### Session 2026-09-14

- Q: *Should the console be able to address an individual replica, not only the proxy?* → A: Yes,
  when the topology overlay is running; it falls back to proxy-only when it is not.
- Q: *Where must the console work — development only, or the packaged stack too?* (FR-001, FR-002) → A: Development only. The development server forwards the console's calls to the MCP system; the packaged build does not carry the console.

- Q: *How is the console itself tested — against substituted answers only, or also against the running MCP system?* (SC-007) → A: Both, in two separately selectable suites, mirroring the split feature 007 already uses.

- Q: *What happens to the seeded data that the writing tools change irreversibly?* (US3) → A: The drift is accepted and made visible; the console shows the value before and after, and names the command that resets the whole system.

- Q: *Should the console be able to send a deliberately malformed request?* (FR-011) → A: Yes, but only a small set of prepared ones — not a free-form request editor.

**Why prepared rather than editable.** The two refusals that make this protocol recognisable happen
only when a request is wrong: a header that disagrees with the body, and a version the server does
not implement. A console that always builds correct requests can never show them, and FR-011 asks for
exactly that difference to be visible. But a full editor for headers and body is a different tool
with a different purpose, and it already exists — it is called `curl`. Three prepared cases reach the
same demonstration in one action and keep the console light, which is what was asked for.

**Why not undo it.** A fee adjustment is a real write to a system of record, and the data outlives
stopping the stack. Offering a "put it back" button would teach the wrong lesson and, worse, would
have to do it by posting an opposite adjustment — leaving two entries in the audit log for something
that was meant to be one. The log would then misdescribe what happened, which is a higher price than
a number that differs between demonstrations.

The reset that does exist costs nothing and touches feature 007 not at all: discarding the system's
stored data and starting it again reloads the seeded fixtures.

**Why both, and not just the first.** Feature 007 learned this expensively: three bugs stayed
invisible to a suite that proved real properties, because substituting a key source removed the only
real exchange on that path and with it the only thing that could fail (research R-017). A console
tested only against invented answers proves it renders what it is handed — not that it speaks to the
server it exists to show. The deterministic suite keeps the rule that it runs with no network and no
credential; the second suite is the one that would catch a header the server rejects or a result
shape that moved.

**Why this settles more than it looks.** The MCP system runs as its own composition, on its own
network, reached through its own port. The existing application's packaged form runs on a separate
network behind a separate entry point, and feature 004 promised the two can run at the same time —
joining them would spend that promise. The browser cannot call the MCP system directly either: it
would be a different origin, and permitting that means changing feature 007's server, which this
specification puts out of scope.

That leaves a forwarder in the development server, and the cost turns out to be nothing. The
credentials this console depends on are minted by a service that does not exist outside development
— its endpoints are gated so that elsewhere they return "not found". A console in the packaged build
could therefore draw an empty screen and nothing else.

**Why this matters enough to state.** Three of feature 007's most distinctive properties only exist
across replicas — a cursor minted by one read by another, a confirmation retry landing elsewhere,
task polls spread over three. The stack publishes only the proxy port by default; the individual
replicas are published by `compose.mcp.topology.yaml`, which the acceptance suite already uses. A
console that could only reach the proxy would be unable to show the feature's headline property, so
it addresses replicas when they are reachable and says plainly when they are not.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Someone sees what the server offers and calls a tool (Priority: P1)

A person opens the console, chooses who to act as, and sees the five tools with their declared
behaviour. They fill in a tool's arguments from its declared input shape, call it, and see both the
structured result and the exact JSON-RPC that went out and came back.

**Why this priority**: it is the whole console in miniature, and everything else is a variation on
it. It is also the smallest thing that replaces reaching for `curl`.

**Independent Test**: open the console against a running stack, call a read-only tool, and confirm
the result and the raw exchange both appear.

**Acceptance Scenarios**:

1. **Given** a running MCP stack, **When** the console is opened, **Then** it lists the five tools in
   the order the server returned them, each showing its declared read-only, destructive, idempotent
   and open-world behaviour.
2. **Given** a tool with declared arguments, **When** it is selected, **Then** the console offers a
   field for each argument, marking which are required.
3. **Given** valid arguments, **When** the tool is called, **Then** the console shows the structured
   result and, alongside it, the request and response exactly as they travelled.
4. **Given** the MCP stack is not running, **When** the console is opened, **Then** it says so and
   names the command that starts it, rather than showing an empty page or a generic failure.

---

### User Story 2 - Someone sees that identity decides what is visible (Priority: P1)

A person switches between the seeded principals and watches the same search return different
results: an advisor sees only its own runs, a firm administrator sees the whole firm, and neither
reaches another firm.

**Why this priority**: it is the clearest demonstration in the system that entitlement lives in one
place, and it needs no writes to show.

**Independent Test**: run the same search as two principals and compare what comes back.

**Acceptance Scenarios**:

1. **Given** the console, **When** a principal is chosen, **Then** the console shows who that
   principal is — user, firm, role, and the advisors it may act for.
2. **Given** the same search run as an advisor and as a firm administrator, **When** both results are
   shown, **Then** the advisor's result contains fewer runs and only its own advisor's.
3. **Given** a search of another firm, **When** it is run, **Then** the console shows the refusal as
   the server phrased it, and shows that it arrived as a successful response carrying an error flag
   rather than as a protocol failure.

---

### User Story 3 - Someone follows a change that asks before it acts (Priority: P2)

A person proposes a fee adjustment. The console shows that nothing was applied and presents the
confirmation the server asked for. They confirm, the change is applied, and a repeat of the same
operation returns the original result without applying anything again.

**Why this priority**: it is the only way to see Multi Round-Trip Requests without writing a client,
and the three-call sequence is hard to believe until watched.

**Independent Test**: walk the three calls and read the resulting fee.

**Acceptance Scenarios**:

1. **Given** a proposed fee change, **When** the first call is made, **Then** the console shows the
   confirmation question the server asked, states that nothing has changed yet, and does not present
   a result as though the change had been applied.
2. **Given** the confirmation, **When** it is sent back, **Then** the change is applied and the
   console shows the identifier the billing system assigned it.
3. **Given** the same operation repeated, **When** it is sent a third time, **Then** the console shows
   the original result and makes clear that nothing happened a second time.
4. **Given** the confirmation is declined, **When** it is sent back, **Then** the console shows that
   nothing was applied.
5. **Given** a fee change is applied, **When** the result is shown, **Then** the console shows the fee
   before and after, so the outcome is readable whatever value the account started from.
6. **Given** the seeded data has drifted from earlier use, **When** someone wants it back, **Then** the
   console names the command that discards the system's stored data and reloads the fixtures.

---

### User Story 4 - Someone watches a long operation and the replicas behind it (Priority: P2)

A person starts a billing run, receives a handle at once, and watches it progress to completion. They
can direct individual calls at a named replica and see that a handle from one is honoured by another.

**Why this priority**: the handle-and-poll shape and the cross-replica property are the two things
most likely to be disbelieved, and both are visible only over time.

**Independent Test**: start a run, poll it to completion, and continue a search page on a different
replica than the one that began it.

**Acceptance Scenarios**:

1. **Given** a started run, **When** the call returns, **Then** the console shows a handle
   immediately rather than waiting, and reports progress as it changes.
2. **Given** a running operation, **When** it is cancelled, **Then** the console shows it reaching a
   cancelled state.
3. **Given** the individual replicas are reachable, **When** a call is directed at one, **Then** the
   console shows which replica answered.
4. **Given** a paged search begun on one replica, **When** the next page is requested from another,
   **Then** the console shows the continuation succeeding, with no overlap between the pages.
5. **Given** the individual replicas are not reachable, **When** the console is opened, **Then** it
   offers the proxy alone and explains why the others are absent, rather than failing.

### Edge Cases

- A request whose headers disagree with its body must be shown as a protocol failure with its code,
  not as a tool failure. The console must make the distinction visible, since the two are precisely
  what the server is careful never to confuse — and must be able to produce one on demand, rather
  than waiting for it to happen.
- A deliberately malformed request must be clearly marked as deliberate, so a refusal it produces is
  not mistaken for a fault in the system.
- An unsupported protocol version must show the versions the server does support.
- A cursor that has expired or does not belong to the caller must show the server's refusal, not an
  empty page.
- A tool call made while the stack is stopping must fail with an explanation, not a blank result.
- Two people using the console at once must not see each other's chosen principal.

## Requirements *(mandatory)*

### Functional Requirements

#### The console itself

- **FR-001**: The console MUST be reachable as a page of the existing web application, alongside the
  pages already there, and MUST NOT change the behaviour of any of them.
- **FR-001a**: The console MUST work when the application is run for development, and MUST NOT be
  carried in the packaged build. Reaching the MCP system MUST require no change to that system and
  no joining of the two systems' networks.
- **FR-002**: The console MUST be visibly for development and testing, and MUST NOT be presented as
  part of the product the existing pages serve.
- **FR-003**: When the MCP system is not reachable, the console MUST say so plainly and name the
  command that starts it.
- **FR-003a**: The console MUST be built so that its behaviour can be exercised without the MCP
  system running, and separately against it. Neither way of testing may require altering the console
  to suit it.

#### Acting as someone

- **FR-004**: The console MUST let a person choose which of the seeded principals to act as, and MUST
  show that principal's user, firm, role, and permitted advisors.
- **FR-005**: The console MUST obtain the credential for the chosen principal itself; a person MUST
  NOT have to mint or paste one.
- **FR-006**: Switching principal MUST affect only subsequent calls, and the console MUST make clear
  which principal any displayed result was obtained as.

#### Calling tools

- **FR-007**: The console MUST list the tools the server offers, in the order the server returned
  them, and MUST show each tool's declared read-only, destructive, idempotent and open-world
  behaviour.
- **FR-008**: The console MUST build its argument fields from the tool's declared input shape rather
  than from a list written into the console, so a tool that changes is reflected without the console
  changing.
- **FR-009**: The console MUST indicate which arguments are required and MUST NOT send a call it can
  already tell is incomplete.
- **FR-010**: For every call, the console MUST show the request and the response exactly as they
  travelled, alongside the readable result.
- **FR-011**: The console MUST distinguish a tool failure from a protocol failure, showing the code
  of the latter.
- **FR-011a**: The console MUST offer a small, fixed set of deliberately malformed requests — at
  least a header disagreeing with the body, a version the server does not implement, and a required
  header omitted — each sendable in one action, and MUST show the refusal and its code. It MUST NOT
  offer free-form editing of headers or body: that is a different tool, and the console is meant to
  stay light.

#### The flows worth watching

- **FR-012**: When a tool asks for confirmation before acting, the console MUST present the question
  as the server phrased it, MUST make clear that nothing has been applied, and MUST send back both
  the answer and the state the server issued.
- **FR-012a**: When a change is applied, the console MUST show the value before and after it, because
  the underlying data keeps every change and will differ between uses.
- **FR-012b**: The console MUST NOT offer to undo an applied change. It MUST instead name the command
  that discards the system's stored data and reloads the seeded fixtures. An undo would have to be a
  second, opposite change, leaving the audit log describing two events where one happened.
- **FR-013**: When a tool returns a handle for a long operation, the console MUST show the handle at
  once and MUST report the operation's progress until it reaches a final state, without a person
  having to poll by hand.
- **FR-014**: The console MUST offer to cancel a long operation it has a handle for.
- **FR-015**: The console MUST carry a page cursor from one call to the next without a person copying
  it, and MUST make clear when more results remain.

#### Seeing the topology

- **FR-016**: The console MUST let a call be directed at the shared entry point or, when they are
  reachable, at a named individual replica, and MUST show which was used.
- **FR-017**: When individual replicas are not reachable, the console MUST offer the shared entry
  point alone and MUST explain their absence rather than failing.

### Key Entities

- **Principal**: one of the seeded identities the console can act as — user, firm, role, permitted
  advisors.
- **Tool**: a capability the server offers, with a name, a description, a declared argument shape, a
  declared result shape, and four declared behaviours.
- **Exchange**: one call and its answer — what was sent, what came back, which principal made it,
  which of the running servers answered, and how long it took.
- **Handle**: a reference to work that outlives the call that started it, with a state that changes
  over time.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A person who has never used the system can call a read-only tool and read its result
  within two minutes of opening the console, without consulting documentation.
- **SC-002**: Every one of feature 007's five tools can be exercised from the console, including the
  confirmation flow and the long-running operation.
- **SC-003**: For any call, a person can see the exact request and response without leaving the
  console or opening a developer tool.
- **SC-003a**: A person can produce, in one action, a refusal of each kind the protocol defines for a
  malformed request, and read what the server said about it.
- **SC-004**: The difference between what an advisor and a firm administrator may see can be
  demonstrated in under a minute, without writing anything.
- **SC-005**: The cross-replica continuation of a paged search can be demonstrated from the console
  when individual replicas are reachable.
- **SC-006**: Opening the console with the MCP system stopped produces an explanation and a command
  to run, and never an empty page or an unexplained failure.
- **SC-007**: The existing pages behave exactly as before, and their tests pass unchanged.
- **SC-008**: The console's own behaviour is covered by tests that run with no network and no
  credential, and separately by tests that exercise it against the running MCP system. The second set
  MUST be selectable on its own, and MUST fail with an instruction naming what to start rather than
  an unexplained error when the system is absent.

## Out of Scope

- Any change to feature 007's server. If the console needs something the server does not offer, that
  is a finding to record, not a licence to change the server inside this feature.
- A client for real use. This is a window for looking at a development system, not a way to
  administer a billing system.
- Authentication beyond the seeded development principals. The credential-minting this relies on
  exists only in development, and no real identity provider is involved.
- Editing, saving or replaying past exchanges, and free-form composition of requests. The console
  shows what happened in the current session; it is not a history, and it is not a tool for building
  arbitrary requests.
- Any language other than the one the existing pages already use.
- Any reachability the server does not already have. The console is subject to the same entitlement
  decisions as any other caller.
- Presence in the packaged build, and any joining of the existing application's network with the MCP
  system's. The two compositions were kept independent on purpose (feature 004), and the console is
  not a reason to couple them.

## Assumptions

- The console lives in the existing web application because the request was to add it to what is
  already there. It is a page among the existing pages, not a second application.
- It is used against a locally running system. Both the console and the credential-minting it depends
  on are development-only, and the latter does not exist outside development — a console in the
  packaged build would have nothing to authenticate with.
- The seeded principals are the ones feature 007 already defines; the console adds none.
- A person using the console is a developer or reviewer of this repository, not a customer. The
  console may therefore show protocol detail that would be noise in a product.
- The seeded data is expected to drift as the console is used. Nothing restores it short of
  discarding the system's stored data, and the console says so rather than pretending otherwise.
- The individual replicas are reachable only when the topology composition is running, which is the
  same composition the acceptance suite already uses.
- Feature 007's server offers no machine-readable description of its HTTP surface, deliberately: its
  contract is the tool declarations it serves. The console therefore learns what it can do by asking
  the server, which is also why FR-008 requires argument fields to be derived rather than written.
