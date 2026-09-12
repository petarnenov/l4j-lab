# Feature Specification: Corporate UI Redesign

**Feature Branch**: `002-corporate-ui-redesign`

**Created**: 2026-09-12

**Status**: Approved, 2026-09-12. Planning and task generation are complete against
constitution v2.2.0, and the quality checklist in `checklists/requirements.md` passes.

**Input**: User description: "Дизайнът трябва да се подобри. Трябва да се подържа дарк и лайт моде. Трябва да е респонсиже. Избери фрее компонрнт лайбръри и я ползвай. Дизайнът да строг корпоративен стил" (with a screenshot of the current New run screen)

## Context

The Financial Agent Chain application works, and it looks like a prototype. The screenshot supplied
with this request shows the problems plainly: content runs the full width of a wide monitor with no
container, the two form controls and their labels sit in an uneven row, the page is mostly empty
space below the fold, and every control is an unstyled browser default. Nothing is wrong
functionally. Everything is wrong presentationally.

This feature replaces the hand-written styling with a consistent visual system built on an
established component library, in a restrained corporate register, working in both a light and a
dark theme and at every screen size from a phone to a wide desktop.

The application's purpose does not change. It teaches how an agent chain works, and the redesign
must make the four node boundaries, the indicator table, and the model exchange easier to read
rather than prettier at their expense.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Read the application without visual friction (Priority: P1)

A learner opens the application on a normal desktop screen. The content sits in a comfortable
measure rather than stretching edge to edge. Headings, labels, values, and helper text are visually
distinct from one another. The launcher reads as one grouped control rather than three loose
elements. A completed run's summary, indicators, and node timeline each read as a defined region.
Nothing on screen looks like an unstyled browser default.

**Why this priority**: This is what was actually asked for and it is the largest single improvement.
It also stands alone: a coherent light-theme desktop layout is a complete, shippable result even if
theming and narrow-screen work never happen.

**Independent Test**: Open every screen at a typical desktop width and confirm each region is
visually defined, the content is within a readable measure, and no control is a browser default.
Delivers the entire visual improvement without depending on theme switching or small screens.

**Acceptance Scenarios**:

1. **Given** the application is open on a wide monitor, **When** the learner views any screen,
   **Then** the content is constrained to a readable measure rather than spanning the full viewport
   width, and is positioned consistently across screens.
2. **Given** the learner is on the launcher, **When** they look at the company and period controls,
   **Then** each control has a label clearly associated with it and the group reads as one form.
3. **Given** a completed run, **When** the learner views it, **Then** the summary, the indicator
   table, and the node timeline are each presented as a distinct, labelled region.
4. **Given** any screen, **When** the learner inspects the controls, **Then** buttons, selects, and
   tables share one consistent visual treatment rather than browser defaults.
5. **Given** a run has failed, **When** the learner views it, **Then** the failure is visually
   distinguished from ordinary content without relying on colour alone.

---

### User Story 2 - Work in the theme the learner prefers (Priority: P2)

A learner who works in a dark environment opens the application and it is already dark, because
their operating system says so. Another learner overrides that from a control in the application and
their choice is remembered the next time they return. Every element remains legible in both themes.

**Why this priority**: Explicitly requested, and it is the second most visible change. It depends on
the design system from User Story 1 existing, but it is independently testable and independently
valuable once that exists.

**Independent Test**: Switch the operating system between light and dark and confirm the application
follows. Override with the in-application control, reload, and confirm the override survives.
Delivers theme support on its own.

**Acceptance Scenarios**:

1. **Given** the learner has never chosen a theme, **When** they open the application, **Then** it
   matches the theme their operating system reports.
2. **Given** the learner selects a theme in the application, **When** they reload or return later,
   **Then** their selection is still in effect and overrides the operating system preference.
3. **Given** either theme is active, **When** the learner reads any screen, **Then** all text,
   controls, tables, code blocks, and status indicators remain legible and meet the contrast
   requirement in FR-014.
4. **Given** a run is in progress, **When** the learner switches theme, **Then** the run continues
   uninterrupted and its displayed state is unchanged.
5. **Given** the learner has chosen a theme, **When** they change their operating system theme,
   **Then** the application keeps the learner's explicit choice.

---

### User Story 3 - Use the application on a small screen (Priority: P3)

A learner opens the application on a phone or a narrow window. The navigation, the launcher, the
indicator table, and the node detail all adapt to the width rather than overflowing. The learner can
still complete a run and read every node boundary without scrolling sideways.

**Why this priority**: Requested, and genuinely useful, but a learner following along in a lesson is
most often on a laptop. The value is real and it is the least urgent of the three.

