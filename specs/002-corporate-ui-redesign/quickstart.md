# Quickstart: Corporate UI Redesign

**Date**: 2026-09-12 | **Plan**: [plan.md](./plan.md)

How to prove the redesign works and that it broke nothing. What each region must show is in
[contracts/ui-regions.md](./contracts/ui-regions.md); the palette is in
[contracts/design-tokens.md](./contracts/design-tokens.md).

Unit tests cover theme resolution, persistence, and layout decisions. They cannot cover pixel
contrast or real rendering at 320 pixels, because jsdom paints nothing (research R-006). Scenarios 3,
4, and 5 below are therefore walked in a real browser.

## Prerequisites

The feature 001 stack running, as in `specs/001-financial-agent-chain/quickstart.md`:

```bash
docker compose up -d
./gradlew :backend:run
cd frontend && npm install && npm run dev
```

## Scenario 1: nothing from feature 001 regressed

```bash
cd frontend && npm test
```

**Expected**: all tests pass, including the original 30, which assert the indicator values render
verbatim, the fictional notice is present, the model exchange appears only on the fourth node, and a
failing node is named. A failure here is a regression, not a test to update (research R-007).

Then run one real chain end to end and walk every row of `contracts/ui-regions.md` against it.

## Scenario 2: the design system replaced the old stylesheet

1. Open each screen at a normal desktop width.
2. Compare against the screenshot in the specification request.

**Expected**: content sits in a centred container rather than spanning the viewport. The launcher
reads as one labelled form. Summary, indicators, and timeline are each a defined region. No control
is a browser default. `frontend/src/styles.css` no longer exists.

## Scenario 3: both themes, and the preference rules

1. Clear site data. Set the operating system to dark. Open the application.
   **Expected**: dark.
2. Switch the operating system to light with the application open.
   **Expected**: follows to light without a reload.
3. Choose dark with the in-application control. Reload.
   **Expected**: dark, and still dark.
4. Switch the operating system to light again.
   **Expected**: stays dark. An explicit choice wins (FR-012).
5. Start a run, and switch theme while it is in progress.
   **Expected**: the run continues, the current node is still named, nothing resets (FR-013).
6. In the browser console, set `localStorage['l4j.theme'] = 'purple'` and reload.
   **Expected**: renders, following the operating system.
7. Open the application in a private window with storage blocked.
   **Expected**: renders and works; the choice just does not persist.

## Scenario 4: contrast, measured

Contrast is enforced by the test suite rather than by a script someone has to remember to run. The gate
resolves the tokens exactly as antd does and measures every pair in `contracts/design-tokens.md`:

```bash
cd frontend && npx vitest run src/theme/tokens.contrast.test.ts
```

**Expected**: all 22 checks pass, including the one proving the naive navy palette really does fail, so the
gate is known to be testing something. A palette change that breaks the floor fails `npm test`.

Then spot-check in the browser with the accessibility inspector on a link, an error message, the primary
button, and an input border, in each theme. Specifically confirm in the dark theme that links are clearly
visible. That is the pair that measured 1.47 to 1 with the naive palette.

## Scenario 5: 320 pixels to a wide desktop

In the browser's responsive mode, at 320, 375, 768, 1024, 1440, and 2560 pixels:

1. Open New run. Start a run.
2. Open the completed run's node detail and select the summarizing node.
3. Open Previous runs.

**Expected at every width**: no horizontal scrollbar on the page. Below 768 pixels the launcher
stacks, the indicators become cards, and the timeline is vertical. The model exchange and payloads
scroll inside their own regions. Resizing between widths keeps the selected node.

## Scenario 6: keyboard only

Unplug the mouse, or do not touch it. Start a run, step through all four nodes, open history, open a
previous run, and change the theme.

**Expected**: every step is possible, and the focus indicator is visible at every step in both themes
(FR-015, SC-006).

## Scenario 7: reduced motion

Enable reduced motion in the operating system accessibility settings, then start a run.

**Expected**: the in-progress indicator does not animate, and steps change without transition
(FR-018).

## Scenario 8: indicator values are still byte-identical

Run the zero-equity company, `stonebridge-paper` for `2025-Q4`, in each theme and at 320 pixels.

**Expected**: values read `-0.0500`, `0.3400`, `-0.0300`, `0.9000`, with trailing zeros intact, and
`debtToEquity` shows not applicable with its reason. A value reading `0.34` or `34%` is a regression
of FR-022, and of the scale bug fixed in feature 001.

## Bundle budget

```bash
cd frontend && npm run build
```

**Expected**: the gzipped JavaScript bundle stays under 600 kilobytes (research R-008).
