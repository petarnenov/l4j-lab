# Data Model: Makefile Entry Point

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

No persisted data changes. The entities are the parts of the Makefile a reader and the self-test reason
about. The concrete instances are in [contracts/make-targets.md](./contracts/make-targets.md).

## Target

| Field | Description | Rule |
|-------|-------------|------|
| name | What is typed after `make` | Lowercase, hyphen-separated, unique; declared `.PHONY` |
| group | One of the target groups below | Set by the nearest preceding `##@` line |
| description | One line shown in help | Required: the `## ` suffix on the target line (FR-003, R-002) |
| needs | Tools checked before anything runs | Checked by `require.sh`; failure starts nothing (FR-015) |
| checks | Preconditions: free port, confirmation, required variable | Evaluated before the delegated command |
| delegates to | The existing command or short sequence of existing commands | Last command on the recipe line; nothing reimplemented (FR-002, R-007) |
| destructive | Deletes or overwrites stored data | If true, requires a Confirmation |

## Target group

`Help`, `Development`, `Packaged system`, `Verification`, `Maintenance`. Printed in that order, which is
the order the groups appear in the Makefile.

## Confirmation

| Field | Description |
|-------|-------------|
| subject | What will be deleted or overwritten, shown in the prompt and in the refusal |
| flag | `CONFIRM=yes` in the environment or on the `make` command line |
| terminal | Whether standard input is a terminal |

States: **flag set** → proceed. **No flag, terminal** → prompt; `yes` proceeds, anything else aborts with
status 1. **No flag, no terminal** → refuse with status 1, naming the flag. The delegated command runs only
from the proceed state (SC-004).

## Development session (`dev`)

States and transitions of `scripts/make/dev.sh`:

```text
starting ──both launched──▶ running
running ──INT/TERM received──▶ stopping(both) ──▶ exit 130
running ──backend or frontend ends──▶ stopping(other) ──▶ exit 1, naming the half that ended
```

Invariant: after exit, no process from either half remains (checked by the self-test, and by port 8080 and
5173 being closed in the quickstart).
