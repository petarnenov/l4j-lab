# Research: Hold the documents to the code

**Feature**: `009-spec-drift-check` | **Phase**: 0

Each entry is a decision, why it was taken, and what was rejected. Several were settled by reading
this repository rather than by reasoning, and those are the ones worth reading.

---

## R-001: When a feature's documents become binding

**Decision**: a feature is held to its code when its specification's `Status` says it is implemented.
The check also verifies that claim, so the signal cannot rot unnoticed.

**Why not "all tasks complete"**, which was the obvious answer and the one the spec assumed: it is
wrong in this repository, and measurably so.

| Feature | Tasks complete | Delivered? |
|---|---|---|
| 001-financial-agent-chain | 101 of 102 | yes |
| 008-mcp-console | 70 of 71 | yes |
| every other completed feature | all | yes |

Both open tasks are open *on purpose*. 001's `T101` asks for a recorded trace against both provider
modes; 008's `T071` asks that someone who has never seen a page reach a result in two minutes. Each
needs something a machine cannot supply, and each is documented as deliberately unfinished. A rule
that treated an unticked box as "not delivered" would exclude the two features with the most
carefully reasoned open tasks — precisely backwards.

**Why `Status` works, and the catch.** It is a declaration, which makes it exactly the thing that
drifts. It has already:

| Feature | Says | Actually |
|---|---|---|
| 006, 007, 008 | `Draft` | implemented and merged to `main` |
| 005 | "All 46 tasks complete" | `tasks.md` holds 54 |

So `Status` alone would be a signal maintained by nobody. **The resolution is to make the check
verify it**: a feature whose tasks are essentially complete but whose `Status` says `Draft` is
reported, and so is a `Status` line whose stated task count disagrees with the file. The arming
switch is human-controlled, and the check watches the switch.

That also answers the spec's edge case about plans written before their code. During implementation a
feature is `Draft`, its plan describes what will exist, and nothing holds it to the repository. The
moment someone declares it implemented, it becomes binding — and if they forget to declare it, the
check says so.

**Alternatives considered**: a percentage threshold (arbitrary, and would still have excluded 001);
inferring delivery from whether the feature's files exist (circular — that is the thing being
checked); a separate manifest listing which features are in scope (one more file to forget).

---

## R-002: No markdown parser, no lint framework

**Decision**: extract claims with a small set of explicit patterns over the raw text, committed as
[contracts/claim-grammar.md](./contracts/claim-grammar.md). No dependency.

**Rationale**: the claim surface is not a grammar, it is about a dozen regular forms — a fenced
command line, an inline backtick path, a markdown link, a bold requirement identifier, a line of a
file tree. A parser would give a syntax tree of prose, and prose is exactly what FR-006 says not to
interpret. The tree would be work spent on the part of the document the check must ignore.

It also keeps the check legible, which FR-013 makes a requirement rather than a preference: someone
should be able to read the patterns and say what is not covered. A hundred lines of named regular
expressions can be read that way; a visitor over an AST cannot.

**Alternatives considered**: `remark` and friends (a dependency tree, for a tree we do not want);
Vale or similar prose linters (they check style, which the spec explicitly puts out of scope);
writing it in Java as a Gradle plugin (the JVM is for the application, and this tool has to read
`Makefile`).

**The cost, stated**: patterns will miss claim forms nobody anticipated. That is why FR-012 requires
the count of checked claims in every passing run — a check whose extraction silently stops matching
would otherwise look identical to a clean repository.

---

## R-003: Where the tool lives, and what runs its tests

**Decision**: `scripts/spec-drift/`, tested by `node --test`, wired into the root project's `check`.

**Rationale**: the repository already has three homes for code — the JVM modules, the frontend, and
build tooling in `frontend/scripts/` and `scripts/make/`. This is the third kind. The two existing
Node tools sit under `frontend/` because they are about the frontend; this one is about `specs/`,
`Makefile` and every module, so it belongs above all of them.

That placement costs the Vitest the frontend already pins, and `node --test` replaces it at a price
of zero: it ships with the Node version `frontend/.nvmrc` already requires. Recorded in the plan's
Complexity Tracking because a second test runner is the kind of thing that should never be silent,
even when it adds no dependency.

**On the root `build.gradle.kts`**: the repository has no root build file today. Adding one with the
`base` plugin gives the root project a `check` lifecycle, which is how FR-008 is satisfied without a
flag anyone has to remember. It carries no application logic, which the constitution requires of
build scripts.

---

## R-004: What counts as a command this project defines

**Decision**: the targets in `Makefile`, the scripts in each `package.json`, and the tasks each
Gradle build file registers. Anything else named in a document — `docker`, `curl`, `git` — is not a
claim this project can be held to.

**Rationale**: FR-003 is about a promise the project makes. `make mcp-up` is a promise; `curl` is an
observation about the reader's machine. Checking the latter would mean failing a build because
someone's container runtime is absent, which is not drift.

