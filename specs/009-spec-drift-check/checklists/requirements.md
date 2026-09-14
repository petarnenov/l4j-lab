# Specification Quality Checklist: Hold the documents to the code

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-14
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

**The one marker was resolved in planning rather than by asking again.** FR-016 asked which features
the check applies to. Phase 0 answered it from the spec's own Out of Scope: this feature may not edit
what a past feature decided, so forcing 001–006 to pass would push against that rule, and checking
only new features would leave known-stale documents with nothing saying so. Everything that declares
itself implemented is checked; pre-existing drift is recorded with a reason and printed on every run;
new drift fails. Recorded in [research.md R-005](../research.md) and the plan's Complexity Tracking.

**A tension worth naming for the planner.** The specification flow writes a plan *before* the code
exists, so for most of a feature's life the plan correctly describes files that are not there. A
check that held every plan to the repository at all times would fail throughout implementation and be
switched off within a day. FR-007 turns this into a requirement rather than an accident: completeness
is read from the task list the project already keeps, so nobody has to remember to arm the check.

**Two things were deliberately kept out**, and both were tempting. Judging prose — feature 008's
F-001 is the standing counter-example, and no document checker could have found it. And correcting
documents automatically, which would quietly make the document the loser of every disagreement, when
sometimes the document is the part that was right.

**The measurable outcomes are unusually concrete** because this feature has a real corpus: every
drift found by hand during feature 008 is written down, so SC-001 can be checked by reintroducing
each one rather than by inventing test cases. A check for a problem that has already happened twice
should be measured against what actually happened.
