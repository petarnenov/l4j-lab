# Research: Makefile Entry Point

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-09-13

Every fact below was checked on the maintainer's machine (GNU Make 3.81, bash 3.2.57, macOS) or read from
the repository, not assumed.

## R-000: LangChain4j capability inventory (Principle I gate)

**Decision**: Not applicable, recorded as required.

**Rationale**: The feature adds a Makefile, shell helper scripts, a CI workflow, and documentation, and
changes one compose port default. It touches no Java source, no build dependency, no agent, workflow,
listener, guardrail, tool, retrieval, MCP, or A2A code. The pinned LangChain4j modules (1.18.0 and
langchain4j-agentic 1.18.0-beta28) are untouched, so there is no capability to take from or leave in the
library. The inventory for the chain lives in `specs/005-langchain4j-declarative-migration/research.md`
(R-011) and stays current.

## R-001: Reconciling a Makefile with the constitution and with feature 003 R-004 (FR-019)

**Decision**: Add the Makefile as a *launcher*, not a build tool. `./gradlew` stays the only backend build
entry point and the only command CI calls. Every target that builds or tests the backend calls the wrapper.

**Rationale**:

- The constitution requires Gradle to be the only backend build tool and every documented command to go
  through the wrapper. The Makefile compiles nothing, resolves no dependency, and declares no task graph.
  `make check` runs `./gradlew check`, character for character, so the documented command still goes
  through the wrapper, now with a shorter name in front of it.
- Feature 003's R-004 rejected "a shell script or Makefile as the root entry point", meaning an entry
  point *beside* `./gradlew` that would own the build. That concern still holds, and the design respects
  it: `./gradlew check` remains the root verification, CI keeps calling the underlying commands (spec
  Assumptions), and removing the Makefile would break nothing.
- What changed is the scope of the request. 003 unified the *build*. 006 unifies *starting things*
  (containers, the dev servers, the packaged stack), which Gradle does not and should not do. A Makefile is
  the conventional launcher on macOS and Linux and needs no install there.
- 003's research file is left as written (it records the decision of its time), and this entry is the
  reconciliation FR-019 asks for.

**Alternatives considered**:

- Gradle tasks for docker compose and the dev servers. This would put application-launch logic into build
  scripts, which the constitution forbids ("build scripts MUST NOT carry application logic"), and would
  make `./gradlew` start containers.
- npm scripts at a root `package.json`. Adds a Node manifest to the root and makes the backend's commands
  depend on Node.
- `just` or `task`. A new tool to install, against the spec's "prefer no new dependency".

## R-002: Help generated from the Makefile, working with Make 3.81 (FR-003, FR-017)

**Decision**: Each target line carries a trailing `## description`, and each group starts with a
`##@ Group` line. `help` is an `awk` program over `$(MAKEFILE_LIST)` that prints group headers and aligned
target descriptions. `.DEFAULT_GOAL := help` makes a bare `make` print help.

**Rationale**: Verified in the scratchpad with `/usr/bin/make` 3.81 and macOS's BSD awk: `.DEFAULT_GOAL`
works (it was introduced in 3.81), `$(MAKEFILE_LIST)` works, and the awk program uses only POSIX features,
so it runs the same under gawk and mawk on Linux. Adding a target with a `##` comment adds it to help with
no second list to edit (US2 scenario 2).

A target without a `##` comment would be invisible in help. The self-test (R-010) fails when any target in
the Makefile lacks one.

**Alternatives considered**: A hand-written `help` echo list (drifts, violates FR-003). `make -qp` database
parsing (output format differs between 3.81 and 4.x).

## R-003: Running the backend and frontend together (FR-005, FR-006)

**Decision**: One helper script, `scripts/make/dev.sh`, written for bash 3.2. It:

1. Starts `./gradlew :backend:run` and `cd frontend && npm install && npm run dev` as two background jobs,
   each with its own process group (`set -m`), each piped through a bash read loop that prefixes lines with
   `[backend]` or `[frontend]`.
2. Traps INT and TERM, and on either sends TERM to both process groups, waits for them, and exits 130 (the
   conventional status after an interrupt).
3. Polls both groups every second, because bash 3.2 has no `wait -n`. When either ends by itself, it stops
   the other and exits 1 with a line naming which half stopped.

