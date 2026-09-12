# Phase 0 Research: Containerized Deployment

**Date**: 2026-09-13 | **Plan**: [plan.md](./plan.md)

All unknowns from Technical Context are resolved here. No NEEDS CLARIFICATION remains. Facts marked
**Verified** were checked on the maintainer's machine on 2026-09-13 (Docker Engine 29.6.1, Docker
Compose v5.2.0).

## R-001: One compose file for the packaged system, separate from development

**Decision**: Add `compose.stack.yaml` at the repository root for the packaged system. Leave the
existing `compose.yaml` untouched as the development file. Start the stack with
`docker compose -f compose.stack.yaml up -d --build`.

**Rationale**: FR-022 keeps the development workflow unchanged, and that workflow publishes the
database port (5432) and the model runtime port (11434) to the host so the backend can run from Gradle.
FR-010 forbids exactly those ports in the packaged system. Two files make the two exposure models
visible side by side, and neither can inherit the other's ports by accident. The stack file declares its
own `name:`, so its containers, network, and volumes never collide with the development project's.

The PostgreSQL service is written out again in the stack file rather than pulled in with `extends` and
`ports: !reset []`. It costs about a dozen repeated lines, and a learner reading the stack file sees
everything the stack runs without chasing a second file and a reset tag (Principle I).

**Alternatives considered**:

- Profiles in one `compose.yaml`. Profiles select services but cannot change a service's published
  ports, so the database would need two service definitions under two names anyway.
- Making `compose.yaml` the stack and moving development to `compose.dev.yaml`. Changes the documented
  development command, which FR-022 forbids.

## R-002: The load balancer and dynamic scaling

**Decision**: One `nginx:1.28-alpine` container, configured by `deploy/load-balancer/nginx.conf`, with
two upstreams that re-resolve their service names through Docker's embedded DNS:

```text
resolver 127.0.0.11 valid=5s ipv6=off;
upstream backend  { zone backend 64k;  server backend:8080  resolve max_fails=1 fail_timeout=10s; }
upstream frontend { zone frontend 64k; server frontend:8080 resolve max_fails=1 fail_timeout=10s; }
```

`/api/` goes to `backend`; everything else goes to `frontend`. Only this container publishes a port.

**Rationale**: With a plain `server backend:8080;`, nginx resolves the name once at startup. After
`--scale backend=3`, the third instance would never receive traffic until nginx was reloaded, which
violates FR-016. The `resolve` parameter on an upstream server, with a shared-memory `zone`, makes
nginx re-query DNS every `valid` period and balance across every address Docker returns for the service
name. It entered open-source nginx in 1.27.3.

**Verified**: `nginx -v` in `nginx:1.28-alpine` reports `nginx/1.28.3`, and `nginx -t` accepts an
upstream `server … resolve;` with a `zone` and a `resolver`.

Round robin is the default algorithm and is enough: no session affinity is needed (spec Assumptions),
because run state is in PostgreSQL and the frontend is static files.

`max_fails=1 fail_timeout=10s` takes a failing instance out of rotation for ten seconds after one
failed attempt, which, with DNS dropping a stopped container within `valid=5s`, meets SC-006.
`proxy_next_upstream error timeout http_502 http_503` retries a failed attempt on another instance.
nginx never retries a non-idempotent request (a `POST /api/runs`) once it has been sent upstream,
unless `non_idempotent` is set, which it is not, so a retry cannot start the same run twice.

**Observed during implementation (T027, 2026-09-13)**: on a settled stack, stopping one of two backends
produced no client error: the access log shows a connect timeout on the stopped address retried on the live
one (`"upstream_status":"504, 200"`, 2.0 s). In a trial run seconds after replicas had been recreated, one
request instead returned 502 after trying only the stopped address, and every later request succeeded. The
likely cause is the 5-second DNS validity window: nginx briefly held a peer list from before the
recreation. SC-006 (success within 10 s) held in both trials. Lowering `valid` would narrow the window at
the cost of more DNS queries; it is left at 5 s.

**Alternatives considered**:

- Resolving through a variable in `proxy_pass`. Works, but gives no `max_fails` accounting and hides
  the upstream behind string interpolation.
- Traefik or HAProxy with Docker service discovery. nginx is named in the request (FR-009).
- Reloading nginx after each scale command. A manual step the maintainer must remember, which FR-016
  forbids.

## R-003: Timeouts and failure responses at the balancer

**Decision**: `proxy_connect_timeout 2s`, `proxy_read_timeout 75s`, `proxy_send_timeout 75s`. When no
upstream address answers, nginx returns `502` immediately; a custom JSON body for `/api/` and a short
HTML page for everything else say the service is unavailable.

