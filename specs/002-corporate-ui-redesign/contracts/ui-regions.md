# Contract: UI Regions

**Date**: 2026-09-12 | **Plan**: [../plan.md](../plan.md)

What every region of the interface must still show after the redesign, and what it must never do.

This is the contract that protects feature 001. A redesign is the likeliest way to quietly break
that feature: by reformatting a value, collapsing a node boundary to save space, dropping a notice
that looked like clutter, or putting logic in a component. Each obligation below is traceable to a
requirement and is meant to be walked, region by region, by a reviewer.

## Global obligations, every region

| Obligation | Source |
|------------|--------|
| No credential is ever rendered, in any region, in either theme | FR-026, 001 FR-018 |
| Legible in both themes, meeting the contrast floors | FR-009, FR-014 |
| Every control operable by keyboard with a visible focus indicator | FR-015 |
| No horizontal page scroll from 320 pixels up | FR-016 |
| Status never conveyed by colour alone | FR-008 |
| No chain, indicator, or orchestration logic in the component | FR-027 |

## Shell

| Must show | Source |
|-----------|--------|
| Navigation between New run and Previous runs | 001 US1, US3 |
| The theme control, reachable from every screen | FR-011 |
| Content inside a constrained, centred container | FR-005 |

## Launcher

| Must show | Source |
|-----------|--------|
| A company control with a visible, associated label | FR-007 |
| A period control with a visible, associated label, narrowed to the selected company | FR-007, 001 US1 |
| A primary action that starts the run, disabled while starting | 001 US1 |
| A readable error when starting fails | 001 FR-015 |

Below 768 pixels the controls stack at full width.

## Run in progress

| Must show | Source |
|-----------|--------|
| That the run is running, not frozen | 001 FR-020, FR-025 |
| The name of the node currently executing | 001 FR-020, FR-025 |
| All four steps, with completed, active, and pending distinguished by more than colour | FR-008 |
| That the summarizing step is the slow one, while it runs | 001 edge case |

Animation stops when reduced motion is requested (FR-018).

## Summary

| Must show | Source |
|-----------|--------|
| The model's summary text, inside a region capped at 24rem that scrolls | 001 edge case, FR-017 |
| **The standing notice that the figures are fictional and this is not investment advice** | FR-023, 001 R-009 |
| That where the summary and the indicators disagree, the indicators are correct | 001 edge case |
| That the summary has not arrived yet, when it has not | 001 US1 |

The notice is presented as an alert, not as small secondary text. It was the easiest element in the
old design to overlook and the most important one not to.

## Indicators

| Must show | Source |
|-----------|--------|
| All five indicators | 001 FR-005 |
| **Each value exactly as supplied: no rounding, reformatting, grouping, or unit conversion** | FR-022, 001 SC-005 |
| A not-applicable indicator's reason, in place of a value | 001 FR-005 |
| The record fields each indicator was derived from | 001 data model |

**Must never**: pass a value through `Number()`, `parseFloat`, `toFixed`, `toLocaleString`, a
percentage formatter, or any antd number-formatting prop. The value is a string and is rendered as
one. Every one of those turns `0.3400` into something that is no longer byte-identical.

Summary and indicators appear on the same screen (FR-020, 001 FR-012). Side by side from 768 pixels;
stacked below. Below 768 pixels the table becomes one card per indicator.

## Node timeline and node detail

| Must show | Source |
|-----------|--------|
| **Exactly four node entries, in execution order** | FR-021, 001 FR-013, SC-002 |
| For each node: what it received, what it produced, how long it took | FR-021, 001 FR-009 |
| For the summarizing node: the full text sent to the model and the full text returned | FR-021, 001 FR-010 |
| Token counts, where the provider reported them | 001 FR-010 |
| A failed node marked by more than colour, with its reason | FR-008, FR-024 |
| "Nothing was produced" for a failed node's output, rather than an empty area | 001 US2 |

**Must never**: collapse, truncate, or hide any of the four boundaries by default to save vertical
space. Stepping between nodes is fine; losing one is not.

Payloads and the model exchange scroll within their own region at every width (FR-017). The selected
node survives a resize (US3 scenario 5).

## Failure

| Must show | Source |
|-----------|--------|
| That the run stopped, and at which node | FR-024, 001 SC-006 |
| The reason, in plain language | FR-024, 001 FR-015 |
| That a timeout is a timeout rather than a generic failure | 001 FR-019 |
| That the earlier nodes' records and the indicators are still available below | FR-024, 001 FR-015 |

## History

| Must show | Source |
|-----------|--------|
| Previous runs, newest first | 001 FR-014 |
| For each: company, period, start time, and outcome | 001 FR-014 |
| Outcome by icon and label as well as colour | FR-008 |
| The summary preview where one exists | 001 US3 |
| A way to load older runs | 001 contract, cursor paging |