**Rationale**: No new dependency. `concurrently` is not installed (checked) and would be an npm package
driving the Gradle backend. Separate process groups are what make "one Ctrl+C stops both" reliable: the
Gradle client, the JVM it forks, npm, and Vite are all killed through their group, so nothing is left
listening on 8080 or 5173 (US1 scenario 2). With job control on, the terminal's Ctrl+C reaches only the
script, which forwards it, so there is exactly one shutdown path to test.

**Implementation notes (from analysis)**: the script uses `set -uo pipefail` without `-e`, and collects
every status with `wait "$pid" || status=$?`, because a half ending non-zero is an expected event. Output is
labeled with a bash read loop, because `sed`'s unbuffered flag differs between macOS (`-l`) and GNU (`-u`).
Job control without a terminal (CI) is checked by the self-test. If bash does not create separate process
groups there, `setsid` is the fallback on Linux, and the outcome is recorded here.

**Alternatives considered**: `make -j2 dev-backend dev-frontend`. Output interleaves unlabeled, an
interrupt reaches both but a child that exits by itself leaves the other running, and failure of one does
not stop the other. `tmux` panes: a new dependency and not scriptable in CI. `wait -n`: bash 4.3+, absent
on macOS.

## R-004: Confirmation for destructive targets (FR-010, FR-013, SC-004)

**Decision**: `scripts/make/confirm.sh "<what will be deleted>"`. With `CONFIRM=yes` in the environment it
proceeds. Otherwise, when standard input is a terminal (`[ -t 0 ]`) it asks `Type yes to continue`, and
anything other than `yes` aborts with status 1. When no terminal is attached it refuses with a message
naming `CONFIRM=yes`. Used by `reset` and `golden`.

**Rationale**: `make reset CONFIRM=yes` is the explicit flag. Make exports command-line variables to recipe
environments (verified: `make foo BACKEND_REPLICAS=3` made `$BACKEND_REPLICAS` visible in the recipe under
3.81), so the flag reaches the script without the Makefile defining it. Requiring the word `yes`, not `y`,
matches the weight of deleting stored runs.

**Alternatives considered**: `read -p` inline in the recipe (each recipe line is a separate shell, so the
answer and the command must share one line; unreadable). A `FORCE=1` name: less clear about what is being
agreed to.

## R-005: Missing tools (FR-015)

**Decision**: `scripts/make/require.sh <tool>...` checks each with `command -v` and fails naming the first
missing one and where to get it. Targets declare what they need: compose targets `docker` (and
`docker compose version`, since Compose v2 is a plugin that can be absent while `docker` exists), npm
targets `node` and `npm`, wrapper targets `java`.

**Rationale**: The Gradle wrapper script launches with whatever `java` is on the `PATH` or in `JAVA_HOME`
(here Homebrew OpenJDK 21), then provisions JDK 25 through the toolchain. Without any Java it cannot start,
so `java` is the right prerequisite name, satisfied by `java` on the `PATH` or an executable
`$JAVA_HOME/bin/java`, matching how the wrapper itself looks. The Node *version* check is not repeated: `:frontend:checkNode`
already enforces `.nvmrc` for Gradle paths, and npm's `engines` field warns for direct npm paths.
Repeating it here would be a second declaration of the same rule.

## R-006: Port checks without a second declaration (FR-008, FR-011a, spec Edge Cases)

