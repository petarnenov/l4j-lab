# Feature Specification: Containerized Deployment

**Feature Branch**: `004-containerized-deployment`

**Created**: 2026-09-13

**Status**: Implemented, 2026-09-13. All 36 tasks in `tasks.md` are complete; provider mode verified: cloud (local mode not exercised, no model pulled). The `stack` CI workflow runs on its first push.

**Input**: User description: "Искам да контейнеризираме проекта. ФЕ и БЕ трябва да са зад лоад балансер нгинх . Само най-малко нужни портове се еьпозват навън. Трабва да може да се склаират хоризона=тално ФЕ и БЕ и за целта ще пуснеш по 2ве инстации по дефолт" (We want to containerize the project. The frontend and backend must sit behind an nginx load balancer. Only the minimum necessary ports are exposed to the outside. The frontend and backend must scale horizontally, and two instances of each run by default.)

## Context

Today the application runs as three separately started pieces: the database in a container, the
backend from the build tool, and the frontend from its development server, each on its own port on the
host. That is a development arrangement. There is no way to start the whole application as one unit, no
single address to open it at, and no way to run more than one copy of either half.

This feature packages the application so that one documented command starts the complete system: the
database, the model runtime for local mode, two frontend instances, two backend instances, and a load
balancer in front of both halves. A learner opens one address. Everything else is reachable only from
inside the system.

The load balancer is named in the request and is therefore part of the requirement, not a design choice
left to the plan.

The application's behavior does not change. What changes is how it is packaged, started, reached, and
scaled.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Start the whole application with one command (Priority: P1)

A learner clones the repository, supplies the model configuration the documentation names, and runs
one documented command. The complete application starts: database, backend, frontend, and load
balancer. They open one address in a browser and use the application exactly as they would in
development: pick a company and period, start a run, watch the four nodes advance, read the summary,
and browse previous runs.

**Why this priority**: This is the containerization itself. Without it nothing else in the request
exists. It is also valuable alone: a single-instance packaged system behind the load balancer is a
complete, shippable result even before scaling is proven.

**Independent Test**: On a machine with only the container runtime installed (no JDK, no Node.js),
run the documented start command, open the documented address, and complete a run end to end in
local or cloud provider mode.

**Acceptance Scenarios**:

1. **Given** a clean clone and a machine with only the container runtime, **When** the learner runs
   the documented start command, **Then** every part of the application starts without the learner
   installing a language runtime or build tool.
2. **Given** the application has started, **When** the learner opens the documented address, **Then**
   the application's start screen loads, its company and period lists are populated, and a run can be
   started and followed to completion.
3. **Given** the application has started, **When** the browser requests any path that is not an API
   path, including one typed by hand or a reload, **Then** the application is served rather than a
   "not found" page. (The application switches screens in memory and has no per-screen addresses today,
   so a reload returns to the start screen; that is existing behavior and unchanged by this feature.)
4. **Given** the database is still starting, **When** the backend instances start, **Then** they wait
   for the database or retry, rather than exiting permanently, and the application becomes usable
   without manual intervention.
5. **Given** the application is running, **When** the learner runs the documented stop command,
   **Then** every part stops, and stored runs are still present after the next start.

---

### User Story 2 - Only the load balancer is reachable from outside (Priority: P1)

A maintainer inspects the running system from the host. Exactly one port is published: the load
balancer's. The backend instances, frontend instances, database, and model runtime answer only on the
internal network. Requests from outside reach the frontend and backend only through the load balancer.

**Why this priority**: Explicitly requested, and a security property rather than a convenience. It
shares P1 with User Story 1 because shipping the stack with the database or backend published would
violate the request even if everything else works.

**Independent Test**: With the system running, list the ports published on the host and confirm only
the load balancer's appears. From the host, attempt to connect to the database, a backend instance,
and a frontend instance directly on their usual ports and confirm each connection is refused.

**Acceptance Scenarios**:

1. **Given** the system is running, **When** a maintainer lists published host ports, **Then** exactly
   one appears, belonging to the load balancer.
