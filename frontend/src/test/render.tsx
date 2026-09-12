import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { THEME_STORAGE_KEY, ThemeProvider } from '../theme/ThemeProvider'

export type TestTheme = 'light' | 'dark'

/**
 * Renders with a fresh query client per test, inside the real ThemeProvider, so every antd component has
 * its context and every test runs in the real palette. A requested theme is set the way a learner sets it,
 * through the stored preference, so the same resolution path that runs in production runs here.
 */
export function renderWithQuery(ui: ReactElement, options: { theme?: TestTheme } = {}) {
  if (options.theme) {
    window.localStorage.setItem(THEME_STORAGE_KEY, options.theme)
  }
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>{ui}</ThemeProvider>
    </QueryClientProvider>,
  )
}
