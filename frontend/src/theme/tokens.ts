import type { ThemeConfig } from 'antd'
import { theme } from 'antd'

/**
 * The single source of every colour, radius, and type value in the interface (FR-004). Values and their
 * measured contrast ratios are the contract in specs/002-corporate-ui-redesign/contracts/design-tokens.md.
 * Change a value here and tokens.contrast.test.ts re-measures it.
 */

export type EffectiveTheme = 'light' | 'dark'

export const sharedTokens = {
  borderRadius: 4,
  fontFamily: 'system-ui, -apple-system, "Segoe UI", Roboto, sans-serif',
}

/** Measured against the light surface: link 11.48, error text 7.75, input border 3.69. */
export const lightSeed = {
  colorPrimary: '#1f3a5f',
  colorLink: '#1f3a5f',
  colorErrorText: '#a8071a',
  colorSuccessText: '#237804',
  colorWarningText: '#874d00',
  colorBorder: '#858585',
  // antd's default is 45% opacity, which renders secondary typography at 3.31 to 1 on the page background.
  // 56% is the faintest that clears 4.5 with margin, and stays visibly lighter than secondary text at 65%.
  colorTextDescription: 'rgba(0,0,0,0.56)',
  colorTextTertiary: 'rgba(0,0,0,0.56)',
}

/**
 * Deliberately not the light seed run through the dark algorithm. That algorithm darkens the accent, and
 * the navy above darkened onto the near-black surface measured 1.47 to 1 for links: a third of the
 * floor, and effectively invisible on a projector. Each theme therefore has its own seed.
 */
export const darkSeed = {
  colorPrimary: '#6f9fd8',
  colorLink: '#8fb6e3',
  colorErrorText: '#ff7875',
  colorSuccessText: '#73d13d',
  colorWarningText: '#ffc53d',
  colorBorder: '#707070',
  // Default 45% measured 4.41 on the black page background. 50% clears 4.5 with margin.
  colorTextDescription: 'rgba(255,255,255,0.50)',
  colorTextTertiary: 'rgba(255,255,255,0.50)',
}

/**
 * The one token whose polarity flips. White on the dark accent measures below 4.5, so the dark theme's
 * primary button carries near-black text instead. Set explicitly; never inherit it.
 */
export const primaryButtonLabel: Record<EffectiveTheme, string> = {
  light: '#ffffff',
  dark: 'rgba(0,0,0,0.88)',
}

export function themeConfig(effective: EffectiveTheme, reducedMotion = false): ThemeConfig {
  const dark = effective === 'dark'
  return {
    algorithm: dark ? theme.darkAlgorithm : theme.defaultAlgorithm,
    token: {
      ...sharedTokens,
      ...(dark ? darkSeed : lightSeed),
      // FR-018: antd animates by default; a learner who asked for less motion gets none.
      motion: !reducedMotion,
    },
    components: {
      Button: { primaryColor: primaryButtonLabel[effective] },
    },
  }
}
