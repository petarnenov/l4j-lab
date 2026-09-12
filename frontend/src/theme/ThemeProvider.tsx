import { ConfigProvider } from 'antd'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { themeConfig } from './tokens'
import { ThemeContext, type ThemePreference } from './useTheme'

export const THEME_STORAGE_KEY = 'l4j.theme'

const DARK_QUERY = '(prefers-color-scheme: dark)'
const REDUCED_MOTION_QUERY = '(prefers-reduced-motion: reduce)'

/**
 * Only `light` and `dark` are ever stored. `system` is the key's absence. Anything else, including a
 * literal "system" from an earlier build or a hand-edited value, is treated as not chosen.
 */
function readStoredPreference(): ThemePreference {
  try {
    const stored = window.localStorage.getItem(THEME_STORAGE_KEY)
    return stored === 'light' || stored === 'dark' ? stored : 'system'
  } catch {
    // Blocked storage, as in a private window. The application must still render (spec edge case).
    return 'system'
  }
}

function writeStoredPreference(next: ThemePreference) {
  try {
    if (next === 'system') window.localStorage.removeItem(THEME_STORAGE_KEY)
    else window.localStorage.setItem(THEME_STORAGE_KEY, next)
  } catch {
    // The choice simply does not persist. Nothing else breaks.
  }
}

function matches(query: string): boolean {
  try {
    return window.matchMedia(query).matches
  } catch {
    return false
  }
}

/**
 * Resolves the effective theme from the learner's preference and the operating system, and hands antd the
 * matching palette.
 *
 * A three-state preference is not optional. FR-010 wants the operating system followed until the learner
 * chooses; FR-012 wants a choice to win and persist. A two-state value cannot tell "follow the system" from
 * "I picked what the system happens to say", and gets the wrong answer the moment the system changes.
 *
 * Both values are read in the useState initialisers, synchronously, so the very first render is already
 * correct. Resolving them in an effect instead would paint light and then flip to dark.
 */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [preference, setPreferenceState] = useState<ThemePreference>(readStoredPreference)
  const [systemDark, setSystemDark] = useState<boolean>(() => matches(DARK_QUERY))
  const [reducedMotion, setReducedMotion] = useState<boolean>(() => matches(REDUCED_MOTION_QUERY))

  // Listen to the operating system only while it is the one deciding.
  useEffect(() => {
    if (preference !== 'system') return
    let query: MediaQueryList
    try {
      query = window.matchMedia(DARK_QUERY)
    } catch {
      return
    }
    const onChange = () => setSystemDark(query.matches)
    onChange()
    query.addEventListener('change', onChange)
    return () => query.removeEventListener('change', onChange)
  }, [preference])

  // Reduced motion is followed live regardless of the theme preference: it is a separate setting.
  useEffect(() => {
    let query: MediaQueryList
    try {
      query = window.matchMedia(REDUCED_MOTION_QUERY)
    } catch {
      return
    }
    const onChange = () => setReducedMotion(query.matches)
    onChange()
    query.addEventListener('change', onChange)
    return () => query.removeEventListener('change', onChange)
  }, [])

  const setPreference = useCallback((next: ThemePreference) => {
    setPreferenceState(next)
    writeStoredPreference(next)
  }, [])

  const effective = preference === 'system' ? (systemDark ? 'dark' : 'light') : preference

  const value = useMemo(
    () => ({ preference, effective, reducedMotion, setPreference }),
    [preference, effective, reducedMotion, setPreference],
  )
  const config = useMemo(() => themeConfig(effective, reducedMotion), [effective, reducedMotion])

  return (
    <ThemeContext.Provider value={value}>
      <ConfigProvider theme={config}>{children}</ConfigProvider>
    </ThemeContext.Provider>
  )
}
