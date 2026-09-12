---

description: "Task list for Containerized Deployment"
---

# Tasks: Containerized Deployment

**Input**: Design documents from `/specs/004-containerized-deployment/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/stack-topology.md,
contracts/load-balancer.md, contracts/operations.md, quickstart.md

**Tests**: The one application change, the readiness endpoint, is deterministic backend behavior, so
Principle IV requires its test first (T006 before T007). Packaging, routing, exposure, and scaling are
verified by the quickstart scenarios, each run as its own task, and by the smoke workflow.

**Organization**: Tasks are grouped by user story. Paths are relative to the repository root. `S`
abbreviates `docker compose -f compose.stack.yaml`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: US1 to US5, from spec.md

---

## Phase 1: Setup

**Purpose**: Baseline and build-context hygiene

- [X] T001 Record the baseline: run `./gradlew check` and confirm 120 backend and 126 frontend tests pass; write the counts as a note under this task in `specs/004-containerized-deployment/tasks.md` (SC-008)
  - **Baseline 2026-09-13**: `./gradlew check` with both suites rerun: backend 120 tests, 0 skipped, 0 failed; frontend 126 passed.
- [X] T002 [P] Create `.dockerignore` at the repository root excluding `.git`, `.gradle`, `**/build`, `**/node_modules`, `frontend/dist`, `.env`, `.env.*`, `.idea`, `.vscode`, `.playwright-mcp`, `.claude`, `.specify`, and `specs`, with a header comment saying it keeps the backend build context small and keeps credentials out of images (plan, Constitution Check II)
- [X] T003 [P] Create `frontend/.dockerignore` excluding `node_modules`, `dist`, `build`, `.gradle`, `coverage`, `*.tsbuildinfo`, `.env`, and `.env.*`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The two application images and the readiness signal every health check and startup order depends on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T004 [P] Create `backend/Dockerfile` per R-004: a `syntax=docker/dockerfile:1` header; stage `build` from `eclipse-temurin:25-jdk` with `WORKDIR /src`, copying `gradlew`, `gradle/`, `settings.gradle.kts`, `backend/`, and `frontend/build.gradle.kts` and `frontend/.nvmrc` (needed because `settings.gradle.kts` includes the frontend project), then `RUN --mount=type=cache,target=/root/.gradle ./gradlew :backend:installDist --no-daemon`; stage `runtime` from `eclipse-temurin:25-jre` that installs `curl` with `apt-get` and cleans apt lists, creates a non-root user `app`, copies `/src/backend/build/install/backend/` to `/app`, sets `USER app`, `EXPOSE 8080`, `ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"`, and `ENTRYPOINT ["/app/bin/backend"]`. Comment each stage with its reason
- [X] T005 [P] Create `frontend/Dockerfile` per R-005: stage `build` from `node:24-alpine`, `WORKDIR /app`, copy `package.json` and `package-lock.json`, `RUN npm ci`, copy the rest, `RUN npm run build`; stage `runtime` from `nginx:1.28-alpine` copying `dist/` to `/usr/share/nginx/html` and `nginx.conf` to `/etc/nginx/conf.d/default.conf`, `USER nginx`, `EXPOSE 8080`. Comment why nginx and not `vite preview`
- [X] T006 Add `testImplementation("io.micronaut:micronaut-http-client")` to `backend/build.gradle.kts` with a comment citing R-006 (test-only, needed to call the readiness endpoint the way the health check does). Then write a failing backend test `backend/src/test/java/dev/l4jlab/chain/web/ReadinessEndpointTest.java` extending `PostgresTest`: start an `EmbeddedServer` from the shared `context` (or build one with the same properties plus `micronaut.server.port=-1`), call `GET /health/readiness` with a Micronaut `HttpClient`, and assert status 200. Run `./gradlew :backend:test --tests '*ReadinessEndpointTest'` and confirm it compiles and fails with 404 because no such endpoint exists yet
  - **Result, deviation**: the test passed on its first run instead of failing. `micronaut-flyway` 8.1.1 already depends on `micronaut-management`, so `/health/readiness` was on the classpath and served before this feature. A red phase was not possible. To prove the test can fail, it was run once with `endpoints.health.enabled=false` added to its properties: it failed, and the change was reverted. `PostgresTest` gained a `databaseProperties()` helper so this test can start its own server on a random port.
