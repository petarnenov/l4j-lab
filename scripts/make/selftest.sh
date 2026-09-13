#!/usr/bin/env bash
# Self-test for the root Makefile and scripts/make/ (feature 006, research R-010).
#
# Nothing real runs: docker, npm, node, java, nc, and the Gradle wrapper are stubs that record how they were
# called. The children see an isolated PATH holding only those stubs and symlinks to named system utilities,
# never /usr/bin, because a real docker (Ubuntu runners) or java launcher (macOS) there would defeat the
# missing-tool cases. Needs no Docker, JDK, Node.js, network, or credential.
#
#   bash scripts/make/selftest.sh
set -uo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
STUBS=$(mktemp -d "${TMPDIR:-/tmp}/l4j-make-selftest.XXXXXX")
trap 'rm -rf "$STUBS"' EXIT
STUB_LOG="$STUBS/calls.log"
FAILURES=0
PASSES=0

# ---------------------------------------------------------------------------------------------------------
# Isolated PATH
# ---------------------------------------------------------------------------------------------------------
mkdir -p "$STUBS/bin" "$STUBS/sys" "$STUBS/parked" "$STUBS/jdk/bin"
for tool in bash sh env awk sed grep cat sleep mktemp make pgrep kill head tail tr basename dirname rm \
    printf uname script ps cut wc sort mv cp mkdir chmod ln date true false test; do
    # type -P finds the binary even when the name is also a shell builtin (printf, kill, test): make runs
    # simple recipe lines without a shell, so it needs the binary.
    found=$(type -P "$tool" 2>/dev/null || true)
    if [ -z "$found" ]; then echo "selftest: required system utility not found: $tool" >&2; exit 2; fi
    ln -s "$found" "$STUBS/sys/$tool"
done
if found=$(command -v setsid 2>/dev/null); then ln -s "$found" "$STUBS/sys/setsid"; fi
TPATH="$STUBS/bin:$STUBS/sys"

# ---------------------------------------------------------------------------------------------------------
# Stubs
# ---------------------------------------------------------------------------------------------------------
# One stub body for every tool. It logs "<name>@<dir> <args> [recorded variables]", where <dir> is "." for the
# repository root or the directory's base name, then sleeps and exits as STUB_SLEEP_<name> and
# STUB_EXIT_<name> say.
cat > "$STUBS/stub" <<'STUB'
#!/usr/bin/env bash
name=$(basename "$0")
dir=.; [ "$PWD" = "$STUB_ROOT" ] || dir=$(basename "$PWD")
if [ "$name" = docker ] && [ "${1:-}" = compose ] && [ "${2:-}" = version ]; then
    exit "${STUB_EXIT_compose:-0}"
fi
line="$name@$dir $*"
if [ "$name" = gradlew ] && [ -n "${GOLDEN_WRITE:-}" ]; then line="$line GOLDEN_WRITE=$GOLDEN_WRITE"; fi
if [ "$name" = docker ]; then
    if [ -n "${BACKEND_REPLICAS:-}" ]; then line="$line BACKEND_REPLICAS=$BACKEND_REPLICAS"; fi
    if [ -n "${FRONTEND_REPLICAS:-}" ]; then line="$line FRONTEND_REPLICAS=$FRONTEND_REPLICAS"; fi
fi
if [ "$name" != nc ]; then printf '%s\n' "$line" >> "$STUB_LOG"; fi
case "$name $*" in
    "docker compose -f compose.stack.yaml config")
        printf '%s\n' 'services:' '  backend:' '    environment:' '      L4J_MODEL_ID: llama3.2' \
            '  load-balancer:' '    ports:' '      - mode: ingress' '        target: 8080' \
            '        published: "8866"' '        protocol: tcp'
        exit 0 ;;
    "docker compose -f compose.stack.yaml ps -q load-balancer")
        [ -n "${STUB_LB_RUNNING:-}" ] && printf '%s\n' "$STUB_LB_RUNNING"
        exit 0 ;;
esac
if [ "$name" = nc ]; then
    for port in ${STUB_OPEN_PORTS:-}; do
        eval "last=\${$#}"
        [ "$port" = "$last" ] && exit 0
    done
    exit 1