**Decision**: `scripts/make/port-free.sh <port> <hint>` fails with the port and the hint when something is
listening (`nc -z 127.0.0.1 <port>`, present on macOS and on GitHub's Linux images).

- **Development**: `dev` and `dev-backend` check 8080, `dev` and `dev-frontend` check 5173. These are the
  Micronaut default in `backend/src/main/resources/application.yml` and Vite's default. The script names
  those two sources in a comment.
- **Packaged system**: `up` and `up-local` do not hardcode a port. They read the resolved published port
  from `docker compose -f compose.stack.yaml config` (which applies `L4J_HTTP_PORT` from the shell or
  `.env`, verified: it printed `published: "8000"` today). The check is skipped when this stack's
  `load-balancer` is already running (`ps -q load-balancer` non-empty), because then the stack itself holds
  the port and `up` is a legitimate re-apply. The hint names `L4J_HTTP_PORT`.

**Rationale**: FR-011a requires the 8866 default to be declared once, in `compose.stack.yaml`. Reading it
back through `compose config` keeps that true, and an override is honored without the Makefile knowing
about it.

**Alternatives considered**: Letting compose fail on bind. Its message names the port but not the
override, and it fails after images are built and other containers started, leaving half a stack.

## R-007: Exit status under Make (FR-014, SC-003)

**Decision**: Recipes run the delegated command as the last command on the recipe line, with no `|| true`,
no pipe after it, and no `-` prefix. The spec's FR-014 and SC-003 were corrected to what Make can do.

**Rationale**: Verified under 3.81: a recipe exiting 3 makes `make` exit **2** and print
`make: *** [target] Error 3`. GNU Make (3.81 and 4.x) maps every failed recipe to 2, and this is not
configurable. Pass and fail are preserved exactly, and the underlying status stays visible. The spec
originally required the exit status itself to match, which no Makefile can deliver, so the requirement was
amended during planning rather than silently missed.

## R-008: Target names (spec Assumptions, SC-002)

**Decision**: Short, unprefixed names for the packaged system, because it is the path the README leads with
and the one needing only Docker: `up`, `up-local`, `pull-model`, `status`, `logs`, `logs-lb`, `scale`,
`down`, `reset`. Development uses `dev`, `dev-backend`, `dev-frontend`, `deps-up`, `deps-down`.
Verification uses `check`, `test`, `test-backend`, `test-frontend`, `test-live`, `check-api`. Maintenance
uses `generate-api`, `lint`, `format`, `golden`. The full mapping is
[contracts/make-targets.md](./contracts/make-targets.md).

Details decided with the names:

- `scale` requires at least one of `BACKEND_REPLICAS` or `FRONTEND_REPLICAS` and runs
  `docker compose -f compose.stack.yaml up -d --wait`. Compose reads both variables itself (FR-011). Since
  each variable defaults to 2 in the compose file, the half not named returns to its variable or default.
  Help says so.
- `pull-model` pulls the model the stack resolves, read from `compose config` (`L4J_MODEL_ID`), rather than
  the `llama3.2` literal in the README. The default stays declared once, in the compose file.
- `down` runs `docker compose -f compose.stack.yaml --profile local down`. Found while validating (T022):
  without the profile, `down` after `up-local` left the `ollama` container running, as the README's plain
  `S down` also does.
- `reset` runs `docker compose -f compose.stack.yaml --profile local down -v`. Without the profile, a
  running `ollama` container is not removed and its volume cannot be deleted.
- `golden` adds `--rerun` to the documented command. Gradle does not track environment variables as test
  inputs, so after a normal run `GOLDEN_WRITE=1 ./gradlew :backend:test --tests '*GoldenRunSnapshotTest'` can
  report UP-TO-DATE and write nothing. `--rerun` is Gradle's built-in per-task option.
- `generate-api` runs `./gradlew :backend:classes` before `npm run generate:api`, so the description it reads
  is current. Both are existing commands.
- `deps-up` and the dev targets use `docker compose up -d --wait`, the documented command plus `--wait`, so
  Flyway does not race the database's first start.

## R-009: Lint and format (FR-012)

**Decision**: `lint` runs `npm run lint` (passes today, exit 0). `format` runs `npm run format`, the existing
formatter, which rewrites files, and help says so. No format *check* target.

**Rationale**: `npx prettier --check .` exits 1 on the current tree (checked), and this project has already
seen `prettier --write` reformat whole files unrelated to a change. A check target would fail on arrival.
Making the tree prettier-clean is a separate change. The spec's FR-012 was reworded to match.

## R-010: Verifying the Makefile itself (SC-002 to SC-005)

**Decision**: `scripts/make/selftest.sh`, run on its own and in a new workflow `.github/workflows/make.yml`
on `macos-latest` (whose `/usr/bin/make` is 3.81) and `ubuntu-latest` (Make 4.x). The workflow triggers only
on `Makefile`, `scripts/make/**`, and the workflow file itself, and needs no Docker, JDK, or credential. Tests
are written before the Makefile (Principle IV spirit for deterministic tooling). Checks:

1. A bare `make` prints every group and exits 0 without invoking a stub.
2. Every target defined in the Makefile has a `##` description, and every target in the contract is present.
3. With stub `docker` and `npm` on `PATH`, and `GRADLEW` pointing at a stub, each target invokes exactly the
   command in the contract (the stubs log their argv).
4. A stub that exits 3 makes the target fail, and output contains `Error 3`.
5. `reset` and `golden` with standard input from `/dev/null` and no `CONFIRM` invoke no stub and exit
   non-zero. With `CONFIRM=yes` they invoke it. Under a pseudo-terminal (`script`), typing `yes` proceeds and
   `y` aborts.
6. `dev.sh` with stubs that sleep: TERM to the script leaves no stub process. A backend stub that exits by
   itself makes the script exit 1 and ends the frontend stub.
7. `require.sh` with an empty `PATH` fails naming the tool.
8. Running any target with `OLLAMA_API_KEY=sentinel-value` never prints `sentinel-value` (FR-016).

The harness's `PATH` holds only the stubs and symlinks to named system utilities, never `/usr/bin`,
because a real `docker` or `java` there would defeat the missing-tool cases on CI runners.

`GRADLEW ?= ./gradlew` is the one overridable variable, and exists for this test. It names a tool path, not
a system setting, so FR-011 is unaffected.

**Rationale**: CI not depending on Make (spec Assumptions) means existing workflows keep calling the
underlying commands. This workflow tests the Makefile, it does not route CI through it. Running on macOS is
the only way to hold SC-005 continuously rather than on one machine once.

## R-011: Changing the published port to 8866 (FR-011a, SC-007)

**Decision**: Change the single declaration in `compose.stack.yaml` to `"${L4J_HTTP_PORT:-8866}:8080"`, and
update every place that names the default:

| File | Change |
|------|--------|
| `compose.stack.yaml` | default `8000` → `8866` |
| `README.md` | the address, the "if taken" sentence, and the override example |
| `.github/workflows/stack.yml` | the three `localhost:8000` probes |
| `specs/004-containerized-deployment/contracts/stack-topology.md` | port table and variable default |
| `specs/004-containerized-deployment/contracts/operations.md` | start row and the coexistence note |
| `specs/004-containerized-deployment/data-model.md` | table row and diagram |
| `specs/004-containerized-deployment/quickstart.md` | every probe URL and the expectation text |
| `specs/004-containerized-deployment/research.md` | R-011 decision, plus a line that 006 changed it and why the rationale still holds |

`specs/004-containerized-deployment/tasks.md` is an execution record: its "Result" lines say what was observed
on 8000 at the time, and rewriting them would falsify that record. It gets one note under its title that the
default moved to 8866 in feature 006. The SC-007 search excludes that file and this feature's own documents,
which mention 8000 only as the previous value.

**Rationale**: 8866 collides with none of the development ports (5432, 8080, 5173, 11434), so 004 R-011's
reason, that the stack and development can run at once, still holds. The `stack` workflow's path filter
includes `compose.stack.yaml`, so this change runs it and proves the new port in CI.

## R-012: What leaves the machine, and credentials (Principle II, FR-016)

**Decision**: Nothing new leaves the machine. The Makefile and scripts contain no credential, never
reference `OLLAMA_API_KEY` by value, and no recipe echoes environment values. Recipes are prefixed with `@`
only where the printed line would be noise, never to hide a value, since no value is ever in a line. `.env`
is read by compose as today. Make does not `include .env`, so the Makefile never parses a secret.

## R-013: The live model test was cached across provider configurations (found in T027)

**Finding**: `make test-live` with no provider configured reported `:backend:liveTest` UP-TO-DATE and kept the
earlier result, which was a pass recorded with a credential present. Gradle does not track environment
variables as task inputs, so the documented command `./gradlew :backend:liveTest` has always had this defect.
It predates this feature.

**Decision**: Set `outputs.upToDateWhen { false }` on the `liveTest` task in `backend/build.gradle.kts`, with a
comment. The fix goes at the source rather than as `--rerun` in the Makefile, so the README command is correct
too.

**Rationale**: A live test's outcome depends on an external provider and a credential, so reusing it is never
right. This is task configuration, not application logic, so the constitution's rule on build scripts holds.
`liveTest` is not part of `check`, so check timings are unchanged.

**Verified**: before the change, `make test-live` with the provider variables unset showed `5 up-to-date` and a
stale pass. After it, two consecutive runs both executed `:backend:liveTest`, and the report records the named
skip: "Skipped: no provider is configured. …".

`golden` keeps `--rerun` (R-008). There the stale result can only mean "nothing written", and the target is
the documented way to request a rewrite.