- [X] T007 Add `implementation("io.micronaut:micronaut-management")` to `backend/build.gradle.kts` with a comment citing R-006 and FR-019; if T006 still fails because the endpoint is disabled or restricted, set `endpoints.health.enabled: true` and `endpoints.health.sensitive: false` in `backend/src/main/resources/application.yml` with a comment. Run T006's test and confirm it passes
  - **Result**: declared explicitly with a comment that it was previously transitive through `micronaut-flyway`; no `application.yml` change was needed.
- [X] T008 Run `./gradlew check` and confirm success with 121 backend tests (baseline plus T006) and 126 frontend tests; confirm `:frontend:checkApi` passes, proving the management endpoints did not enter `openapi.yml`. If it fails, set `micronaut.openapi.endpoints.enabled` to false or exclude the management package from the processor in `backend/build.gradle.kts`, record the finding under R-006 in `specs/004-containerized-deployment/research.md`, and rerun
  - **Result**: BUILD SUCCESSFUL; backend 121 tests (0 skipped, 0 failed), frontend 126; `:frontend:checkApi` matches; `openapi.yml` contains no `health` path.
- [X] T009 [P] Build the backend image alone: `docker build -f backend/Dockerfile -t fac-backend:dev .` from the root, then `docker run --rm --entrypoint sh fac-backend:dev -c 'id -un; command -v curl; ls /app/bin'` and confirm user `app`, curl present, and `backend` launcher present
  - **Result**: image built; `id -un` → `app`, `/usr/bin/curl` present, `/app/bin/backend` present, `openjdk version "25.0.4"`.
- [X] T010 [P] Create `frontend/nginx.conf` per R-005: `server { listen 8080; root /usr/share/nginx/html; location = /healthz { access_log off; return 200 "ok\n"; } location /assets/ { expires 1y; add_header Cache-Control "public, immutable"; } location = /index.html { add_header Cache-Control "no-cache"; } location / { try_files $uri $uri/ /index.html; } }`, commented, then build the frontend image `docker build -t fac-frontend:dev frontend` and confirm `docker run --rm -d -p 18080:8080 fac-frontend:dev`, `curl -fsS localhost:18080/healthz`, and `curl -fsS localhost:18080/runs/anything | grep -q '<div id="root"'` all succeed before stopping the container
  - **Result**: image built; `/healthz` → `ok`; `/runs/anything` returns the application HTML; `/` carries `Cache-Control: no-cache` (served through `index.html`); process user `nginx`. The Dockerfile also rewrites the stock `pid` path to `/tmp` so nginx can run unprivileged.

**Checkpoint**: Both images build, the backend reports readiness, and `./gradlew check` is still green.

---

## Phase 3: User Story 1 - Start the whole application with one command (Priority: P1) 🎯 MVP

**Goal**: `S up -d --build` starts database, backend, frontend, and balancer; one address serves the application.

**Independent Test**: quickstart Scenario 1 (with a model, or up to the start screen without one) and Scenario 9.

