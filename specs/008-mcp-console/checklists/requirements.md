# Specification Quality Checklist: MCP console

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

Two passes were needed, and what changed is worth recording because it is the same mistake twice.

**First pass, corrected.** Several requirements named the mechanism rather than the need: "shows the
JSON-RPC request and response", "mints a token from `/dev/token`", "polls `tasks/get` until
terminal", "addresses `mcp-a`, `mcp-b`, `mcp-c`". Each was rewritten to say what a person must be
able to see or do — FR-010, FR-005, FR-013, FR-016 — leaving how to the plan. The protocol names
still appear in the Overview and Assumptions, where they identify the system under test rather than
prescribe a design.

**A tension worth naming for the planner.** This feature's purpose is to make a protocol visible, so
"no implementation details" sits awkwardly: the thing a user wants to look at *is* an implementation
detail of feature 007. Resolved by treating feature 007's wire format as the subject matter — which a
requirement may refer to — and this console's own construction as the implementation, which it may
not. FR-010 says a person must see what travelled; it does not say how the console obtains or renders
it.

**Settled by default, then partly revisited.** Three things were first settled without asking, each
recorded in Assumptions with its reason: the console lives in the existing application (the request
said so), it is development-only (the credential-minting it depends on does not exist elsewhere),
and it derives argument fields from what the server declares (the server offers no other description
of itself). The middle one was subsequently asked anyway and confirmed, because "development-only"
has two readings — absent from the packaged build, or merely unadvertised in it — and only the
first one is testable.

**After clarification.** Five questions were asked in total. Four of them changed the spec in ways a
default would not have reached: that the console must be absent from the packaged build, that it is
tested both in isolation and against the running system, that irreversible writes are made visible
rather than undone, and that a deliberately malformed request must be producible on demand — without
which FR-011's distinction between a tool failure and a protocol failure could be stated but never
shown.
