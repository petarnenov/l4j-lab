---

description: "Task list for feature 006: Makefile entry point"
---

# Tasks: Makefile Entry Point

**Input**: Design documents from `specs/006-makefile-entrypoint/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/make-targets.md](./contracts/make-targets.md), [quickstart.md](./quickstart.md)

**Tests**: Requested. The plan (R-010) requires `scripts/make/selftest.sh` to be written before the code it
checks, per Principle IV. Each story adds its cases first, confirms they fail, then implements.

**Organization**: Tasks are grouped by user story so each story can be implemented and tested on its own.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an incomplete task)
- **[Story]**: The user story the task belongs to (US1 to US4)

## Conventions used in every task

- Work on `main`, from the repository root.
- Every script is bash 3.2 compatible: no `wait -n`, no associative arrays, no `${var,,}`, no `mapfile`.
  Each script starts with `#!/usr/bin/env bash` and `set -euo pipefail`, except `dev.sh`, which uses
  `set -uo pipefail` (see T010). Every script is committed executable (`chmod +x`).
- The Makefile must stay GNU Make 3.81 compatible: no `.ONESHELL`, no `$(file ...)`, no `::=`, and only
  POSIX awk.
- Recipes use a literal tab.
- `S` means `docker compose -f compose.stack.yaml`.
- The exact command of every target is in `contracts/make-targets.md`, and the error message shapes are in
  that file's "Error messages" table.
- No file, recipe, or test prints an environment value. The only credential-related variable ever named is
  `OLLAMA_API_KEY`, and only in documentation.
- The self-test runs with `bash scripts/make/selftest.sh`. It must pass under `/usr/bin/make` (3.81) on this
  machine.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: The test harness, the Makefile skeleton, and the workflow that runs the harness on Make 3.81
and Make 4.x.

- [X] T001 Create `scripts/make/selftest.sh`, the stub-based harness only, with no test cases yet. It must:
  - Make a temp directory with `mktemp -d`, removed by an EXIT trap.
  - Create a stub `bin/` holding executables `docker`, `npm`, `node`, `java`, and `nc`, plus a stub wrapper
    file `gradlew`.
  - Make each stub append one line to `$STUB_LOG`: its name, the basename of its working directory, and its
    arguments, space-joined.
  - Have stub `gradlew` also record `GOLDEN_WRITE=<value>`, and stub `docker` also record
    `BACKEND_REPLICAS`/`FRONTEND_REPLICAS` values when set.
  - Have each stub exit with `$STUB_EXIT_<name>` (default 0), and sleep `$STUB_SLEEP_<name>` seconds first
    when set (for the dev tests).
  - Give stub `docker` these special cases:
    - `docker compose version` exits `$STUB_EXIT_compose` (default 0).
    - A `compose ... config` call prints a fixture containing `published: "8866"` and
      `L4J_MODEL_ID: llama3.2`.
    - A `compose ... ps -q load-balancer` call prints `$STUB_LB_RUNNING` (default empty).
  - Have stub `nc` exit 0 (port open) when its port argument is listed in `$STUB_OPEN_PORTS`, and 1
    otherwise.
  - Build an isolated `PATH`, never `/usr/bin:/bin`, because GitHub's Ubuntu runner has a real
    `/usr/bin/docker` and macOS has a `/usr/bin/java` launcher, either of which would defeat a
    missing-tool test. `$STUBS/sys/` holds symlinks, resolved once with `command -v` from the caller's
    `PATH`, to exactly the utilities the scripts and make need: `bash env awk sed grep cat sleep mktemp
    make pgrep kill head tr basename dirname rm printf uname script`, plus `setsid` when present (Linux).
    Symlink only those found, and fail the harness naming any required one that is missing. Stub `bin/` and `sys/` are separate directories,
    and `PATH="$STUBS/bin:$STUBS/sys"`.
  - Provide `without_tool <name>`, which moves one stub out of `bin/` for a single case and restores it
    afterwards.
  - Provide `run_make <args...>`, which runs `make` from the repository root with that isolated `PATH` and
    `GRADLEW=$STUBS/gradlew`, captures output and status into `$OUT`/`$STATUS`, and resets `$STUB_LOG`
    first.
  - Provide `run_tty <answer> <command...>`, which runs a command under a pseudo-terminal and types
    `<answer>` into it.
    - The input is `(printf '%s\n' "$answer"; sleep 1) | script ...`: `script -q /dev/null <command>` on
      macOS and `script -qec "<command>" /dev/null` on Linux, selected with `uname`.
    - The trailing `sleep 1` keeps input open. This was verified on macOS: with a bare `printf 'yes\n' |`,
      `script` exits at end of input before the child reads, so the answer is lost in every attempt, while
      with the sleep 3 of 3 attempts were read.
    - Strip `\r` from the captured output before asserting.
  - Provide assertions `expect_status`, `expect_calls` (exact ordered lines in the log), `expect_no_calls`,
    `expect_output_contains`, and `expect_output_lacks`.
  - Print `ok <name>` or `FAIL <name>` for each case, and exit 1 if any case failed.
- [X] T002 Create the root `Makefile` skeleton with no targets yet:
  - A header comment saying that it is a launcher only: every target runs a command the README documents,
    `./gradlew` stays the build (research R-001), and the command of each target is in
    `specs/006-makefile-entrypoint/contracts/make-targets.md`.
  - `SHELL := /bin/bash`, `.DEFAULT_GOAL := help`, `GRADLEW ?= ./gradlew` (with a comment that only the
    self-test overrides it), `STACK := docker compose -f compose.stack.yaml`, and `SCRIPTS := scripts/make`.
  - No `include .env` and no variable holding a port, model, or replica count (FR-011).
