import { createContext, useContext } from 'react'
import type { EffectiveTheme } from './tokens'

/** `system` means the learner has not chosen, and the operating system decides. */
export type ThemePreference = 'system' | 'light' | 'dark'

export interface ThemeState {
  preference: ThemePreference
  effective: EffectiveTheme
  /** The learner's system asks for less motion. Animations and spinners honour it (FR-018). */
  reducedMotion: boolean
  setPreference: (next: ThemePreference) => void
}

export const ThemeContext = createContext<ThemeState | null>(null)

/**
 * The one way a component learns the theme. Nothing reads localStorage or the media query directly, so the
 * resolution rules in data-model.md live in exactly one place: ThemeProvider.
 */
export function useTheme(): ThemeState {
  const state = useContext(ThemeContext)
  if (!state) throw new Error('useTheme must be used inside ThemeProvider')
  return state
}