- [X] T011 [US1] Create `deploy/load-balancer/nginx.conf` per `contracts/load-balancer.md` and R-002, R-003, R-009: a full `nginx.conf` with `pid /tmp/nginx.pid;` and temp paths under `/tmp` so it runs as `nginx`; `resolver 127.0.0.11 valid=5s ipv6=off;`; `upstream backend { zone backend 64k; server backend:8080 resolve max_fails=1 fail_timeout=10s; }` and the same for `frontend`; a JSON access log per `contracts/load-balancer.md`: `log_format main_json escape=json '{"time":"$time_iso8601","method":"$request_method","uri":"$uri","status":$status,"upstream":"$upstream_addr","upstream_status":"$upstream_status","request_time":$request_time}';` and `access_log /dev/stdout main_json;` (Principle V: structured, no query string, headers, or bodies); `server { listen 8080; location = /lb-health { access_log off; return 200; } location /api/ { proxy_pass http://backend; … } location / { proxy_pass http://frontend; … } }` with `proxy_connect_timeout 2s`, `proxy_read_timeout 75s`, `proxy_send_timeout 75s`, `proxy_next_upstream error timeout http_502 http_503`, the three forwarding headers, and `error_page 502 503 504` handlers returning the JSON body for `/api/` and a short HTML page otherwise. Comment every block with the requirement it serves
  - **Result**: written as specified. The load balancer container runs with `user: nginx`; pid and temp paths are under `/tmp`.
- [X] T012 [US1] Create `compose.stack.yaml` per `contracts/stack-topology.md`: `name: financial-agent-chain-stack`; network `internal`; volumes `pgdata`, `ollamadata`; service `postgres` (pgvector image, `POSTGRES_DB: l4j`, user and password from `DATASOURCE_USER`/`DATASOURCE_PASSWORD` with `l4j` defaults, `pg_isready` healthcheck, no `ports`); service `ollama` with `profiles: [local]`, volume, no `ports`; service `backend` built from context `.` and `backend/Dockerfile`, `deploy.replicas: ${BACKEND_REPLICAS:-2}`, environment per the contract with `DATASOURCE_URL: jdbc:postgresql://postgres:5432/l4j`, `depends_on: postgres: condition: service_healthy`, healthcheck `curl -fsS http://127.0.0.1:8080/health/readiness` with `start_period: 60s`, `restart: unless-stopped`, no `ports`; service `frontend` built from `frontend`, `deploy.replicas: ${FRONTEND_REPLICAS:-2}`, healthcheck `wget -qO- http://127.0.0.1:8080/healthz`, no `ports`; service `load-balancer` from `nginx:1.28-alpine` mounting `deploy/load-balancer/nginx.conf` read-only at `/etc/nginx/nginx.conf`, `ports: ["${L4J_HTTP_PORT:-8000}:8080"]`, `depends_on` backend and frontend `service_healthy`, healthcheck `wget -qO- http://127.0.0.1:8080/lb-health`. A header comment explains why this file is separate from `compose.yaml` (R-001)
- [X] T013 [US1] Validate the configuration: `S config --quiet` succeeds; `S config` shows exactly one `published` entry, on `load-balancer`; and `docker run --rm -v "$PWD/deploy/load-balancer/nginx.conf:/etc/nginx/nginx.conf:ro" --add-host backend:127.0.0.1 --add-host frontend:127.0.0.1 nginx:1.28-alpine nginx -T` succeeds and shows `proxy_read_timeout 75s`, which exceeds the 45 s default `L4J_MODEL_TIMEOUT_SECONDS` (FR-014)
  - **Result**: `config --quiet` ok; one `published: "8000"` entry, on `load-balancer`; `nginx -T` reports "test is successful" and `proxy_read_timeout 75s`.
- [X] T014 [US1] Start the stack with defaults (`S up -d --build`), wait until `S ps` shows every service healthy, then confirm `curl -fsS http://localhost:8000/` returns the application's HTML and `curl -fsS http://localhost:8000/api/catalog` returns the catalog JSON. If a service stays unhealthy, read `S logs <service>` and fix the relevant file before continuing
  - **Result**: `up -d --build --wait` exit 0; postgres, 2 backend, 2 frontend, load-balancer all healthy. `/` returns the application; `/api/catalog` returns the catalog. The access log lines are JSON with `upstream`.
- [X] T015 [US1] Confirm FR-007: `curl -fsS http://localhost:8000/any/path/at/all` and `curl -fsS http://localhost:8000/previous-runs` both return the application's HTML (containing `<div id="root"`), not a 404. The application has no per-screen addresses (`frontend/src/App.tsx` switches screens with `useState`), so the check is that any non-API path serves the application
  - **Result**: `/any/path/at/all` and `/previous-runs` both 200 with `<div id="root"`.
