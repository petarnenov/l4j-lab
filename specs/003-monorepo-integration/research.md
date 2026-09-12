# Phase 0 Research: Monorepo Integration

**Date**: 2026-09-12 | **Plan**: [plan.md](./plan.md)

All unknowns from Technical Context are resolved here. No NEEDS CLARIFICATION remains.

## R-001: A stable location for the API description

**Decision**: Pass `-Amicronaut.openapi.filename=openapi` to the annotation processor through the
existing `compilerArgs` block in `backend/build.gradle.kts`. The description is then always written to
`backend/build/classes/java/main/META-INF/swagger/openapi.yml`, whatever the application version. The
frontend scripts read that one path. The description is not committed.

**Rationale**: The current file name, `financial-agent-chain-0.1.yml`, is Micronaut OpenAPI's default
of title plus version. Overriding only the file name is a one-argument change in the file that already
configures the processor, so a learner sees the whole decision next to `micronaut.processing.group`.
The property exists in the version the Platform BOM manages: `micronaut.openapi.filename` and
`micronaut.openapi.target.file` are both present in `micronaut-openapi-7.1.3.jar`, read from the
Gradle cache on 2026-09-12.

Not committing the description keeps exactly one committed generated artifact, `schema.d.ts`, with
one drift check. Committing both would add a second drift check for no new protection, since the
types already change whenever the description does.

**Verify during implementation**: the exact output path after the argument is applied, and that an
incremental compile rewrites the file when a web DTO changes. `micronaut.processing.incremental(true)`
is on. If an incremental compile leaves a stale description, the contract task depends on a full
`classes` run for that source set rather than disabling incremental processing project-wide.

**Verified during implementation (T004, 2026-09-12)**: after `./gradlew :backend:clean :backend:classes`
the only file in `META-INF/swagger/` is `openapi.yml`. A temporary `probeField` added to
`StartRunResponse` appeared in `openapi.yml` after an incremental `:backend:classes` with no clean, and
disappeared again after reverting and recompiling incrementally. Field changes therefore propagate.

**But incremental processing corrupts the description (found in T005)**: after an incremental compile
that recompiled 7 of the classes, `check:api` reported 11 missing lines in `schema.d.ts`, every one a
Javadoc-derived `@description` on a type whose source was not recompiled. Micronaut OpenAPI reads
Javadoc from source elements, and classes loaded from the previous output carry none. A clean compile
matched the committed file exactly. The drift was false, and it predates this feature: any
`generate:api` after an incremental build would have silently stripped the descriptions.

**Revised decision**: `micronaut { processing { incremental(false) } }` in `backend/build.gradle.kts`,
with a comment giving the reason. Gradle then reports "Full recompilation is required because
BeanDefinitionInjectProcessor is not incremental" and the description is complete after any compile;
the same probe produced zero drift. The cost is a full backend compile, about two seconds at this size.
This replaces the fallback above, a separate non-incremental compile only for `checkApi`, which would
have added a second compile task whose purpose a reader could not guess.

**Alternatives considered**:

- `micronaut.openapi.target.file` pointing at a root `contracts/openapi.yml`. A path outside the build
  directory breaks Gradle's clean and up-to-date model, and invites committing the file.
- A Gradle `Copy` task exporting the file to a shorter path. Adds a task whose only job is to rename
  a file the processor can already name.
- Committing the description. Rejected above.

## R-002: One version, declared once

**Decision**: `version = "0.1.0"` in `backend/build.gradle.kts` is the single declaration. The build
passes it to the processor as `-Amicronaut.openapi.expand.api.version=<project.version>`, and
`@Info(version = "${api.version}")` in `Application.java` reads it. The frontend manifest keeps a
literal `"version"` field, because npm requires one, and a Gradle verification task fails if it
differs from `project.version`, naming both values.

**Rationale**: `micronaut.openapi.expand.*` is the processor's placeholder mechanism, present in the
7.1.3 jar alongside `openapi.version`. It lets the annotation stay a readable literal-looking value
while the number lives in the build. Semantic form `0.1.0` matches what npm already uses; Gradle
accepts any string.

