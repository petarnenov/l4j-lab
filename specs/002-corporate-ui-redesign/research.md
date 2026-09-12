# Phase 0 Research: Corporate UI Redesign

**Date**: 2026-09-12 | **Plan**: [plan.md](./plan.md) | **Spec**: [spec.md](./spec.md)

All unknowns from Technical Context are resolved. No NEEDS CLARIFICATION remains.

Every version and licence below was read from the npm registry on the date above, and every contrast
ratio was computed from the tokens antd actually resolves, not from its documentation. R-003 exists
because that measurement contradicted the obvious assumption.

## R-001: Which component library

**Decision**: Ant Design, `antd` 6.6.3, with `@ant-design/icons`.

**Rationale**: the request asked for three things a component library has to deliver together: a
strict corporate style, a light and a dark theme, and a free licence.

- **Corporate by design.** Ant Design is an enterprise design language first and a component set
  second. Its density, its restrained defaults, and its data-display components, `Table`,
  `Descriptions`, and `Steps`, map directly onto what this interface shows. The other candidates
  either carry a strong brand of their own or aim at a softer, consumer register.
- **Both themes from one seed.** Its theme algorithms derive a complete palette from a small set of
  seed tokens, and design tokens are a first-class concept in the library, which is FR-004 met
  directly rather than approximated.
- **Free, verifiably.** MIT licence, declared plainly in the package, with no key and no fee.
- **No new build machinery.** Styling is self-contained through `@ant-design/cssinjs`. No Sass, no
  PostCSS configuration, no emotion runtime. Vite needs no change.
- **React 19 without a patch.** Its peer range is React 18 and above, and unlike antd 5 it declares
  no React 19 compatibility shim among its dependencies.

**Candidates evaluated**, all read from the registry:

| Library | Version | Licence | React 19 | Why not chosen |
|---------|---------|---------|----------|----------------|
| `@mantine/core` | 9.6.1 | MIT | yes | Excellent and accessible, and neutral rather than corporate. Requires PostCSS configuration, which is build machinery this project would otherwise not need. The closest second. |
| `@mui/material` | 9.4.0 | MIT | yes | Material Design is a specific, recognisable brand language, which reads as Google rather than as a corporate house style. Also brings the emotion runtime. |
| `@carbon/react` | 1.116.0 | Apache-2.0 | yes | IBM's system is the most literally corporate of all, and it is built on Sass with a heavy grid that would dominate a two-page interface. |
| `@fluentui/react-components` | 9.74.7 | MIT | yes | A strong enterprise option. Its theming model is more involved than antd's for the same two-theme outcome. |
| `primereact` | 11.1.0 | "SEE LICENSE IN LICENSE.md" | yes | The package does not declare a licence plainly. FR-002 requires the licence to permit use as published, and an indirection is exactly what that requirement guards against. |
| KendoReact | 16.1.0 | "SEE LICENSE IN LICENSE.md" | yes | Commercial, with a limited free subset. Fails FR-002 for the full set. Considered specifically because a `kendo-react` integration server is configured in this workspace. |

**Principle I justification**, to be quoted in the pull request as the constitution requires: the
mechanism this project teaches is the agent chain, and all of it lives in Java. A library that styles
the presentation layer obscures none of it. The alternative, a hand-built corporate design system
with two accessible themes, would be more code, would very likely be less accessible, and would
teach nothing about agents.

## R-002: Resolving and persisting the theme

**Decision**: A three-state preference, `system`, `light`, or `dark`, held in a React context and
persisted to one `localStorage` key. The effective theme is derived: `system` resolves through the
`prefers-color-scheme` media query and follows it live; `light` and `dark` override it.

**Rationale**: FR-010 and FR-012 cannot both hold with a two-state value. FR-010 wants the operating
system followed when the learner has not chosen; FR-012 wants an explicit choice to win and persist.
Only a third state, meaning "not chosen", distinguishes "follow the system" from "I picked the same
thing the system happens to say". The edge case of a learner changing their operating system theme
after choosing is exactly where the two-state design gets the wrong answer.

Every read and write of storage is wrapped. A blocked or throwing store, and a stored value outside
the three allowed, both fall back to `system`. Those are two of the spec's edge cases and they are
the reason the provider is more than a `useState`.

**Avoiding a flash of the wrong theme**: the effective theme is resolved synchronously during the
first render from storage and the media query, so the first paint is already correct. No effect runs
after mount to correct it.