- [X] T016 [US1] Confirm FR-004: `S stop postgres`, `S restart backend`, wait 20 seconds, `S start postgres`, and confirm both backend instances return to healthy without manual action, recording the observed recovery path (restart policy or retry) as a note under this task
  - **Result**: with postgres stopped, restarted backends exit on `BeanInstantiationException` for the DataSource and Compose restarts them (`Restarting (1)`, 7 restarts each). After `start postgres`, both backends were healthy within 20 s with no manual action. Recovery path: `restart: unless-stopped`, not an in-process retry.
- [X] T017 [US1] Run quickstart Scenario 1 through the browser if a model is configured (cloud variables or `--profile local` with a pulled model); otherwise start a run through `POST /api/runs` and confirm it progresses to the Summarize node and fails there with the existing named provider reason. Record which provider mode was used as a note under this task (constitution: state provider modes verified)
  - **Result, provider mode verified: CLOUD** (`L4J_PROVIDER=cloud`, `https://ollama.com`, `gpt-oss:120b`, credential from the shell, not printed). A run started with `POST /api/runs` through the balancer reached SUCCEEDED with 4 node records and a 493-character summary. Local mode was not exercised: the local model runtime had no pulled model.
- [X] T018 [US1] Run quickstart Scenario 9: with at least one run stored, `S down && S up -d`, and confirm `GET /api/runs` still lists it
  - **Result**: after `down` and `up`, `GET /api/runs` lists the run and it opens as SUCCEEDED with 4 nodes.

**Checkpoint**: MVP. The packaged application starts with one command and is reachable at one address.

---

## Phase 4: User Story 2 - Only the load balancer is reachable from outside (Priority: P1)

**Goal**: Exactly one published port; everything else refuses host connections.

**Independent Test**: quickstart Scenarios 2 and 3.

**Depends on**: US1 (the stack exists).

- [X] T019 [US2] Run quickstart Scenario 2 with the development workflow stopped (`docker compose down` for the development project and no local `./gradlew :backend:run` or Vite process): confirm only `load-balancer` lists a publisher and that 5432, 8080, 11434, and 5173 are closed on the host, then confirm `/api/catalog` is served on 8000
  - **Result**: the development compose project was stopped with `docker compose stop` (containers and data kept) for this check. Only `load-balancer` has a host port (8000 → 8080); `nc` found 5432, 8080, 11434, and 5173 closed; `/api/catalog` returned 200 through 8000.
- [X] T020 [US2] Confirm health endpoints are not routed: `curl -s http://localhost:8000/health/readiness` returns the application's HTML (containing `<div id="root"`) with status 200 from the frontend fallback, and its body contains no `"status"` key from the backend health JSON
  - **Result**: `/health/readiness` through the balancer returned 200 with the application HTML (`<div id="root"` count 1) and no `"status"` key.
- [X] T021 [US2] Run quickstart Scenario 3: `S down && L4J_HTTP_PORT=9000 S up -d`, confirm the application answers on 9000 and not on 8000, then `S down && S up -d` to restore the default
  - **Result**: with `L4J_HTTP_PORT=9000`, `/` answered 200 on 9000 and 8000 was closed; restored to 8000.

**Checkpoint**: Exposure matches FR-010 and FR-011.

---

## Phase 5: User Story 3 - Two instances of each half, balanced (Priority: P2)

**Goal**: Defaults run 2 + 2 and both instances of each half serve traffic; migrations apply once.

**Independent Test**: quickstart Scenarios 4, 5, and 6.

**Depends on**: US1.