fi
echo "stub $name ran"
eval "pause=\${STUB_SLEEP_$name:-}"
[ -n "$pause" ] && sleep "$pause"
eval "code=\${STUB_EXIT_$name:-0}"
exit "$code"
STUB
chmod +x "$STUBS/stub"
for tool in docker npm node java nc; do cp "$STUBS/stub" "$STUBS/bin/$tool"; done
cp "$STUBS/stub" "$STUBS/gradlew"
cp "$STUBS/stub" "$STUBS/jdk/bin/java"

# Moves one stub out of PATH for a single case; restore_tools puts every parked stub back.
without_tool() { mv "$STUBS/bin/$1" "$STUBS/parked/$1"; }
restore_tools() { for f in "$STUBS"/parked/*; do [ -e "$f" ] && mv "$f" "$STUBS/bin/"; done; return 0; }

# ---------------------------------------------------------------------------------------------------------
# Runners. Every child gets a clean environment: nothing from the caller's shell (no provider settings,
# no JAVA_HOME) leaks in unless a case passes it.
# ---------------------------------------------------------------------------------------------------------
base_env() {
    printf '%s\n' "PATH=$TPATH" "HOME=${HOME:-/tmp}" "LANG=C" "STUB_LOG=$STUB_LOG" "STUB_ROOT=$ROOT" \
        "GRADLEW=$STUBS/gradlew"
}

# run_make [VAR=value...] [target...]: variables go on make's command line, which make exports to recipes.
run_make() {
    : > "$STUB_LOG"
    local envs=()
    while IFS= read -r e; do envs+=("$e"); done < <(base_env)
    OUT=$(cd "$ROOT" && env -i "${envs[@]}" make "$@" </dev/null 2>&1)
    STATUS=$?
}

# run_cmd [VAR=value...] -- command [args...]: runs a script directly, from the repository root.
run_cmd() {
    : > "$STUB_LOG"
    local envs=()
    while IFS= read -r e; do envs+=("$e"); done < <(base_env)
    while [ "$#" -gt 0 ] && [ "$1" != "--" ]; do envs+=("$1"); shift; done
    shift
    OUT=$(cd "$ROOT" && env -i "${envs[@]}" "$@" </dev/null 2>&1)
    STATUS=$?
}

# run_tty <answer> [VAR=value...] -- command [args...]: runs under a pseudo-terminal and types <answer>.
# The trailing sleep keeps input open: with a bare pipe, script exits at end of input before the child reads.
run_tty() {
    : > "$STUB_LOG"
    local answer="$1"; shift
    local envs=()
    while IFS= read -r e; do envs+=("$e"); done < <(base_env)
    while [ "$#" -gt 0 ] && [ "$1" != "--" ]; do envs+=("$1"); shift; done
    shift
    # The command goes into a file so no quoting has to survive script's own argument handling.
    { printf '%q ' env -i "${envs[@]}" "$@"; printf '\necho rc=$?\n'; } > "$STUBS/tty-command.sh"
    if [ "$(uname)" = Darwin ]; then
        OUT=$(cd "$ROOT" && { printf '%s\n' "$answer"; sleep 1; } | script -q /dev/null bash "$STUBS/tty-command.sh" 2>&1 | tr -d '\r')
    else
        OUT=$(cd "$ROOT" && { printf '%s\n' "$answer"; sleep 1; } | script -qec "bash $STUBS/tty-command.sh" /dev/null 2>&1 | tr -d '\r')
    fi
    # rc= can share a line with a prompt that printed no newline.
    STATUS=$(printf '%s\n' "$OUT" | grep -o 'rc=[0-9]*' | tail -1 | cut -d= -f2)
}

# ---------------------------------------------------------------------------------------------------------
# Assertions
# ---------------------------------------------------------------------------------------------------------
pass() { PASSES=$((PASSES + 1)); printf 'ok   %s\n' "$1"; }
fail() {
    FAILURES=$((FAILURES + 1)); printf 'FAIL %s\n' "$1"
    [ -n "${2:-}" ] && printf '%s\n' "$2" | sed 's/^/     | /'
    return 0
}

expect_status() { # <name> <0|nonzero|N>
    case "$2" in
        0) [ "$STATUS" = 0 ] && pass "$1" || fail "$1" "expected status 0, got $STATUS"$'\n'"$OUT" ;;
        nonzero) [ -n "$STATUS" ] && [ "$STATUS" != 0 ] && pass "$1" || fail "$1" "expected a failure, got $STATUS"$'\n'"$OUT" ;;
        *) [ "$STATUS" = "$2" ] && pass "$1" || fail "$1" "expected status $2, got $STATUS"$'\n'"$OUT" ;;
    esac
}

expect_calls() { # <name> <line>...: exactly these stub calls, in order
    local name="$1"; shift
    local want got
    want=$(printf '%s\n' "$@")
    got=$(cat "$STUB_LOG")
    [ "$want" = "$got" ] && pass "$name" || fail "$name" "expected calls:"$'\n'"$want"$'\n'"got:"$'\n'"$got"
}

expect_no_calls() { # <name>
    [ ! -s "$STUB_LOG" ] && pass "$1" || fail "$1" "expected no calls, got:"$'\n'"$(cat "$STUB_LOG")"
}

expect_log_contains() { # <name> <text>
    grep -qF -- "$2" "$STUB_LOG" && pass "$1" || fail "$1" "log lacks: $2"$'\n'"$(cat "$STUB_LOG")"
}

expect_output_contains() { # <name> <text>
    case "$OUT" in *"$2"*) pass "$1" ;; *) fail "$1" "output lacks: $2"$'\n'"$OUT" ;; esac
}

expect_output_lacks() { # <name> <text>
    case "$OUT" in *"$2"*) fail "$1" "output unexpectedly contains: $2" ;; *) pass "$1" ;; esac
}

# ---------------------------------------------------------------------------------------------------------
# Cases
# ---------------------------------------------------------------------------------------------------------

echo "== helpers"

without_tool docker
run_cmd -- scripts/make/require.sh docker
expect_status "require: missing docker fails" 1
expect_output_contains "require: names docker" "Missing tool: docker"
restore_tools

without_tool java
run_cmd -- scripts/make/require.sh java
expect_status "require: missing java (no JAVA_HOME) fails" 1
expect_output_contains "require: names java" "Missing tool: java"
run_cmd "JAVA_HOME=$STUBS/jdk" -- scripts/make/require.sh java
expect_status "require: JAVA_HOME with bin/java satisfies java" 0
restore_tools

run_cmd STUB_EXIT_compose=1 -- scripts/make/require.sh docker
expect_status "require: docker without the compose plugin fails" 1
expect_output_contains "require: names docker compose" "Missing tool: docker compose"

run_cmd -- scripts/make/require.sh docker node npm java
expect_status "require: all tools present" 0

run_cmd STUB_OPEN_PORTS=8080 -- scripts/make/port-free.sh 8080 "hint text"
expect_status "port-free: open port fails" 1
expect_output_contains "port-free: names the port" "Port 8080 is in use"
expect_output_contains "port-free: shows the hint" "hint text"
run_cmd -- scripts/make/port-free.sh 8080 "hint text"
expect_status "port-free: free port passes" 0

run_cmd CONFIRM=yes -- scripts/make/confirm.sh "stored runs"
expect_status "confirm: CONFIRM=yes proceeds" 0
run_cmd -- scripts/make/confirm.sh "stored runs"
expect_status "confirm: no terminal, no flag refuses" 1
expect_output_contains "confirm: refusal names the flag" "CONFIRM=yes"
expect_output_contains "confirm: refusal names the subject" "stored runs"
run_cmd CONFIRM=no -- scripts/make/confirm.sh "stored runs"
expect_status "confirm: CONFIRM=no without terminal refuses" 1

run_tty yes -- scripts/make/confirm.sh "stored runs"
expect_status "confirm: terminal answer yes proceeds" 0
run_tty y -- scripts/make/confirm.sh "stored runs"
expect_status "confirm: terminal answer y aborts" 1
expect_output_contains "confirm: abort says so" "Aborted."

echo "== development"

run_make deps-up
expect_status "deps-up: succeeds" 0
expect_calls "deps-up: starts the development containers" "docker@. compose up -d --wait"

run_make deps-down
expect_calls "deps-down: stops them, keeping data" "docker@. compose down"

run_make dev-backend
expect_calls "dev-backend: containers, then the backend from source" \
    "docker@. compose up -d --wait" "gradlew@. :backend:run"

run_make dev-frontend
expect_calls "dev-frontend: npm install, then the dev server, in frontend/" \
    "npm@frontend install" "npm@frontend run dev"

run_make STUB_OPEN_PORTS=8080 dev-backend
expect_status "dev-backend: port 8080 taken fails" nonzero
expect_output_contains "dev-backend: names 8080" "Port 8080 is in use"
expect_no_calls "dev-backend: starts nothing when the port is taken"

run_make STUB_OPEN_PORTS=5173 dev-frontend
expect_status "dev-frontend: port 5173 taken fails" nonzero
expect_output_contains "dev-frontend: names 5173" "Port 5173 is in use"
expect_no_calls "dev-frontend: starts nothing when the port is taken"

run_make STUB_OPEN_PORTS=5173 dev
expect_status "dev: a taken port fails" nonzero
expect_no_calls "dev: starts no container when a port is taken"

without_tool docker
run_make deps-up
expect_status "deps-up: missing docker fails" nonzero
expect_output_contains "deps-up: names docker" "Missing tool: docker"
expect_no_calls "deps-up: calls nothing without docker"
restore_tools

# Starts dev.sh in the background with a clean environment. Its pid is DEV_PID, its output $STUBS/dev.out.
start_dev() {
    : > "$STUB_LOG"
    local envs=()
    while IFS= read -r e; do envs+=("$e"); done < <(base_env)
    envs+=("$@")
    (cd "$ROOT" && exec env -i "${envs[@]}" scripts/make/dev.sh </dev/null >"$STUBS/dev.out" 2>&1) &
    DEV_PID=$!
}

wait_for_log() { # <fixed text> <seconds>
    local i=0
    while [ "$i" -lt $(($2 * 10)) ]; do
        grep -qF -- "$1" "$STUB_LOG" 2>/dev/null && return 0
        sleep 0.1; i=$((i + 1))
    done
    return 1
}

# Waits up to <seconds> for DEV_PID to end; sets STATUS (empty if it is still running) and OUT.
wait_dev() {
    local i=0
    while kill -0 "$DEV_PID" 2>/dev/null && [ "$i" -lt $(($1 * 10)) ]; do sleep 0.1; i=$((i + 1)); done
    if kill -0 "$DEV_PID" 2>/dev/null; then
        STATUS=""
        kill -KILL "$DEV_PID" 2>/dev/null
    else
        wait "$DEV_PID"; STATUS=$?
    fi
    OUT=$(cat "$STUBS/dev.out")
}

leftover_stubs() { pgrep -f "$STUBS/(bin|gradlew)" 2>/dev/null || true; }

start_dev STUB_SLEEP_gradlew=30 STUB_SLEEP_npm=30 DEV_DEBUG=1
if wait_for_log "gradlew@. :backend:run" 10 && wait_for_log "npm@frontend install" 10; then
    pass "dev.sh: starts both halves"
else
    fail "dev.sh: starts both halves" "$(cat "$STUB_LOG")"
fi
sleep 0.5
OUT=$(cat "$STUBS/dev.out")
pgids=$(printf '%s\n' "$OUT" | sed -n 's/^\[dev\] pgids \([0-9]*\) \([0-9]*\)$/\1 \2/p')
self_pgid=$(ps -o pgid= -p "$DEV_PID" | tr -d ' ')
set -- $pgids
if [ "$#" = 2 ] && [ "$1" != "$2" ] && [ "$1" != "$self_pgid" ] && [ "$2" != "$self_pgid" ]; then
    pass "dev.sh: each half has its own process group without a terminal"
else
    fail "dev.sh: each half has its own process group without a terminal" "pgids='$pgids' script=$self_pgid"
fi
kill -TERM "$DEV_PID"
wait_dev 5
expect_status "dev.sh: TERM stops it with 130" 130
[ -z "$(leftover_stubs)" ] && pass "dev.sh: TERM leaves no process from either half" \
    || fail "dev.sh: TERM leaves no process from either half" "$(ps -o pid,pgid,command -p $(leftover_stubs | tr '\n' ',')0)"
expect_output_contains "dev.sh: labels backend output" "[backend] stub gradlew ran"
expect_output_contains "dev.sh: labels frontend output" "[frontend] stub npm ran"
expect_output_lacks "dev.sh: no job-control noise on shutdown" "Terminated"

start_dev STUB_EXIT_gradlew=3 STUB_SLEEP_npm=30
wait_dev 5
expect_status "dev.sh: a half exiting by itself ends it with 1" 1
expect_output_contains "dev.sh: names the half and its status" "backend stopped (status 3)"
[ -z "$(leftover_stubs)" ] && pass "dev.sh: the other half is stopped" \
    || fail "dev.sh: the other half is stopped" "$(leftover_stubs)"
expect_output_lacks "dev.sh: no job-control noise after one half exits" "Terminated"

# A real Ctrl+C typed into a terminal running make dev: the interrupt reaches make and dev.sh together.
: > "$STUB_LOG"
{
    envs=()
    while IFS= read -r e; do envs+=("$e"); done < <(base_env)
    printf '%q ' env -i "${envs[@]}" STUB_SLEEP_gradlew=30 STUB_SLEEP_npm=30 make dev
    printf '\n'
} > "$STUBS/ctrl-c.sh"
feed() {
    local i=0
    while [ "$i" -lt 100 ] && ! grep -qF "npm@frontend install" "$STUB_LOG" 2>/dev/null; do sleep 0.1; i=$((i + 1)); done
    sleep 0.5; printf '\003'; sleep 3
}
if [ "$(uname)" = Darwin ]; then
    OUT=$(cd "$ROOT" && feed | script -q /dev/null bash "$STUBS/ctrl-c.sh" 2>&1 | tr -d '\r')
else
    OUT=$(cd "$ROOT" && feed | script -qec "bash $STUBS/ctrl-c.sh" /dev/null 2>&1 | tr -d '\r')
fi
expect_output_contains "make dev: Ctrl+C in a terminal stops both halves" "[dev] stopped"
[ -z "$(leftover_stubs)" ] && pass "make dev: Ctrl+C leaves no process" \
    || fail "make dev: Ctrl+C leaves no process" "$(leftover_stubs)"

echo "== help"

CONTRACT_TARGETS="help dev dev-backend dev-frontend deps-up deps-down up up-local pull-model status logs logs-lb \
scale down reset check test test-backend test-frontend test-live check-api generate-api lint format golden"

run_make
expect_status "help: bare make succeeds" 0
expect_no_calls "help: bare make starts nothing"
for group in "Development" "Packaged system" "Verification" "Maintenance"; do
    expect_output_contains "help: shows the $group group" "$group"
done
BARE_OUT="$OUT"
run_make help
[ "$OUT" = "$BARE_OUT" ] && pass "help: make help equals bare make" || fail "help: make help equals bare make"

undescribed=$(grep -E '^[a-z][a-z0-9-]*:' "$ROOT/Makefile" | grep -v '## ' || true)
[ -z "$undescribed" ] && pass "help: every target has a description" \
    || fail "help: every target has a description" "$undescribed"

run_make help
missing_targets=""
for t in $CONTRACT_TARGETS; do
    case "$OUT" in *"  $t "*) ;; *) missing_targets="$missing_targets $t" ;; esac
done
[ -z "$missing_targets" ] && pass "contract-complete: every contract target is in help" \
    || fail "contract-complete: every contract target is in help" "missing:$missing_targets"

cp "$ROOT/Makefile" "$STUBS/Makefile.drift"
printf '\nzz-new: ## New thing\n\t@true\n' >> "$STUBS/Makefile.drift"
: > "$STUB_LOG"
OUT=$(cd "$ROOT" && env -i PATH="$TPATH" make -f "$STUBS/Makefile.drift" help 2>&1)
expect_output_contains "help: a new target appears without editing a list" "zz-new"
expect_output_contains "help: with its description" "New thing"

echo "== packaged system"

S="docker@. compose -f compose.stack.yaml"

run_make up
expect_status "up: succeeds" 0
expect_calls "up: resolves the port, checks the balancer, starts the stack" \
    "$S config" "$S ps -q load-balancer" "$S up -d --build --wait"

run_make STUB_OPEN_PORTS=8866 up
expect_status "up: port taken by something else fails" nonzero
expect_output_contains "up: names the resolved port" "Port 8866 is in use"
expect_output_contains "up: names the override" "L4J_HTTP_PORT"
expect_calls "up: does not start the stack when the port is taken" "$S config" "$S ps -q load-balancer"

run_make STUB_OPEN_PORTS=8866 STUB_LB_RUNNING=abc123 up
expect_status "up: port held by this stack's own balancer proceeds" 0
expect_calls "up: re-applies the running stack" \
    "$S config" "$S ps -q load-balancer" "$S up -d --build --wait"

run_make up-local
expect_calls "up-local: starts with the model runtime" \
    "$S config" "$S ps -q load-balancer" "$S --profile local up -d --build --wait"

run_make pull-model
expect_output_contains "pull-model: says which model before pulling" "Pulling llama3.2"
expect_calls "pull-model: pulls the model the stack resolves" \
    "$S config" "$S --profile local exec ollama ollama pull llama3.2"

run_make status
expect_calls "status: ps" "$S ps"
run_make logs
expect_calls "logs: all logs" "$S logs"
run_make logs-lb
expect_calls "logs-lb: the balancer's access log" "$S logs --no-log-prefix load-balancer"

run_make scale
expect_status "scale: without counts fails" nonzero
expect_output_contains "scale: names BACKEND_REPLICAS" "BACKEND_REPLICAS"
expect_output_contains "scale: names FRONTEND_REPLICAS" "FRONTEND_REPLICAS"
expect_no_calls "scale: calls nothing without counts"

run_make scale BACKEND_REPLICAS=3
expect_status "scale: with a count succeeds" 0
expect_calls "scale: compose reads the count" "$S up -d --wait BACKEND_REPLICAS=3"

run_make down
expect_calls "down: stops the stack and the model runtime, keeping data" "$S --profile local down"

run_make reset
expect_status "reset: refuses without a terminal or CONFIRM" nonzero
expect_output_contains "reset: names CONFIRM=yes" "CONFIRM=yes"
expect_no_calls "reset: deletes nothing without confirmation"

run_make reset CONFIRM=yes
expect_status "reset: with CONFIRM=yes succeeds" 0
expect_calls "reset: removes containers and volumes, the model runtime included" "$S --profile local down -v"

run_make STUB_EXIT_docker=3 status
expect_status "status: a failing command fails the target" nonzero
expect_output_contains "status: the underlying status is shown" "Error 3"

echo "== verification and maintenance"

G="gradlew@."

run_make check
expect_status "check: succeeds" 0
expect_calls "check: ./gradlew check" "$G check"
run_make test
expect_calls "test: both suites through the wrapper" "$G :backend:test :frontend:test"
run_make test-backend
expect_calls "test-backend: the backend suite" "$G :backend:test"
run_make test-frontend
expect_calls "test-frontend: npm test in frontend/" "npm@frontend test"
run_make test-live
expect_calls "test-live: the live model task" "$G :backend:liveTest"
run_make check-api
expect_calls "check-api: the contract check" "$G :frontend:checkApi"

run_make generate-api
expect_calls "generate-api: compile the description, then regenerate the types" \
    "$G :backend:classes" "npm@frontend run generate:api"
run_make lint
expect_calls "lint: npm run lint in frontend/" "npm@frontend run lint"
run_make format
expect_calls "format: npm run format in frontend/" "npm@frontend run format"

run_make golden
expect_status "golden: refuses without a terminal or CONFIRM" nonzero
expect_output_contains "golden: names CONFIRM=yes" "CONFIRM=yes"
expect_no_calls "golden: rewrites nothing without confirmation"
run_make golden CONFIRM=yes
expect_status "golden: with CONFIRM=yes succeeds" 0
expect_calls "golden: rewrites through the filtered, rerun test task" \
    "$G :backend:test --tests *GoldenRunSnapshotTest --rerun GOLDEN_WRITE=1"

run_make STUB_EXIT_gradlew=3 check
expect_status "check: a failing build fails the target" nonzero
expect_output_contains "check: the underlying status is shown" "Error 3"

without_tool node
for t in check test check-api; do
    run_make "$t"
    expect_status "$t: missing node fails" nonzero
    expect_output_contains "$t: names node" "Missing tool: node"
    expect_no_calls "$t: runs no build without node"
done
restore_tools

leaks=""
for t in $CONTRACT_TARGETS; do
    [ "$t" = dev ] && continue
    run_make OLLAMA_API_KEY=sentinel-7f3a "$t"
    case "$OUT" in *sentinel-7f3a*) leaks="$leaks $t" ;; esac
done
[ -z "$leaks" ] && pass "credential: no target prints the credential" \
    || fail "credential: no target prints the credential" "printed by:$leaks"
named=$(grep -l OLLAMA_API_KEY "$ROOT/Makefile" "$ROOT"/scripts/make/*.sh | grep -v selftest.sh || true)
[ -z "$named" ] && pass "credential: the Makefile and scripts never name it" \
    || fail "credential: the Makefile and scripts never name it" "$named"

# ---------------------------------------------------------------------------------------------------------
echo
echo "selftest: $PASSES passed, $FAILURES failed"
[ "$FAILURES" -eq 0 ]
