# Phase 1 Data Model: Corporate UI Redesign

**Date**: 2026-09-12 | **Plan**: [plan.md](./plan.md) | **Spec**: [spec.md](./spec.md)

This feature persists nothing on the server and changes no backend entity. Everything here is client
state. The run, node, and indicator entities from feature 001 are consumed exactly as they are; see
`specs/001-financial-agent-chain/data-model.md`.

## Theme Preference

The learner's choice of theme. Maps to the Theme Preference entity in the specification.

| Field | Type | Notes |
|-------|------|-------|
| preference | `system` \| `light` \| `dark` | `system` means the learner has not chosen |

**Storage**: one `localStorage` key, `l4j.theme`, holding `light` or `dark`. The `system` preference is represented by the key's absence, never by a stored `system` value: choosing System removes the key. This matches FR-012's wording, where a choice holds until the learner clears it. Nothing else is stored.

**Validation rules**

- Any stored value other than `light` or `dark` resolves to `system`, including a literal `system` left by an earlier build. A value left behind by a future
  version, a hand-edited value, or a corrupted one must not stop the application rendering.
- A storage read or write that throws, as in a private window or with site data blocked, is caught
  and treated as `system`. The application keeps working; the choice simply does not persist.

**State transitions**

```text
            learner picks light                learner picks dark
  system ─────────────────────────> light ─────────────────────────> dark
     ^                                │  ^                              │
     │         learner picks system   │  └──────── learner picks light ─┘
     └────────────────────────────────┘
```

Any state may move to any other; the learner drives every transition. No transition happens without
the learner, which is the point of FR-012: an operating system change never overwrites a choice.

## Effective Theme

Not stored. Derived on every render from the preference and the operating system.

| Preference | Operating system reports | Effective theme |
|------------|--------------------------|-----------------|
| `system` | dark | dark |
| `system` | light | light |
| `system` | nothing | light, the defined default (FR-010) |
| `light` | anything | light |
| `dark` | anything | dark |

While the preference is `system`, a change in the operating system theme updates the effective theme
live, without a reload. While it is `light` or `dark`, that change is ignored.

## Design Tokens

Not stored. A static definition in `frontend/src/theme/tokens.ts`, with one resolution per effective
theme. The names and the measured values are the contract in
[contracts/design-tokens.md](./contracts/design-tokens.md).

## Relationships

```text
localStorage['l4j.theme'] ──> Theme Preference ─┐
                                                ├──> Effective Theme ──> Design Tokens ──> every component
prefers-color-scheme ───────────────────────────┘
```

The Effective Theme is the only thing components read. None of them reads storage or the media query
directly, so the resolution rules above live in exactly one place.
