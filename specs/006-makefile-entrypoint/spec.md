# Feature Specification: Makefile Entry Point

**Feature Branch**: `006-makefile-entrypoint`

**Created**: 2026-09-13

**Status**: Implemented, 2026-09-13. All 32 tasks complete. Status corrected 2026-09-14: it read
`Draft` after delivery, which is what feature 009's check exists to notice.

**Input**: User description: "Добави Макефиле. Искам да стартирам всичко от там." (Add a Makefile. I want to start everything from there.)

## Context

Running this project today means remembering commands spread across four tools and three places:

- **Development:** start the database and model runtime with the container runtime, run the backend
  through the Gradle wrapper, and run the frontend development server from `frontend/` with npm.
- **Packaged system:** start, scale, inspect, and stop through a long compose command with a file flag,
  plus provider variables and an optional profile.
- **Verification:** the root verification command, per-half test commands, the live model test, the API
  type regeneration, and the golden snapshot rewrite (an environment variable on a filtered test run).

The README lists these, but each is a different incantation. A maintainer asked for one place to start
everything.

This feature adds a Makefile at the repository root as that one place. Each target is a short, named
command that runs the existing command it stands for. The Makefile adds no behavior of its own: the build
tools, the compose files, and the scripts remain the source of truth, and every existing command keeps
working.

Feature 003's research (R-004) rejected a Makefile as a second entry point beside the Gradle wrapper. The
maintainer now asks for one explicitly. The plan must reconcile this with the constitution's build rules,
and the Makefile's design must make clear that it delegates to the wrapper rather than replacing it.

## Clarifications

### Session 2026-09-13

- Q: Порт 8866 да стане ли стойността по подразбиране за nginx в целия проект, или само за `make`? → A: Option A. The packaged system's default published port becomes 8866 in its single declaration (the compose file for the packaged system), still overridable with `L4J_HTTP_PORT`; README, the stack CI workflow, and feature 004's documents are updated to match.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Start development with one command (Priority: P1)

A developer clones the repository and runs one command to get a working development environment: the
database and model runtime start, the backend starts from source, and the frontend development server
starts. Stopping it with one keystroke stops the backend and the frontend together. A separate command
stops the containers.

**Why this priority**: "Start everything" is the request, and development is where commands are typed
most often. It delivers value alone, even before the other targets exist.

**Independent Test**: On a clean clone with the prerequisites installed, run the development start
command, open the frontend address, complete a run, press the interrupt key, and confirm both processes
have stopped.

**Acceptance Scenarios**:

1. **Given** a clean clone with the prerequisites, **When** the developer runs the development start
   command, **Then** the database and model runtime containers start, the backend becomes reachable,
   and the frontend development server becomes reachable, without the developer running anything else.
2. **Given** the development environment is running, **When** the developer presses the interrupt key
   once, **Then** both the backend and the frontend processes stop, and no process is left listening on
   their ports.
3. **Given** the development environment is not running, **When** the developer runs the command to
   start only the backend or only the frontend, **Then** that half starts on its own (with the containers
   it needs).
4. **Given** the containers are running, **When** the developer runs the development stop command,
   **Then** the development containers stop and their data is kept.

---

### User Story 2 - Discover every command from one place (Priority: P1)

A newcomer runs the Makefile with no target, or with a help target, and sees every available command
grouped by purpose, each with a one-line description.

**Why this priority**: A single entry point is only useful if people can find what is in it. It shares P1
because without it the Makefile is another file to read.

**Independent Test**: Run the Makefile with no target and confirm the help lists every target with a
description, grouped into development, packaged system, verification, and maintenance.

**Acceptance Scenarios**:

1. **Given** a clean clone, **When** a newcomer runs the Makefile with no target, **Then** a grouped list
   of all targets and their descriptions is printed and nothing is started.
2. **Given** a target is added to the Makefile, **When** help is printed, **Then** the new target appears
   with its description without a separate list being edited.

---

### User Story 3 - Operate the packaged system without long commands (Priority: P2)

A maintainer starts, stops, scales, inspects, and resets the packaged system from short targets. Provider
mode and scaling are passed the same way the packaged system already reads them (environment variables),
so no configuration moves into the Makefile.

**Why this priority**: The packaged system's commands are the longest ones in the project, but they are
typed less often than development commands.

**Independent Test**: Start the packaged system in cloud mode through the Makefile, open the application,
scale the backend to three, view the load balancer log, and stop it keeping data; then start it in local
mode and pull a model.