2. **Given** the system is running, **When** a connection is attempted from the host to the database,
   a backend instance, a frontend instance, or the model runtime on their own ports, **Then** it is
   refused.
3. **Given** the system is running, **When** a browser requests an application screen or an API
   endpoint through the load balancer, **Then** it is served.
4. **Given** the default published port is already taken on the host, **When** the maintainer sets the
   documented setting for the published port, **Then** the system starts on that port instead, with no
   file edited.

---

### User Story 3 - Two instances of each half, balanced (Priority: P2)

With default settings, two frontend instances and two backend instances run. The load balancer spreads
requests across the instances of each half. A run started through one backend instance can be
followed to completion while its progress requests are answered by either instance, because every
instance reads the same stored state.

**Why this priority**: The scaling half of the request. It depends on User Stories 1 and 2 being in
place, and a single instance of each already delivers the packaged application.

**Independent Test**: Start with defaults, confirm two instances of each half are running, send a
series of API requests through the load balancer, and confirm from the load balancer's access log
that both backend instances answered. Start a run and confirm its detail view reaches a terminal state.

**Acceptance Scenarios**:

1. **Given** default settings, **When** the system starts, **Then** two frontend instances and two
   backend instances are running.
2. **Given** two backend instances, **When** twenty consecutive API requests are sent through the load
   balancer, **Then** each instance answers at least one of them.
3. **Given** two frontend instances, **When** twenty consecutive screen requests are sent through the
   load balancer, **Then** each instance answers at least one of them.
4. **Given** a run started through the load balancer, **When** its detail is polled until it finishes,
   **Then** it reaches the same terminal state and shows the same four node records regardless of which
   instance answered each poll.
5. **Given** both backend instances start at the same moment against an empty database, **When**
   startup completes, **Then** the database schema has been created exactly once and both instances
   are serving.

---

### User Story 4 - Scale either half up or down (Priority: P2)

A maintainer changes the number of frontend or backend instances through a documented setting or
command, without editing any file, and the load balancer begins sending traffic to the new set of
instances. Scaling down removes instances from rotation without the learner seeing errors for requests
that are not already in flight.

**Why this priority**: "Must be able to scale horizontally" is the requirement; two instances is its
default demonstration. It follows User Story 3 because it generalizes it.

**Independent Test**: Scale the backend to three instances, confirm three are running and all three
answer requests through the load balancer; scale it to one and confirm the application still works.
Repeat for the frontend.

**Acceptance Scenarios**:

1. **Given** the system is running with defaults, **When** the maintainer scales the backend to three
   instances using the documented method, **Then** three backend instances run and each answers
   requests through the load balancer, with no load balancer configuration edited by hand.
2. **Given** the system is running, **When** the maintainer scales either half down to one instance,
   **Then** the application remains usable through the same address.
3. **Given** an instance of either half is stopped or crashes, **When** requests arrive, **Then** the
   load balancer sends them to the remaining healthy instances, and the learner sees no error for new
   requests once the failure has been detected.

---

### User Story 5 - The documentation teaches the packaged system (Priority: P3)

A newcomer reads the README and learns how to start and stop the packaged system, which single address
to open, which one port is published and how to change it, how to scale each half, which environment
variables each provider mode needs in the packaged system, and how the development workflow from
earlier features still works alongside it.

**Why this priority**: Required by the constitution for any fresh-clone path, but it documents the
stories above rather than adding behavior.

**Independent Test**: A reader who has not seen this feature follows only the README to start the
system, complete a run, scale the backend to three, and stop it.

**Acceptance Scenarios**:

1. **Given** the README, **When** a newcomer looks for how to run the application, **Then** the
   packaged start command, the address, and the provider-mode variables are stated in one section.
2. **Given** the README, **When** a developer looks for the development workflow, **Then** the existing
   commands for running the halves outside containers are still documented and still work.

---

### Edge Cases

- **A backend instance dies during a run.** The run it was executing stops advancing and stays in its
  last non-terminal state. Today nothing reclaims such a run, and this feature does not add that. The
  README states the limitation. See Assumptions.