**Rationale**: FR-014 requires the balancer's read timeout to exceed the model timeout
(`L4J_MODEL_TIMEOUT_SECONDS`, default 45). No current API call blocks on the model, because runs execute
off the request thread, but the margin keeps that true if a future endpoint does. A two-second connect
timeout makes a dead instance fail fast and fall through to the next (FR-013).

## R-004: Backend image

**Decision**: `backend/Dockerfile`, built with the repository root as context, in two stages:

1. `eclipse-temurin:25-jdk` runs `./gradlew :backend:installDist --no-daemon` with a BuildKit cache mount
   on `/root/.gradle`.
2. `eclipse-temurin:25-jre` receives `backend/build/install/backend/`, installs `curl` for the health
   check, runs as a non-root user, and starts `bin/backend`.

**Rationale**: FR-003 requires the image to be built by the project's own build. `installDist` comes from
the `application` plugin the Micronaut plugin already applies, and its output is a readable launcher
script plus a `lib/` directory, so a learner can open the image and see exactly what runs. The context is
the root because `settings.gradle.kts` includes both subprojects and the wrapper lives at the root.

**Verified**: `eclipse-temurin:25-jre` reports `openjdk version "25.0.4"` and contains neither `curl` nor
`wget`, so the health check needs one installed.

**Alternatives considered**:

- The Micronaut Gradle plugin's `dockerBuild` task. Requires the JDK and Docker on the host to build the
  image, which FR-002 rules out for starting the stack, and generates a Dockerfile a learner never sees.
- A fat jar. Adds the Shadow plugin for no gain over `installDist`.
- A Java-based health probe instead of `curl`. Starts a JVM every health interval.

## R-005: Frontend image and instance

**Decision**: `frontend/Dockerfile`, context `frontend/`, in two stages:

1. `node:24-alpine` runs `npm ci` and `npm run build` (the existing `tsc -b && vite build`).
2. `nginx:1.28-alpine` serves `dist/` on port 8080 with `frontend/nginx.conf`: `try_files $uri /index.html`
   so any path returns the application (FR-007; the application has no per-screen addresses), long cache headers for hashed assets, no cache for `index.html`, and
   `location = /healthz { return 200; }` for readiness.

The instance runs as the unprivileged `nginx` user on 8080, not 80.

**Rationale**: The build output is static files, so a small web server is the right runtime; Node is not
needed after the build. Using nginx for both the frontend instances and the balancer means one
configuration language in the stack. `node:24` matches `frontend/.nvmrc`.

**Alternatives considered**: `vite preview` in a Node container. Documented by Vite as unsuitable for
production serving, and heavier.

## R-006: Readiness signal for the backend

**Decision**: Add `io.micronaut:micronaut-management` to `backend/build.gradle.kts`, versioned by the
Platform BOM. It provides `GET /health`, `/health/liveness`, and `/health/readiness`; readiness includes
the datasource check. The compose health check calls `curl -fsS http://127.0.0.1:8080/health/readiness`.
The load balancer routes only `/api/`, so health endpoints are not reachable from outside.

**Rationale**: FR-019 requires readiness to gate traffic and startup order. A hand-written controller
would duplicate what the framework's management module already does, and the constitution requires
Micronaut to own HTTP and configuration. Principle I's dependency test passes: the module does not hide
the agent loop or any mechanism this project teaches.

**Verified**: `micronaut-management` is managed by `micronaut-platform-5.1.5.pom` in the Gradle cache.

**Test**: the readiness test calls the endpoint over HTTP, because HTTP is exactly how the compose health
check and the balancer's operators reach it; a bean-level call would pass even if the endpoint were not
exposed. That needs `testImplementation("io.micronaut:micronaut-http-client")`, versioned by the Platform
BOM. It is test-only, adds nothing to the image, and hides no mechanism the project teaches (Principle I;
constitution, Additional constraints). No existing test uses an HTTP client today; the controller tests
call beans directly, which stays as it is.

**Found during implementation (T006, 2026-09-13)**: `micronaut-management` was already on the runtime
classpath before this feature, brought in transitively by `micronaut-flyway` 8.1.1, so `/health/readiness`
already answered. The readiness test therefore passed on its first run; it was shown to fail with
`endpoints.health.enabled=false`. The module is still declared explicitly, so the readiness signal the stack
depends on is visible in the build file and does not rest on another module's dependency choices. The
generated `openapi.yml` contains no health path, and `:frontend:checkApi` passes.

**Verify during implementation**: that the management endpoints do not appear in the generated OpenAPI
description, which `:frontend:checkApi` would report as drift; and the exact readiness response when the
database is down.

## R-007: Database startup, migrations, and several backends

**Decision**: Backend services declare `depends_on: postgres: condition: service_healthy` and
`restart: unless-stopped`. Flyway stays as today, running on each instance's startup.

**Rationale**: `service_healthy` covers FR-004 for a normal start; `restart: unless-stopped` covers a
database that becomes unavailable later or a slow first initialization. Flyway takes a PostgreSQL
advisory lock around migration, so two instances starting together serialize: the first applies
V1 and V2, the second finds the schema current (FR-005).

