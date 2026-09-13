#!/usr/bin/env bash
# Fails naming the first missing tool, before a make target starts anything (feature 006, FR-015, R-005).
#
#   scripts/make/require.sh docker java node npm
set -euo pipefail

hint() {
    case "$1" in
        docker | "docker compose") echo "Install Docker with the Compose plugin: https://docs.docker.com/get-docker/." ;;
        node | npm) echo "Install Node.js at the version in frontend/.nvmrc (nvm install in frontend/)." ;;
        java) echo "Install a JDK 17 or newer to launch the Gradle wrapper; it provisions Java 25 itself." ;;
        *) echo "Install it and run again." ;;
    esac
}

missing() {
    echo "Missing tool: $1. $(hint "$1") Nothing was started." >&2
    exit 1
}

for tool in "$@"; do
    case "$tool" in
        java)
            # The wrapper launches with java on the PATH or with $JAVA_HOME/bin/java, so either will do.
            command -v java >/dev/null 2>&1 || [ -x "${JAVA_HOME:-/nonexistent}/bin/java" ] || missing java
            ;;
        docker)
            command -v docker >/dev/null 2>&1 || missing docker
            # Compose v2 is a plugin: docker can be present without it.
            docker compose version >/dev/null 2>&1 || missing "docker compose"
            ;;
        *)
            command -v "$tool" >/dev/null 2>&1 || missing "$tool"
            ;;
    esac
done
