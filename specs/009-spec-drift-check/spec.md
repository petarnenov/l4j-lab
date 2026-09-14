# Feature Specification: Hold the documents to the code

**Feature Branch**: `009-spec-drift-check`

**Created**: 2026-09-14

**Status**: Draft

**Input**: User description: "Синхронизирай документни и реален код"

## Overview

A check that fails when a specification document says something about the code that is no longer
true, so drift is caught by the build rather than by someone happening to ask.

**The request was to synchronise them, and they now are.** The documents for feature 008 were
brought back in line by hand twice in one day. This feature exists because of how that went, not
instead of it: synchronising is a state, and a state with nothing holding it decays. What is asked
for here is the thing that holds it.

### What happened twice, in one feature

The first time, a review pass found a test file named for a source file that did not exist, three
delivered modules missing from the plan's file tree, and a requirement that no task cited. All real,
all invisible to every test in the repository, all found only because someone went looking.

The second time was worse, because the lesson had already been learned. Four fixes landed in
response to continuous integration — a split build configuration, a Dockerfile correction, a scanner
configuration, a new verification task — and within hours the same documents were stale again in six
places. Nothing failed. Nothing could have: no test in this project reads a document.

**The pattern is the interesting part.** Drift does not appear when someone is careless. It appears
when someone is *responding to something urgent* — a red build, a failing check — and the document
is not what is urgent. That is precisely when nobody re-reads the plan, and precisely when the plan
stops being true.

### Why this is worth a feature in this repository in particular

This project is a specification-driven lab. Its specifications are not documentation *about* the
product; they are a substantial part of what the project is for. A plan that quietly stops
describing the code is a worse failure here than in a repository where the code is the only artifact
that matters — and there is already a working example of what that costs: feature 007's contracts
claim its tool declarations are served verbatim, and they are not. That took a live test suite and
three days to discover (feature 008, finding F-001). Prose cannot be checked mechanically, which is
exactly why the claims that *can* be should be.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The build says which document stopped being true (Priority: P1)

Someone changes the code — adds a module, renames a file, moves a directory — and runs the project's
verification. It fails, naming the document, the line, and the claim that no longer holds.

**Why this priority**: it is the whole feature in miniature. Everything else is another kind of claim
checked the same way. It is also the only story that changes the economics: until a document can
fail, keeping it true depends on someone remembering.

**Independent Test**: rename a file the plan names, run verification, and read the failure.

**Acceptance Scenarios**:

1. **Given** a feature that declares itself implemented and whose plan names a file, **When** that file is renamed or deleted
   and verification runs, **Then** it fails naming the document, the line, and the missing path.
2. **Given** a directory a feature has marked as completely accounted for, **When** a file is added
   beneath it that no document of that feature mentions, **Then** verification fails naming it.
3. **Given** a directory carrying no such mark, **When** a file is added beneath it, **Then**
   verification does not fail — completeness is something a document claims, never something the
   check infers, because inferring it produces failures about files the feature never touched.
4. **Given** documents and code that agree, **When** verification runs, **Then** it passes and says
   how many claims it checked, so a check that silently stopped checking anything is visible.
5. **Given** a feature that does not declare itself implemented, **When** verification runs, **Then** its documents
   are not held to the code, because a plan written before the code is *supposed* to describe files
   that do not exist yet.

---

### User Story 2 - Every command a document tells someone to run exists (Priority: P1)

A newcomer follows a quickstart. Every command it names is one the project actually offers.

**Why this priority**: it is the drift with the sharpest edge for the reader. A file path in a plan
that has gone stale costs a moment of confusion; a command that no longer exists costs a newcomer
their first ten minutes and some of their confidence that the documents can be trusted at all.

**Independent Test**: rename a command a quickstart names, run verification, read the failure.

**Acceptance Scenarios**:

1. **Given** a document naming a command the project provides, **When** that command is renamed,
   **Then** verification fails naming the document and the command.
