import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { renderWithQuery } from '../../test/render'
import { PageCursor, type SearchPage } from './PageCursor'

function page(index: number, overrides: Partial<SearchPage> = {}): SearchPage {
  return {
    targetId: 'a',
    runs: [{ run_id: `run-${index}`, executed_by_advisor_id: 'adv-101', status: 'COMPLETED' }],
    totalMatchCount: 24,
    truncated: true,
    nextCursor: `cursor-${index}`,
    refineHint: 'Narrow by advisor_id or a date range.',
    ...overrides,
  }
}

/**
 * T052 (FR-015, US4-4, SC-005).
 */
describe('carrying a page cursor', () => {
  it('offers the next page without anyone copying a cursor', async () => {
    const user = userEvent.setup()
    const onNext = vi.fn()
    renderWithQuery(<PageCursor pages={[page(1)]} onNextPage={onNext} pending={false} />)

    await user.click(screen.getByRole('button', { name: /next page/i }))
    expect(onNext).toHaveBeenCalledWith('cursor-1')
  })

  it('says more results remain, in the server own count and hint', () => {
    renderWithQuery(<PageCursor pages={[page(1)]} onNextPage={vi.fn()} pending={false} />)

    const region = screen.getByRole('region', { name: /pages/i })
    expect(region).toHaveTextContent('24')
    expect(region).toHaveTextContent('Narrow by advisor_id or a date range.')
  })

  it('never shows the cursor as though it meant something', () => {
    renderWithQuery(<PageCursor pages={[page(1)]} onNextPage={vi.fn()} pending={false} />)

    expect(screen.getByRole('region', { name: /pages/i })).not.toHaveTextContent('cursor-1')
  })

  it('stacks pages rather than replacing them, so absence of overlap is visible', () => {
    renderWithQuery(
      <PageCursor
        pages={[page(1), { ...page(2), targetId: 'b' }]}
        onNextPage={vi.fn()}
        pending={false}
      />,
    )

    const region = screen.getByRole('region', { name: /pages/i })
    expect(region).toHaveTextContent('run-1')
    expect(region).toHaveTextContent('run-2')
  })

  it('names the target each page came from, which is how the cross-replica hop is seen', () => {
    renderWithQuery(
      <PageCursor
        pages={[page(1), { ...page(2), targetId: 'b' }]}
        onNextPage={vi.fn()}
        pending={false}
      />,
    )

    const region = screen.getByRole('region', { name: /pages/i })
    expect(region).toHaveTextContent('mcp-a')
    expect(region).toHaveTextContent('mcp-b')
  })

  it('stops offering a next page when no more results remain', () => {
    renderWithQuery(
      <PageCursor
        pages={[page(1, { truncated: false, nextCursor: undefined, refineHint: undefined })]}
        onNextPage={vi.fn()}
        pending={false}
      />,
    )

    expect(screen.queryByRole('button', { name: /next page/i })).not.toBeInTheDocument()
    expect(screen.getByRole('region', { name: /pages/i })).toHaveTextContent(/no more/i)
  })
})