**Independent Test**: Complete a full run and open the node detail at a narrow viewport, confirming
no horizontal scrolling of the page and no clipped content. Delivers small-screen support
independently of theming.

**Acceptance Scenarios**:

1. **Given** a viewport at the minimum supported width, **When** the learner views any screen,
   **Then** the page does not scroll horizontally and no content is clipped or overlapped.
2. **Given** a narrow viewport, **When** the learner opens the launcher, **Then** the controls stack
   and remain operable at a comfortable touch size.
3. **Given** a narrow viewport, **When** the learner views the summary and the indicators, **Then**
   both remain readable, reflowing rather than shrinking below the minimum legible text size.
4. **Given** a narrow viewport, **When** the learner opens a node's detail, **Then** wide content
   such as payloads and the model exchange scrolls within its own region rather than forcing the
   whole page sideways.
5. **Given** the viewport is resized between narrow and wide, **When** the layout adapts, **Then**
   no state is lost, including the selected node in the timeline.

---

### Edge Cases

- The operating system reports no theme preference at all. The application must still pick a
  definite theme rather than rendering unstyled.
- The learner's browser blocks the storage used to remember a theme choice. The application must
  keep working and fall back to following the operating system.
- A learner arrives with a stored theme value that is no longer valid, for example after the set of
  themes changes. The application must fall back rather than fail to render.
- The model returns a summary far longer than expected. It must stay inside its own scroll region in
  both themes and at every width, as it does today.
- A node payload is wide, deeply nested, or long. It must scroll inside its own region and must
  never widen the page.
- A company name is much longer than the ones in the sample dataset. Labels, dropdown options, and
  history rows must wrap or truncate visibly rather than overflowing.
- The learner has asked their system to reduce motion. Any animation, including the in-progress
  indicator, must respect that.
- The application is displayed on a projector or shared screen at low contrast. This is why the
  contrast requirement is stated as a measurable floor rather than a matter of taste.
- A run is in progress while the learner switches theme or resizes. Polling must continue and the
  displayed state must not reset.

## Requirements *(mandatory)*

### Functional Requirements

#### Design system

- **FR-001**: The interface MUST be built on an established third-party component library rather
  than bespoke element styling, and that library MUST be used for the interactive controls it
  provides rather than re-implemented alongside it.
- **FR-002**: The chosen component library MUST be usable at no cost, with no paid licence, no
  licence key, and no per-seat fee, and its licence MUST permit use in this project as published.
- **FR-003**: The visual style MUST be restrained and corporate: a neutral palette, one accent
  colour used sparingly for primary actions and active states, conventional typography, and no
  decorative ornament.
- **FR-004**: Typography, spacing, colour, and border treatment MUST come from a single declared set
  of design tokens rather than values repeated at each usage site.
- **FR-005**: All page content MUST sit within a constrained, consistently positioned container
  rather than spanning the full width of an arbitrarily wide viewport.
- **FR-006**: Each major region, being the launcher, the run summary, the indicator table, the node
  timeline, the node detail, and the run history, MUST be presented as a visually defined and
  labelled region.
- **FR-007**: Every form control MUST have a visible, programmatically associated label.
- **FR-008**: Run outcome, node outcome, and in-progress state MUST be conveyed by at least one
  visual channel in addition to colour, so the distinction survives a colour-blind reader and a
  monochrome projector.

#### Theming

- **FR-009**: The application MUST support a light theme and a dark theme, and every screen and
  component MUST be legible in both.
- **FR-010**: On first visit, the application MUST adopt the theme reported by the operating system,
  and MUST fall back to a defined default when no preference is reported.
- **FR-011**: The learner MUST be able to override the theme from a control within the application,
  reachable from every screen.
- **FR-012**: An explicit theme choice MUST persist across reloads and across sessions on the same
  device, and MUST take precedence over the operating system preference until the learner clears it.
- **FR-013**: Switching theme MUST NOT reload the page, interrupt a run in progress, or reset any
  displayed state.

#### Accessibility and responsiveness

- **FR-014**: Text and meaningful interface elements MUST meet a contrast ratio of at least 4.5 to 1
  for body text and 3 to 1 for large text and interface components, in both themes.
- **FR-015**: The application MUST be usable without a pointing device: every control MUST be
  reachable and operable by keyboard, with a visible focus indicator in both themes.
- **FR-016**: The application MUST remain usable from a viewport 320 pixels wide up to a wide
  desktop, with no horizontal scrolling of the page at any width in that range.
- **FR-017**: Content that is inherently wide, being node payloads, the model exchange text, and the
  indicator table, MUST scroll within its own bounded region rather than widening the page.
- **FR-018**: Animation and transition MUST be suppressed when the learner's system requests reduced
  motion.

#### Preserving what the application already does

