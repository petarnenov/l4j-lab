# Contract: Design Tokens

**Date**: 2026-09-12 | **Plan**: [../plan.md](../plan.md)

The single source of every colour, radius, and type value in the interface (FR-004). Defined once in
`frontend/src/theme/tokens.ts` and handed to antd's `ConfigProvider`. No component declares a colour
of its own.

## Why two seeds and not one

Applying one seed to both antd theme algorithms is the approach most theming guides show, and it is
the approach that fails here. The dark algorithm darkens the accent, and a navy accent darkened onto
a near-black surface measures 1.47 to 1, which fails the contrast floor by a factor of three. Each
theme therefore has its own seed. The measurements are in research R-003.

## Shared tokens

| Token | Value | Purpose |
|-------|-------|---------|
| `borderRadius` | `4` | Restrained corners, per the corporate register (FR-003) |
| `fontFamily` | `system-ui, -apple-system, "Segoe UI", Roboto, sans-serif` | Conventional, no web font download |

## Light theme

Algorithm: `theme.defaultAlgorithm`.

| Token | Value | Measured against surface | Floor |
|-------|-------|------:|------:|
| `colorPrimary` | `#1f3a5f` | 11.48 as interface | 3.0 |
| `colorLink` | `#1f3a5f` | 11.48 | 4.5 |
| `colorErrorText` | `#a8071a` | 7.75 | 4.5 |
| `colorSuccessText` | `#237804` | 5.59 | 4.5 |
| `colorWarningText` | `#874d00` | 6.79 | 4.5 |
| `colorBorder` | `#858585` | 3.69 as interface | 3.0 |
| `colorTextDescription` | `rgba(0,0,0,0.56)` | 4.66 on page background | 4.5 |
| `colorTextTertiary` | `rgba(0,0,0,0.56)` | 4.66 on page background | 4.5 |
| Primary button label | `#ffffff` | 11.48 on primary | 4.5 |

## Dark theme

Algorithm: `theme.darkAlgorithm`.

| Token | Value | Measured against surface | Floor |
|-------|-------|------:|------:|
| `colorPrimary` | `#6f9fd8` | 5.14 as interface | 3.0 |
| `colorLink` | `#8fb6e3` | 6.63 | 4.5 |
| `colorErrorText` | `#ff7875` | 7.19 | 4.5 |
| `colorSuccessText` | `#73d13d` | 9.58 | 4.5 |
| `colorWarningText` | `#ffc53d` | 11.67 | 4.5 |
| `colorBorder` | `#707070` | 3.72 as interface | 3.0 |
| `colorTextDescription` | `rgba(255,255,255,0.50)` | 4.58 or better on page background | 4.5 |
| `colorTextTertiary` | `rgba(255,255,255,0.50)` | 4.58 or better on page background | 4.5 |
| Primary button label | `rgba(0,0,0,0.88)` | 5.31 on primary | 4.5 |

The primary button label is dark in the dark theme. White on `#6f9fd8` fails; this is the one token
whose polarity flips between themes, and it must be set explicitly rather than inherited.

## Why description and tertiary text are overridden

Amended during implementation. The original contract left all secondary text to the algorithm, having
measured `colorTextSecondary`. But antd's `Typography type="secondary"` paints with `colorTextDescription`,
and several components use `colorTextTertiary`, both at 45% opacity. Rendered in Chromium, secondary text
measured 3.31 to 1 in light and 4.41 in dark, failing FR-014, while the token gate stayed green because it
was measuring a token nothing rendered. The gate now measures the tokens components actually use.

## Tokens left to the algorithm

Surface, page background, body text, and secondary text are left to antd's algorithms. They were
measured and all pass with margin: body text 16.56 light and 13.40 dark, secondary text 6.98 light
and 8.19 dark. Overriding them would add values to maintain for no gain.

Decorative dividers use antd's `colorBorderSecondary`. They identify no control, so the 3 to 1
interface floor does not apply.

## Rules

- **Status colours are text tokens.** Error, success, and warning are set as `*Text` tokens. antd's
  generated status colours are designed for fills and icons, and as text on a light surface they
  measure 2.27 to 3.27, which fails. Failure reasons are text (FR-024), so this is load-bearing.
- **Status is never colour alone.** Every use of a status colour is paired with an icon and a label
  (FR-008).
- **Motion is a token.** When the learner requests reduced motion, antd's motion tokens are disabled
  (FR-018).
- **Breakpoints are antd's.** 480, 576, 768, 992, 1200, 1600 pixels. No second set is declared.

## Verification

A change to any value in this contract must be re-measured. The measurement in R-003 is a short
script against `theme.getDesignToken`, and it is the only acceptable evidence that a changed token
still meets FR-014.
