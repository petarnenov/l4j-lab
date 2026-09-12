# Implementation Plan: Containerized Deployment

**Branch**: `004-containerized-deployment` | **Date**: 2026-09-13 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-containerized-deployment/spec.md`

## Summary

Package the application as a single-host container stack started by one command. A new
`compose.stack.yaml` runs PostgreSQL, two backend replicas, two frontend replicas, and an nginx load
balancer, all on a private network, with the balancer as the only published port. The balancer
re-resolves each service name through Docker DNS, so `--scale` changes take effect without touching its
configuration. The backend image is built by the project's own Gradle `installDist`; the frontend image is
built by `npm run build` and served by nginx. The only application change is the Micronaut management
module for a readiness endpoint. The existing development `compose.yaml`, `./gradlew check`, and all
application behavior stay as they are.

## Technical Context

**Language/Version**: Java 25 (backend, unchanged); TypeScript 5.7 with Node.js 24 for the frontend build
(unchanged); nginx configuration; Compose file format as supported by Docker Compose v2 and later (verified
on v5.2.0).

**Primary Dependencies**: `nginx:1.28-alpine` (1.28.3 verified, supports upstream `resolve`);
`eclipse-temurin:25-jdk` and `:25-jre` (25.0.4 verified); `node:24-alpine`; `pgvector/pgvector:pg17`
(existing); `ollama/ollama` (existing, profile `local`); `io.micronaut:micronaut-management` via the
Micronaut Platform BOM 5.1.5 (verified managed).

**Storage**: PostgreSQL in the stack's own named volume `pgdata`. No schema or migration change.

**Testing**: No existing test changes (baseline 120 backend, 126 frontend). The readiness endpoint gets one
backend test that calls it over HTTP, which adds `micronaut-http-client` to the test classpath only (R-006),
so the backend count becomes 121 (SC-008). The feature is validated by the scenarios in
[quickstart.md](./quickstart.md), and a new path-filtered CI workflow runs the packaging smoke checks (R-012).

**Target Platform**: A single Docker host: developer machines on macOS, Linux, and Windows with Docker
Desktop or Engine; GitHub-hosted Ubuntu runners for the smoke workflow.

**Project Type**: Web application (backend plus frontend) with a container packaging layer.

**Performance Goals**: A stopped instance leaves rotation within 10 seconds (SC-006); a scaled-up instance
joins within 5 seconds (DNS `valid=5s`).

**Constraints**: One published host port. No credential in any image or committed file. `./gradlew check`
must not require Docker images. No application behavior change beyond the readiness endpoint.

**Scale/Scope**: Five services (one optional), two built images, three nginx or compose configuration files,
one CI workflow, one backend dependency plus one test-only dependency, README section.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Checked against constitution v2.2.0.

| Principle | Gate | Status |
|-----------|------|--------|
| I. Learning-First Transparency | Mechanism readable end to end | PASS. Routing, upstream membership, timeouts, and failure responses are in one commented `nginx.conf`. Images are plain multi-stage Dockerfiles running the project's own build; no generated Dockerfile (R-004). |
| I. Learning-First Transparency | Agent loop stays visible in project code | PASS. Untouched. |
| I. Learning-First Transparency | No dependency added merely to save lines | PASS. `micronaut-management` supplies a readiness signal FR-019 requires and hides no taught mechanism (R-006). No reverse-proxy or service-discovery framework beyond the nginx the request names. |
| II. Provider-Agnostic Inference | Endpoint, model, credential are configuration | PASS. Passed through the environment with the same names and defaults as development (R-010). |
| II. Provider-Agnostic Inference | Credentials never committed or baked | PASS. `OLLAMA_API_KEY` comes from the shell or the git-ignored `.env`; Dockerfiles copy no environment file. `.dockerignore` excludes `.env*`. |
| II. Provider-Agnostic Inference | Missing configuration fails at startup | PASS, unchanged. A cloud-mode backend without a credential still aborts naming the variable; its readiness never turns healthy and the balancer returns 502. |
| II. Provider-Agnostic Inference | Plan states what leaves the machine | PASS. Unchanged from feature 001: only the summarizing prompt, and only in cloud mode. The stack adds no egress. |
| II. Provider-Agnostic Inference | PostgreSQL with pgvector, no second datastore | PASS. Same image; one database per project. |
| II. Provider-Agnostic Inference | PostgreSQL startable through a committed container definition; fresh clone reaches a running system with documented commands | PASS, strengthened. One command starts the whole application (FR-001). |
| II. Provider-Agnostic Inference | Docs state required variables per provider mode | PASS. `contracts/stack-topology.md` and FR-025. |
| III. Protocol Contracts | Contracts from a single source | PASS. No API change. R-006 flags that management endpoints must not enter the OpenAPI description; `:frontend:checkApi` enforces it. |
| IV. Test-First | Deterministic behavior has failing tests first | PASS. The readiness endpoint gets a backend test before the dependency is wired; the test-only HTTP client it needs is justified in R-006. Packaging is validated by quickstart scenarios and the smoke workflow. |
| IV. Test-First | Non-model tests need no credential or network to a provider | PASS. The smoke workflow runs the default local provider without the model runtime and makes no model call. |
| V. Observable Agent Runs | Logs are structured, no secrets or bodies | PASS. Backend logging is unchanged. The balancer's access log is written as one JSON object per line (R-009), carrying the upstream address and status but no request or response body, headers, or query string. |
| Stack | Locked entries respected | PASS. No language, framework, build tool, datastore, or provider added or replaced (R-013). |
| Stack | Gradle is the only backend build tool; documented commands go through the wrapper | PASS. The backend image runs `./gradlew :backend:installDist` inside the build stage. |
| Stack | Micronaut owns HTTP, configuration, and health | PASS. Readiness comes from Micronaut's management module, not a hand-written controller. |
| Stack | Configuration through environment variables with documented defaults | PASS. |
| Additional | Dependency additions justified against Principle I | PASS. R-006. |
| Workflow | Spec and plan exist before implementation | PASS. |

**Result**: all applicable gates pass. No entry in Complexity Tracking.

### Data Egress

Nothing new leaves the machine. In cloud mode, backend instances send the same summarizing prompt they send
in development. The smoke workflow sends nothing to a model provider.

### Post-Design Re-Check

Re-evaluated after Phase 1, against the artifacts now on disk.

- **Principle I**: `data-model.md` draws the whole stack and its one published port in one diagram, and
  `contracts/load-balancer.md` lists every routing and timeout decision a reader would otherwise have to
  infer from nginx directives.
- **Principle II**: `contracts/stack-topology.md` gives both provider modes their exact variables and start
  flags. The default `local` provider without `--profile local` starts cleanly and fails only when a run
  reaches the model, with the existing named reason, which is the same behavior as development without
  Ollama running.
- **Principle III**: R-006 records the OpenAPI risk from the new module, with the existing drift check as the
  guard.
- **Principle IV**: `quickstart.md` Scenario 10 keeps `./gradlew check` and its counts as a gate, and the
  smoke workflow keeps packaging verified without a credential.
- **Known limitation** carried from the spec: orphaned runs on a stopped backend instance are documented,
  not solved.

No gate changed status. Complexity Tracking stays empty.

## Project Structure

### Documentation (this feature)

```text
specs/004-containerized-deployment/
├── plan.md                  # This file
├── research.md              # Phase 0: R-001 … R-013
├── data-model.md            # Components, startup order, rules, instance lifecycle
├── quickstart.md            # Validation scenarios 1 to 11, with 8b and 8c
├── contracts/
│   ├── stack-topology.md    # Services, ports, volumes, environment, provider modes
│   ├── load-balancer.md     # Routing, upstream behavior, failure responses, access log
│   └── operations.md        # Start, scale, stop, and development commands
├── checklists/
│   └── requirements.md
└── tasks.md                 # Created by /speckit-tasks, not by this command
```

### Source Code (repository root)

```text
compose.stack.yaml                        # NEW: the packaged system (R-001, R-008, R-010, R-011)
compose.yaml                              # UNCHANGED: development database and model runtime
.dockerignore                             # NEW: for the backend build context (root)
README.md                                 # MODIFY: packaged system section (FR-025)
deploy/
└── load-balancer/
    └── nginx.conf                        # NEW: routing, upstreams, timeouts, failure pages (R-002, R-003, R-009)
.github/workflows/
└── stack.yml                             # NEW: build images and run smoke checks (R-012)
backend/
├── Dockerfile                            # NEW: Gradle installDist → JRE runtime (R-004)
├── build.gradle.kts                      # MODIFY: add micronaut-management, test-only micronaut-http-client (R-006)
└── src/
    ├── main/resources/application.yml    # MODIFY: health endpoint settings if needed (R-006)
    └── test/java/dev/l4jlab/chain/web/
        └── ReadinessEndpointTest.java    # NEW: readiness answers 200 with a reachable database
frontend/
├── Dockerfile                            # NEW: npm build → nginx runtime (R-005)
├── nginx.conf                            # NEW: static serving, SPA fallback, /healthz (R-005)
└── .dockerignore                         # NEW
```

**Structure Decision**: Packaging files sit beside what they package: each half's `Dockerfile` in its own
directory, the balancer's configuration under `deploy/load-balancer/`, and the stack file at the root beside the
development `compose.yaml`. No `docker/` or `infra/` tree duplicates the existing layout.

## Complexity Tracking

No constitution violations to justify.