- [X] T003 [P] Create `.github/workflows/make.yml`:
  - Name `make`, triggered on `push` and `pull_request` with paths `Makefile`, `scripts/make/**`, and
    `.github/workflows/make.yml`.
  - `permissions: contents: read`.
  - One job `selftest` with `strategy.matrix.os: [macos-latest, ubuntu-latest]` and `runs-on: ${{ matrix.os }}`.
  - Steps: `actions/checkout@v7`, `make --version | head -1`, and `bash scripts/make/selftest.sh`.
  - No secrets and no Docker, JDK, or Node setup. A comment explains that macOS runners ship GNU Make 3.81,
    which is what SC-005 needs.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The three helpers every target group uses: tool checks, port checks, and confirmation.

**⚠️ CRITICAL**: Stories 1, 3, and 4 call these helpers. Finish this phase first.

- [X] T004 Add helper test cases to `scripts/make/selftest.sh`, calling the scripts directly and not through
  make. Run the harness and confirm these cases FAIL because the scripts do not exist.
  - `without_tool docker`, then `require.sh docker` with the isolated `PATH` exits 1, and its output
    contains `Missing tool: docker`. Repeat with `without_tool java` under
    `env -u JAVA_HOME`: it exits 1 and names `java`. With `without_tool java` and
    `JAVA_HOME=$STUBS/jdk`, where `$STUBS/jdk/bin/java` is an executable stub, it exits 0.
  - `require.sh docker` with `STUB_EXIT_compose=1` exits 1 and names `docker compose`.
  - `require.sh docker node npm java` with all stubs present exits 0.
  - `port-free.sh 8080 "hint text"` with `STUB_OPEN_PORTS=8080` exits 1 with `Port 8080 is in use` and
    `hint text`, and with no open ports exits 0.
  - `confirm.sh "stored runs"` with `CONFIRM=yes` exits 0.
  - With `CONFIRM` unset and stdin `</dev/null`, it exits 1 and its output contains `CONFIRM=yes` and
    `stored runs`.
  - With `CONFIRM=no` and no terminal, it also exits 1.
  - **Interactive (C2)**: `run_tty yes scripts/make/confirm.sh "stored runs"` → exit 0.
    `run_tty y scripts/make/confirm.sh "stored runs"` → exit 1, and the output contains `Aborted.` If
    `script` does not propagate the child's status on a platform, the harness appends `; echo "rc=$?"`
    inside the command and asserts on `rc=` instead.
- [X] T005 [P] Implement `scripts/make/require.sh <tool>...` per research R-005:
  - Check each tool with `command -v`. For `java`, also accept `[ -x "$JAVA_HOME/bin/java" ]` when
    `JAVA_HOME` is set, because the Gradle wrapper launches with either (research R-005).
  - For `docker`, also run `docker compose version >/dev/null 2>&1`.
  - On the first missing tool, print to stderr `Missing tool: <tool>. <hint> Nothing was started.` and exit 1.
  - Hints:
    - docker: "Install Docker with the Compose plugin: https://docs.docker.com/get-docker/."
    - node/npm: "Install Node.js at the version in frontend/.nvmrc (nvm install in frontend/)."
    - java: "Install a JDK 17 or newer to launch the Gradle wrapper; it provisions Java 25 itself."
- [X] T006 [P] Implement `scripts/make/port-free.sh <port> <hint>` per research R-006:
  - If `nc -z 127.0.0.1 <port>` succeeds, print to stderr `Port <port> is in use. <hint>` and exit 1.
  - Otherwise exit 0.
  - A comment says that it only probes and never kills anything.
- [X] T007 [P] Implement `scripts/make/confirm.sh <subject>` per research R-004 and the data-model
  Confirmation states:
  - `CONFIRM=yes` → exit 0.
  - Otherwise, when `[ -t 0 ]`, prompt `This deletes <subject>. Type yes to continue: `, read one line, and
    exit 0 only on exactly `yes`. Anything else prints `Aborted.` and exits 1.
  - Otherwise print `This deletes <subject>. Refusing without a terminal; run with CONFIRM=yes to proceed.`
    to stderr and exit 1.
- [X] T008 Run `bash scripts/make/selftest.sh` and confirm every helper case from T004 passes.

  - **Result**: red first: 20 of 20 helper cases failed before the scripts existed. Green after T005 to T007:
    20 of 20, including the pseudo-terminal `yes`/`y` cases. `rc=` shares a line with the prompt, so the
    harness reads it with `grep -o`.

**Checkpoint**: The helpers are green. The story phases can begin.

---

## Phase 3: User Story 1 - Start development with one command (Priority: P1) 🎯 MVP

**Goal**: `make dev` starts the development containers, the backend from source, and the frontend dev
server, with labeled output. One Ctrl+C stops both processes. Separate targets start either half and start
or stop the containers.

**Independent Test**: Quickstart Scenario 4. Run `make dev`, complete a run in the browser, press Ctrl+C
once, and confirm 8080 and 5173 are closed.

### Tests for User Story 1 ⚠️ (write first, confirm red)

