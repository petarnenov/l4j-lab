# Specification Quality Checklist: Close the findings against the MCP billing server

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

**No clarification markers, and that is unusual for a feature this size.** The reason is that this
specification describes work that has already been investigated: each of the seven items carries a
reproduction recorded by the feature that found it. There was nothing to guess about *what* is wrong —
only about what to do, and that is the plan's question rather than this document's.

**One decision was deliberately left to the plan rather than settled here.** FR-006 requires that the
contract and the server describe the same confirmation exchange, and does not say which of the two
should move. Both are defensible: the server follows the protocol's own `ElicitResult` shape, so the
contract is arguably the one that is wrong; but the contract is what a client author reads, and it has
been published in that form. Naming the requirement without naming the remedy is the honest altitude —
a specification should say the two must agree.

**Written as user stories despite being a defect list**, which took some care. Each of the seven is a
problem *somebody has*: a caller who gets a crash instead of a refusal, a client author whose
confirmation is read as a decline, a model reading declarations that lost their enumerations, a
newcomer following a table into files that do not exist. Framing them that way is not decoration — it
is what makes them prioritisable against each other, and the priorities differ sharply.

**The Out of Scope section does real work here.** A feature whose input is a list of complaints invites
scope creep in every direction: rewriting 007's plan to match what was built, adding the capability a
finding hints at, fixing findings recorded elsewhere. Each of those is excluded by name.

**FR-013 is the requirement most likely to be argued with**, and it is the one worth keeping. Restoring
a declared constraint the server does not actually enforce would replace a lie about what is declared
with a lie about what is validated. Which way that resolves is a plan decision; that it must be decided
and recorded is not.


### Corrected after planning, 2026-09-14

**The specification described the wrong defect, and Phase 0 is why.** US1, FR-001 through FR-004 and
SC-001 were written from finding F-006 as recorded — *"a cross-advisor search returns HTTP 500"* — and
planning then traced the crash to any empty result set. The advisor filter was one way to reach it.

That is not a failure of the process; Phase 0 exists to learn things. The failure would have been
leaving the specification describing an entitlement bug while the plan and the tasks fixed a null
dereference, which is exactly the drift feature 009 was built to catch — and which an analysis pass
caught here, in the feature that closes findings.

US1 now leads with the defect that exists and keeps the three scenarios that already pass, marked as
regression protection rather than as work. FR-001a was added for the requirement nothing covered: no
query is answered with a 500.

**Two smaller corrections from the same pass**: the Out of Scope section claimed the MCP console
"gains from these fixes without changing", and three tasks change it — it now says what is true, that
the console is edited in one direction only, to remove workarounds these fixes make unnecessary. And
FR-009's versioning check is marked as answered by research rather than left reading like work.