2. **Given** a document naming a command that belongs to a tool rather than this project, **When**
   verification runs, **Then** it does not fail: only commands this project defines are claims this
   project can be held to.
3. **Given** a command that exists, **When** verification runs, **Then** it passes without the
   command being executed — checking that something exists must never run it.

---

### User Story 3 - Every requirement is accounted for (Priority: P2)

Someone finishing a feature can see that no requirement was quietly dropped, and no work was done
that no requirement asked for.

**Why this priority**: it catches a different failure — not a document that went stale, but one that
was never finished. It is also the check that was performed by hand for feature 008 and found three
requirements covered in substance but cited nowhere.

**Independent Test**: remove a requirement's only citation from the task list and run verification.

**Acceptance Scenarios**:

1. **Given** a completed feature, **When** a requirement identifier appears in its specification but
   in no task, **Then** verification fails naming the requirement.
2. **Given** a task that cites no requirement and belongs to no setup or maintenance phase, **When**
   verification runs, **Then** it is reported, so work nobody asked for is visible.
3. **Given** a requirement deliberately left unmet and recorded as such, **When** verification runs,
   **Then** it does not fail — an unmet requirement that says so is a decision, not a drift.

---

### User Story 4 - A reference that points nowhere is a broken promise (Priority: P3)

Documents refer to each other and to files in the repository. Every such reference resolves.

**Why this priority**: the smallest of the four, and the most mechanical. It matters because these
documents are read by following references, and one that dead-ends teaches the reader to stop
following them.

**Independent Test**: point a link at a file that does not exist and run verification.

**Acceptance Scenarios**:

1. **Given** a document linking to another file in the repository, **When** that file moves, **Then**
   verification fails naming both.
2. **Given** a link to something outside the repository, **When** verification runs, **Then** it is
   not followed — the check must not need a network.

### Edge Cases

- A feature part-way through implementation must not be held to its plan. Its plan describes what
  will exist, which is the point of writing one first. The check needs a signal for when a feature
  has finished becoming true, and it must not be a human remembering to flip it.
- A document that deliberately names something absent — a command a reader should *not* run, a path
  that illustrates a mistake — must be expressible without disabling the check for the whole file.
- A file that exists but is deliberately unmentioned, such as a generated artifact, must not force a
  choice between a false failure and mentioning it.
- A claim the check cannot verify must not be reported as verified. Silence about prose is honest;
  a passing check that implies the prose was read is not.
- Renaming a document must not be a way to make its failures disappear unnoticed.
- The check must be readable enough that someone can tell what it does *not* check. Its value
  depends on nobody believing it covers more than it does.

## Requirements *(mandatory)*

### Functional Requirements

#### What is checked

- **FR-001**: The system MUST verify that every repository path exists, for every feature that
  declares itself implemented.
- **FR-002**: The system MUST verify that every file beneath a directory a feature has marked complete
  is named by that feature's documents, so additions to it cannot land undocumented.
- **FR-003**: The system MUST verify that every command a document instructs a reader to run is a
  command this project defines.
- **FR-004**: The system MUST verify that every requirement identifier in the specification of a
  feature that declares itself implemented is cited by at least one task.
- **FR-005**: The system MUST verify that every reference from one document to another file in the
  repository resolves.
- **FR-006**: The system MUST NOT attempt to verify claims made in prose, and MUST NOT report them as
  checked.

#### When it is checked

- **FR-007**: The system MUST hold a feature's documents to its code only once that feature **declares
  itself implemented**, and MUST read that declaration from a field the specification already carries.
  Declaring is deliberately not the same as being finished: a feature can be delivered and still say
  otherwise, and three in this repository do. The system MUST therefore also report a feature whose
  work is evidently complete while its declaration says draft, so the declaration cannot quietly
  become a way of not being checked.
- **FR-008**: The system MUST run as part of the project's ordinary verification, not as a command
  someone has to remember.
- **FR-009**: The system MUST run without a network, without credentials, and without starting
  anything.
