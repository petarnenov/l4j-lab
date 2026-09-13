#!/usr/bin/env bash
# Guards a destructive make target (feature 006, FR-010, FR-013, R-004).
#
#   CONFIRM=yes           proceed without asking
#   a terminal attached   ask; only the word "yes" proceeds
#   no terminal           refuse, naming CONFIRM=yes, so CI or a script can never delete by accident
#
#   scripts/make/confirm.sh "the stack's stored runs"
set -euo pipefail

subject="$1"

if [ "${CONFIRM:-}" = yes ]; then
    exit 0
fi

if [ -t 0 ]; then
    printf 'This deletes %s. Type yes to continue: ' "$subject"
    read -r answer || answer=
    if [ "$answer" = yes ]; then
        exit 0
    fi
    echo "Aborted."
    exit 1
fi

echo "This deletes $subject. Refusing without a terminal; run with CONFIRM=yes to proceed." >&2
exit 1