- [X] T009 [US1] Add Development cases to `scripts/make/selftest.sh`. Run the harness and confirm they FAIL.
  - **Delegation**:
    - `run_make deps-up` → calls exactly `docker compose up -d --wait`.
    - `run_make deps-down` → `docker compose down`.
    - `run_make dev-backend` with `STUB_EXIT_gradlew` unset → `docker compose up -d --wait`, then
      `gradlew :backend:run`, in that order.
    - `run_make dev-frontend` → `npm install` then `npm run dev`, both with working directory `frontend`.
  - **Port checks**:
    - `run_make dev-backend` with `STUB_OPEN_PORTS=8080` → non-zero status, output contains `8080`, no stub
      called except `nc`.
    - `run_make dev-frontend` with `STUB_OPEN_PORTS=5173` → the same, naming `5173`.
    - `run_make dev` with `STUB_OPEN_PORTS=5173` → fails before calling `docker compose up`.
  - **Missing tool**: `without_tool docker`, then `run_make deps-up` → non-zero status,
    `Missing tool: docker`, no calls.
  - **dev.sh directly**, with `GRADLEW` set to the stub and `STUB_SLEEP_gradlew=30`, `STUB_SLEEP_npm=30`:
    - Start it in the background with `set -m` and wait until both stubs appear in the log.
    - Send TERM to the script. It exits 130 within 5 seconds, and `pgrep -f "$STUBS/"` finds no process.
    - Output lines from the stubs are prefixed `[backend]` and `[frontend]`, and the output lacks
      `Terminated` (L1).
  - **Process groups without a terminal (U3)**: `dev.sh` run with `</dev/null` and no tty prints
    `[dev] pgids <backend> <frontend>` (a diagnostic line printed only when `DEV_DEBUG=1`, a test-only flag listed in the contract's inputs; T010
    prints it right after both jobs start). Both pgids differ from each
    other and from the script's pid's group.
  - **dev.sh one-half exit**: with `STUB_EXIT_gradlew=3` and no sleep for gradlew, and `STUB_SLEEP_npm=30`:
    - The script exits 1 within 5 seconds.
    - Output contains `backend stopped (status 3)`.
    - No `npm` stub process remains.

### Implementation for User Story 1

  - **Result**: red first: 16 of 29 development cases failed. The 13 that passed were absence checks, which
    hold trivially while nothing runs. An extra case, not in the task, types a real Ctrl+C into a
    pseudo-terminal running `make dev`.
- [X] T010 [US1] Implement `scripts/make/dev.sh` per research R-003 and the data-model development session
  states.
  - **Shell options**: `set -uo pipefail` only, not `-e`. A half ending with a non-zero status is an expected
    event here, and `-e` would end the script before it stops the other half. Collect every status
    explicitly with `status=0; wait "$pid" || status=$?`. `pipefail` makes a pipeline's status that of the
    half's command, not of the labeling loop.
  - **Start**: `set -m`, then start two background pipelines, labeled with a read loop (not `sed`, whose
    unbuffered flag differs between macOS and Linux):
    - `"${GRADLEW:-./gradlew}" :backend:run 2>&1 | label backend`
    - `(cd frontend && npm install && npm run dev) 2>&1 | label frontend`
    - `label() { while IFS= read -r line; do printf '[%s] %s\n' "$1" "$line"; done; }`
  - **No terminal (U3)**: `set -m` must still give each job its own process group when no terminal is
    attached, as on CI runners. The first thing the harness checks (T009) is that the two pgids differ from
    the script's own. If bash refuses job control without a terminal, fall back to starting each half with
    `setsid` where available (Linux) and record the finding in research R-003.
  - **Track**: record each job's process group id (`jobs -p` right after launch).
  - **Interrupt**: `trap` INT and TERM to send `kill -TERM -- -<pgid>` to both groups, wait for them, print
    `[dev] stopped`, and exit 130.
  - **Poll**: every 1 second, check each group with `kill -0 -- -<pgid>`. When one is gone, collect its status
    with `wait`, print `[dev] <backend|frontend> stopped (status N); stopping <other>.`, TERM the other
    group, wait, and exit 1.
  - **Escalate**: after TERM, if a group is still alive after 10 seconds, send KILL.
  - **Quiet shutdown (L1)**: with `set -m`, bash prints `Terminated: 15 ...` for every job it reaps after a
    kill (observed on macOS bash 3.2). The noise goes away as follows:
    - In the normal case, collect the status of the half that ended by itself first.
    - Before killing, `set +m`, which stops job notifications for the remaining jobs.
    - Then reap the killed groups with `wait "$pid" 2>/dev/null`.
    - Never redirect the halves' own output.
    - The self-test's TERM case asserts that the output lacks `Terminated`.
  - A header comment explains why process groups and polling are used (bash 3.2, R-003).
- [X] T011 [US1] Add the `##@ Development` group to `Makefile`. Each target line carries a `## ` description,
  and every target is in `.PHONY`.
  - **`deps-up`** (`## Start PostgreSQL and the model runtime for development`): `@$(SCRIPTS)/require.sh
    docker` then `docker compose up -d --wait`.
  - **`deps-down`** (`## Stop the development containers (data kept)`): require docker, then
    `docker compose down`.
  - **`dev-backend`** (`## Run the backend from source on 8080, with its containers`):
    - require `docker java`;
    - `port-free.sh 8080 "Stop what is listening on it and run again."`;
    - `docker compose up -d --wait`;
    - `$(GRADLEW) :backend:run`.
  - **`dev-frontend`** (`## Run the frontend dev server on 5173`):
    - require `node npm`;
    - `port-free.sh 5173 "..."`;
    - `cd frontend && npm install && npm run dev`.
  - **`dev`** (`## Start everything for development; Ctrl+C stops both halves`):
    - require `docker java node npm`;
    - check both ports;
    - `docker compose up -d --wait`;
    - `GRADLEW=$(GRADLEW) $(SCRIPTS)/dev.sh`.
  - Comments beside the port numbers name their sources: the Micronaut default in
    `backend/src/main/resources/application.yml` and Vite's default.
  - Each delegated command is the last command on its recipe line (R-007).
