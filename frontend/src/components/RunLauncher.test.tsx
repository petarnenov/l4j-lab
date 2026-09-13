import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { renderWithQuery } from '../test/render'
import { setViewportWidth } from '../test/setup'
import { RunLauncher } from './RunLauncher'

/**
 * Rewritten for antd's Select, which renders no native <option>; its list exists only while open. Every
 * fact the original asserted is still asserted: both companies listed, periods narrowed to the selected
 * company, and starting a run handing back the identifier.
 */

async function openAndReadOptions(label: string) {
  const user = userEvent.setup()
  const combobox = screen.getByRole('combobox', { name: label })
  await user.click(combobox)
  await screen.findAllByRole('listbox')
  return { user }
}

function optionTitles() {
  return Array.from(document.querySelectorAll('.ant-select-item-option')).map((o) =>
    o.getAttribute('title'),
  )
}

describe('RunLauncher', () => {
  it('labels both controls so they can be found by name (FR-007)', async () => {
    renderWithQuery(<RunLauncher onStarted={vi.fn()} />)

    expect(await screen.findByRole('combobox', { name: 'Company' })).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Reporting period' })).toBeInTheDocument()
  })

  it('lists both companies from the catalog', async () => {
    renderWithQuery(<RunLauncher onStarted={vi.fn()} />)
    await screen.findByRole('combobox', { name: 'Company' })

    await openAndReadOptions('Company')

    await waitFor(() =>
      expect(optionTitles()).toEqual(
        expect.arrayContaining(['Northwind Lighting (fictional)', 'Harbor Foods (fictional)']),
      ),
    )
  })

  it('narrows the periods to the selected company', async () => {
    renderWithQuery(<RunLauncher onStarted={vi.fn()} />)
    await screen.findByRole('combobox', { name: 'Company' })

    const { user } = await openAndReadOptions('Company')
    await waitFor(() => expect(optionTitles()).toContain('Harbor Foods (fictional)'))
    await user.click(
      document.querySelector('.ant-select-item-option[title="Harbor Foods (fictional)"]')!,
    )

    await openAndReadOptions('Reporting period')
    await waitFor(() => expect(optionTitles()).toContain('2024-Q2'))
    expect(optionTitles()).not.toContain('2025-Q2')
  })

  it('starts a run and hands the identifier back', async () => {
    const user = userEvent.setup()
    const onStarted = vi.fn()
    renderWithQuery(<RunLauncher onStarted={onStarted} />)

    const button = await screen.findByRole('button', { name: 'Run the chain' })
    await waitFor(() => expect(button).toBeEnabled())
    await user.click(button)

    await waitFor(() =>
      expect(onStarted).toHaveBeenCalledWith('11111111-1111-1111-1111-111111111111'),
    )
  })

  it('groups the controls into one form', async () => {
    renderWithQuery(<RunLauncher onStarted={vi.fn()} />)
    const form = await screen.findByRole('form', { name: 'Start a run' })
    expect(within(form).getByRole('button', { name: 'Run the chain' })).toBeInTheDocument()
  })

  it('stacks its controls in one full-width column below 768 pixels, and stays operable', async () => {
    setViewportWidth(375)
    const onStarted = vi.fn()
    const user = userEvent.setup()
    renderWithQuery(<RunLauncher onStarted={onStarted} />)

    const form = await screen.findByRole('form', { name: 'Start a run' })
    await waitFor(() => expect(form.firstElementChild).toHaveStyle({ flexDirection: 'column' }))

    const button = within(form).getByRole('button', { name: 'Run the chain' })
    await waitFor(() => expect(button).toBeEnabled())
    await user.click(button)
    await waitFor(() => expect(onStarted).toHaveBeenCalled())
  })

  it('keeps its controls in one row from 768 pixels', async () => {
    setViewportWidth(1024)
    renderWithQuery(<RunLauncher onStarted={vi.fn()} />)
    const form = await screen.findByRole('form', { name: 'Start a run' })
    expect(form.firstElementChild).toHaveStyle({ flexDirection: 'row' })
  })
})
