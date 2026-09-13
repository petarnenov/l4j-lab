# Contract: Makefile Targets

**Feature**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md) | **Research**: [research.md](../research.md)

"Stack" below means the packaged system (spec US3). All targets run from the repository root. `S` abbreviates `docker compose -f compose.stack.yaml`. `W` is
`$(GRADLEW)`, which defaults to `./gradlew`. "Needs" lists what `scripts/make/require.sh` checks before
anything starts (R-005). The self-test (R-010) asserts this table: each target invokes exactly the command
shown, in order.

## Help

| Target | Needs | Runs | Notes |
|--------|-------|------|-------|
| `help` (default) | — | awk over the Makefile | Prints groups and descriptions; starts nothing |

## Development

| Target | Needs | Checks | Runs |
|--------|-------|--------|------|
| `dev` | docker, java, node, npm | 8080 and 5173 free | `docker compose up -d --wait`, then `scripts/make/dev.sh` (`W :backend:run` and `cd frontend && npm install && npm run dev`, labeled; R-003) |
| `dev-backend` | docker, java | 8080 free | `docker compose up -d --wait`, then `W :backend:run` |
| `dev-frontend` | node, npm | 5173 free | `cd frontend && npm install && npm run dev` |
| `deps-up` | docker | — | `docker compose up -d --wait` |
| `deps-down` | docker | — | `docker compose down` (named volumes kept) |

## Packaged system

| Target | Needs | Checks | Runs |
|--------|-------|--------|------|
| `up` | docker | published port free unless this stack's balancer is running (R-006) | `S up -d --build --wait` |
| `up-local` | docker | as `up` | `S --profile local up -d --build --wait`. The stack's default provider is local, and cloud settings in the shell or `.env` override it |
| `pull-model` | docker | — | prints `Pulling <model> ...`, then `S --profile local exec ollama ollama pull <L4J_MODEL_ID from S config>`. A cloud model id in the shell would be pulled too, so unset cloud settings for local use |
| `status` | docker | — | `S ps` |
| `logs` | docker | — | `S logs` |
| `logs-lb` | docker | — | `S logs --no-log-prefix load-balancer` |
| `scale` | docker | `BACKEND_REPLICAS` or `FRONTEND_REPLICAS` set, else fail naming both | `S up -d --wait` (compose reads the variables) |
| `down` | docker | — | `S --profile local down` (data kept; the profile makes it stop a model runtime started by `up-local`) |
| `reset` | docker | confirmation (R-004) | `S --profile local down -v` (stored runs and pulled models deleted) |

The published port defaults to **8866**, declared once in `compose.stack.yaml` and overridable with
`L4J_HTTP_PORT` (FR-011a). The Makefile never names the number.

## Verification

| Target | Needs | Runs |
|--------|-------|------|
| `check` | java, node, npm | `W check` |
| `test` | java, node, npm | `W :backend:test :frontend:test` |
| `test-backend` | java | `W :backend:test` |
| `test-frontend` | node, npm | `cd frontend && npm test` |
| `test-live` | java | `W :backend:liveTest` (skips with its named reason when no provider is configured) |
| `check-api` | java, node, npm | `W :frontend:checkApi` |

## Maintenance

| Target | Needs | Checks | Runs |
|--------|-------|--------|------|
| `generate-api` | java, node, npm | — | `W :backend:classes`, then `cd frontend && npm run generate:api` |
| `lint` | node, npm | — | `cd frontend && npm run lint` |
| `format` | node, npm | — | `cd frontend && npm run format` (rewrites files; R-009) |
| `golden` | java | confirmation (R-004) | `GOLDEN_WRITE=1 W :backend:test --tests '*GoldenRunSnapshotTest' --rerun` |

## Inputs the Makefile passes through, never defines

| Variable | Read by | Used for |
|----------|---------|----------|
| `L4J_PROVIDER`, `L4J_MODEL_BASE_URL`, `L4J_MODEL_ID`, `L4J_MODEL_TIMEOUT_SECONDS`, `OLLAMA_API_KEY` | application, compose (shell or `.env`) | provider mode |
| `L4J_HTTP_PORT` | compose | published port (default 8866) |
| `BACKEND_REPLICAS`, `FRONTEND_REPLICAS` | compose | instance counts (default 2) |
| `DATASOURCE_USER`, `DATASOURCE_PASSWORD` | compose | stack database credentials |
| `CONFIRM` | `scripts/make/confirm.sh` | `yes` skips the prompt for `reset` and `golden` |
| `GRADLEW` | Makefile | wrapper path, overridden only by the self-test |
| `JAVA_HOME` | `require.sh`, Gradle wrapper | an alternative to `java` on the `PATH` for launching the wrapper |
| `DEV_DEBUG` | `scripts/make/dev.sh` | test-only: `1` prints the two process group ids after start (self-test U3 check) |

## Behavior guarantees

- **Exit status**: a target fails exactly when its delegated command fails. Make exits 2 and prints
  `Error N` with the underlying status (R-007).
- **Interrupt**: one Ctrl+C during `dev` stops both processes. `dev.sh` exits 130, and make reports
  `Error 130` and exits 2. If either half exits by itself, the other is stopped, and `dev.sh` exits 1 naming
  the half (make exits 2) (R-003).
- **Refusal without a terminal**: `reset` and `golden` without `CONFIRM=yes` and without a terminal invoke
  nothing. `confirm.sh` exits 1, and make reports `Error 1` and exits 2.
- **Statuses in general**: every status named above is the script's or the delegated command's. The status
  of `make` itself is 0 on success and 2 on any failure (R-007).
- **Credentials**: no target prints an environment value, and the Makefile and scripts contain none.
- **Compatibility**: GNU Make 3.81 and later, bash 3.2 and later, POSIX awk (FR-017).

## Error messages

| Situation | Message (shape) |
|-----------|-----------------|
| Tool absent | `Missing tool: <tool>. <where to get it>. Nothing was started.` |
| Port taken (dev) | `Port <port> is in use. The backend needs it. Stop what is listening on it and run again.` (or "The frontend dev server needs it.") |
| Port taken (stack) | `Port <port> is in use. Stop what is listening on it, or choose another port with L4J_HTTP_PORT=<port> make up.` |
| `scale` without counts | `Set BACKEND_REPLICAS and/or FRONTEND_REPLICAS, e.g. make scale BACKEND_REPLICAS=3.` |
| Destructive, no terminal | `<target> deletes <what>. Refusing without a terminal; run with CONFIRM=yes to proceed.` |
| Half exited in `dev` | `[dev] <backend|frontend> stopped (status N); stopping <other>.` |