**Alternatives considered**:

- CSS-only theming through `prefers-color-scheme`. Follows the system and cannot be overridden or
  persisted, so it fails FR-011 and FR-012. Rejected.
- A server-stored preference. There is no account and no user, and this is a single-learner local
  instance. Rejected as out of scope in the spec's assumptions.

## R-003: Contrast, measured, and the palette that passes

**Finding**: antd's defaults do not meet FR-014, and the obvious corporate choice fails worse.

Computed from the tokens antd 6.6.3 resolves, against the WCAG floors of 4.5 to 1 for text and 3 to 1
for interface components:

| Pair | antd default, light | antd default, dark | Navy seed, dark |
|------|------|------|------|
| Link or accent on surface | 4.10, fails | 3.55, fails | **1.47, fails badly** |
| Error text on surface | 3.27, fails | 4.35, fails | 4.35, fails |
| Success text on surface | 2.27, fails | 6.18 | 6.18 |
| Input border on surface | 1.41, fails | 1.83, fails | 1.83, fails |

The navy result is the important one. A dark navy accent is the natural corporate choice, and the
dark algorithm darkens the accent further onto a near-black surface, producing a link colour a
learner on a projector effectively cannot see. Applying one seed to both algorithms, which is what
most theming guides show, is precisely the approach that fails.

**Decision**: separate seed tokens per theme, recorded in `contracts/design-tokens.md`. Every pair
below was re-measured with the resolved values:

| Pair | Light | Dark | Floor |
|------|------:|-----:|------:|
| Body text on surface | 16.56 | 13.40 | 4.5 |
| Secondary text on surface | 6.98 | 8.19 | 4.5 |
| Secondary text on page background | 6.76 | 8.60 | 4.5 |
| Link on surface | 11.48 | 6.63 | 4.5 |
| Label on primary button | 11.48 | 5.31 | 4.5 |
| Primary as an interface component | 11.48 | 5.14 | 3.0 |
| Error text on surface | 7.75 | 7.19 | 4.5 |
| Success text on surface | 5.59 | 9.58 | 4.5 |
| Warning text on surface | 6.79 | 11.67 | 4.5 |
| Input border on surface | 3.69 | 3.72 | 3.0 |

Every pair passes in both themes. Two details carry weight:

- **The primary button's label flips colour between themes.** White on the light navy; near-black on
  the lighter dark-theme accent. Forcing white in both would fail in dark.
- **Status colours are set as text tokens, not left to the algorithm.** antd's generated error and
  success colours are designed as fills and icons. Used as text on a light surface they fail, which
  matters here because failure reasons are text (FR-024).

Decorative dividers use antd's secondary border token and are exempt from the 3 to 1 floor, which
applies only to borders a learner needs to identify a control.

**Alternatives considered**: accepting antd's defaults and documenting the gap. That fails a
measurable success criterion, SC-002, on day one. Rejected.

## R-004: Responsive strategy

**Decision**: antd's breakpoint tokens, read through `Grid.useBreakpoint`, with one layout decision
per region rather than a global grid:

| Region | Below 768 pixels | From 768 pixels |
|--------|------------------|-----------------|
| Page container | full width with side padding | centred, maximum width capped |
| Launcher | controls stacked, full width | controls in one row |
| Summary and indicators | stacked | side by side |
| Indicator table | one card per indicator | table |
| Node timeline | vertical steps | horizontal steps |
| Payloads and model exchange | scroll within their own region | same |

The breakpoints are antd's own, measured as 480, 576, 768, 992, 1200, and 1600 pixels. Using the
library's values rather than inventing a second set keeps FR-004's single source of truth.

**The indicator table is the one real decision.** A five-row, three-column table does fit at 320
pixels if it scrolls, but a learner reading financial indicators on a phone should not be scrolling
sideways to see a value next to its name. Below the breakpoint each indicator becomes a card, and the
value is still rendered verbatim (FR-022).

**Alternatives considered**: a horizontally scrolling table at every width. Satisfies FR-016
technically, and fails SC-003 in practice. Rejected.

## R-005: Mapping the existing regions onto components

**Decision**: each existing region maps to one antd component family, with the existing component
file, name, and props preserved.

