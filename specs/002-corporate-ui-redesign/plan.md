# Implementation Plan: Corporate UI Redesign

**Branch**: `002-corporate-ui-redesign` | **Date**: 2026-09-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-corporate-ui-redesign/spec.md`

## Summary

Replace the hand-written stylesheet with a design system built on Ant Design, in a restrained
corporate register, with a light and a dark theme and a layout that works from a 320 pixel phone to
a wide desktop.

Ant Design is chosen because it is the enterprise design language the request asked for, it is MIT
licensed with no key and no fee, and its theme algorithms give both themes from one token seed
rather than from two hand-maintained palettes. It also brings no new build machinery: its styling is
self-contained, so no Sass, no PostCSS, and no emotion enter the project.

The backend is untouched. No endpoint, no payload, and no chain behaviour changes. The whole of this
feature lives under `frontend/src/`, and its hardest requirement is the one that forbids it from
changing anything: the four node boundaries, the byte-identical indicator values, the fictional-data
notice, and the failure messaging all have to survive the restyle intact.

## Technical Context

**Language/Version**: TypeScript 5.x on React 19, unchanged

**Component library**: Ant Design (`antd`) 6.6.3, MIT. Styling through its bundled
`@ant-design/cssinjs`; no additional styling toolchain. See R-001.

**Primary Dependencies**: `antd` and `@ant-design/icons`, added to the existing React 19, Vite 6,
TanStack Query 5 frontend. Nothing is removed except the hand-written `styles.css`.

**Build**: Vite 6, unchanged. No PostCSS, Sass, or CSS-in-JS runtime beyond what antd ships.

**Storage**: `localStorage`, for one key holding the learner's theme choice. No server involvement,
no account, no synchronisation.

**Testing**: Vitest with Testing Library and Mock Service Worker, unchanged. The 30 existing
frontend tests are the regression harness and must keep passing throughout.

**Target Platform**: Modern browsers, from a 320 pixel viewport to a wide desktop.

**Project Type**: Web application frontend only. The backend is out of scope for this feature.

**Performance Goals**: Theme switching is perceptible as instant, with no page reload and no refetch.
The added bundle weight must stay proportionate; the budget is recorded in R-008.

**Constraints**: Contrast floors of 4.5 to 1 for body text and 3 to 1 for large text and interface
components, in both themes. No horizontal page scrolling at any width from 320 pixels up. Indicator
values rendered exactly as supplied, with no formatting applied by the interface.

**Scale/Scope**: Seven existing components, three pages, one shell. One new theme control, one theme
context, one token definition. No new page and no new data.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Checked against constitution v2.2.0.

| Principle | Gate | Status |
|-----------|------|--------|
| I. Learning-First Transparency | The agent loop stays visible | PASS. This feature touches no backend file. `ChainRunner`, the four nodes, and the single model call site are untouched. |
| I. Learning-First Transparency | No dependency added merely to save lines | PASS with justification. The mechanism this project teaches is the chain, which lives entirely in Java. A library that styles the presentation layer obscures none of it. Writing a corporate design system, two themes, and an accessible component set by hand would be more code, less accessible, and would teach nothing about agents. See R-001. |
| I. Learning-First Transparency | Lowest useful abstraction | PASS. antd components are used directly. No bespoke wrapper layer is introduced around them, because a wrapper would hide the library a reader needs to look up. The one exception is the theme provider, which exists to satisfy FR-010 through FR-013 and is roughly thirty readable lines. |
| I. Learning-First Transparency | The node trace stays readable | PASS, and strengthened. R-005 makes the payload and model-exchange regions easier to scan, which is the opposite of hiding them. FR-021 forbids collapsing any of the four boundaries. |
| II. Provider-Agnostic Inference | Credential never shown on screen | PASS. FR-026 restates it, and the existing backend test that asserts no response field carries the credential is unaffected. The interface renders what the API returns and adds nothing. |
| II. Provider-Agnostic Inference | Provider mode recorded and visible | PASS. The run header continues to show provider mode and model identifier; R-005 keeps it in the redesigned header. |
| III. Protocol Contracts Before Implementation | Contracts declared before handlers | PASS. `contracts/design-tokens.md` and `contracts/ui-regions.md` are written before any component is edited. The second is the checkable statement of what each region must still show. |
| III. Protocol Contracts Before Implementation | Contracts shared from a single source | PASS, unchanged. The API shapes still come from the generated `schema.d.ts`. This feature adds no hand-written request or response type. |
| III. Protocol Contracts Before Implementation | MCP and A2A schemas | NOT APPLICABLE. No tool and no agent message. |
| IV. Test-First | Failing tests before implementation | PASS. Task ordering puts the theme, responsive, and contrast tests ahead of the components they cover. |
| IV. Test-First | Frontend logic tested with Vitest | PASS. The existing 30 tests stay and are extended; R-006 covers the `matchMedia` and storage mocking that jsdom requires. |
| IV. Test-First | Tests run with no credential and no network | PASS. Mock Service Worker already intercepts every request; this feature adds no new one. |
| V. Observable Agent Runs | Trace retrievable in the user interface | PASS, and this is the gate most at risk. A redesign is the likeliest way to quietly drop part of the trace to save vertical space. FR-021 and `contracts/ui-regions.md` make each element of the trace an explicit obligation, and R-007 keeps them as regression tests. |
| Stack | Locked entries respected | PASS. React on Vite, TanStack Query, Vitest. antd is a new dependency alongside them, not a replacement of a locked entry, so no amendment is required. |
| Stack | Server state not duplicated into another store | PASS. TanStack Query remains the only holder of run and catalog state. The theme preference is client state that no endpoint returns, so holding it in a context duplicates nothing. |
| Stack | Dependency additions justified against Principle I | PASS. Recorded in R-001 and in the Principle I rows above, ready to be quoted in the pull request as the constitution requires. |
| Workflow | Spec and plan exist before implementation | PASS. |

**Result**: all applicable gates pass. No entry in Complexity Tracking.

### Post-Design Re-Check

Re-evaluated after Phase 1, against the artifacts now on disk.

- **Principle I**: the design added one abstraction, the theme provider, and R-002 records why a
  three-state preference cannot be avoided if FR-010 and FR-012 are both to hold. Everything else is
  antd used directly.
- **Principle III**: both contracts were written before any component was edited.
  `contracts/ui-regions.md` turned out to be the more valuable of the two, because it converts nine
  preservation requirements into a per-region checklist a reviewer can walk.
- **Principle IV**: R-006 resolved the one real testing obstacle, which is that jsdom implements
  neither `matchMedia` nor a storage failure mode, and both are load-bearing for FR-010 and the
  blocked-storage edge case.
- **Principle V**: `contracts/ui-regions.md` lists every element of the trace that must survive, so
  the gate is now checkable rather than a matter of reviewer memory.

No gate changed status. Complexity Tracking stays empty.

## Project Structure

### Documentation (this feature)

```text
specs/002-corporate-ui-redesign/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── design-tokens.md
│   └── ui-regions.md
├── checklists/
│   └── requirements.md
└── tasks.md             # Created by /speckit-tasks, not by this command
```

### Source Code

Only `frontend/` changes. The backend tree is untouched.

```text
frontend/
├── package.json                      # + antd, @ant-design/icons
└── src/
    ├── main.tsx                      # QueryClientProvider wraps ThemeProvider wraps ConfigProvider
    ├── App.tsx                       # Layout shell: header, nav, content container
    ├── theme/
    │   ├── ThemeProvider.tsx         # resolves system/light/dark, persists the choice
    │   ├── useTheme.ts               # the hook components read
    │   ├── tokens.ts                 # the seed and component tokens, one place (FR-004)
    │   └── ThemeToggle.tsx           # the control required by FR-011
    ├── components/                   # the existing seven, restyled, same names and props
    │   ├── RunLauncher.tsx           # Form, Select, Button
    │   ├── RunProgress.tsx           # Steps
    │   ├── IndicatorTable.tsx        # Table, with a card layout below the narrow breakpoint
    │   ├── SummaryPanel.tsx          # Card + Typography + Alert for the standing notice
    │   ├── NodeTimeline.tsx          # Steps or Segmented, plus NodeDetail
    │   ├── NodeDetail.tsx            # Descriptions + bounded scroll regions
    │   └── RunHistory.tsx            # List or Table
    ├── pages/                        # the existing three, layout only
    ├── hooks/                        # unchanged
    ├── api/                          # unchanged, still generated
    └── test/
        ├── setup.ts                  # + matchMedia and storage mocks (R-006)
        └── render.tsx                # + theme wrapper, so every test renders in a theme
```

**Structure Decision**: A new `theme/` directory holds the four files that are genuinely new. Every
existing component keeps its file name, its props, and its test file, because the 30 existing tests
are the regression harness for FR-019 and rewriting them would forfeit exactly the protection they
provide. `styles.css` is deleted once the last component stops referencing it.

## Complexity Tracking

No constitutional violations. This section is intentionally empty.