- [X] T012 [US1] Run `bash scripts/make/selftest.sh` until every US1 and helper case passes.
- [X] T013 [US1] Run quickstart Scenario 4 for real with `/usr/bin/make`:
  - `make dev`: the backend answers on `http://localhost:8080/api/catalog` and the frontend on
    `http://localhost:5173`.
  - Ctrl+C once in the terminal, so the interrupt passes through `make` as well as the script. Then:
    - `nc -z` shows 8080 and 5173 closed;
    - `pgrep -fl 'GradleWrapperMain|dev.l4jlab.chain.Application|vite'` finds nothing started by this run;
    - the Gradle daemon may stay, which is expected.
  - With `make dev-backend` running in another terminal, `make dev` fails naming 8080.
  - `make deps-down`.
  - Record a `Result:` line under this task.
  - **Result**: 49 of 49 green (helpers and development) in about 10 seconds. `set -m` without a terminal
    gave separate process groups on macOS bash 3.2, so the `setsid` fallback was not needed. One shared
    finding: `set +m` right after both jobs start keeps their groups, and it also keeps the poll loop in the
    terminal's foreground group. That is what makes a typed Ctrl+C reach the script: with job control left
    on, `sleep 1` would take the terminal and swallow the interrupt. It also removes the `Terminated`
    notices (L1).
  - **Result (T013)**: real run with `/usr/bin/make` 3.81 in a pseudo-terminal:
    - `make dev` started the containers, the backend (`Startup completed in 916ms`, `/api/catalog`
      answered), and Vite (`/` served `<div id="root"`), with 20 `[backend]` and 17 `[frontend]` lines.
    - A typed Ctrl+C printed `[dev] stopped`, and make reported `Error 130`. Afterwards 8080 and 5173 were
      closed, and `pgrep` found no Gradle wrapper, application JVM, or Vite process.
    - With `make dev-backend` running, `make dev` failed with `Port 8080 is in use.` and started nothing.
    - `make deps-down` removed both containers and kept the `pgdata` volume. The development containers
      were started again afterwards (`make deps-up`), because they had been running before the check.

**Checkpoint**: US1 works on its own. Before US2, run the targets by name. Bare `make` still needs `help`
from US2.

---

## Phase 4: User Story 2 - Discover every command from one place (Priority: P1)

**Goal**: A bare `make`, or `make help`, prints every target grouped by purpose with one line each,
generated from the Makefile.

**Independent Test**: Quickstart Scenario 2. `make` prints all groups, starts nothing, and exits 0.

### Tests for User Story 2 ⚠️ (write first, confirm red)

- [X] T014 [US2] Add Help cases to `scripts/make/selftest.sh`. Run the harness and confirm they FAIL.
  - `run_make` with no target has status 0, no stub calls, and output containing the headers `Development`,
    `Packaged system`, `Verification`, and `Maintenance`.
  - `run_make help` gives identical output.
  - **Completeness**: every name matched by `grep -E '^[a-z][a-z0-9-]*:' Makefile` has `## ` on its line.
  - **Contract**: every target in this fixed list appears in help output as `  <name> `: `help dev
    dev-backend dev-frontend deps-up deps-down up up-local pull-model status logs logs-lb scale down reset
    check test test-backend test-frontend test-live check-api generate-api lint format golden`.
  - The Packaged system, Verification, and Maintenance names stay red until US3 and US4 land. Keep them in
    one assertion, named `contract-complete`, so the failure is explicit.
  - **Drift**: copy the Makefile to the temp dir, append `zz-new: ## New thing`, run
    `make -f <copy> help`, and expect `zz-new` and `New thing` in the output.

### Implementation for User Story 2

- [X] T015 [US2] Add `##@ Help` and the `help` target (`## Show this help`) at the top of `Makefile`.
  - The recipe is one POSIX awk program over `$(MAKEFILE_LIST)`:
    - A line matching `^##@ ` prints a blank line and the text after `##@ `.
    - A line matching `^[a-zA-Z0-9_-]+:.*## ` prints `  %-14s %s` with the target name and the text after
      `## `.
  - Start the output with a usage line: `Usage: make <target> [VAR=value]. Settings come from the
    environment or .env; see README.md.`
  - Verified pattern: research R-002. Remember `$$` escaping for awk fields.
- [X] T016 [US2] Run the harness. The US2 cases pass, except `contract-complete`, which stays red until
  Phase 6. Run `/usr/bin/make` (3.81) bare and confirm the grouped output. Record a `Result:` line. Do not
  push while `contract-complete` is red, because the `make` workflow would fail.
  - **Result**:
    - Red first: 8 cases failed with no `help` target.
    - The first green attempt found a harness defect: make runs a simple recipe line such as `printf` without a
      shell, so it needs the binary. The harness had skipped linking names that are also bash builtins, so
      it now resolves every utility with `type -P`.
    - After the fix, `/usr/bin/make` 3.81 bare prints the usage line and the Help and Development groups,
      exits 0, and starts nothing.
    - The drift case (a new `zz-new` target appended) shows in help.
    - As planned, `contract-complete` and the Packaged system, Verification, and Maintenance headers stay red
      until their stories land.

