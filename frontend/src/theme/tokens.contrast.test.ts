import { theme } from 'antd'
import { describe, expect, it } from 'vitest'
import { darkSeed, lightSeed, primaryButtonLabel, sharedTokens } from './tokens'

/**
 * The contrast gate (FR-014, SC-002). Resolves the tokens exactly as antd will, and measures every pair
 * in contracts/design-tokens.md against its WCAG floor. A palette change that fails accessibility fails
 * `npm test`. This replaces the manual script the quickstart originally named.
 *
 * Why it exists: applying one seed to both antd algorithms is what theming guides show, and with a
 * navy accent it measured 1.47 to 1 for links in dark mode, a third of the floor.
 */

type Rgba = [number, number, number, number]

function parse(colour: string): Rgba {
  if (colour.startsWith('#')) {
    const hex = colour.slice(1)
    const full = hex.length === 3 ? hex.split('').map((c) => c + c).join('') : hex
    return [0, 2, 4].map((i) => parseInt(full.slice(i, i + 2), 16)).concat(1) as Rgba
  }
  const parts = colour.match(/[\d.]+/g)!.map(Number)
  return [parts[0], parts[1], parts[2], parts[3] ?? 1]
}

function composite(fg: Rgba, bg: Rgba): [number, number, number] {
  return [0, 1, 2].map((i) => fg[i] * fg[3] + bg[i] * (1 - fg[3])) as [number, number, number]
}

function luminance([r, g, b]: [number, number, number]): number {
  const channel = (v: number) => {
    const s = v / 255
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4
  }
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
}

export function contrast(foreground: string, background: string): number {
  const bg = parse(background)
  const fg = composite(parse(foreground), bg)
  const a = luminance(fg)
  const b = luminance([bg[0], bg[1], bg[2]])
  return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05)
}

const TEXT = 4.5
const INTERFACE = 3

const themes = [
  { name: 'light', algorithm: theme.defaultAlgorithm, seed: lightSeed, label: primaryButtonLabel.light },
  { name: 'dark', algorithm: theme.darkAlgorithm, seed: darkSeed, label: primaryButtonLabel.dark },
] as const

describe.each(themes)('$name theme contrast', ({ algorithm, seed, label }) => {
  const t = theme.getDesignToken({ algorithm, token: { ...sharedTokens, ...seed } })

  it.each([
    ['body text on surface', () => t.colorText, () => t.colorBgContainer, TEXT],
    ['secondary text on surface', () => t.colorTextSecondary, () => t.colorBgContainer, TEXT],
    ['secondary text on page background', () => t.colorTextSecondary, () => t.colorBgLayout, TEXT],
    // Typography type="secondary" paints with colorTextDescription, not colorTextSecondary, and other
    // components use colorTextTertiary. The gate originally measured only colorTextSecondary and passed
    // while rendered secondary text measured 3.31 in Chromium. Measure the tokens components actually use.
    ['description text on surface', () => t.colorTextDescription, () => t.colorBgContainer, TEXT],
    ['description text on page background', () => t.colorTextDescription, () => t.colorBgLayout, TEXT],
    ['tertiary text on surface', () => t.colorTextTertiary, () => t.colorBgContainer, TEXT],
    ['tertiary text on page background', () => t.colorTextTertiary, () => t.colorBgLayout, TEXT],
    ['link on surface', () => t.colorLink, () => t.colorBgContainer, TEXT],
    ['label on primary button', () => label, () => t.colorPrimary, TEXT],
    ['primary as an interface component', () => t.colorPrimary, () => t.colorBgContainer, INTERFACE],
    ['error text on surface', () => t.colorErrorText, () => t.colorBgContainer, TEXT],
    ['success text on surface', () => t.colorSuccessText, () => t.colorBgContainer, TEXT],
    ['warning text on surface', () => t.colorWarningText, () => t.colorBgContainer, TEXT],
    ['input border on surface', () => t.colorBorder, () => t.colorBgContainer, INTERFACE],
  ])('%s meets its floor', (_pair, fg, bg, floor) => {
    expect(contrast(fg(), bg())).toBeGreaterThanOrEqual(floor)
  })
})

describe('palette rules', () => {
  it('the primary button label flips polarity between themes, because white fails on the dark accent', () => {
    expect(primaryButtonLabel.light).toBe('#ffffff')
    expect(primaryButtonLabel.dark).not.toBe('#ffffff')
  })

  it('the naive palette really does fail, so this gate is testing something', () => {
    const naive = theme.getDesignToken({ algorithm: theme.darkAlgorithm, token: { colorPrimary: '#1f3a5f' } })
    expect(contrast(naive.colorPrimary, naive.colorBgContainer)).toBeLessThan(INTERFACE)
  })
})