**Alternatives considered**:

- Writing the version into `package.json` from Gradle. A build that edits a committed source file is
  surprising to a reader and produces a dirty tree on every run.
- Reading the version from `package.json` into Gradle. Makes the frontend manifest the source of the
  backend's version, which inverts the contract direction.

## R-003: One project name

**Decision**: `financial-agent-chain`. `settings.gradle.kts` changes `rootProject.name` from `l4j-lab`.
The OpenAPI title and the README already use it. The frontend package keeps the suffix
`financial-agent-chain-frontend`, which shares the base name as FR-012 requires and stays unique if a
second package ever appears.

**Rationale**: Two of the three places already agree. The root project name affects only the IDE
project label and Gradle's output headers; subproject paths `:backend` and `:frontend` do not change.

**Alternatives considered**: `l4j-lab` everywhere. It names the umbrella lab in the constitution title,
not this application, and would change the API description a learner reads.

## R-004: How the root build invokes the frontend

**Decision**: Make `frontend/` a Gradle subproject with its own `frontend/build.gradle.kts`, applying
only the `base` plugin and declaring `Exec` tasks that call npm:

| Task | Runs | Depends on |
|------|------|------------|
| `:frontend:checkNode` | Verifies `node` is on the PATH and its major version matches `frontend/.nvmrc` | nothing |
| `:frontend:npmCi` | `npm ci`, skipped when `node_modules/.package-lock.json` is newer than `package-lock.json` | `checkNode` |
| `:frontend:test` | `npm test` | `npmCi` |
| `:frontend:checkApi` | `npm run check:api` | `npmCi`, `:backend:classes` |
| `:frontend:checkVersion` | Compares `package.json` version with `project.version` | nothing |
| `:frontend:check` | lifecycle | `test`, `checkApi`, `checkVersion` |

`./gradlew check` from the root then runs `check` in both subprojects.

**Rationale**: This is the smallest wiring that satisfies FR-005 to FR-011. Each task is one `Exec`
with its command spelled out, so a learner reads `npm test` in the build file and knows exactly what
Gradle runs. npm remains the frontend's build tool (FR-009): Gradle never resolves a JavaScript
dependency or runs Vite itself. Making `frontend` a real subproject, rather than adding tasks to the
root, keeps the task paths symmetrical (`:backend:check`, `:frontend:check`) and keeps the root
build script empty of task logic.

The cross-project `dependsOn(":backend:classes")` is the direct expression of "the description exists
before the check runs" (FR-006). Configuration cache and isolated projects are not enabled in this
repository, so the dependency is legal and readable.

**Windows**: the tasks resolve `npm.cmd` on Windows and `npm` elsewhere, one conditional in the
build file.

**Alternatives considered**:

- The `com.github.node-gradle.node` plugin. It can download a pinned Node, but it adds a third-party
  plugin, hides which Node binary runs behind its own tasks, and its compatibility with Gradle 9.4.1
  was not verifiable offline. Rejected against Principle I.
- Root-level tasks without a `frontend` subproject. Works, but puts task logic in the root script
  and breaks the `:half:check` symmetry.
- A shell script or Makefile as the root entry point. A second entry point beside `./gradlew`, which
  the constitution's single-build-tool rationale argues against.

## R-005: Node provisioning

**Decision**: Node is a documented prerequisite, like Docker. `frontend/.nvmrc` holds the major
version (`24`), which is the single declaration: `:frontend:checkNode` reads it locally, and CI's
`actions/setup-node` reads it through `node-version-file`. `package.json` gains `"engines": { "node":
">=24" }` so npm itself warns on a mismatch.

**Rationale**: The README already documents `cd frontend && npm install`, so the project already
depends on an installed Node. Making the dependency explicit and checked, with a message naming the
required version, is the honest fix. `24` matches the version in use on the maintainer's machine
(`v24.14.1`).

**Alternatives considered**: Gradle-provisioned Node through the plugin in R-004. Rejected there.

## R-006: The contract check itself