**Verified during implementation (T023, 2026-09-13)**: after `down -v` and `up`, `backend-2` logged
"Current version of schema \"public\": << Empty Schema >>", migrated to v1 and v2, and "Successfully applied 2
migrations"; `backend-1`, one second later, logged "Current version … : 2" and "Schema \"public\" is up to date.
No migration necessary." Both became healthy.

**Verify during implementation**: start two backend replicas against an empty volume and confirm one
"Successfully applied 2 migrations" and one "Schema … is up to date" in the logs.

## R-008: Instance count and scaling interface

**Decision**: `deploy.replicas: ${FRONTEND_REPLICAS:-2}` and `${BACKEND_REPLICAS:-2}` in the stack file.
Maintainers scale with either the environment variables or
`docker compose -f compose.stack.yaml up -d --scale backend=3`. Replicated services declare no
`container_name` and publish no port, which replicas require.

**Rationale**: FR-015 and FR-016. Both methods need no file edit, and R-002 makes the balancer follow
either one.

## R-009: Instance identity

**Decision**: Rely on Compose's per-replica log prefix (`backend-1`, `backend-2`) for FR-018, and write the
balancer's access log as one JSON object per line (`log_format main_json escape=json`) including
`upstream` (`$upstream_addr`) and `upstream_status`, so a maintainer can count which instance served each
request (SC-003) with `docker compose logs load-balancer`.

**Why JSON**: Principle V requires structured logs. Its section is about agent runs, but a plain combined
log line is exactly the kind of unstructured log it rules out, and `escape=json` makes the balancer's log
structured at the cost of one directive. The format logs `$uri` (path without query string) and no headers
or bodies, so nothing sensitive is written.

**Rationale**: Both need no application change. A response header naming the upstream would expose
internal addresses to every browser, which a local teaching stack does not need.

## R-010: Model runtime and provider configuration

**Decision**: The stack's `ollama` service sits behind the Compose profile `local`. Backend environment
defaults in the stack: `L4J_PROVIDER=${L4J_PROVIDER:-local}`,
`L4J_MODEL_BASE_URL=${L4J_MODEL_BASE_URL:-http://ollama:11434}`, and the other `L4J_*` variables and
`OLLAMA_API_KEY` passed through from the environment or a local `.env`, which is already ignored by git.
`DATASOURCE_URL` points at `jdbc:postgresql://postgres:5432/l4j`; `DATASOURCE_USER` and
`DATASOURCE_PASSWORD` default to the development values and are overridable (FR-021).

**Rationale**: Cloud mode needs no multi-gigabyte model runtime, and local mode should not force a pull
on a learner using cloud mode. A profile makes that one flag, `--profile local`. The defaults mirror
`application.yml`, so the stack's behavior with no variables set is the application's documented
default. No credential enters an image or a committed file (FR-020).

## R-011: Published port

**Decision**: `"${L4J_HTTP_PORT:-8000}:8080"` on the load balancer, which listens on 8080 inside the
container as an unprivileged user.

**Rationale**: 8080 on the host is where the development backend runs, which caused a port conflict
earlier in this project, and 5173 is Vite's. 8000 collides with neither, so the stack and the
development workflow can run at the same time.

## R-012: Continuous integration for the stack

**Decision**: Add `.github/workflows/stack.yml`, triggered by changes to `compose.stack.yaml`,
`deploy/**`, `backend/Dockerfile`, `frontend/Dockerfile`, `frontend/nginx.conf`, `.dockerignore`,
`frontend/.dockerignore`, and the workflow itself. It builds the images, starts the stack, waits for
health, and runs the smoke checks from the quickstart: the start screen and `/api/catalog` through the
balancer, exactly one published port, and both backend replicas seen in the access log.

**Rationale**: A Dockerfile can break without any Java or TypeScript change, and the existing workflows
would never notice. Filtering to packaging paths keeps image builds off ordinary code changes, and FR-023
keeps `./gradlew check` free of Docker. The smoke run uses the default `local` provider without the
`local` profile, so it needs no credential and no model; it checks packaging, not summaries.

## R-013: Constitution

**Decision**: No amendment.

**Rationale**:

- The stack section lists runtimes and frameworks. nginx and container images add no language, build
  tool, application framework, agent framework, datastore, or inference provider, and replace none.
- Principle II already requires PostgreSQL to be startable through a committed container definition;
  the stack adds a second such definition, not a second datastore.
- "Dependency additions MUST be justified against Principle I": `micronaut-management` is justified in
  R-006. nginx is infrastructure outside the application and is required by the request.
- Principle II: "a fresh clone MUST reach a running system using only documented commands plus the
  credentials the documentation names." The stack strengthens this: one command, and FR-025 documents
  the variables.