**Acceptance Scenarios**:

1. **Given** cloud provider variables in the environment or `.env`, **When** the maintainer runs the
   packaged start target, **Then** the packaged system builds and starts, the command returns once
   every service is healthy, and the application is served at port 8866.
2. **Given** local mode is wanted, **When** the maintainer runs the local-mode start target, **Then** the
   packaged system starts with the model runtime included, and a separate target pulls the configured
   model into it.
3. **Given** the packaged system is running, **When** the maintainer runs the scale target with a backend
   count or a frontend count, **Then** that many instances run.
4. **Given** the packaged system is running, **When** the maintainer runs the status, logs, or load
   balancer log targets, **Then** the corresponding output is shown.
5. **Given** the packaged system is running, **When** the maintainer runs the stop target, **Then** it
   stops with data kept; **and when** the maintainer runs the destructive reset target, **Then** it asks
   for confirmation before deleting stored data, unless confirmation is given up front.

---

### User Story 4 - Verify and maintain from short targets (Priority: P2)

A developer runs the whole verification, either half's tests, the live model test, the API type
regeneration, the lint and format checks, and the golden snapshot rewrite from named targets.

**Why this priority**: These already have root commands, so the gain is naming and discoverability rather
than new capability.

**Independent Test**: Run the verification target and confirm it is the same run as the root verification
command; run each other target once and confirm it runs its underlying command.

**Acceptance Scenarios**:

1. **Given** a clean clone, **When** the developer runs the verification target, **Then** the root
   verification command runs and its exit status is the target's exit status.
2. **Given** no provider is configured, **When** the developer runs the live model test target, **Then**
   it skips with the existing named reason rather than failing.
3. **Given** a backend API type changed, **When** the developer runs the API type regeneration target,
   **Then** the frontend's committed types are regenerated and the verification target passes again.
4. **Given** the golden snapshot must be rewritten on purpose, **When** the developer runs the snapshot
   rewrite target, **Then** it asks for confirmation before overwriting the committed snapshot files,
   unless confirmation is given up front.

---

### Edge Cases

- **Missing prerequisite**: a target that needs the container runtime, Node.js, or a JDK-capable machine
  and cannot find it fails with a message naming the missing tool, before starting anything.
- **Port already in use**: the development start command finds the backend or frontend port taken (as
  happened in this project with a leftover backend on 8080), or the packaged start target finds 8866
  taken. It fails with a message naming the port and, for the packaged system, the `L4J_HTTP_PORT`
  override, rather than starting half the environment.
- **One development process exits**: if the backend or the frontend exits on its own during development,
  the other is stopped too and the command exits with a failure, so a half-running environment is never
  left behind.
- **Destructive targets in non-interactive use**: CI or a script cannot answer a prompt. Destructive
  targets accept an explicit confirmation flag and refuse to run without it when no terminal is attached.
