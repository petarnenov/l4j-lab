import { act, render, screen } from '@testing-library/react'
import { theme } from 'antd'
import { describe, expect, it } from 'vitest'
import { blockStorage, setReducedMotion, setSystemColorScheme } from '../test/setup'
import { THEME_STORAGE_KEY, ThemeProvider } from './ThemeProvider'
import { useTheme } from './useTheme'

/** Renders the resolved state and the token antd actually applied, and records every render's theme. */
function Probe({ renders }: { renders?: string[] }) {
  const { preference, effective, reducedMotion } = useTheme()
  const { token } = theme.useToken()
  renders?.push(effective)
  return (
    <dl>
      <dt>preference</dt>
      <dd data-testid="preference">{preference}</dd>
      <dt>effective</dt>
      <dd data-testid="effective">{effective}</dd>
      <dt>surface</dt>
      <dd data-testid="surface">{token.colorBgContainer}</dd>
      <dt>motion</dt>
      <dd data-testid="motion">{String(token.motion)}</dd>
      <dt>reduced motion</dt>
      <dd data-testid="reduced">{String(reducedMotion)}</dd>
    </dl>
  )
}

function mount(renders?: string[]) {
  return render(
    <ThemeProvider>
      <Probe renders={renders} />
    </ThemeProvider>,
  )
}

const effective = () => screen.getByTestId('effective').textContent
const preference = () => screen.getByTestId('preference').textContent

describe('ThemeProvider resolves the effective theme (data-model.md)', () => {
  it('follows a dark operating system when nothing is stored', () => {
    setSystemColorScheme('dark')
    mount()
    expect(preference()).toBe('system')
    expect(effective()).toBe('dark')
  })

  it('falls back to light when the operating system reports nothing (FR-010)', () => {
    setSystemColorScheme(null)
    mount()
    expect(effective()).toBe('light')
  })

  it('lets a stored explicit choice override the operating system (FR-012)', () => {
    setSystemColorScheme('dark')
    localStorage.setItem(THEME_STORAGE_KEY, 'light')
    mount()
    expect(preference()).toBe('light')
    expect(effective()).toBe('light')
  })

  it('follows an operating system change live while the preference is system', () => {
    setSystemColorScheme('light')
    mount()
    expect(effective()).toBe('light')

    act(() => setSystemColorScheme('dark'))
    expect(effective()).toBe('dark')
  })

  it('ignores an operating system change once the learner has chosen', () => {
    setSystemColorScheme('light')
    localStorage.setItem(THEME_STORAGE_KEY, 'dark')
    mount()

    act(() => setSystemColorScheme('light'))
    expect(effective()).toBe('dark')
  })

  it('treats an unrecognised stored value as system rather than failing to render', () => {
    setSystemColorScheme('dark')
    localStorage.setItem(THEME_STORAGE_KEY, 'purple')
    mount()
    expect(preference()).toBe('system')
    expect(effective()).toBe('dark')
  })

  it('treats a literal stored "system" as system, since the key is only ever light or dark', () => {
    setSystemColorScheme('light')
    localStorage.setItem(THEME_STORAGE_KEY, 'system')
    mount()
    expect(preference()).toBe('system')
  })

  it('still renders, following the system, when storage is blocked', () => {
    setSystemColorScheme('dark')
    blockStorage()
    mount()
    expect(preference()).toBe('system')
    expect(effective()).toBe('dark')
  })

  it('applies the dark palette to antd, not just a label', () => {
    setSystemColorScheme('dark')
    mount()
    expect(screen.getByTestId('surface').textContent).toBe('#141414')
  })
})

describe('ThemeProvider first paint (research R-002)', () => {
  it('is already correct on the first render, with no light-to-dark correction after mount', () => {
    setSystemColorScheme('dark')
    const renders: string[] = []
    mount(renders)
    expect(renders[0]).toBe('dark')
    expect(renders).not.toContain('light')
  })

  it('reads a stored choice on the first render too', () => {
    setSystemColorScheme('light')
    localStorage.setItem(THEME_STORAGE_KEY, 'dark')
    const renders: string[] = []
    mount(renders)
    expect(renders[0]).toBe('dark')
  })
})

describe('ThemeProvider reduced motion (FR-018, research R-009)', () => {
  it('turns antd motion off when the system asks for reduced motion', () => {
    setReducedMotion(true)
    mount()
    expect(screen.getByTestId('motion').textContent).toBe('false')
    expect(screen.getByTestId('reduced').textContent).toBe('true')
  })

  it('leaves motion on otherwise', () => {
    mount()
    expect(screen.getByTestId('motion').textContent).toBe('true')
  })

  it('follows a change to the setting while open', () => {
    mount()
    act(() => setReducedMotion(true))
    expect(screen.getByTestId('motion').textContent).toBe('false')
  })
})