- **FR-010**: The system MUST NOT execute any command it is checking for the existence of.

#### What it says

- **FR-011**: When a claim fails, the system MUST name the document, the location within it, and the
  claim, so the failure can be acted on without re-deriving it.
- **FR-012**: When everything passes, the system MUST report how many claims it checked, so a check
  that has silently stopped checking is visible.
- **FR-013**: The system MUST make plain, in what it reports and in its own source, which kinds of
  claim it does not check.

#### Exceptions

- **FR-014**: The system MUST allow a specific claim to be exempted, at the claim rather than the
  file, and MUST require a stated reason recorded alongside the exemption.
- **FR-015**: The system MUST report its exemptions when it runs, so an exemption cannot become
  permanent by being forgotten.

#### Scope of application

- **FR-016**: The system MUST be applied to every feature that declares itself implemented, including
  those that predate it. Drift already present when the check arrives MUST be recorded with a reason
  rather than fixed, because this feature may not edit what a past feature decided, and MUST be
  reported on every run so that recording it cannot become forgetting it. New drift MUST fail.

### Key Entities

- **Claim**: something a document asserts about the repository that can be checked without judgement
  — a path, a command, a requirement identifier, a reference. Has a source document, a location, and
  a verdict.
- **Feature record**: the set of documents describing one feature, and the signal for whether its
  work is complete.
- **Exemption**: a claim deliberately not checked, with its reason.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The **structural** drift found by hand during feature 008 is caught by this check when
  reintroduced: a test file named for a source file that did not exist, three delivered modules absent
  from a plan's file tree, and three requirements cited by no task.
- **SC-001a**: The drift from that same episode which this check **cannot** catch is stated rather
  than implied, and a reader of the output can tell it apart from drift that was checked and passed.
  Four items qualify: a count written in prose ("two findings" where there were six), a phrase in
  prose ("three touched files" where there were six), a file added at the repository root that no
  document's file tree covers, and a build script added without being documented — which is an
  absence, and this check verifies presence. The first two are prose, which FR-006 forbids checking
  at all. Promising to catch them would make SC-001 unsatisfiable by design.
- **SC-002**: A person who renames a file, a command, or a document can learn which documents that
  breaks without reading any of them.
- **SC-003**: The check completes fast enough that nobody is tempted to skip it, and adds no
  noticeable time to the verification it joins.
- **SC-004**: A reader of the check's output can state what it does not cover.
- **SC-005**: The check reports what it checked and what it exempted on every run, and a run that
  checked nothing is distinguishable from a run that found nothing wrong.
- **SC-006**: No claim the check makes requires a network, a credential, or a running service.

## Out of Scope

- Judging whether prose is accurate. Feature 008's finding F-001 is the standing example: a contract
  claimed tool declarations were served verbatim and they were not, and only a suite that spoke to
  the real server could tell. That is what live tests are for, and it does not generalise into a
  document checker.
- Writing or correcting documents. This reports drift; closing it is a person's decision, because the
  document is sometimes the thing that was right.
- Checking documents against anything outside this repository.
- Any change to what the existing specifications say. If a document must change to pass, that is a
  finding to raise, not a licence to edit the record of what a past feature decided.
- Enforcing a house style, a heading structure, or a template. This is about truth, not form.

## Assumptions

- Completeness is read from the task list each feature already keeps, where finished work is marked
  as such. This is a record the project maintains anyway, which is what makes it a usable signal
  rather than one more thing to remember.
- The documents worth checking are those the specification flow already produces, plus the
  repository's own README, which names commands more prominently than anything else.
- "Commands this project defines" means the ones its own build, launcher and package manifest
  declare. A command belonging to an external tool is outside what this project can promise.
- Drift is reported as a build failure rather than a warning, on the same reasoning the project
  already applies elsewhere: a warning nobody must act on is a warning nobody acts on.
- The six findings feature 008 recorded against feature 007 stay open. This check would not have
  found any of them; they are claims about behaviour, not about structure.
