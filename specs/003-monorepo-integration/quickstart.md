# Quickstart: Validating Monorepo Integration

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

Run these scenarios after implementation. Each maps to a user story or success criterion. Commands
are in [contracts/root-commands.md](./contracts/root-commands.md); CI selection is in
[contracts/ci-workflows.md](./contracts/ci-workflows.md).

## Prerequisites

- Node.js with the major version in `frontend/.nvmrc` (24)
- Docker, for the backend database tests (they skip with a named message without it)
- No model credential. Unset `OLLAMA_API_KEY` for these scenarios to prove none is needed.

## Baseline, before implementation (SC-007)

Record the counts reported by `./gradlew :backend:test` and `cd frontend && npm test`. At the time of
implementation (T001) they were 120 and 120. After this feature the frontend has 126: the same 120 plus 6 for `scripts/api-contract.mjs`.

## Scenario 1: One command verifies everything (US1, SC-001)

```bash
git clone <repository> /tmp/l4j-verify && cd /tmp/l4j-verify
./gradlew check
```

Use a fresh clone rather than `git clean` in the working copy, which would also delete untracked
work and local `.env` files.

**Expect**: Node is checked, `npm ci` runs, the backend compiles, and `:backend:test`,
`:frontend:test`, `:frontend:checkApi`, `:frontend:checkVersion` all execute. The build succeeds. No
model call is attempted. Test counts equal the baseline.

## Scenario 2: Stale frontend types fail the root command (US1, SC-002)

1. Add a field to a record in `backend/src/main/java/dev/l4jlab/chain/web/`.
2. Run `./gradlew check`.

**Expect**: failure at `:frontend:checkApi`, naming `src/api/schema.d.ts` and `npm run generate:api`.

3. Run `cd frontend && npm run generate:api`, then `./gradlew check` again.

**Expect**: success. Revert the field and regenerate afterwards.

## Scenario 3: A version bump needs one edit (US2, SC-003)

1. Change `version` in `backend/build.gradle.kts` to `0.2.0`. Do not edit anything else.
2. Run `./gradlew :frontend:checkApi`.

**Expect**: succeeds, and `backend/build/classes/java/main/META-INF/swagger/openapi.yml` reports
`version: 0.2.0`.

3. Run `./gradlew :frontend:checkVersion`.

**Expect**: fails naming `0.2.0`, `0.1.0`, and both files. Set `frontend/package.json` to `0.2.0` and
it passes. Revert both.

## Scenario 4: Missing description explains itself (US2)

```bash
rm -rf backend/build
cd frontend && npm run check:api
```

**Expect**: non-zero exit naming the expected description path and `./gradlew :backend:classes`.

## Scenario 5: One name, one version (US3, SC-004)

Compare `rootProject.name` in `settings.gradle.kts`, `info.title` and `info.version` in the generated
description, and `name` and `version` in `frontend/package.json`.

**Expect**: base name `financial-agent-chain` in all three; one version in all three.

## Scenario 6: Missing Node is named (edge case)

Run `./gradlew :frontend:checkNode` with `node` removed from the PATH (for example
`PATH=/usr/bin:/bin ./gradlew :frontend:checkNode` on macOS without a system Node).

**Expect**: failure naming Node.js, version 24, and `frontend/.nvmrc`.

## Scenario 7: CI selects by path (US4, SC-005)

After pushing to GitHub, open three pull requests: one touching only `frontend/src/`, one only
`backend/src/`, one only `specs/`.

**Expect**: the selection table in [contracts/ci-workflows.md](./contracts/ci-workflows.md). The
specs-only pull request runs no workflow.

**Note**: skipped workflows report no status. Do not mark these workflows as required checks in
branch protection without accounting for that.

## Scenario 8: README alone is enough (US5, SC-006)

A reader who has not seen this feature follows only the README's Test section to run Scenario 1 and to
recover from Scenario 2.

**Expect**: they succeed without opening a build file.
