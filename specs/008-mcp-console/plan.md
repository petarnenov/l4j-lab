# Implementation Plan: MCP console

**Branch**: `008-mcp-console` | **Date**: 2026-09-14 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/008-mcp-console/spec.md`

## Summary

A development-only page added to the existing React application that drives feature 007's MCP
billing server by hand and shows the protocol while it does it: the five tools with their declared
annotations, argument fields derived from each tool's `inputSchema`, the exact JSON-RPC that
travelled, the confirmation round trip, the handle-and-poll task, the page cursor, and a fixed set
of three deliberately malformed requests.

The technical approach has one load-bearing decision, and the rest follow from it: **the browser
never talks to the MCP stack directly — the Vite dev server forwards to it.** That keeps the request
same-origin (no CORS, so no change to feature 007), keeps the two Compose networks apart (feature
004's promise), and makes the console structurally absent from the packaged build, because the
forwarder only exists while `vite` is running. Everything else — the principal picker, the replica
selector, the exchange log — is ordinary React over TanStack Query with no new runtime dependency.

## Technical Context

**Language/Version**: TypeScript 5.7 on Node 24 (`frontend/.nvmrc`); no Java in this feature

**Primary Dependencies**: React 19, antd 6.6.3, TanStack Query 5, Vite 6 — all already pinned in
`frontend/package.json`. **No new runtime dependency is added** (see R-003 for why a JSON-Schema form
library was rejected).

**Storage**: None. All console state is in-memory React state for the life of a tab. Nothing is
persisted, which is also what settles the "two people at once" edge case (R-010).

**Testing**: Vitest, in two separately selectable suites mirroring the split feature 007 already uses
(`test` vs `topologyTest`):
- deterministic — `npm test`, jsdom + MSW, no network and no credential (Principle IV)
- live — `npm run test:mcp` / `./gradlew :frontend:mcpConsoleTest` / `make test-console`, against a
  stack started by `make mcp-up`, failing with that instruction when it is absent

**Target Platform**: A development browser against `vite dev` on port 5173. Not the packaged build.

**Project Type**: Web frontend — an addition to the existing `frontend/` module. No backend module is
created or changed.

**Performance Goals**: SC-001 — a first read-only tool call within two minutes of opening the page,
with no documentation. This is an information-architecture target, not a latency one; the server
already guarantees the only latency budget that exists (a handle in under a second, 007 SC-003).
Task polling uses the server's own `pollIntervalMs` rather than a number chosen here.

**Constraints**:
- Absent from `vite build` output, verified by a committed check, not asserted (FR-001a)
- No change to any file under `mcp-server/`, `legacy-billing-api/`, `token-issuer/`,
  `deploy/mcp/`, or `compose.mcp*.yaml` (spec Out of Scope)
- The two Compose networks stay unjoined
- Existing pages and their tests unchanged (SC-007)
- A bearer token is never rendered in full (Principle II; see R-009 for how this coexists with FR-010)

**Scale/Scope**: One page. 5 tools, 6 fixture principals, 4 call targets (proxy + three replicas),
3 malformed requests, 4 MCP methods driven (`server/discover`, `tools/list`, `tools/call`,
`tasks/get`, `tasks/cancel`).

## Constitution Check

*GATE: evaluated before Phase 0, re-evaluated after Phase 1. Both passes recorded.*

| Principle | Applies? | Verdict | How |
|---|---|---|---|
| **I. Declarative-First, Library-First** | Partly | **PASS** | The feature touches **no LangChain4j module** — it adds TypeScript to `frontend/` and one Makefile target group. The capability inventory the amendment requires is recorded as such in [research.md R-013](./research.md), with the evidence and the condition that would re-open the gate, rather than silently skipped. The library-first habit is honoured on the frontend too: no new dependency, and the one place tempted toward a library (JSON-Schema form rendering) is justified in R-003. |
| **II. Provider-Agnostic Inference** | Partly | **PASS** | No inference. The clauses that do apply are the configuration and credential ones: every port the forwarder targets is an environment variable with the same name and default Compose already uses (R-001), and no credential is committed, logged, persisted, or shown on screen (R-009). |
| **III. Protocol Contracts Before Implementation** | Yes | **PASS, one narrow exception** | The console declares no new tool or message; it consumes 007's. The single-source rule is honoured where it can be: argument fields are derived from the served `inputSchema` (FR-008), and the deterministic suite serves `tools/list` from the **committed** `specs/007-mcp-billing-server/contracts/tools/*.json` rather than a hand-copied fixture (R-004). The exception is the TypeScript envelope types — recorded in Complexity Tracking with the check that binds them. |
| **IV. Test-First at Deterministic Boundaries** | Yes | **PASS** | Every behaviour here is deterministic — no model is involved anywhere in this feature. Tests first, red, then green. Two suites, split by task and not by a flag, the deterministic one with no network and no credential, the live one selectable alone and failing with an instruction (R-005). |
| **V. Observable Agent Runs** | No | **N/A** | No agent runs. The console's exchange log is a sibling idea, not the trace this principle governs; it persists nothing and is out of scope for run history by the spec's own exclusion. |
| **Stack constraints** | Yes | **PASS** | React + Vite, TanStack Query for server state with no second store, Vitest. The live suite is a distinct Gradle task, never a flag (`:frontend:mcpConsoleTest`), which is the form the constitution requires for separately selectable suites. |
| **Workflow gates** | Yes | **PASS** | Spec and plan exist before implementation; the LangChain4j inventory question is answered explicitly in R-013; no new deterministic behaviour will merge without a test. |

**Post-Phase-1 re-evaluation**: unchanged. Phase 1 introduced four contract files and one data model,
added no dependency, added no backend code, and the exceptions are recorded below.

**Post-implementation**: still unchanged, and the finding count is not. Phase 1 predicted two
observations about feature 007 (R-007, R-011). Implementation produced **six**, and the three the
live suite added are heavier than the two predicted: a `tools/list` that drops eighteen schema
keywords it claims to serve verbatim (F-001), a confirmation retry whose documented shape the server
reads as a refusal (F-005), and an entitlement path answering HTTP 500 with an internal message
(F-006). All six are filed rather than acted on, which is what the spec's Out of Scope section
requires, and each is pinned by a test so a change in either direction is noticed.

**Two more came out of CI**, and they are a different kind: F-007, the packaged backend image having
been unbuildable since feature 007 included three projects the Docker context lacks, and F-008, a
development database password on `main` since before that. Both went unnoticed for a whole feature
because the checks that catch them run on pull requests and this repository had none until this one.
F-007 is the single finding this feature **fixed** rather than recorded — it is build plumbing rather
than 007's server, and it stood between the branch and `main`. See [findings.md](./findings.md).

## Project Structure

### Documentation (this feature)

```text
specs/008-mcp-console/
├── plan.md                        # This file
├── spec.md
├── research.md                    # Phase 0 — R-001..R-014
├── data-model.md                  # Phase 1 — Principal, Target, Tool, Exchange, Handle, Page
├── quickstart.md                  # Phase 1 — run and verify
├── checklists/
│   └── requirements.md
└── contracts/
    ├── README.md                  # index, and the rule that 007's contracts win
    ├── dev-proxy.md               # the forwarder: paths, targets, rewrites, failure shapes
    ├── mcp-client.md              # what the console puts on the wire for each method
    ├── console-surface.md         # the UI contract: regions, controls, what each must show
    └── malformed-requests.md      # the fixed catalogue of three, and each expected refusal
```

### Source Code (repository root)

```text
frontend/
├── vite.config.ts                 # + the dev-only forwarder (R-001); the /api proxy is untouched
├── vitest.mcp.config.ts           # the live suite's project (R-005)
├── tsconfig.build.json            # what the production build type-checks: no test files (R-015)
├── package.json                   # + "test:mcp", "check:dev-only" scripts; no new dependency
├── build.gradle.kts               # + :frontend:mcpConsoleTest (not in check), + checkDevOnly and typecheck (in check)
├── scripts/
│   └── check-dev-only.mjs         # builds and asserts the console is absent from dist/ (R-002)
└── src/
    ├── App.tsx                    # + one dev-only nav item, guarded by import.meta.env.DEV
    └── mcp/
        ├── McpConsolePage.tsx     # the page: composes everything below
        ├── targets.ts             # proxy + three replicas; base URLs; reachability probing
        ├── principals.ts          # the six fixture names only — the rest comes from the token
        ├── transport.ts           # envelope + headers; splits protocol failure from tool failure
        ├── wire.ts                # the TypeScript envelope types (the Principle III exception)
        ├── devOnlyMarker.ts        # the one string check-dev-only.mjs greps the build for
        ├── liveFixture.ts          # the live suite's targets, minting and stack precheck (R-005)
        ├── malformed.ts           # the fixed catalogue of three (FR-011a)
        ├── schemaForm.tsx         # argument fields derived from inputSchema (FR-008)
        ├── hooks/
        │   ├── usePrincipalToken.ts
        │   ├── useReachableTargets.ts
        │   ├── useDiscover.ts
        │   ├── useTools.ts
        │   ├── useToolCall.ts
        │   └── useTask.ts
        ├── components/
        │   ├── StackOffline.tsx       # FR-003: says so, and names `make mcp-up`
        │   ├── PrincipalPicker.tsx    # FR-004, FR-006
        │   ├── TargetPicker.tsx       # FR-016, FR-017
        │   ├── ToolList.tsx           # FR-007
        │   ├── ToolCallPanel.tsx      # FR-008, FR-009
        │   ├── ExchangeView.tsx       # FR-010, FR-011
        │   ├── ExchangeLog.tsx        # every call this session made, newest first
        │   ├── MalformedPanel.tsx     # FR-011a
        │   ├── ElicitationPanel.tsx   # FR-012, FR-012a, FR-012b
        │   ├── TaskWatcher.tsx        # FR-013, FR-014
        │   └── PageCursor.tsx         # FR-015
        ├── test/
        │   └── mcpHandlers.ts         # MSW handlers serving 007's committed tool contracts (R-004)
        ├── **/*.test.ts(x)            # deterministic suite, co-located per repo convention
        └── **/*.live.test.ts          # live suite, excluded from the default vitest config; every
                                       # request in it is built and sent by transport.ts (R-005)

Makefile                           # + mcp-up-topology, mcp-reset, test-console (R-006, R-014)
backend/Dockerfile                 # + the three project build files settings.gradle.kts includes (F-007)
.gitguardian.yaml                  # the two compose files' development passwords, and why (F-008)
```

**Structure Decision**: an addition inside the existing `frontend/` module, confined to a new
`src/mcp/` directory plus a small set of touched files: `App.tsx` (one guarded nav item),
`vite.config.ts` (the forwarder and the alias), `build.gradle.kts` (three verification tasks),
`tsconfig.json` (the alias' path mapping and `vite/client` types), `src/test/handlers.ts` (registers
the MCP handlers with the existing MSW server, changing no existing handler), and `src/App.test.tsx`
(assertions appended, none modified). Implementation added two outside the module — `backend/Dockerfile`
and `.gitguardian.yaml` — both recorded as findings F-007 and F-008.
No new Gradle module and no backend code, because the console needs neither: its only server-side
requirement is forwarding, and the Vite dev server already does that for `/api`. Confining it to one
directory is what makes SC-007 checkable by inspection — nothing under `src/components/`,
`src/pages/`, `src/hooks/`, `src/api/`, or `src/theme/` changes.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| Hand-written TypeScript types for the MCP wire envelope (`src/mcp/wire.ts`), against Principle III's "the same shape MUST NOT be declared twice by hand" | The console must read `resultType`, `inputRequests`, `requestState`, `taskId`, `status`, and the JSON-RPC error shape to behave correctly at all. Feature 007 publishes no machine-readable description of its HTTP surface — deliberately, as the spec's Assumptions record: its contract is the tool declarations it serves, and those cover tool *arguments*, not the envelope around them. | Generating from an OpenAPI description was rejected because there is none to generate from, and adding one means changing feature 007's server, which this feature puts out of scope. Deriving the envelope at runtime was rejected because the console must know the field names before it can branch on them. **The exception is bounded by a check, not by a promise**: `wire.ts` describes only the envelope, never a tool's arguments or results (those stay derived), and the live suite asserts each declared field against what the running server actually sends — so a moved field fails a test rather than rendering blank. Recorded also as a finding for a future feature 007 change. |
| A second copy of the issuer's principal claims in `src/mcp/test/mcpHandlers.ts` (`FIXTURE_CLAIMS`), against the same Principle III clause | The deterministic suite must mint a token-shaped credential for each of the six seeded principals, and `007/contracts/token-issuer.md` publishes that table only as markdown prose. There is nothing machine-readable to read it from. | Reading the markdown table at test time was rejected as parsing prose for a fixture. Minting through the real issuer was rejected outright: the deterministic suite must run with no network and no credential (Principle IV). **Bounded**: this copy lives only in test infrastructure — `principals.ts`, the module the console actually ships, holds the six *names* and nothing else, and every claim the console displays is decoded from the token the server minted. If the two ever disagree, the live suite is what notices, since it uses the real issuer. |