**Checkpoint**: US1 and US2 together are the MVP. One command starts development, and help lists what
exists.

---

## Phase 5: User Story 3 - Operate the packaged system without long commands (Priority: P2)

**Goal**: Short targets start, scale, inspect, stop, and reset the stack. The load balancer publishes on
8866 by default, declared once in compose.

**Independent Test**: Quickstart Scenario 5.

### Port change (FR-011a)

- [X] T017 [US3] In `compose.stack.yaml`:
  - Change the load balancer's port to `"${L4J_HTTP_PORT:-8866}:8080"`.
  - Keep the existing `FR-010, FR-011` comment and add `006 FR-011a: default 8866`.
  - Add one line to the header comment saying that the application is served at
    `http://localhost:8866` unless `L4J_HTTP_PORT` says otherwise.
  - Verify with `docker compose -f compose.stack.yaml config | grep published`, which should print
    `published: "8866"`.
- [X] T018 [P] [US3] In `.github/workflows/stack.yml`, replace the three `http://localhost:8000` probes
  (lines 45, 46, 60) with `http://localhost:8866`. Nothing else changes.

### Tests for User Story 3 ⚠️ (write first, confirm red)

- [X] T019 [US3] Add Packaged system cases to `scripts/make/selftest.sh`. Run the harness and confirm they
  FAIL.
  - **Start**:
    - `run_make up` with no open port has calls, in order, `docker compose -f compose.stack.yaml config`, the
      `ps -q load-balancer` call, and `docker compose -f compose.stack.yaml up -d --build --wait`.
    - `run_make up` with `STUB_OPEN_PORTS=8866` and `STUB_LB_RUNNING` empty → non-zero status, output
      contains `8866` and `L4J_HTTP_PORT`, no `up` call.
    - `run_make up` with `STUB_OPEN_PORTS=8866` and `STUB_LB_RUNNING=abc123` → proceeds to the `up` call.
    - `run_make up-local` → ends with `docker compose -f compose.stack.yaml --profile local up -d --build --wait`.
  - **Model**: `run_make pull-model` prints `Pulling llama3.2` and calls `docker compose -f compose.stack.yaml --profile local exec ollama ollama
    pull llama3.2`, with the model taken from the config fixture.
  - **Inspect**:
    - `status` → `... ps`.
    - `logs` → `... logs`.
    - `logs-lb` → `... logs --no-log-prefix load-balancer`.
  - **Scale**:
    - `run_make scale` with neither variable → non-zero status, output names both `BACKEND_REPLICAS` and
      `FRONTEND_REPLICAS`, no docker calls.
    - `run_make scale BACKEND_REPLICAS=3` → `... up -d --wait`, and the stub log records
      `BACKEND_REPLICAS=3`.
  - **Stop**:
    - `down` → `... down`.
    - `run_make reset </dev/null` with `CONFIRM` unset → non-zero status, no `down` call, output names
      `CONFIRM=yes`.
    - `run_make reset CONFIRM=yes` → `docker compose -f compose.stack.yaml --profile local down -v`.
  - **Failure passthrough**: `run_make status` with `STUB_EXIT_docker=3` → non-zero status, and output
    contains `Error 3`.

### Implementation for User Story 3

- [X] T020 [US3] Add the `##@ Packaged system` group to `Makefile` per the contract. Every target requires
  docker first, and every target is in `.PHONY`.
  - **`up`** (`## Build and start the stack (the packaged system) on http://localhost:8866; provider from env or .env`)
    and **`up-local`** (`## Start the stack with the local model runtime; the stack defaults to local inference, so unset any cloud settings (L4J_PROVIDER, L4J_MODEL_ID, ...) in the shell or .env`):
    - Resolve the port in one recipe line: `port=$$($(STACK) config | awk '/published:/{gsub(/"/,"",$$2); print $$2; exit}')`.
    - Then `if [ -z "$$($(STACK) ps -q load-balancer 2>/dev/null)" ]; then $(SCRIPTS)/port-free.sh "$$port"
      "Stop what is listening on it, or choose another port with L4J_HTTP_PORT=<port> make up."; fi`.
    - Then run `$(STACK) up -d --build --wait` (`up-local` adds `--profile local`).
    - 8866 is never written in the recipe.
  - **`pull-model`** (`## Pull the model named by L4J_MODEL_ID (default llama3.2) into the local runtime`).
    Before pulling, print `Pulling <model> into the local model runtime.` so a cloud model id in the shell is
    visible before a large download. The id is not a credential. Recipe: `model=$$($(STACK) config
    | awk '/L4J_MODEL_ID:/{print $$2; exit}')`, then `$(STACK) --profile local exec ollama ollama pull
    "$$model"`.
  - **`status`** (`## Show the stack's services and health`): `$(STACK) ps`.
  - **`logs`** (`## Show all stack logs`): `$(STACK) logs`.
  - **`logs-lb`** (`## Show the load balancer's JSON access log`): `$(STACK) logs --no-log-prefix
    load-balancer`.
  - **`scale`** (`## Scale with BACKEND_REPLICAS=N and/or FRONTEND_REPLICAS=N (the other half returns to
    its setting)`):
    - Fail with the contract message when both variables are empty.
    - Otherwise run `$(STACK) up -d --wait`. Compose reads the variables, and the Makefile does not pass
      `--scale`.
  - **`down`** (`## Stop the stack (stored runs kept)`): `$(STACK) down`.
  - **`reset`** (`## Stop the stack and DELETE stored runs and pulled models (asks first)`):
    `$(SCRIPTS)/confirm.sh "the stack's stored runs and pulled models"`, then
    `$(STACK) --profile local down -v`.