| Region | Component | Preserves |
|--------|-----------|-----------|
| Shell and navigation | `Layout`, `Menu` or `Segmented`, plus the theme control | FR-005, FR-011 |
| Launcher | `Form`, `Select`, `Button` | FR-007 |
| Run in progress | `Steps` with the current step marked | FR-025 |
| Summary | `Card`, `Typography`, `Alert` for the standing notice | FR-020, FR-023 |
| Indicators | `Table`, or a `Card` list below the breakpoint | FR-022 |
| Node timeline | `Steps` | FR-021 |
| Node detail | `Descriptions`, bounded scroll regions for payloads | FR-017, FR-021 |
| Failure | `Alert` of type error, with an icon | FR-008, FR-024 |
| History | `List` or `Table` with `Tag` for status | FR-008 |

**Amended during implementation, history rows**: the table above names `List or Table` for history.
antd 6.6.3 deprecates `List`, and its suggested replacement `Listy` is a virtualised scrolling primitive
for very long lists, not a bordered list of rows. History is a semantic `<ul>` of native buttons styled
from theme tokens instead: no deprecated component, and keyboard operation without hand-written handlers.
Likewise `Spin tip`, `Alert message`, and `Steps items.description` are deprecated in antd 6 in favour
of `description`, `title`, and `content`, and the implementation uses the current names.

**The fictional-data notice becomes an `Alert`** rather than small grey text. It was the easiest
element in the old design to overlook and the most important one not to.

**Status uses an icon and a label as well as colour** everywhere, so FR-008 holds on a monochrome
projector and for a colour-blind learner.

## R-006: Testing theming and responsiveness under jsdom

**Decision**: extend `frontend/src/test/setup.ts` with a controllable `matchMedia` mock and a
`localStorage` that can be made to throw, and extend `render.tsx` so every test renders inside the
theme provider.

**Rationale**: jsdom implements neither `matchMedia`, nor `ResizeObserver`, nor a failing storage. All three are load-bearing. `ResizeObserver` is used by antd's `Table` and `Segmented`, and without a stub they fail to render under test. Of the other two:
FR-010 is a statement about `matchMedia`, antd's `Grid.useBreakpoint` depends on it, and the
blocked-storage edge case cannot be tested against a store that always succeeds. Without the mock,
antd components that read breakpoints fail to render at all under test.

**What is tested at unit level, and what is not**: theme resolution, persistence, fallback, and the
breakpoint-driven layout choices are testable in Vitest. Actual pixel contrast and actual rendering
at 320 pixels are not, because jsdom performs no layout and no painting. Those are verified in
`quickstart.md` in a real browser, and R-003's token measurement is repeatable as a script.

**Alternatives considered**: a browser test runner such as Playwright for the whole suite. It would
cover layout and contrast, and it adds a second test runner to a stack the constitution locks to
Vitest. Rejected for this feature; the gap is covered by the quickstart walkthrough.

## R-007: Not regressing feature 001

**Decision**: keep all 30 existing frontend tests unmodified in intent, and let them fail loudly if the
redesign drops anything. Where an assertion depends on markup that changes, such as a CSS class,
rewrite it to assert the same user-visible fact through a role or text query instead.

**Rationale**: FR-019 through FR-027 are the requirements most likely to be broken quietly. The
existing tests already assert several of them: the indicator values render verbatim, the fictional
notice is present, the model exchange appears only on the fourth node, the failing node is named.
Those tests are a free regression harness, and rewriting them from scratch alongside the components
would forfeit that protection at exactly the moment it matters.

## R-008: Bundle weight

**Decision**: accept antd's weight, rely on its ES module tree-shaking through Vite, and record a
budget: the production JavaScript bundle must not exceed 600 kilobytes gzipped.

**Rationale**: the current bundle is 86 kilobytes gzipped. antd's full minified distribution is 1.4
megabytes before compression, and an application importing a dozen components tree-shakes to a
fraction of that. A learning project run locally does not have the bandwidth constraints of a public
site, and the budget exists to catch an accidental whole-library import rather than to optimise.

**Alternatives considered**: importing icons individually rather than from the package root. Adopted
as a practice, since the icon package is the usual source of accidental bloat.

## R-009: Reduced motion

**Decision**: configure antd's motion tokens off when `prefers-reduced-motion: reduce` matches, and
suppress the in-progress indicator's animation under the same query.

**Rationale**: FR-018. antd animates by default, including step transitions and alert entrance, and
the existing spinner rotates continuously for as long as a model call takes, which can be the better
part of a minute.