- **Cloud provider mode with no credential.** Backend instances fail at startup naming the missing
  variable, as they do today. The load balancer then reports the backend as unavailable rather than
  serving a partial application silently.
- **Local provider mode.** The model runtime runs inside the system on the internal network only. Pulling
  a model into it is a documented one-time step; its port is not published.
- **All instances of one half are down.** The load balancer answers with a clear unavailable response,
  not a hang until the client times out.
- **A long-polling or slow model call.** The load balancer's upstream timeout is longer than the
  configured model timeout, so a slow summary is never cut off by the balancer before the application's
  own timeout decides.
- **An arbitrary path or a reload.** Served as the application, not a "not found" page (User Story 1,
  scenario 3). The application has no per-screen addresses, so a reload shows the start screen, as it
  does in development.
- **Stored data across restarts.** Runs persist in a named volume and survive a stop and start. A
  documented command removes the data deliberately.
- **Default published port in use.** Overridable through a documented setting (User Story 2,
  scenario 4).
- **Development database port.** The existing development workflow starts the database with its port
  published so the backend can run from the build tool. The packaged system must not publish it. The two
  modes are kept distinct so neither silently inherits the other's exposure.

## Requirements *(mandatory)*

### Functional Requirements

**Packaging and startup**

- **FR-001**: The complete application (database, frontend, backend, load balancer, and the model
  runtime for local mode) MUST start with one documented command from the repository root.
- **FR-002**: Starting the packaged system MUST require only a container runtime on the host. No JDK,
  Node.js, or build tool may be required on the host.
- **FR-003**: The frontend and backend images MUST be built from the repository's source by the same
  build steps the project already uses, so the packaged application is the application under test.
- **FR-004**: Backend instances MUST tolerate the database being unavailable at startup, by waiting or
  retrying until it is ready, rather than exiting permanently.
- **FR-005**: Concurrent startup of several backend instances against the same database MUST apply
  database migrations exactly once and leave every instance serving.
- **FR-006**: Stored runs MUST persist across stopping and starting the system, and removing them MUST
  be a separate, documented, deliberate action.
- **FR-007**: The frontend instances MUST return the application for any path that does not name a
  static file, so a hand-typed path or a reload never yields a "not found" page. Adding per-screen
  addresses to the application is out of scope (FR-024).

**Load balancing and exposure**

- **FR-008**: A single load balancer MUST front both halves: requests for the API path go to backend
  instances, and all other requests go to frontend instances.
- **FR-009**: The load balancer MUST be nginx (named in the request).
- **FR-010**: Exactly one host port MUST be published, belonging to the load balancer. Backend,
  frontend, database, and model runtime ports MUST NOT be published to the host.
- **FR-011**: The published host port MUST be configurable through a documented setting without editing
  a file, with a documented default.
- **FR-012**: The load balancer MUST distribute requests across all running instances of each half, and
  MUST stop sending new requests to an instance that is stopped or failing.
- **FR-013**: When no instance of a half is available, the load balancer MUST return an explicit
  unavailable response within 5 seconds.
- **FR-014**: The load balancer's upstream timeouts MUST exceed the backend's configured model timeout,
  so the application's own timeout, not the balancer's, decides a slow run.

**Scaling**

- **FR-015**: With default settings, two frontend instances and two backend instances MUST run.
- **FR-016**: The number of instances of each half MUST be changeable through a documented setting or
  command, without editing a file, and the load balancer MUST include the new instances without its
  configuration being edited by hand.
- **FR-017**: Any backend instance MUST be able to answer any request for any run, whichever instance
  started it. No request may depend on reaching a particular instance.
- **FR-018**: Each running instance MUST identify itself in its logs, so a maintainer can see which
  instance served a request.

**Health**

- **FR-019**: Each frontend and backend instance MUST expose a readiness signal the system uses to decide
  whether it can receive traffic, and startup ordering MUST use it rather than fixed delays.

**Configuration and secrets**

- **FR-020**: Provider mode, model identifier, base URL, timeout, and credential MUST reach backend
  instances through environment configuration, exactly as today (constitution, Principle II). No
  credential may be written into an image or a committed file.