- [X] T021 [US3] Run the harness until every US3 case passes.
- [X] T022 [US3] Run quickstart Scenario 5 for real with `/usr/bin/make`, stopping the development containers
  first if they hold a port.
  - **Cloud stack**:
    - `make up` with the cloud variables from the shell. Never print them.
    - `/` and `/api/catalog` answer on 8866, and 8000 is closed.
    - `make status`.
    - `make scale BACKEND_REPLICAS=3`, then `make status` shows three backends.
    - `make logs-lb | tail -3` shows `"upstream"`.
    - `make down`.
  - **Override**: `L4J_HTTP_PORT=9000 make up` answers on 9000. Then `make down`.
  - **Local stack**: the maintainer's shell exports cloud settings, including `L4J_MODEL_ID=gpt-oss:120b`,
    which `pull-model` would otherwise try to download locally. Run the local part with them removed:
    `env -u L4J_PROVIDER -u L4J_MODEL_BASE_URL -u L4J_MODEL_ID -u OLLAMA_API_KEY make up-local`, then the same
    `env -u ...` prefix with `make pull-model`.
    - Confirm the pull names `llama3.2`, the compose default. The pull may report the model already present.
    - Check that no `.env` sets `L4J_MODEL_ID` first (`test ! -f .env || grep -c L4J_MODEL_ID .env`, which
      prints a count, never a value).
  - Finish with `make down`, and record a `Result:` line under this task.
  - **Result (T017 to T021)**:
    - `compose config` resolves `published: "8866"` with `L4J_HTTP_PORT` unset.
    - `stack.yml` probes 8866 (3 places).
    - Red first: 26 of 28 packaged-system cases failed. After T020: 28 of 28 green, and 85 in total, with only
      the 3 US4-dependent help checks red.
  - **Result (T022)**, real run with `/usr/bin/make` 3.81 and cloud settings from the shell (values never
    printed; no `.env`):
    - `make up` returned once every service was healthy. `/` served `<div id="root"`, `/api/catalog`
      answered, and 8000 was closed. The balancer publishes `[::]:8866->8080`.
    - `make status` showed 2 backend, 2 frontend, balancer, and database. `make scale BACKEND_REPLICAS=3`
      made 3 backends, and `make logs-lb` lines carry `"upstream"`.
    - `make down` exited 0.
    - `L4J_HTTP_PORT=9000 make up` answered on 9000 with 8866 closed. Then `make down`.
    - Local part, with the cloud settings removed through `env -u`: `make up-local` exited 0. The model
      runtime's list was empty, and `make pull-model` printed `Pulling llama3.2 into the local model
      runtime.` and ended with `success`. The API answered on 8866.
    - **Finding**: `make down` after `up-local` left the `ollama` container running, because a plain `down`
      does not see profiled services. The README's `S down` behaves the same way. `down` now runs
      `S --profile local down`, test first (the stub case went red, then green). Verified for real: after
      `up-local`, `make down` left 0 containers and kept both volumes. The contract and research R-008 are
      updated.
    - The stack was running before this check, so it was started again in cloud mode with `make up`.

**Checkpoint**: The packaged system is fully operable from make, on 8866.

---

## Phase 6: User Story 4 - Verify and maintain from short targets (Priority: P2)

**Goal**: Named targets for the root verification, each suite, the live test, the contract check, type
regeneration, lint, the formatter, and the confirmed golden rewrite.

**Independent Test**: Quickstart Scenario 6. `make check` and `./gradlew check` give the same result and the
same test counts.

### Tests for User Story 4 ⚠️ (write first, confirm red)

- [X] T023 [US4] Add Verification and Maintenance cases to `scripts/make/selftest.sh`. Run the harness and
  confirm they FAIL.
  - **Verification delegation**:
    - `check` → `gradlew check`.
    - `test` → `gradlew :backend:test :frontend:test`.
    - `test-backend` → `gradlew :backend:test`.
    - `test-frontend` → `npm test` in `frontend`.
    - `test-live` → `gradlew :backend:liveTest`.
    - `check-api` → `gradlew :frontend:checkApi`.
  - **Maintenance delegation**:
    - `generate-api` → `gradlew :backend:classes`, then `npm run generate:api` in `frontend`.
    - `lint` → `npm run lint` in `frontend`.
    - `format` → `npm run format` in `frontend`.
  - **Golden rewrite**:
    - `run_make golden </dev/null` → non-zero status and no gradlew call.
    - `run_make golden CONFIRM=yes` → `gradlew :backend:test --tests *GoldenRunSnapshotTest --rerun` with
      `GOLDEN_WRITE=1` recorded.
  - **Passthrough**: `run_make check` with `STUB_EXIT_gradlew=3` → non-zero status, and output contains
    `Error 3`. With `STUB_EXIT_gradlew=0` → status 0.
  - **Needs (C1)**: `without_tool node`, then `run_make check`, `run_make test`, and `run_make check-api` each
    fail naming `node` with no gradlew call.
  - **Credential (FR-016)**: for every non-destructive target in the contract list except `dev` (whose stubs
    would sleep), run with `OLLAMA_API_KEY=sentinel-7f3a` and expect the output to lack `sentinel-7f3a`. Also
    `grep -c OLLAMA_API_KEY Makefile scripts/make/*.sh` (excluding `selftest.sh`) returns 0.

### Implementation for User Story 4

- [X] T024 [US4] Add the `##@ Verification` group to `Makefile` per the contract, adding each target to
  `.PHONY`. Tool needs:
  - `check`, `test`, and `check-api` require `java node npm`, because they reach `:frontend:*` tasks, and
    FR-015 wants the failure before the backend suite spends minutes.
  - `test-backend` and `test-live` require `java`.
  - `test-frontend` requires `node npm`.
  - `check` (`## Verify everything: both suites and the API contract (same as ./gradlew check)`)
  - `test` (`## Run the backend and frontend test suites`)
  - `test-backend` (`## Run the backend tests (Docker for database tests, no credential)`)
  - `test-frontend` (`## Run the frontend tests`)
  - `test-live` (`## Run the live model tests against the configured provider (skips when none)`)
  - `check-api` (`## Check the frontend API types match the backend`)
- [X] T025 [US4] Add the `##@ Maintenance` group to `Makefile`, with each target added to `.PHONY`:
  - **`generate-api`** (`## Regenerate the frontend API types from the backend`): `$(GRADLEW)
    :backend:classes`, then `cd frontend && npm run generate:api`.
  - **`lint`** (`## Lint the frontend`).
  - **`format`** (`## Format the frontend with prettier (rewrites files)`).
  - **`golden`** (`## Rewrite the golden run snapshots (asks first)`):
    - `$(SCRIPTS)/confirm.sh "the committed golden run snapshots in backend/src/test/resources/golden"`.
    - Then `GOLDEN_WRITE=1 $(GRADLEW) :backend:test --tests '*GoldenRunSnapshotTest' --rerun`.
    - A comment cites research R-008 for `--rerun`.
- [X] T026 [US4] Run the harness. Every case passes, including `contract-complete` from T014.
- [X] T027 [US4] Run quickstart Scenario 6 for real with `/usr/bin/make`:
  - `make check`, then `./gradlew check`. Both pass, with backend 145 and frontend 126 tests.
  - `make test-live` with the provider variables unset in that shell (`env -u OLLAMA_API_KEY -u L4J_PROVIDER
    make test-live`) reports the named skip.
  - `make lint` exits 0.
  - `make golden </dev/null` refuses, and `git status --porcelain backend/src/test/resources/golden` is empty.
  - Record a `Result:` line.
  - **Result (T023 to T026)**:
    - Red first: 20 of 28 cases failed. The ones that passed were refusal and absence checks, which hold
      trivially without targets.
    - After T024 and T025, the whole harness is green: 116 passed, 0 failed, `contract-complete` included, in
      about 12 seconds on `/usr/bin/make` 3.81.
    - The credential sentinel was printed by no target, and no script or the Makefile names `OLLAMA_API_KEY`.
  - **Result (T027)**, real runs:
    - `make check` and `./gradlew check` both exited 0 with `BUILD SUCCESSFUL`, 145 backend tests (from the
      results files) and `Tests 126 passed (126)` for the frontend.
    - `make lint` exited 0.
    - `make golden </dev/null` refused, naming `CONFIRM=yes`. Make exited 2 with `Error 1`, and no golden
      file changed.
    - **Finding**: `make test-live` with the provider unset reported `:backend:liveTest` UP-TO-DATE and kept a
      stale pass. The documented command has the same defect. Fixed in `backend/build.gradle.kts` with
      `outputs.upToDateWhen { false }` (research R-013). Two consecutive runs then executed the task and
      recorded the named skip.

**Checkpoint**: All four stories work on their own and together.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T028 [P] Update `README.md` (FR-018, FR-011a):
  - **New section.** Add `## Run it with make` directly after "Where to look, in order". It covers:
    - the platform scope (macOS, Linux, WSL; native Windows uses the commands below);
    - `make` for help;
    - the four groups with their most used targets (`make up`, `make up-local` plus `make pull-model`,
      `make dev`, `make check`);
    - `CONFIRM=yes` for `reset` and `golden`;
    - that settings still come from the shell or `.env`, and that `make scale BACKEND_REPLICAS=3` passes one
      through.
  - **Existing sections.** In "Run the packaged system", "Run it for development", "Test it", and
    "Contracts":
    - keep every existing command;
    - add the matching make target beside each command block as a one-line lead-in (for example
      "`make up`, which runs:");
    - change `http://localhost:8000` to `http://localhost:8866`;
    - change "If 8000 is taken" to "If 8866 is taken";
    - leave the `L4J_HTTP_PORT=9000` example as is.
  - **CI paragraph.** Mention the fourth and fifth workflows, `stack` and `make`, and note that no workflow
    routes CI through make.
- [X] T029 [P] Update feature 004's documents to 8866 (FR-011a, research R-011).
  - **Port edits:**
    - `specs/004-containerized-deployment/contracts/stack-topology.md`: port table and the `L4J_HTTP_PORT`
      default.
    - `contracts/operations.md`: the start row address and "publishes only 8000".
    - `data-model.md`: table row and diagram.
    - `quickstart.md`: every `localhost:8000` and the "on 8000" expectation.
  - **`research.md`**: change R-011's decision to 8866 and add a line: "Changed from 8000 in feature 006
    at the maintainer's request; 8866 collides with none of 5432, 8080, 5173, 11434, so the rationale
    stands."
  - **`tasks.md`**: add one note under the title, "Note (feature 006): the published default is now 8866.
    Results below record checks run on 8000 at the time." Do not edit its task or result lines.
- [X] T030 Run the SC-007 search from quickstart Scenario 5 (`grep -rn '8000' ...` with the two documented
  exclusions). Confirm no remaining line refers to the load balancer's port, and fix any that does.
- [X] T031 Run the final checks:
  - quickstart Scenarios 1, 3, and 7 with `/usr/bin/make`;
  - `bash scripts/make/selftest.sh`, which exits 0;
  - `./gradlew check`, which must be green with unchanged counts (SC-006);
  - `docker compose -f compose.stack.yaml config --quiet`.

  Confirm every command in `README.md` has a target in `contracts/make-targets.md` (SC-002). Record a
  `Result:` line.
- [X] T032 Confirm that `git status` shows only the files listed in plan.md's Project Structure plus this
  feature's `specs/006-makefile-entrypoint/` documents. Confirm `scripts/make/*.sh` are executable
  (`git ls-files -s scripts/make` shows mode `100755`, or `chmod +x` before commit). Do not commit unless
  the maintainer asks.

  - **Result (T028 to T032)**:
    - **README**: a new "Run it with make" section follows "Where to look". Each existing command block
      names its make target, every command stays, the address is `http://localhost:8866`, and the CI
      paragraph mentions `stack` and `make`. The `down --profile local` note comes from the T022 finding.
    - **Feature 004 documents**: 18 references moved to 8866 across data-model, stack-topology, operations,
      and quickstart. R-011 is rewritten, and a note sits under the `tasks.md` title, whose result lines are
      untouched.
    - **SC-007 search** (the two documented exclusions): the only line left is 004 R-011's "Changed from
      8000 in feature 006", which names 8000 as the previous value.
    - **Final checks**:
      - `bash scripts/make/selftest.sh` gives 116 passed, 0 failed, on GNU Make 3.81.
      - `make reset </dev/null` and `make golden </dev/null` refused, naming `CONFIRM=yes`, with exit 2.
      - Typing `n` to `make golden` in a terminal printed `Aborted.`
      - No golden file changed, and both stack volumes are intact.
      - `env PATH=/usr/bin:/bin make up` printed `Missing tool: docker. ... Nothing was started.`
      - `compose config --quiet` is ok.
      - `./gradlew :backend:test --rerun :frontend:test --rerun check` gives `BUILD SUCCESSFUL`: 145 backend
        tests with 0 skipped and 0 failed, and 126 frontend tests passed (SC-006).
      - SC-002: every command in the README has a target, named beside it.
    - **git status**: exactly the plan's files plus this feature's documents. The five scripts are
      executable. Nothing is committed.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies. T003 runs in parallel with T001 and T002.
- **Foundational (Phase 2)**: Depends on T001, since the tests extend the harness. T005, T006, and T007 run in
  parallel after T004.
- **US1 (Phase 3)**: Depends on Phase 2.
- **US2 (Phase 4)**: Depends on T002 only. It can run before or alongside US1, but it shares `Makefile` and
  `selftest.sh`, so sequence the edits.
- **US3 (Phase 5)**: Depends on Phase 2. T017 and T018 have no dependency on the harness and can start at
  any time.
- **US4 (Phase 6)**: Depends on Phase 2. `contract-complete` (T014) closes only after US3 and US4.
- **Polish (Phase 7)**: T028 and T029 can start once T017 fixes the port. T030 to T032 come last.

### Within each story

The test cases in `selftest.sh` come first and are confirmed red. The helper or Makefile targets follow, then
the harness goes green, then the real run.

### Shared files (no [P] across stories)

`Makefile` and `scripts/make/selftest.sh` are edited by every story, so story tasks touching them run one
after another.

## Parallel Opportunities

```bash
# Phase 1
T001 selftest harness   |  T003 .github/workflows/make.yml
# Phase 2, after T004
T005 require.sh  |  T006 port-free.sh  |  T007 confirm.sh
# Independent of the harness, any time
T017 compose.stack.yaml port  |  T018 stack.yml probes
# Polish
T028 README.md  |  T029 specs/004 documents
```

## Implementation Strategy

### MVP (US1 + US2, both P1)

1. Phases 1 and 2.
2. US1: `make dev` and the development targets, validated for real (T013).
3. US2: help. A bare `make` lists what exists (T016).
4. **Stop and validate**: a developer can start everything for development with one command and discover it.

### Incremental delivery

1. US3: the packaged system and the port change, then CI's `stack` workflow proves 8866.
2. US4: verification and maintenance. `contract-complete` goes green.
3. Polish: the README makes make primary, feature 004's documents follow the port, and the final checks run.

## Notes

- **Constitution**: no LangChain4j or Gradle build change (research R-000, R-001). `./gradlew check` must
  stay green with unchanged counts.
- **Real runs**: never print `OLLAMA_API_KEY` or any provider variable value.
- **Deviations from literal README commands**: `--wait`, `--profile local` on reset, and `--rerun` on golden
  are intentional (research R-008). Keep the comments that say why.