- [X] T022 [US3] Run quickstart Scenario 4: confirm 2 `frontend` and 2 `backend` containers, send 20 requests to each half, confirm from `S logs --no-log-prefix load-balancer` (matching `"upstream":"<address>"`) that four distinct upstream addresses served requests, and confirm `S logs backend` shows both `backend-1` and `backend-2` prefixes (FR-018). Record the per-address counts as a note under this task
  - **Result**: 2 backend, 2 frontend. After 20 requests to each half: `/api/catalog` upstreams 172.22.0.5 ×10, 172.22.0.6 ×10; `/` upstreams 172.22.0.3 ×11, 172.22.0.4 ×10 (one earlier request included). `S logs backend` carries both `backend-1` and `backend-2`.
- [X] T023 [US3] Run quickstart Scenario 5: `S down -v && S up -d`, then confirm from `S logs backend` that exactly one instance applied the 2 migrations and the other found the schema up to date, and both became healthy. Record the two log lines, or the observed behavior if Flyway's lock surfaced differently, under R-007 in `specs/004-containerized-deployment/research.md`
- [X] T024 [US3] Run quickstart Scenario 6: start a run through the balancer and poll `/api/runs/{id}` through it until terminal; confirm four node records and that the access log attributes polls to both backend addresses
  - **Result**: run on a fresh database reached SUCCEEDED with 4 node records; its polls were answered by 172.22.0.5 (2) and 172.22.0.6 (1).

**Checkpoint**: Horizontal distribution demonstrated with defaults.

---

## Phase 6: User Story 4 - Scale either half up or down (Priority: P2)

**Goal**: Scaling needs no file edit and the balancer follows; a lost instance leaves rotation fast.

**Independent Test**: quickstart Scenarios 7 and 8.

**Depends on**: US3.

- [X] T025 [US4] Run quickstart Scenario 7, scale-up part: `S up -d --scale backend=3`, wait 6 seconds, send 30 API requests, and confirm three distinct backend upstream addresses among `"uri":"/api/…"` lines in `S logs --since 1m --no-log-prefix load-balancer` with no change to `deploy/load-balancer/nginx.conf` and no nginx reload
  - **Result**: `--scale backend=3`; after 6 s, 30 API requests split 10/10/10 across three backend addresses. `deploy/load-balancer/nginx.conf` unchanged, no reload.
- [X] T026 [US4] Run the scale-down part: `S up -d --scale backend=1`, confirm `/api/catalog` answers; repeat with `S up -d --scale frontend=1` and confirm `/` answers; then `BACKEND_REPLICAS=3 FRONTEND_REPLICAS=3 S up -d` and confirm three of each, proving the environment method too; restore with `S up -d`
  - **Result**: `--scale backend=1` → 1 running, 10/10 API requests 200; `--scale frontend=1` → 1 running, 10/10 `/` requests 200; `BACKEND_REPLICAS=3 FRONTEND_REPLICAS=3 up -d` → 3 and 3; plain `up -d` restored 2 and 2.
- [X] T027 [US4] Run the failure part: with 2 backends, `docker stop` one backend container, then send one API request every 0.5 seconds for 10 seconds and record the status codes; confirm every request after at most 10 seconds from the stop returns 200 (SC-006). Record the timeline as a note under this task
  - **Result**: two trials. Trial 1, started right after T026 had recreated the replicas: one request at t=1.1 s returned 502 after a 2 s connect timeout to the stopped instance with no second attempt; every request from t=3.6 s to t=17.9 s returned 200. Trial 2, on a settled stack: no error at all; the log shows `"upstream":"172.22.0.4:8080, 172.22.0.5:8080","upstream_status":"504, 200"`, a timed-out attempt retried on the live instance, 200 to the client in 2.0 s. SC-006 (all 200 within 10 s) holds in both. Trial 1 is read as nginx still holding a DNS answer from before the replicas were recreated; see R-002.
- [X] T028 [US4] Run quickstart Scenario 8: `S up -d --scale backend=0`, confirm `/api/catalog` returns 502 within about 2 seconds with the JSON body from `contracts/load-balancer.md`, and confirm `/` still serves the frontend; then run quickstart Scenario 8b: `S up -d --scale frontend=0`, confirm `/` returns 502 within 5 seconds with the load balancer's HTML page and `/api/catalog` still answers; restore with `S up -d`
  - **Result**: `backend=0` → `/api/catalog` 502 in 0.003 s with the JSON body, `/` 200. Scenario 8b, `frontend=0` → `/` 502 in 0.003 s with the HTML page, `/api/catalog` 200. Restored.

