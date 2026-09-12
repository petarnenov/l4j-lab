---

description: "Task list for Corporate UI Redesign"
---

# Tasks: Corporate UI Redesign

**Input**: Design documents from `/specs/002-corporate-ui-redesign/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/design-tokens.md, contracts/ui-regions.md

**Tests**: Test tasks ARE included. Constitution Principle IV is NON-NEGOTIABLE for frontend logic too:
test, red, implement, green, in Vitest. The plan's Constitution Check commits to putting the theme,
responsive, and contrast tests ahead of the components they cover.

**Organization**: Tasks are grouped by user story so each story can be implemented, tested, and demoed
independently.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Exact file paths are included in every task

## Read Before Starting

**Test-first is enforced here because it was not in feature 001.** That implementation wrote code before
tests, and two real defects reached a running application that the full suite had passed: a bean that
disabled itself in production, and indicator values that lost their scale in storage. Neither would
have survived a test written first against the observable behaviour. Every "Tests" block below is to be
written, run, and seen to fail before its implementation block begins.

**The 30 existing frontend tests are the regression harness, not obstacles.** They already assert that
indicator values render verbatim, the fictional notice is present, the model exchange appears only on
the fourth node, and a failing node is named. Three of them depend on native markup that Ant Design
replaces, and those must be **rewritten to assert the same user-visible fact**, never weakened to pass:

| Test file | Query that breaks | Why | Rewrite to |
|-----------|-------------------|-----|------------|
| `RunLauncher.test.tsx` | `getByRole('option', …)` | antd `Select` renders no native `<option>`; its list exists only while open | open the combobox, then query the rendered options |
| `NodeTimeline.test.tsx` | `getAllByRole('button')` expecting 4 | antd `Steps` does not render one `<button>` per step | query each step by its node name |
| `RunHistory.test.tsx` | `getAllByRole('listitem')` and `getAllByRole('button')[0]` | antd `List` and `Table` markup differs | query each run by company and period text |

A test deleted or loosened to go green is a regression of FR-019 by another route.

**jsdom lacks two browser APIs antd depends on.** `matchMedia`, used by `Grid.useBreakpoint`, and
`ResizeObserver`, used by `Table` and `Segmented`. Without both mocks, antd components fail to render at
all under test, and the failure looks like a component bug. T005 installs them first.

## Path Conventions

Frontend only. The backend is not touched by any task in this list.

- Source: `frontend/src/`
- New theme code: `frontend/src/theme/`
- Tests: beside the file they cover, as `*.test.tsx` or `*.test.ts`

---

## Phase 1: Setup (Shared Infrastructure)

<!-- T001 baseline, 2026-09-12: 30 frontend tests passing; production JS 277.62 kB, 86.16 kB gzipped. -->

**Purpose**: Add the library and record the baseline every later task is measured against

- [X] T001 Record the baseline before any change: run `npm test` in `frontend/` and confirm all 30 tests pass, and run `npm run build` and note the gzipped JavaScript size (currently about 86 kilobytes), recording both in a comment at the top of `specs/002-corporate-ui-redesign/tasks.md` under this task
- [X] T002 Add `antd` pinned at `6.6.3` and `@ant-design/icons` to `frontend/package.json` and install, confirming the install reports no unmet React 19 peer dependency and that `frontend/node_modules/antd/package.json` declares the MIT licence (FR-002)
- [X] T003 Confirm the 30 baseline tests still pass with antd installed but unused, in `frontend/`, so any later failure is attributable to a change rather than to the dependency itself

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Test environment, the token contract, and the provider every story renders inside

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Test environment

- [X] T004 Extend `frontend/src/test/setup.ts` with a controllable `window.matchMedia` mock exposing helpers to set the reported colour scheme and viewport width and to fire change events, a `ResizeObserver` stub, and a helper that makes `localStorage` throw on read and write, so the blocked-storage edge case can be tested
- [X] T005 [P] Extend `frontend/src/test/render.tsx` so `renderWithQuery` wraps its tree in antd `ConfigProvider` with no theme configured yet, accepting an optional theme argument that later stories use to render in dark. It must not import `frontend/src/theme/tokens.ts`, which does not exist until T007

### Token contract, test first

- [X] T006 Write the failing contrast gate `frontend/src/theme/tokens.contrast.test.ts`: for each theme, resolve the tokens through antd `theme.getDesignToken` with that theme's algorithm and seed, compute WCAG relative-luminance contrast, and assert every pair in `specs/002-corporate-ui-redesign/contracts/design-tokens.md` meets its floor, including the primary button label polarity flip and the status text tokens. This replaces the `scripts/measure-contrast.mjs` the quickstart names, as an enforced test rather than a manual step
- [X] T007 Implement `frontend/src/theme/tokens.ts` exporting the shared tokens, a light seed, and a dark seed with exactly the values in `contracts/design-tokens.md`, plus a `primaryButtonLabel` per theme, until T006 passes. Add a comment above the dark seed recording why it is not the light seed darkened: the naive navy measured 1.47 to 1 for links

### Provider wiring

- [X] T008 Wrap the application in antd `ConfigProvider` and antd `App` in `frontend/src/main.tsx`, inside the existing `QueryClientProvider`, using the light tokens from `frontend/src/theme/tokens.ts` statically for now, and pass the same light tokens to the `ConfigProvider` in `frontend/src/test/render.tsx` from T005, so tests render in the real palette
- [X] T009 Run `npm test` in `frontend/` and confirm the 30 baseline tests plus T006 pass with the provider in place

**Checkpoint**: The token contract is proven by a test, jsdom can render antd, and every existing test is green. Story work can begin.

---

## Phase 3: User Story 1 - Read the application without visual friction (Priority: P1) 🎯 MVP

**Goal**: Every screen uses one corporate visual system on a desktop in the light theme: a constrained
container, labelled regions, antd controls throughout, and no browser defaults.

**Independent Test**: Open every screen at a desktop width and walk `contracts/ui-regions.md`, confirming
each region is defined and labelled, content sits in a centred container, and no control is a browser
default. Delivers the whole visual improvement without theming or small screens.

### Tests for User Story 1 ⚠️ Write first, confirm they fail

- [X] T010 [P] [US1] Write `frontend/src/App.test.tsx` asserting the shell renders a navigation with New run and Previous runs, the main content inside a landmark region, and each page's regions exposed with accessible names
- [X] T011 [P] [US1] Rewrite the three native-option assertions in `frontend/src/components/RunLauncher.test.tsx` to open the company and period comboboxes and query the rendered options, keeping every original fact asserted: both companies listed, periods narrowed to the selected company, and starting a run handing back the identifier
- [X] T012 [P] [US1] Extend `frontend/src/components/IndicatorTable.test.tsx` to assert `-0.0500`, `0.3400`, and `2.0000` render with trailing zeros intact, and that no element's full text is exactly `0.34` or `34%` and no value is locale-grouped (FR-022). Use exact full-text string queries, which Testing Library applies by default, never a regex: `0.34` is a substring of the correct `0.3400`, so a regex would reject the right value
- [X] T013 [P] [US1] Write `frontend/src/components/RunProgress.test.tsx` asserting all four steps render, the currently executing node is named, the summarizing step says it is the slow one, completed and pending steps are distinguishable by text or icon and not colour alone, and nothing renders once the run is terminal
- [X] T014 [P] [US1] Extend `frontend/src/components/SummaryPanel.test.tsx` to assert the fictional-data and not-investment-advice notice is exposed with the `alert` role, not as incidental text (FR-023), and that the summary text sits inside a region carrying a `24rem` maximum height with vertical overflow scrolling (FR-017)
- [X] T015 [P] [US1] Extend `frontend/src/pages/RunDetailPage.test.tsx` to assert a failed run's failure is exposed with the `alert` role and names the failing node, and that provider mode and model identifier remain visible in the run header
- [X] T016 [P] [US1] Rewrite `frontend/src/components/NodeTimeline.test.tsx` to find each of the four steps by node name rather than by counting buttons, keeping every original fact asserted: four entries in execution order, durations shown, the failing node marked, selection switching the detail; and add that the received payload, produced payload, and model exchange each sit in a region carrying a `24rem` maximum height with overflow scrolling (FR-017)
- [X] T017 [P] [US1] Rewrite `frontend/src/components/RunHistory.test.tsx` to find each run by its company and period text rather than by `listitem` and `button` counts, keeping every original fact asserted: newest first, status shown, clicking opens the run, preview shown only where present

### Implementation for User Story 1

- [X] T018 [US1] Rebuild the shell in `frontend/src/App.tsx` with antd `Layout`, a `Layout.Header` holding the navigation, and a `Layout.Content` whose inner container is centred with a capped maximum width (FR-005)
- [X] T019 [P] [US1] Rebuild `frontend/src/components/RunLauncher.tsx` with antd `Form` in vertical label layout, two `Select` controls with associated labels, and a primary `Button`, preserving the existing props and the period narrowing (FR-007)
- [X] T020 [P] [US1] Rebuild `frontend/src/components/RunProgress.tsx` with antd `Steps` over the four nodes, marking the current step, pairing each state with an icon, and keeping the slow-step message for Summarize (FR-008, FR-025)
- [X] T021 [P] [US1] Rebuild `frontend/src/components/IndicatorTable.tsx` with antd `Table`, rendering each value as the supplied string inside `Typography.Text` with a monospace style and passing it through no formatter, number column type, or `render` transform that alters it; show not-applicable with its reason (FR-022)
- [X] T022 [P] [US1] Rebuild `frontend/src/components/SummaryPanel.tsx` as an antd `Card` titled Summary, with the summary text in a region capped at `24rem` that scrolls, and the standing notice as an antd `Alert` of type `info` with an icon (FR-017, FR-020, FR-023)
- [X] T023 [P] [US1] Rebuild `frontend/src/components/NodeDetail.tsx` with antd `Descriptions` for position, name, duration, and outcome, and the received payload, produced payload, and model exchange each in a `pre` region capped at `24rem` that scrolls; show the failure reason as an `Alert` of type `error` (FR-017, FR-021, FR-024)
- [X] T024 [P] [US1] Rebuild `frontend/src/components/NodeTimeline.tsx` with antd `Steps` over exactly the four node records, clickable to select, marking a failed step with the error status and an icon, and rendering `NodeDetail` for the selection (FR-021)
- [X] T025 [P] [US1] Rebuild `frontend/src/components/RunHistory.tsx` with antd `List`, each run showing company, period, start time, and outcome as an antd `Tag` with an icon, plus the summary preview where present and a Load more button (FR-008)
- [X] T026 [US1] Rebuild `frontend/src/pages/RunDetailPage.tsx` with an antd `Typography.Title` header showing company and period, provider mode and model as `Tag`s, the failure as an antd `Alert` of type `error` naming the node with the earlier-work reassurance, and the summary, indicators, and timeline as titled `Card` regions (FR-006, FR-024)
- [X] T027 [P] [US1] Rebuild `frontend/src/pages/NewRunPage.tsx` and `frontend/src/pages/HistoryPage.tsx` with antd `Typography` and `Card` for their headings and regions
- [X] T028 [US1] Delete `frontend/src/styles.css` and remove its import from `frontend/src/main.tsx`, confirming no component still references a class it defined
- [X] T029 [US1] Run `npm test` in `frontend/` and confirm every test passes, then walk every row of `specs/002-corporate-ui-redesign/contracts/ui-regions.md` against a real run in the browser at a desktop width

**Checkpoint**: The whole interface uses the corporate design system in the light theme on desktop. This is the MVP.

---

## Phase 4: User Story 2 - Work in the theme the learner prefers (Priority: P2)

**Goal**: Light and dark themes that follow the operating system until the learner chooses, with the
choice persisted and never overwritten by a later system change.

**Independent Test**: Walk quickstart Scenario 3 in the browser: follow the system, override, reload,
change the system again, switch mid-run, and try an invalid stored value and blocked storage.

### Tests for User Story 2 ⚠️ Write first, confirm they fail

- [X] T030 [P] [US2] Write `frontend/src/theme/ThemeProvider.test.tsx` covering every row of the effective-theme table in `specs/002-corporate-ui-redesign/data-model.md`: no stored value and a dark system resolves dark; no system preference resolves light; a stored `light` overrides a dark system; a system change while `system` updates live; a system change while `dark` is ignored; a stored `purple` resolves as `system`; and a throwing `localStorage` resolves as `system` and still renders
- [X] T031 [US2] After T030, add cases to the same `frontend/src/theme/ThemeProvider.test.tsx` asserting the effective theme is correct on the first render with no post-mount correction, so there is no flash of the wrong theme (research R-002)
- [X] T032 [P] [US2] Write `frontend/src/theme/ThemeToggle.test.tsx` asserting three options labelled System, Light, and Dark, each with an icon, operable by keyboard, that choosing Light or Dark writes that value to the `l4j.theme` key, and that choosing System removes the key rather than writing `system` (FR-012)
- [X] T033 [P] [US2] Extend `frontend/src/pages/RunDetailPage.test.tsx` to render a running run, switch theme, and assert the current node is still named, the displayed state is unchanged, and no additional request was made (FR-013)
- [X] T034 [P] [US2] Extend `frontend/src/components/IndicatorTable.test.tsx` and `frontend/src/components/SummaryPanel.test.tsx` to render each in the dark theme and assert values stay verbatim and the notice is still present

### Implementation for User Story 2

- [X] T035 [US2] Implement `frontend/src/theme/ThemeProvider.tsx`: a context holding the three-state preference, resolving the effective theme synchronously on first render from storage and `prefers-color-scheme`, wrapping every storage read, write, and removal in try and catch, validating the stored value, removing the key when the learner chooses System, subscribing to the media query only while the preference is `system`, and rendering antd `ConfigProvider` with the matching algorithm and seed from `frontend/src/theme/tokens.ts`
- [X] T036 [P] [US2] Implement `frontend/src/theme/useTheme.ts` exposing the preference, the effective theme, and a setter, so no component reads storage or the media query directly
- [X] T037 [US2] Implement `frontend/src/theme/ThemeToggle.tsx` as an antd `Segmented` with System, Light, and Dark options, each with an icon imported individually from `@ant-design/icons`
- [X] T038 [US2] Replace the static `ConfigProvider` in `frontend/src/main.tsx` with `ThemeProvider`, keeping `QueryClientProvider` outermost so theme changes never remount query state
- [X] T039 [US2] Place `ThemeToggle` in the header of `frontend/src/App.tsx`, reachable from every screen (FR-011)
- [X] T040 [US2] Update `frontend/src/test/render.tsx` so the optional theme argument renders through `ThemeProvider` with a forced preference, replacing the static provider from T005

**Checkpoint**: Both themes work, the preference rules hold, and User Story 1 is unaffected.

---

## Phase 5: User Story 3 - Use the application on a small screen (Priority: P3)

**Goal**: Every screen works from 320 pixels up with no horizontal page scroll, adapting layout at antd's
768 pixel breakpoint.

**Independent Test**: Walk quickstart Scenario 5 at 320, 375, 768, 1024, 1440, and 2560 pixels, completing
a run and opening the node detail at each width.

### Tests for User Story 3 ⚠️ Write first, confirm they fail

- [X] T041 [P] [US3] Extend `frontend/src/components/IndicatorTable.test.tsx` to set the mocked viewport below 768 pixels and assert one card per indicator with no table, and at 768 and above assert the table, with values verbatim in both layouts (research R-004, FR-022)
- [X] T042 [P] [US3] Extend `frontend/src/components/RunLauncher.test.tsx` to assert the form uses a stacked, full-width layout below 768 pixels and remains operable
- [X] T043 [P] [US3] Extend `frontend/src/components/NodeTimeline.test.tsx` to assert a vertical step direction below 768 pixels and horizontal above, and that the selected node survives a breakpoint change fired through the mock (US3 scenario 5)
- [X] T044 [P] [US3] Extend `frontend/src/pages/RunDetailPage.test.tsx` to assert summary and indicators stack below 768 pixels and sit side by side at and above it, both still present (FR-020)

### Implementation for User Story 3

- [X] T045 [P] [US3] Add the below-breakpoint card layout to `frontend/src/components/IndicatorTable.tsx` using antd `Grid.useBreakpoint`, rendering each indicator as a `Card` with its name, verbatim value or not-applicable reason, and derived-from fields
- [X] T046 [P] [US3] Make `frontend/src/components/RunLauncher.tsx` stack its controls at full width below the `md` breakpoint, and let long company names wrap in its `Select` options rather than overflow
- [X] T047 [P] [US3] Set the `Steps` direction in `frontend/src/components/NodeTimeline.tsx` from `Grid.useBreakpoint`, keeping the selected index in state that does not reset on a layout change
- [X] T048 [US3] Lay out the summary and indicators in `frontend/src/pages/RunDetailPage.tsx` with antd `Row` and `Col` spanning 24 below `md` and 12 from `md`
- [X] T049 [P] [US3] Reduce the container side padding and let the header navigation and theme control wrap below the `md` breakpoint in `frontend/src/App.tsx`
- [X] T050 [P] [US3] Make company names wrap rather than overflow in `frontend/src/components/RunHistory.tsx`. Wrapping cannot be observed in jsdom, so it is verified at 320 pixels in T057

**Checkpoint**: All three user stories are independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T051 Write a failing test in `frontend/src/theme/ThemeProvider.test.tsx` that with `prefers-reduced-motion: reduce` matching, antd motion is disabled and the progress indicator does not animate, then implement it in `frontend/src/theme/ThemeProvider.tsx` and `frontend/src/components/RunProgress.tsx` (FR-018, research R-009)
- [X] T052 [P] Assert in `frontend/src/App.test.tsx` that the navigation, launcher, timeline steps, history rows, and theme control are all reachable by tab order and operable by keyboard in both themes (FR-015, SC-006)
- [X] T053 [P] Search `frontend/src/` for any import from the `@ant-design/icons` package root and replace it with an individual icon import (research R-008)
- [X] T054 [P] Run `npm run build` in `frontend/` and confirm the gzipped JavaScript bundle is under 600 kilobytes, recording the figure against the T001 baseline (research R-008)
- [X] T055 [P] Update Scenario 4 in `specs/002-corporate-ui-redesign/quickstart.md` to point at the `frontend/src/theme/tokens.contrast.test.ts` gate from T006 instead of the script it named, since contrast is now enforced by `npm test`
- [X] T056 [P] Add the design system, the two themes, and the contrast gate to `README.md` at the repository root
- [X] T057 Walk quickstart Scenarios 1 through 8 in `specs/002-corporate-ui-redesign/quickstart.md` in a real browser, in both themes, at 320, 375, 768, 1024, 1440, and 2560 pixels (SC-001), including the byte-identical indicator check against `stonebridge-paper` for `2025-Q4`, and record the results. This task is mandatory, not a courtesy check: it is the only verification for the visible focus indicator (FR-015), the absence of horizontal page scroll (FR-016), in-region scrolling as actually painted (FR-017), and company name wrapping, none of which jsdom can observe
<!-- T057 results, 2026-09-12, Chromium, against a live Ollama Cloud run:
  Scenario 1  120 frontend and 112 backend tests pass.
  Scenario 2  centred 1200px container, labelled regions, no native selects, styles.css deleted.
  Scenario 3  all 7 steps pass: follows OS, follows live, persists, ignores OS after a choice, survives a mid-run
              switch, renders with an invalid stored value, renders with storage blocked.
  Scenario 4  rendered contrast passes in both themes after fixing secondary text (was 3.31 light, 4.41 dark).
  Scenario 5  no horizontal scroll at 320, 375, 768, 1024, 1440, 2560 after fixing a 3px menu overflow at 320.
  Scenario 6  every tab stop shows a focus indicator in both themes; menu operable by arrow keys and Enter.
  Scenario 7  under reduced motion nothing spins during the model call.
  Scenario 8  -0.0500 and 0.3400 render with trailing zeros at every width and in both themes.
  Bundle      361.08 kB gzipped, within the 600 kB budget (baseline 86.16 kB). -->
- [X] T058 Re-check the feature against constitution v2.2.0: confirm no file under `backend/` changed, that the Principle I justification for antd from research R-001 is ready to quote in the pull request, that TanStack Query is still the only holder of server state, that no component in `frontend/src/components/` or `frontend/src/pages/` computes, rounds, or derives an indicator or a run outcome (FR-027), and that FR-026 is evidenced by the unchanged backend `CredentialLeakTest` and `RunControllerTest` credential assertions, which still pass, since the interface renders only what the API returns

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies, starts immediately
- **Foundational (Phase 2)**: Depends on Setup. BLOCKS all user stories
- **User Story 1 (Phase 3)**: Depends on Foundational. No dependency on US2 or US3
- **User Story 2 (Phase 4)**: Depends on Foundational. Restyled components from US1 are what it themes, so in practice it follows US1, but its own new files live entirely in `frontend/src/theme/`
- **User Story 3 (Phase 5)**: Depends on US1, because it adapts the layouts US1 builds. Independent of US2
- **Polish (Phase 6)**: Depends on the stories you intend to ship

### Critical path inside Foundational

T004 unblocks every test that renders an antd component. T005 adds the test wrapper without tokens, so it
depends on nothing. T006 is written before T007 and fails until it. T008 depends on T007 and supplies the
tokens to both `main.tsx` and the T005 wrapper. T009 is the gate for all story work.

### Within each user story

- Tests are written and confirmed failing before the implementation they cover
- Rewritten regression tests (T011, T016, T017) keep every fact the original asserted; only the query changes
- Components before the pages that compose them

### Parallel Opportunities

- Foundational: T004, T005, and T006 run together
- US1: all eight test tasks T010 through T017 run together; the component rebuilds T019 through T025 run together, as each is a different file; T027 alongside them
- US2: T030, T032, T033, and T034 run together; T031 follows T030 in the same file
- US3: T041 through T044 run together; T045, T046, T047, T049, T050 run together
- Polish: T052 through T056 run together
- US2 and US3 can be staffed in parallel once US1 lands, since US2 works in `theme/` and US3 in layouts

---

## Parallel Example: User Story 1

```bash
# Write all eight failing tests for User Story 1 together:
Task: "App shell test in frontend/src/App.test.tsx"
Task: "Rewrite option queries in frontend/src/components/RunLauncher.test.tsx"
Task: "Verbatim value guard in frontend/src/components/IndicatorTable.test.tsx"
Task: "New test in frontend/src/components/RunProgress.test.tsx"
Task: "Notice as alert in frontend/src/components/SummaryPanel.test.tsx"
Task: "Failure as alert in frontend/src/pages/RunDetailPage.test.tsx"
Task: "Rewrite step queries in frontend/src/components/NodeTimeline.test.tsx"
Task: "Rewrite row queries in frontend/src/components/RunHistory.test.tsx"

