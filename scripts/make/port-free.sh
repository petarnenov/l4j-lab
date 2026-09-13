#!/usr/bin/env bash
# Fails naming the port when something already listens on it (feature 006, FR-008, R-006). It only probes;
# it never stops anything.
#
#   scripts/make/port-free.sh 8080 "Stop what is listening on it and run again."
set -euo pipefail

port="$1"
hint="$2"

if nc -z 127.0.0.1 "$port" >/dev/null 2>&1; then
    echo "Port $port is in use. $hint" >&2
    exit 1
fi
