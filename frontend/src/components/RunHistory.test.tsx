import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { renderWithQuery } from '../test/render'
import { RunHistory } from './RunHistory'

/**
 * Rewritten so runs are found by company and period text rather than by listitem and button counts,
 * which antd's List renders differently. Every fact the original asserted is still asserted.
 */

async function runRow(company: RegExp) {
  return (await screen.findByRole('button', { name: company })) as HTMLElement
}

describe('RunHistory', () => {
  it('lists previous runs newest first with company, period, status and time', async () => {
    renderWithQuery(<RunHistory onOpen={vi.fn()} />)

    const first = await runRow(/Northwind Lighting/)
    const second = await runRow(/Harbor Foods/)

    expect(first).toHaveTextContent('2025-Q2')
    expect(first).toHaveTextContent('SUCCEEDED')
    expect(second).toHaveTextContent('FAILED')
    // Newest first: the Northwind run started later and must come earlier in the document.
    expect(first.compareDocumentPosition(second) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })

  it('shows each outcome with an icon, not colour alone (FR-008)', async () => {
    renderWithQuery(<RunHistory onOpen={vi.fn()} />)
    const first = await runRow(/Northwind Lighting/)
    expect(within(first).getByRole('img')).toBeInTheDocument()
  })

  it('opens a run when its row is clicked', async () => {
    const user = userEvent.setup()
    const onOpen = vi.fn()
    renderWithQuery(<RunHistory onOpen={onOpen} />)

    await user.click(await runRow(/Northwind Lighting/))

    expect(onOpen).toHaveBeenCalledWith('11111111-1111-1111-1111-111111111111')
  })

  it('shows a summary preview only where one exists', async () => {
    renderWithQuery(<RunHistory onOpen={vi.fn()} />)

    const first = await runRow(/Northwind Lighting/)
    const second = await runRow(/Harbor Foods/)
    expect(first).toHaveTextContent('Revenue grew 0.2500')
    expect(second).not.toHaveTextContent('Revenue grew')
  })
})