# Then rebuild the seven components together, one file each:
Task: "RunLauncher with Form and Select in frontend/src/components/RunLauncher.tsx"
Task: "RunProgress with Steps in frontend/src/components/RunProgress.tsx"
Task: "IndicatorTable with Table in frontend/src/components/IndicatorTable.tsx"
Task: "SummaryPanel with Card and Alert in frontend/src/components/SummaryPanel.tsx"
Task: "NodeDetail with Descriptions in frontend/src/components/NodeDetail.tsx"
Task: "NodeTimeline with Steps in frontend/src/components/NodeTimeline.tsx"
Task: "RunHistory with List and Tag in frontend/src/components/RunHistory.tsx"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Complete Phase 1: Setup, including the recorded baseline
2. Complete Phase 2: Foundational, with the contrast gate green
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: walk `contracts/ui-regions.md` against a real run
5. Demo: the same application, now with a deliberate corporate visual system

### Incremental Delivery

1. Setup plus Foundational, the foundation is ready
2. Add User Story 1, validate against the regions contract, ship the MVP
3. Add User Story 2, validate with quickstart Scenario 3
4. Add User Story 3, validate with quickstart Scenario 5
5. Polish, then walk all eight quickstart scenarios in both themes

### Parallel Team Strategy

1. The team completes Setup and Foundational together
2. One developer takes User Story 1, since US3 depends on its layouts
3. Once US1 lands, one developer takes User Story 2 in `theme/` and another takes User Story 3 in the layouts
4. The two meet only at `frontend/src/App.tsx`, where US2 adds the toggle and US3 adapts the header, so agree the header structure first

---

## Notes

- [P] means different files with no dependency on an incomplete task
- Every "Tests" block is written and seen to fail first; that is the lesson feature 001 paid for
- Never pass an indicator value through `Number`, `parseFloat`, `toFixed`, `toLocaleString`, a percentage formatter, or an antd numeric column type. It is a string and stays one
- Never collapse or hide one of the four node boundaries to save space
- A test rewritten to pass must still assert everything the original asserted
- The backend is out of scope. If a task seems to need a backend change, stop and raise it rather than make it
- Commit after each task or logical group, and stop at any checkpoint to validate a story independently