- **FR-021**: Database credentials used by the packaged system MUST be configurable through the
  environment, with development defaults documented as unsuitable for anything but local use.

**Development workflow and verification**

- **FR-022**: The existing development workflow (database in a container with its port published,
  backend from the build tool, frontend from its development server) MUST keep working unchanged and
  MUST remain documented.
- **FR-023**: The root verification command from feature 003 MUST keep passing, and building the
  frontend and backend images MUST NOT be required for it.
- **FR-024**: No application behavior, API shape, database migration, or screen may change as part of
  this feature, except for what FR-018 and FR-019 add.

**Documentation**

- **FR-025**: The README MUST document starting, stopping, the address to open, the published port and
  how to change it, scaling each half, the environment variables for each provider mode in the packaged
  system, removing stored data, and the limitation that a run on a failed instance is not reclaimed.

### Key Entities

- **Load balancer**: The one externally reachable component. Routes the API path to the backend pool
  and everything else to the frontend pool. Has one published port.
- **Backend pool**: Two or more interchangeable backend instances, each reading and writing the same
  database, each reporting readiness.
- **Frontend pool**: Two or more interchangeable instances serving the built client application, each
  reporting readiness.
- **Database**: One instance with persistent storage, reachable only on the internal network.
- **Model runtime**: Present for local provider mode only, reachable only on the internal network.
- **Internal network**: The private network all components share. Only the load balancer is attached
  to the host.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On a machine with only a container runtime, a newcomer who has set the provider variables
  the documentation names for their chosen mode goes from a clean clone to a completed run in the
  browser using one start command and one address, with zero other tools installed.
- **SC-002**: With the system running, exactly 1 host port is published, and connection attempts from
  the host to each of the other 4 components' ports are refused in 100 percent of attempts.
- **SC-003**: With default settings, 2 frontend and 2 backend instances run, and across 20 consecutive
  requests to each half through the load balancer, every instance answers at least once.
- **SC-004**: A run started through the load balancer reaches its terminal state with all four node
  records in 100 percent of trials while both backend instances serve its polls.
- **SC-005**: Scaling the backend to 3 and then to 1 instance takes one documented action each, with no
  file edited, and the application remains usable after each change.
- **SC-006**: When one of two backend instances is stopped, new requests through the load balancer
  succeed within 10 seconds of the stop and continue to succeed.
- **SC-007**: After a stop and start, 100 percent of previously completed runs are still listed and
  open with their node records intact.
- **SC-008**: The root verification command still passes; no existing test is removed or skipped, and the
  only change in test counts is the readiness endpoint's own test (backend baseline plus one).

## Assumptions

- **Scope of "containerize"**: a local, single-host packaged system started from the repository, suited
  to learners and maintainers. Orchestrated multi-host deployment, a container registry, TLS
  termination, and a production hosting target are out of scope.
- **Published port**: plain HTTP on one port, defaulting to a common unprivileged web port, overridable
  through the environment. HTTPS is out of scope for a local teaching system.
- **Orphaned runs**: a run whose backend instance dies mid-execution is not reclaimed. Detecting and
  failing or resuming such runs is a behavior change to the chain and is left to a later feature. This
  feature documents the limitation.
- **Load balancing strategy**: no session affinity is needed, because all run state lives in the
  database and the frontend is static. The plan chooses the distribution algorithm.
- **Model runtime**: local provider mode keeps the model runtime inside the system. Cloud provider mode
  does not need it; whether it starts by default in cloud mode is a plan decision.
- **Readiness**: adding a readiness endpoint to the backend is permitted by FR-024 and is the only
  backend change expected. The plan decides its form within the Micronaut stack.
- **Verification**: the packaged system is verified by the quickstart scenarios for this feature. Adding
  the image builds or a packaged smoke test to CI is a plan decision; FR-023 only forbids making the
  existing root verification depend on it.
- **Constitution**: the stack section names PostgreSQL started "through a committed container or script
  definition" and no container orchestrator. The plan checks whether adding nginx and container images
  for the application requires an amendment.
