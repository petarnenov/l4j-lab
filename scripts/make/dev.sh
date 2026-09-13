#!/usr/bin/env bash
# Runs the backend and the frontend together for `make dev` (feature 006, FR-005, FR-006, research R-003).
#
# Each half runs as its own job with its own process group, so stopping a half stops everything it started
# (the Gradle client and the JVM, npm and Vite). Output is labeled [backend] or [frontend]. One interrupt
# stops both. If either half ends by itself, the other is stopped too and the script exits 1.
#
# Written for bash 3.2, which macOS ships: there is no `wait -n`, so the halves are polled once a second.
# Job control (`set -m`) is on only while the two jobs start, to give each its own group. It is turned off
# at once so that the poll loop's own commands stay in the terminal's foreground group (where Ctrl+C
# lands) and so bash prints no "Terminated" notices when the halves are stopped.
#
# No -e: a half ending with a non-zero status is an event this script handles, not a reason to quit early.
set -uo pipefail

GRADLEW="${GRADLEW:-./gradlew}"
state=$(mktemp -d "${TMPDIR:-/tmp}/l4j-dev.XXXXXX")
trap 'rm -rf "$state"' EXIT

label() { while IFS= read -r line; do printf '[%s] %s\n' "$1" "$line"; done; }

# Each half records its own exit status before its output pipe closes, so the label loop cannot mask it.
set -m
{ "$GRADLEW" :backend:run; echo $? > "$state/backend"; } 2>&1 | label backend &
backend=$(jobs -p | tail -1)
{ cd frontend && npm install && npm run dev; echo $? > "$state/frontend"; } 2>&1 | label frontend &
frontend=$(jobs -p | tail -1)
set +m

if [ "${DEV_DEBUG:-}" = 1 ]; then echo "[dev] pgids $backend $frontend"; fi

alive() { kill -0 -- "-$1" 2>/dev/null; }

# TERM both groups, give them 10 seconds, then KILL whatever is left, and reap quietly.
stop_all() {
    kill -TERM -- "-$backend" "-$frontend" 2>/dev/null
    local i=0
    while { alive "$backend" || alive "$frontend"; } && [ "$i" -lt 100 ]; do sleep 0.1; i=$((i + 1)); done
    kill -KILL -- "-$backend" "-$frontend" 2>/dev/null
    wait 2>/dev/null
}

on_interrupt() {
    stop_all
    echo "[dev] stopped"
    exit 130
}
trap on_interrupt INT TERM

ended() { [ -f "$state/$1" ] || ! alive "$2"; }

while :; do
    for half in backend frontend; do
        if [ "$half" = backend ]; then pgid=$backend; other=frontend; else pgid=$frontend; other=backend; fi
        if ended "$half" "$pgid"; then
            status=$(cat "$state/$half" 2>/dev/null || echo "unknown")
            echo "[dev] $half stopped (status $status); stopping $other."
            stop_all
            exit 1
        fi
    done
    sleep 1
done
