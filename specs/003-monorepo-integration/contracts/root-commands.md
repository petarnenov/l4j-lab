# Contract: Verification Commands

**Feature**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md)

The commands a maintainer, a learner, and CI rely on. Each row is a promise: what the command runs,
what it needs, and what it says when it fails. Changing any row is a change to this contract.

## Root

| Command | Runs | Needs | Never runs |
|---------|------|-------|------------|
| `./gradlew check` | `:backend:check`, `:frontend:check` | JDK toolchain (provisioned), Node matching `.nvmrc`, Docker optional | `:backend:liveTest`, any model call |

## Backend (unchanged by this feature)

| Command | Runs | Needs |
|---------|------|-------|
| `./gradlew :backend:test` | 120 default backend tests | Docker optional (DB tests skip with a named message) |
| `./gradlew :backend:check` | `:backend:test` | as above |
| `./gradlew :backend:liveTest` | Live model test | `OLLAMA_API_KEY` or `L4J_PROVIDER=local`; skips with a named message otherwise |

## Frontend via Gradle (new)

| Command | Runs | Depends on |
|---------|------|------------|
| `./gradlew :frontend:checkNode` | Node presence and major version check | — |
| `./gradlew :frontend:npmCi` | `npm ci` when the lock file changed | `checkNode` |
| `./gradlew :frontend:test` | `npm test` (126 tests) | `npmCi` |
| `./gradlew :frontend:checkApi` | `npm run check:api` | `npmCi`, `:backend:classes` |
| `./gradlew :frontend:checkVersion` | `package.json` version equals `project.version` | — |
| `./gradlew :frontend:check` | `test`, `checkApi`, `checkVersion` | — |

## Frontend via npm (unchanged commands, new implementation of the API scripts)

| Command (in `frontend/`) | Runs |
|--------------------------|------|
| `npm test` | Vitest suite |
| `npm run generate:api` | Regenerates `src/api/schema.d.ts` from the API description |
| `npm run check:api` | Fails if `src/api/schema.d.ts` differs from a fresh generation |

## Failure messages

Each failure names its cause and its fix. Wording may be refined; the named elements may not be
removed.

| Condition | Emitted by | Must name |
|-----------|-----------|-----------|
| `node` not on PATH | `:frontend:checkNode` | "Node.js", the required major version, `frontend/.nvmrc` |
| Node major version differs | `:frontend:checkNode` | found version, required version |
| API description absent | `check:api` / `generate:api` | the expected path, `./gradlew :backend:classes` |
| Committed types stale | `check:api` | `src/api/schema.d.ts`, `npm run generate:api` |
| Manifest version differs | `:frontend:checkVersion` | both versions, both file paths |
| Any sub-check fails under `./gradlew check` | Gradle | the failing task path, e.g. `:frontend:checkApi` |
