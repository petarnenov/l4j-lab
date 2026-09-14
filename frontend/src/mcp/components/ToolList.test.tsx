import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { renderWithQuery } from '../../test/render'
import { TOOL_CONTRACTS } from '../test/mcpHandlers'
import { ToolList } from './ToolList'

/**
 * T021 (FR-007, US1-1). The declarations come from the committed contracts, so a hint flipped there
 * changes what this test sees, in the same commit.
 */
describe('the tools the server offers', () => {
  it('lists them in the order the server returned them, never sorted', () => {
    renderWithQuery(<ToolList tools={TOOL_CONTRACTS} selected={null} onSelect={vi.fn()} />)

    const names = screen
      .getAllByRole('button', { name: /^Select tool/ })
      .map((button) => button.getAttribute('data-tool'))

    expect(names).toEqual([
      'search_billing_runs',
      'get_billing_run_status',
      'get_run_failures',
      'post_fee_adjustment',
      'start_billing_run',
    ])
  })

  it('shows all four declared behaviours for a read-only tool', () => {
    renderWithQuery(<ToolList tools={TOOL_CONTRACTS} selected={null} onSelect={vi.fn()} />)

    const entry = screen.getByRole('button', { name: /^Select tool search_billing_runs/ })
    expect(within(entry).getByText('read-only')).toBeInTheDocument()
    expect(within(entry).getByText('non-destructive')).toBeInTheDocument()
    expect(within(entry).getByText('idempotent')).toBeInTheDocument()
    expect(within(entry).getByText('open world')).toBeInTheDocument()
  })

  it('shows the opposite of each for the tool that writes and destroys', () => {
    renderWithQuery(<ToolList tools={TOOL_CONTRACTS} selected={null} onSelect={vi.fn()} />)

    const entry = screen.getByRole('button', { name: /^Select tool post_fee_adjustment/ })
    expect(within(entry).getByText('writes')).toBeInTheDocument()
    expect(within(entry).getByText('destructive')).toBeInTheDocument()
    // Idempotent *because* of operation_id, not in spite of the write. The hint would be a lie
    // without the idempotency record, which is why the console shows it rather than inferring it.
    expect(within(entry).getByText('idempotent')).toBeInTheDocument()
  })

  it('shows that starting a run is not idempotent', () => {
    renderWithQuery(<ToolList tools={TOOL_CONTRACTS} selected={null} onSelect={vi.fn()} />)

    const entry = screen.getByRole('button', { name: /^Select tool start_billing_run/ })
    expect(within(entry).getByText('not idempotent')).toBeInTheDocument()
    expect(within(entry).getByText('non-destructive')).toBeInTheDocument()
  })

  it('shows each title and description as the server wrote them', () => {
    renderWithQuery(<ToolList tools={TOOL_CONTRACTS} selected={null} onSelect={vi.fn()} />)

    expect(screen.getByText('Search billing runs')).toBeInTheDocument()
    expect(screen.getByText(TOOL_CONTRACTS[0].description)).toBeInTheDocument()
  })

  it('reports which tool was chosen', async () => {
    const user = userEvent.setup()
    const onSelect = vi.fn()
    renderWithQuery(<ToolList tools={TOOL_CONTRACTS} selected={null} onSelect={onSelect} />)

    await user.click(screen.getByRole('button', { name: /^Select tool get_run_failures/ }))
    expect(onSelect).toHaveBeenCalledWith('get_run_failures')
  })
})