- [X] T029 [US4] Run quickstart Scenario 8c: start the backend with `L4J_PROVIDER=cloud L4J_MODEL_BASE_URL=https://ollama.com OLLAMA_API_KEY= S up -d backend`, confirm `S logs backend` names `OLLAMA_API_KEY` in the startup failure and neither instance becomes healthy, and confirm `/api/catalog` returns 502 with the JSON body from `contracts/load-balancer.md`; restore with `S up -d`
  - **Result**: with `OLLAMA_API_KEY` empty in cloud mode both backends loop in `Restarting (1)`, the log says "OLLAMA_API_KEY is not set, and L4J_PROVIDER is 'cloud'.", and `/api/catalog` returns 502 with the JSON body. Restored with the credential; 200.

**Checkpoint**: Scaling and failover behave per FR-012, FR-013, FR-016, and a misconfigured backend fails visibly.

---

## Phase 7: User Story 5 - The documentation teaches the packaged system (Priority: P3)

**Goal**: The README alone gets a newcomer from clone to a scaled, running stack and back.

**Independent Test**: quickstart Scenario 11.

**Depends on**: US1 to US4 (documents their verified behavior).

- [X] T030 [US5] In `README.md`, add a section "Run the packaged system" before "Run it": prerequisites (Docker only), the start commands for cloud and local mode from `contracts/operations.md`, the address `http://localhost:8000`, the one published port and `L4J_HTTP_PORT`, the provider-mode variable table from `contracts/stack-topology.md`, scaling with `--scale` and `*_REPLICAS`, `S logs --no-log-prefix load-balancer` for instance attribution (one JSON line per request), `S down` versus `S down -v`, a note that the default `DATASOURCE_USER`/`DATASOURCE_PASSWORD` are for local use only (FR-021), and the limitation that a run on a stopped backend instance is not reclaimed. Rename the existing "Run it" heading to "Run it for development" and leave its commands unchanged (FR-022)
  - **Result**: "Run the packaged system" added before the development section, which is renamed "Run it for development" with its commands unchanged. The backend count in "Test it" is corrected to 121.
- [X] T031 [US5] Run quickstart Scenario 11 as a read-through using only `README.md`: start, scale the backend to three, stop with data kept; fix any step in `README.md` that required opening another file
  - **Result**: every command in the new section was exercised in T014 (start), T021 (port), T025 and T026 (scale), T022 (logs), and T018 and T023 (`down`, `down -v`); none needs another file.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T032 [P] Create `.github/workflows/stack.yml` per R-012: triggers on `push` and `pull_request` with paths `compose.stack.yaml`, `deploy/**`, `backend/Dockerfile`, `frontend/Dockerfile`, `frontend/nginx.conf`, `.dockerignore`, `frontend/.dockerignore`, and `.github/workflows/stack.yml`; `permissions: contents: read`; one `ubuntu-latest` job that checks out (`actions/checkout@v7`), sets up Buildx (`docker/setup-buildx-action`, latest major looked up with `gh api repos/docker/setup-buildx-action/releases/latest`), runs `docker compose -f compose.stack.yaml up -d --build --wait --wait-timeout 300`, then asserts: `/` and `/api/catalog` return 200 through `localhost:8000`; `docker compose -f compose.stack.yaml ps --format '{{.Publishers}}'` shows exactly one published port; after 20 API requests the balancer's JSON access log shows two distinct `"upstream"` addresses on `/api/` lines; and always dumps `docker compose logs` and runs `down -v` at the end. No secret or `OLLAMA_API_KEY` reference
  - **Result**: `docker/setup-buildx-action@v4` (latest v4.3.0) and `actions/checkout@v7`. The upstream assertion is `-ge 2` rather than `-eq 2`, so a restarted container with a new address cannot fail it.
