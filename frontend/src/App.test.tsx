import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { App } from './App'
import { NodeTimeline } from './components/NodeTimeline'
import { RunHistory } from './components/RunHistory'
import { nodes } from './test/handlers'
import { renderWithQuery } from './test/render'

describe('App shell', () => {
  it('puts the navigation inside a page header', () => {
    renderWithQuery(<App />)

    const header = screen.getByRole('banner')
    const nav = within(header).getByRole('navigation', { name: 'Main' })
    expect(within(nav).getByText('New run')).toBeInTheDocument()
    expect(within(nav).getByText('Previous runs')).toBeInTheDocument()
  })

  it('renders the page content inside the main landmark', () => {
    renderWithQuery(<App />)
    expect(
      within(screen.getByRole('main')).getByRole('heading', { name: 'Run the chain' }),
    ).toBeInTheDocument()
  })

  it('constrains the content to a centred container rather than the full viewport (FR-005)', () => {
    renderWithQuery(<App />)

    const container = screen.getByRole('main').firstElementChild as HTMLElement
    expect(container).toHaveStyle({ marginLeft: 'auto', marginRight: 'auto' })
    expect(container.style.maxWidth).not.toBe('')
  })

  it('exposes the launcher as a named region', () => {
    renderWithQuery(<App />)
    expect(screen.getByRole('region', { name: 'Run the chain' })).toBeInTheDocument()
  })
})

/**
 * FR-015 and SC-006: every control reachable and operable without a pointing device, in both themes.
 * Whether the focus ring is visible is a painting question jsdom cannot answer; T057 checks it in Chromium.
 */
describe.each(['light', 'dark'] as const)('keyboard access in the %s theme', (theme) => {
  async function tabUntil(user: ReturnType<typeof userEvent.setup>, target: Element, limit = 20) {
    for (let i = 0; i < limit && document.activeElement !== target; i++) await user.tab()
    return document.activeElement === target
  }

  it('reaches the navigation, the theme control, both selects, and the start button', async () => {
    const user = userEvent.setup()
    renderWithQuery(<App />, { theme })
    const company = await screen.findByRole('combobox', { name: 'Company' })

    expect(await tabUntil(user, screen.getByRole('menu'))).toBe(true)
    // Only the checked radio of a group is a tab stop; the arrow keys move within it. This test runs with a
    // stored preference, so that is the option matching the theme, not System.
    const checkedTheme = screen.getByRole('radio', { name: theme === 'dark' ? 'Dark' : 'Light' })
    expect(await tabUntil(user, checkedTheme)).toBe(true)
    expect(await tabUntil(user, company)).toBe(true)
    expect(await tabUntil(user, screen.getByRole('combobox', { name: 'Reporting period' }))).toBe(
      true,
    )
    const start = screen.getByRole('button', { name: 'Run the chain' })
    await waitFor(() => expect(start).toBeEnabled())
    expect(await tabUntil(user, start)).toBe(true)
  })

  it('exposes the navigation as a keyboard menu with a tab stop and named items', async () => {
    // Moving through the menu with the arrow keys cannot be exercised here: rc-menu decides which items are
    // focusable from their rendered size, and jsdom performs no layout, so the arrows never move. Arrow keys
    // followed by Enter were verified to switch screens in Chromium, and are re-checked in T057.
    renderWithQuery(<App />, { theme })
    const menu = screen.getByRole('menu')
    expect(menu).toHaveAttribute('tabindex', '0')
    expect(within(menu).getByRole('menuitem', { name: 'New run' })).toBeInTheDocument()
    expect(within(menu).getByRole('menuitem', { name: 'Previous runs' })).toBeInTheDocument()
  })

  it('opens a history row from the keyboard', async () => {
    const user = userEvent.setup()
    const opened: string[] = []
    renderWithQuery(<RunHistory onOpen={(id) => opened.push(id)} />, { theme })
    const row = await screen.findByRole('button', { name: /Northwind Lighting/ })

    await tabUntil(user, row)
    await user.keyboard('{Enter}')
    expect(opened).toEqual(['11111111-1111-1111-1111-111111111111'])
  })

  it('steps between node boundaries from the keyboard', async () => {
    const user = userEvent.setup()
    renderWithQuery(<NodeTimeline nodes={nodes()} />, { theme })
    const summarize = within(screen.getByRole('group', { name: 'Node steps' }))
      .getAllByRole('button')
      .find((s) => /Summarize/.test(s.textContent ?? ''))!

    await tabUntil(user, summarize)
    await user.keyboard('{Enter}')
    expect(screen.getByRole('heading', { name: '4. Summarize' })).toBeInTheDocument()
  })
})
