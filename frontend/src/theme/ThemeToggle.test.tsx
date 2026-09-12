import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { setSystemColorScheme } from '../test/setup'
import { THEME_STORAGE_KEY, ThemeProvider } from './ThemeProvider'
import { ThemeToggle } from './ThemeToggle'

/**
 * antd's Segmented hides the native radio behind `pointer-events: none`, so a person clicks the visible
 * label. Tests do the same rather than reaching past it.
 */
function option(name: string) {
  return screen.getByRole('radio', { name }).closest('label') as HTMLElement
}

function mount() {
  return render(
    <ThemeProvider>
      <ThemeToggle />
    </ThemeProvider>,
  )
}

describe('ThemeToggle', () => {
  it('offers System, Light, and Dark by name', () => {
    mount()
    expect(screen.getByRole('radio', { name: 'System' })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: 'Light' })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: 'Dark' })).toBeInTheDocument()
  })

  it('gives each option an icon without letting the icon into its accessible name', () => {
    mount()
    const group = screen.getByRole('radiogroup', { name: 'Theme' })
    // Three decorative icons, hidden from assistive technology so the names stay "Light", not "sun Light".
    expect(group.querySelectorAll('svg')).toHaveLength(3)
    expect(group.querySelectorAll('[role="img"]:not([aria-hidden="true"])')).toHaveLength(0)
  })

  it('starts on System when nothing is stored', () => {
    setSystemColorScheme('dark')
    mount()
    expect(screen.getByRole('radio', { name: 'System' })).toBeChecked()
  })

  it('writes an explicit choice to storage', async () => {
    const user = userEvent.setup()
    mount()
    await user.click(option('Dark'))
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark')
    expect(screen.getByRole('radio', { name: 'Dark' })).toBeChecked()
  })

  it('removes the key when the learner goes back to System, rather than storing "system" (FR-012)', async () => {
    const user = userEvent.setup()
    localStorage.setItem(THEME_STORAGE_KEY, 'light')
    mount()
    await user.click(option('System'))
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBeNull()
  })

  it('is operable by keyboard alone (FR-015)', async () => {
    const user = userEvent.setup()
    mount()
    // One Tab lands on the checked radio, and the arrow keys move the selection: the radiogroup pattern.
    // antd would otherwise put a dead tab stop on the group first, where the arrows do nothing.
    await user.tab()
    expect(screen.getByRole('radio', { name: 'System' })).toHaveFocus()
    await user.keyboard('{ArrowRight}')
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('light')
  })
})