**Gradle tasks are read, not run.** FR-010 forbids executing what is being checked, and
`./gradlew tasks` would configure every project — slow, and a side effect for a question about
existence. The task names are read from the build files as text, which is less precise and honest
about being so: a task registered dynamically will be missed, and the grammar says that.

---

## R-005: Two kinds of exception, and why one is not enough

**Decision**: an inline marker at the claim, and a committed baseline of pre-existing drift. Both
carry reasons; both are reported on every run.

They answer different questions:

- **Inline** (`<!-- drift-ok: … -->` on the claim's line) is for a document that names something
  absent *on purpose* — a path that illustrates a mistake, a command a reader should not run. FR-014
  requires this be expressible at the claim rather than by disabling a file, because the rest of the
  file is still worth checking.
- **Baseline** (`scripts/spec-drift/baseline.json`) is for drift that already exists in records this
  feature is forbidden to edit. It is a statement about history, not about a claim's intent.

**What stops the baseline becoming permanent**: it is printed on every run (FR-015), and an entry
that no longer matches real drift fails. So closing a baselined drift forces the entry out, and the
list can only shrink by itself.

**Alternative considered**: one mechanism for both. Rejected because it would either put
historical accidents inline in documents this feature must not edit, or move deliberate authorial
intent out of the document that intends it.

---

## R-006: Reported drift is a failure, not a warning

**Decision**: a claim that fails, outside the baseline and without an exemption, fails the build.

**Rationale**: this repository already applies the reasoning elsewhere — `vite.config.ts` raises a
test timeout rather than tolerating flaky failures, `check:dev-only` fails a build rather than
printing a notice. A warning nobody must act on is a warning nobody acts on, and the failure mode
this feature exists to fix is precisely "nothing broke, so nobody looked".

**The counter-argument, and why it loses**: a document can be wrong in the other direction — the code
changed and the document was right. The check cannot tell which. But it does not need to: failing
says *these two disagree*, and a person decides which to change. That is the same posture the spec
takes in Out of Scope, where automatic correction is excluded for exactly this reason.

---

## R-007: What the check must say about what it does not check

**Decision**: every run prints the claim kinds it checked with counts, and one line naming the
categories it does not attempt. The source carries the same list beside the patterns.

**Rationale**: FR-013, and a specific failure this repository has already suffered. Feature 007's
contracts state that tool declarations are served verbatim; they are not, and it took feature 008's
live suite to find out (finding F-001 there). A check that printed "documents verified" would invite
exactly that belief about prose. Printing what was checked, and what was not, is the difference
between a useful tool and a false assurance.

---

## R-008: LangChain4j capability inventory (Constitution Principle I)

**Decision**: **this feature touches no LangChain4j module**, so the inventory covers an empty set,
and this entry is the record the amendment requires rather than a skipped gate.

**Evidence**: the planned change set is `scripts/spec-drift/**` (new Node), a new root
`build.gradle.kts` carrying one task registration, and one `Makefile` target. No file under
`backend/`, `mcp-server/`, `legacy-billing-api/`, `token-issuer/` or `frontend/src/` is edited; no
JVM dependency is added; nothing is compiled that was not compiled before. The tool reads text files
and reports.

**The condition that re-opens this gate**: if the check ever needs to understand the application
rather than the repository's structure — for instance to verify a claim about an agent's behaviour —
that is a different feature, and the inventory applies to it.

---

## R-009: completeness is claimed, never inferred

**Added during analysis, after the first rule was measured.** The grammar first said that naming a
directory claimed its contents were accounted for. That reads well and fails immediately.

**What measuring it showed.** Feature 008's plan names `frontend/` as the root of its file tree. Under
the inferred rule, that plan would have to account for `node_modules`, `package-lock.json`,
`eslint.config.js`, `index.html`, `nginx.conf` and `tsconfig.tsbuildinfo` — six files with no
relationship to the feature. Six false failures from one directory, in the one case the rule was
designed from.

**Why no patch fixes it.** A plan's file tree describes what a feature *touches*, not what a directory
*contains*. For `frontend/src/mcp/`, which feature 008 created, those coincide. For `frontend/`, which
it reached into, they do not. Nothing in the tree separates the two, because the difference is intent.

**Decision**: a directory line in a fenced tree carrying the token `[complete]` claims its contents
are accounted for. Nothing else does.

**The cost, stated**: FR-002 applies to nothing written before this check, since no existing feature
carries a marker. The alternatives were editing eight past specifications, which this feature may not
do, or six false failures, which would have the check switched off inside a week. A check that is
narrow and believed beats one that is broad and ignored.

**Why this is the same shape as the exemption rule (R-005)**: both put the assertion in the document,
at the place it applies, made by the person who knows. A heuristic that guesses intent produces
exactly the noise that gets a check disabled.