- **FR-019**: The redesign MUST NOT change any behaviour specified in feature 001. Every acceptance
  scenario in `specs/001-financial-agent-chain/spec.md` MUST still hold.
- **FR-020**: The summary and the indicators it was built from MUST remain visible together on one
  screen.
- **FR-021**: All four node boundaries MUST remain inspectable, each showing its input, its output,
  its duration, and for the summarizing node the full model exchange.
- **FR-022**: Indicator values MUST be displayed exactly as supplied, with no rounding, reformatting,
  locale grouping, or unit conversion applied by the interface.
- **FR-023**: The standing notice that the companies and figures are fictional and that the content
  is not investment advice MUST remain visible alongside the summary in both themes.
- **FR-024**: When a run fails or times out, the failing node and the reason MUST remain readable on
  screen, and the records of nodes that completed MUST remain visible.
- **FR-025**: The node currently executing MUST remain named on screen while a run is in flight.
- **FR-026**: No credential MUST ever be rendered, in either theme, in any region, including within
  the displayed model exchange.
- **FR-027**: The redesign MUST NOT move any chain, indicator, or orchestration logic into the
  interface. The interface remains presentation only.

### Key Entities

- **Theme Preference**: The learner's explicit choice of light or dark, or its absence meaning follow
  the operating system. Stored per device, readable and clearable by the learner.
- **Design Tokens**: The named colour, typography, spacing, and border values that define the visual
  system, each with a light and a dark resolution.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every screen renders without horizontal page scrolling at every viewport width from
  320 pixels to 2560 pixels.
- **SC-002**: In both themes, one hundred percent of text and interface elements meet the contrast
  floors stated in FR-014, verified by measurement rather than judgement.
- **SC-003**: A learner who has never used the application can start a run and read the result
  within five minutes on a phone, which the current interface does not support at all.
- **SC-004**: An explicit theme choice survives a reload and a new session in one hundred percent of
  attempts on the same device.
- **SC-005**: Every acceptance scenario from feature 001 passes unchanged after the redesign, with no
  regression in the four-node trace, the indicator values, or the failure messaging.
- **SC-006**: Every interactive control is reachable and operable by keyboard alone, with a focus
  indicator visible in both themes, in one hundred percent of controls.
- **SC-007**: Indicator values displayed on screen are byte-identical to the values supplied, across
  ten runs in each theme.
- **SC-008**: Switching theme completes without a page reload and without altering any displayed run
  state, in one hundred percent of attempts.
- **SC-009**: A reviewer shown the interface alongside the screenshot in this request identifies it
  as the same application with a deliberate visual system, rather than as an unstyled prototype.

## Assumptions

- The choice of component library is delegated to the planning phase, which must justify it against
  Principle I of the constitution and against the free-licence requirement in FR-002. This
  specification deliberately names no library, because the decision belongs with the research that
  weighs licence, bundle size, accessibility support, and theming model.
- "Corporate" means restrained and professional rather than any specific company's brand. No brand
  guideline, palette, or logo was supplied, so the planning phase chooses a neutral palette.
- The two existing screens and their regions stay as they are in structure. This feature restyles and
  reorganises them; it adds no new page, no new data, and no new interaction beyond the theme
  control.
- The set of themes is exactly two, light and dark. A high-contrast or system-forced-colours mode is
  out of scope for this feature, though FR-014 keeps the door open to it.
- Internationalisation, right-to-left layout, and translation are out of scope. The interface stays
  in English.
- The application remains a single-learner local instance, so a theme preference is stored per device
  with no account and no synchronisation.
- The minimum supported width of 320 pixels reflects the narrowest phone still in common use.
- Printing and print stylesheets are out of scope.

## Dependencies

- **Feature 001, Financial Agent Chain**: this feature restyles that interface and must not regress
  it. Its specification is the authority on what each screen must show.
- **A third-party component library**: to be selected during planning, subject to FR-002. Adding it
  is a dependency addition, which the constitution requires be justified against Principle I in the
  pull request. The justification is available: the chain logic being taught lives entirely in the
  backend, so a library that styles the presentation layer obscures no mechanism this project exists
  to teach.
- **Constitutional standing**: the constitution locks the frontend to React built with Vite, with
  TanStack Query for server state and Vitest for testing. This feature changes none of those. It adds
  a component library alongside them, which is a new dependency rather than a replacement of a locked
  stack entry, so no amendment is required.
- **An observation for planning, not a decision**: this workspace has a `kendo-react` integration
  server configured, though it failed to connect in the session where this specification was written.
  That may indicate a preference for KendoReact. Note that KendoReact is a commercial library with a
  limited free subset, so any proposal to use it must be checked against FR-002 before it is chosen.
