# Contract: what counts as a claim

A claim is something a document asserts about this repository that can be verified without
judgement. This file is the complete list. If a form is not here, the check does not see it — and
FR-013 requires the check to say so rather than let a reader assume otherwise.

## The five kinds

### 1. Path

A repository-relative path in inline code, or a filename in a fenced file-tree block.

```text
`frontend/src/mcp/transport.ts`          → claims that file exists
`scripts/spec-drift/`                     → claims that directory exists
├── check.mjs                             → inside a fenced tree, resolved against the tree's root
```

**Recognised** when the text is inside backticks or a fenced block, and either contains `/` or ends
in a known extension (`.ts .tsx .mjs .js .java .kts .json .yaml .yml .md .sql .sh`).

**Not recognised**: a bare word, a path inside a URL, a path containing a placeholder segment
(`<name>`, `{id}`, `…`, `***`). Those are illustrations, not claims.

#### Completeness is claimed by marking, never inferred

FR-002 requires that a completed feature's implementation files be named by its documents, and
"belonging to a feature" needs a definition that produces no false failures.

**The rule**: a directory line inside a fenced file tree whose comment contains the token
`[complete]` claims that every file directly beneath it is named somewhere in that feature's
documents. Nothing else is a completeness claim.

```text
        |-- components/            # [complete] every file here belongs to this feature
        |   |-- ToolList.tsx
        |   `-- ExchangeView.tsx
```

**Why marking rather than inference.** The first draft of this grammar said a named directory was
itself a completeness claim. Checked against the feature it was designed from, it demanded that
feature 008's plan account for `node_modules`, `package-lock.json`, `eslint.config.js`, `index.html`,
`nginx.conf` and `tsconfig.tsbuildinfo` -- six files with no relationship to that feature -- because
its tree names `frontend/` as its root. Six false failures from one directory, in the single case the
rule was built from.

The reason is structural rather than a detail to patch around: **a plan's file tree describes what a
feature touches, not the contents of a directory.** For `frontend/src/mcp/`, which feature 008
created, the tree happens to be both. For `frontend/`, which it merely reached into, it is only the
first. Nothing distinguishes those from the tree alone, because the difference is intent, and intent
has to be written down.

**What this costs, stated plainly**: FR-002 does not apply retroactively. Features 001 through 008
carry no markers, so a file added to one of their directories today is invisible to this check. The
alternative was editing eight past specifications, which this feature may not do, or six false
failures, which would have the check switched off within a week.

**What it buys**: no false positives, and a claim that holds because someone asserted it rather than
because a heuristic guessed. The same reasoning as the inline exemption below -- the document says
what it means, at the place it means it.

### 2. Command

An instruction to run something this project defines.

```text
make mcp-up                → a target in Makefile
npm run test:mcp           → a script in some package.json
./gradlew :frontend:check  → a task registered in some build.gradle.kts
```

**Recognised** only for those three prefixes, and only in a fenced block or inline code.

**Not recognised**: anything else — `docker`, `curl`, `git`, `node`, `jq`. Those describe the
reader's machine, not a promise this project makes (research R-004). A Gradle task is matched against
task names read as *text* from the build files; a task registered dynamically will be missed, and
this sentence is the reason that is acceptable rather than a bug.

### 3. Requirement

A requirement or success-criterion identifier in a feature's `spec.md`.

```text
**FR-012**: …     → claims some task in tasks.md cites FR-012
**SC-003**: …     → likewise
```

**Recognised** only in `spec.md`, only in the bold-identifier form the template uses.

**Not recognised**: a mention of `FR-012` elsewhere. Other documents refer to requirements
constantly, and each reference is not a separate promise.

### 4. Reference

A markdown link to a file in this repository.

```text
[quickstart.md](./quickstart.md)          → claims that file exists
[findings.md](specs/008-mcp-console/findings.md)
```

**Recognised** when the target is relative, has no scheme, and the link is **outside a fenced
block**.

**Not recognised**: `http:` and `https:` links (FR-009 forbids a network), anchors within the same
file, `mailto:`, and anything inside a fence.

**Why the fence matters here but not for paths.** Rendered markdown turns `[a](b)` outside a fence
into something a reader can click — a promise. Inside a fence it is literal text, and this very file
contains two such lines as examples. A first draft of the link check flagged them, which is a small
demonstration of the failure this grammar exists to prevent: a checker that cannot tell an
illustration from an assertion produces noise, and noise gets switched off.

A file tree inside a fence *is* a claim, because it is read as a description of real files. The two
rules differ because the two forms differ in what a reader takes from them.

### 5. Status

The `**Status**:` line of a feature's `spec.md`, checked for internal consistency.

**Recognised claims**: that the declared status is one of the known set; that a feature declaring
itself implemented has a `tasks.md`; that a task count stated in the line matches the file; and that
a feature whose tasks are complete does not still declare itself a draft.

This is the one kind that checks a document against another document rather than against code. It
exists because the status field is what arms every other check (research R-001), and an arming
switch nobody watches is one nobody maintains.

## Exemption

An HTML comment on the claim's line:

```markdown
See `frontend/src/does-not-exist.ts` <!-- drift-ok: names a file deliberately, to show the failure -->
```

Covers that one claim. A reason is required; a marker without one is an error. A marker on a line
carrying no claim is also an error — it has outlived what it excused.

## What this grammar deliberately does not cover

Printed by every run, and repeated here so the boundary is in the contract and not only in the code:

- **Any claim made in prose.** "The server serves the contracts verbatim" is a sentence, not a form.
  Feature 008's finding F-001 is the standing example of such a sentence being false for months; no
  document checker would have found it, and a live test suite did.
- **Whether a document's description of something is *accurate*** — only whether the things it names
  exist. A plan may describe a module's purpose entirely wrongly and pass.
- **Anything outside this repository**, including every external link.
- **Code, tests, or behaviour.** The check reads documents and the filesystem. It runs nothing.