**Decision**: Replace the two inline npm scripts with one small Node script,
`frontend/scripts/api-contract.mjs`, invoked as `generate:api` (`node scripts/api-contract.mjs
generate`) and `check:api` (`node scripts/api-contract.mjs check`). It:

1. Resolves the description path from R-001 and, if absent, exits non-zero with a message naming the
   path and `./gradlew :backend:classes` (FR-003).
2. Runs `openapi-typescript` to the committed `src/api/schema.d.ts` (generate) or to
   `node_modules/.cache/schema.check.d.ts` (check).
3. In check mode compares the two files byte for byte and, on difference, exits non-zero with
   `npm run generate:api` as the fix.

**Rationale**: The current `check:api` uses `diff -q`, which does not exist on a stock Windows shell
and prints only "files differ". A script lets the failure explain itself, which is the same standard
the backend holds its error messages to. The script stays in the frontend's language and tooling.

**Alternatives considered**: Keeping inline scripts with `test -f` guards. Still POSIX-only and still
silent about the fix.

## R-007: Continuous integration on GitHub Actions

**Decision**: Three workflow files, each triggered on `push` and `pull_request` with native `paths`
filters:

| Workflow | Command | Toolchains |
|----------|---------|------------|
| `backend.yml` | `./gradlew :backend:check` | JDK 25 |
| `frontend.yml` | `npm ci && npm test` in `frontend/` | Node from `.nvmrc` |
| `contract.yml` | `./gradlew :frontend:checkApi :frontend:checkVersion` | JDK 25, Node from `.nvmrc` |

The path sets are in [contracts/ci-workflows.md](./contracts/ci-workflows.md). Shared build files and
the workflow files themselves appear in all three sets.

**Rationale**: Native `paths` filters need no third-party action and are readable in the workflow
header. Three files, one per concern, mirror the three checks the spec names and make a failure's
source visible from its workflow name. The frontend workflow uses the per-half npm commands, which
FR-016 permits and which avoids installing a JDK to run Vitest. Hosted Ubuntu runners have Docker, so
the backend's Testcontainers tests run rather than skip.

`actions/setup-java` with `distribution: temurin`, `java-version: 25`, and `gradle/actions/setup-gradle`
provide the JDK and Gradle caching. `actions/setup-node` with `cache: npm` and
`cache-dependency-path: frontend/package-lock.json` provides Node. Action versions are pinned by
major tag in the workflow files and recorded at implementation time.

**Known behavior**: a workflow skipped by `paths` reports no status. If branch protection later marks
these checks as required, a docs-only pull request would wait forever. That is a branch-protection
decision outside this feature; the quickstart notes it.

**Alternatives considered**:

- One workflow with `dorny/paths-filter` and conditional jobs. Allows a single required check, but adds
  a third-party action to the path every change goes through.
- One workflow running everything. Violates FR-014.

## R-008: Constitution amendment

**Decision**: No amendment. The plan records the justification (FR-018).

**Rationale**, against each Language and build bullet:

- "Gradle is the only build tool for the backend." The backend's build tool is unchanged. The frontend
  was never covered by this bullet and remains built by npm, which the stack section implies by naming
  Vite.
- "Build scripts use the Gradle Kotlin DSL … MUST NOT carry application logic." `frontend/build.gradle.kts`
  is Kotlin DSL and holds task wiring, a Node version check, and a version comparison. None of that is
  application logic.
- "Every documented command MUST go through [the wrapper], so the build never depends on what happens
  to be installed." Read in context, this bullet is about the backend's Gradle and has never covered
  the documented npm commands. The root `check` now depends on Node being installed, which it did not
  before. This widens a dependency rather than breaking a rule, and R-005 makes it explicit and checked.
- Principle IV: "Any change that widens this dependency further MUST say so in the feature plan." That
  clause concerns the credential dependency of the full suite. This feature does not widen it: no check
  added here needs a credential.

If a reviewer reads the wrapper bullet as project-wide, the remedy is a PATCH clarification scoping it
to the backend, not a change to this design.