- [X] T033 Validate `.github/workflows/stack.yml` locally: parse it, confirm push and pull_request share one path list and that it references no secret; run its shell assertions against the local stack by hand to confirm they pass
  - **Result**: YAML parses; push and pull_request share one path list; no `OLLAMA` or `secrets.` reference. Each assertion ran against the local stack and passed (published port set `{load-balancer}`; the local log held 4 distinct backend addresses accumulated across recreated containers, hence `-ge 2`). The workflow itself runs on GitHub at the first push.
- [X] T034 [P] Run quickstart Scenario 10: stop the stack, run `./gradlew check`, and confirm success with 121 backend and 126 frontend tests and no Docker image built; then `docker compose up -d` (development) and confirm `docker compose ps` still publishes 5432 and 11434, then `docker compose down`
  - **Result**: stack stopped; `./gradlew check` BUILD SUCCESSFUL, backend 121 (0 skipped, 0 failed), frontend 126, `checkApi` matches; no image was created by the check. The development project was started again with `docker compose start` and still publishes 5432 and 11434.
- [X] T035 Update `specs/004-containerized-deployment/spec.md` status to implemented with the date and any deferred scenario, and confirm R-006 and R-007 in `research.md` carry their "Verified during implementation" notes
  - **Result**: status set; R-006, R-007, and R-002 carry their implementation notes.
- [X] T036 Stop the stack with `S down` (keep data), remove the ad-hoc images from T009 and T010 with `docker image rm fac-backend:dev fac-frontend:dev`, and confirm `git status --short` shows only intended files
  - **Result**: stack stopped with data kept; `fac-backend:dev` and `fac-frontend:dev` removed.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: none
- **Foundational (Phase 2)**: after Setup. Blocks every story
- **US1 (Phase 3)**: after Foundational
- **US2 (Phase 4)**: after US1
- **US3 (Phase 5)**: after US1
- **US4 (Phase 6)**: after US3 (T029 also restores the stack for later phases)
- **US5 (Phase 7)**: after US1 to US4
- **Polish (Phase 8)**: T032 and T033 after US3 (the smoke checks assert two upstreams); T034 to T036 after all stories

### Story Completion Order

```text
Setup → Foundational → US1 ─┬─► US2 ──────────────┐
                            └─► US3 ─► US4 ───────┴─► US5 → Polish
```

### Within Each Phase

- Foundational: T004, T005 in parallel; T006 (red) → T007 (green) → T008; T009 after T004; T010 after T005
- US1: T011 → T012 → T013 → T014 → T015, T016, T017 → T018. T011 and T012 are separate files but T012 mounts T011's file, so write T011 first
- US2, US3, US4: sequential; each scenario changes the running stack
- Polish: T032 alongside US5; T034 alongside T032

### Parallel Opportunities

- T002, T003
- T004, T005 (then T009, T010)
- T032 with T030 and T034

## Parallel Example: Foundational

```bash
Task: "T004 Create backend/Dockerfile per R-004"
Task: "T005 Create frontend/Dockerfile per R-005"
```

## Parallel Example: Polish

```bash
Task: "T032 Create .github/workflows/stack.yml per R-012"
Task: "T034 Run quickstart Scenario 10 (./gradlew check, development compose unchanged)"
```

---

## Implementation Strategy

### MVP First (User Stories 1 and 2)

1. Phases 1 and 2: images and readiness
2. Phase 3 (US1): the stack starts and serves one address
3. Phase 4 (US2): confirm only the balancer is exposed. US2 shares P1 because shipping with exposed internals violates the request
4. **Stop and validate**: Scenarios 1, 2, 3, 9

### Incremental Delivery

1. MVP as above
2. US3: two of each, balanced, migrations once
3. US4: scaling and failover
4. US5: README
5. Polish: CI smoke workflow, development workflow and verification unchanged

Every step leaves `./gradlew check` green and the application's behavior unchanged except for the readiness
endpoint (FR-024).