- **Make version**: macOS ships an old GNU Make (3.81 on the maintainer's machine). Every target must work
  with it.
- **Windows**: `make` is not part of a standard Windows install. The README states that the Makefile is for
  macOS and Linux (and WSL), and that the underlying commands remain the documented path on Windows.
- **Credentials**: no target prints, echoes, or writes the model credential, and the Makefile never
  contains one.

## Requirements *(mandatory)*

### Functional Requirements

**Entry point**

- **FR-001**: A Makefile MUST exist at the repository root.
- **FR-002**: Every target MUST delegate to an existing documented command (the Gradle wrapper, the compose
  files, npm scripts, or project scripts). The Makefile MUST NOT reimplement build, test, or deployment
  logic.
- **FR-003**: Running the Makefile with no target MUST print help and start nothing. Help MUST list every
  target, grouped by purpose, with a one-line description, generated from the Makefile itself so it cannot
  drift.
- **FR-004**: Every existing command documented in the README MUST keep working unchanged.

**Development**

- **FR-005**: One target MUST start the full development environment: the development containers, the
  backend from source, and the frontend development server, with the output of both processes visible and
  labeled.
- **FR-006**: Interrupting that target once MUST stop both the backend and the frontend. If either exits by
  itself, the other MUST be stopped and the target MUST exit with a failure.
- **FR-007**: Separate targets MUST start only the backend and only the frontend, and start and stop the
  development containers, with stopping keeping data.
- **FR-008**: The development start targets MUST check that their ports are free and fail naming the port
  if not.

**Packaged system**

- **FR-009**: Targets MUST start the packaged system in the default mode and in local mode (with the model
  runtime), pull the configured local model, show status, show logs, show the load balancer log, scale the
  backend and the frontend to a given count, and stop it keeping data.
- **FR-010**: A target MUST stop the packaged system and delete its stored data, and it MUST require
  confirmation (interactive, or an explicit flag when no terminal is attached).
- **FR-011**: Provider mode, model, credential, published port, and replica counts MUST be read from the
  environment or `.env` exactly as the packaged system already reads them. The Makefile MUST NOT define
  their values.
- **FR-011a**: The packaged system's load balancer MUST be published on port 8866 by default. The default
  MUST be declared once, in the packaged system's compose file, and MUST remain overridable with
  `L4J_HTTP_PORT`. Every place that names the default (README, the stack CI workflow, feature 004's
  contracts, quickstart, data model, and research) MUST name 8866, and nothing may still assume 8000.

**Verification and maintenance**

- **FR-012**: Targets MUST run the root verification, the backend tests, the frontend tests, the live model
  test, the API contract check, API type regeneration, the lint check, and the frontend formatter (the
  project has a formatter that rewrites files, not a format check; see plan research R-009).
- **FR-013**: A target MUST rewrite the golden run snapshot, and it MUST require confirmation as in FR-010.
- **FR-014**: Every target MUST succeed exactly when the command it delegates to succeeds and fail exactly
  when it fails. GNU Make exits with status 2 for any failed recipe and names the underlying status in its
  error line (`Error N`); that line MUST remain visible (plan research R-007).

**Safety and documentation**

- **FR-015**: A target that needs a tool that is absent MUST fail naming the tool before starting anything.
- **FR-016**: No target may print, echo, or write the model credential, and the Makefile MUST NOT contain a
  credential.
- **FR-017**: The Makefile MUST work with GNU Make 3.81 and later on macOS and Linux.
- **FR-018**: The README MUST present the Makefile targets as the primary way to run the project, keep the
  underlying commands for reference, and state the platform scope (macOS, Linux, WSL).
- **FR-019**: The feature plan MUST reconcile the Makefile with the constitution's build rules (the Gradle
  wrapper as the only backend build tool, and documented commands going through it) and with feature 003's
  earlier rejection of a Makefile, and record the outcome.

### Key Entities

- **Target**: a named command in the Makefile, with a group, a one-line description, and the single existing
  command (or short sequence of existing commands) it delegates to.
- **Target group**: development, packaged system, verification, maintenance, and help.
- **Confirmation**: the interactive prompt or explicit flag that destructive targets require.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A developer on a clean clone starts the full development environment with 1 command and stops
  both application processes with 1 interrupt.
- **SC-002**: 100 percent of the commands documented in the README have a Makefile target, and every target
  appears in the help output with a description.
- **SC-003**: For every verification and test target, the result (pass or fail) equals that of the command it
  delegates to in 100 percent of runs, and on failure the underlying exit status is shown.
- **SC-004**: Destructive targets (stored data deletion, snapshot rewrite) delete or overwrite nothing
  without confirmation, in 100 percent of attempts without confirmation.
- **SC-005**: The Makefile runs without errors under the oldest supported Make (GNU Make 3.81).
- **SC-006**: No README command stops working, and the root verification command still passes with
  unchanged test counts.
- **SC-007**: After starting the packaged system with default settings, the application answers on port 8866,
  port 8000 is closed, and a search of the repository's runnable files and documentation finds no remaining
  reference to 8000 as the load balancer's port, excluding historical execution records (feature 004's task
  results) and this feature's own documents, which name 8000 only as the previous value.

## Assumptions

- **Platform**: macOS and Linux, including WSL. Native Windows keeps using the underlying commands.
- **Prerequisites**: unchanged from the README: container runtime, Node.js at the version in `frontend/.nvmrc`,
  and the Gradle wrapper provisioning the JDK. The Makefile checks for them but does not install them.
- **Development runner**: running the backend and the frontend together with labeled output and joint
  shutdown may use a small script or an existing tool; the plan chooses, preferring no new dependency.
- **Scope**: the Makefile is a convenience entry point only. CI keeps calling the underlying commands, so CI
  does not depend on Make.
- **Target names**: short and conventional (for example `dev`, `up`, `down`, `test`, `check`); the plan
  fixes the exact names in a contract.
- **Constitution**: the change touches no LangChain4j capability, so the capability inventory required by
  Principle I is recorded as not applicable, with that reason.
