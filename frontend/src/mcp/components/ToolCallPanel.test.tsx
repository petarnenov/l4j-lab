import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { renderWithQuery } from '../../test/render'
import { TOOL_CONTRACTS } from '../test/mcpHandlers'
import { ToolCallPanel } from './ToolCallPanel'

const search = TOOL_CONTRACTS[0]

/**
 * T023 (FR-009). The console does not send a call it can already tell is incomplete — a refusal the
 * server would have produced anyway is a worse way to learn a field was empty.
 */
describe('calling a tool', () => {
  it('will not send while a required argument is empty', () => {
    renderWithQuery(<ToolCallPanel tool={search} onCall={vi.fn()} pending={false} />)

    expect(screen.getByRole('button', { name: /^Call search_billing_runs/ })).toBeDisabled()
  })

  it('says which argument is still missing rather than only refusing', async () => {
    renderWithQuery(<ToolCallPanel tool={search} onCall={vi.fn()} pending={false} />)

    const missing = await screen.findByRole('region', { name: 'Missing arguments' })
    expect(within(missing).getByText(/firm_id/)).toBeInTheDocument()
  })

  it('sends once every required argument is filled', async () => {
    const user = userEvent.setup()
    const onCall = vi.fn()
    renderWithQuery(<ToolCallPanel tool={search} onCall={onCall} pending={false} />)

    await user.type(screen.getByLabelText(/^firm_id/), 'firm-alpha')
    const call = screen.getByRole('button', { name: /^Call search_billing_runs/ })
    await waitFor(() => expect(call).toBeEnabled())
    await user.click(call)

    // page_size comes with it because the server declared `default: 20` and the field is visibly
    // prefilled with it. Sending what is on the screen is the honest thing; dropping it would mean
    // the request differed from what a person could see.
    expect(onCall).toHaveBeenCalledWith({ firm_id: 'firm-alpha', page_size: 20 })
  })

  it('omits an untouched optional argument rather than sending it empty', async () => {
    const user = userEvent.setup()
    const onCall = vi.fn()
    renderWithQuery(<ToolCallPanel tool={search} onCall={onCall} pending={false} />)

    await user.type(screen.getByLabelText(/^firm_id/), 'firm-alpha')
    await waitFor(() =>
      expect(screen.getByRole('button', { name: /^Call search_billing_runs/ })).toBeEnabled(),
    )
    await user.click(screen.getByRole('button', { name: /^Call search_billing_runs/ }))

    expect(Object.keys(onCall.mock.calls[0][0])).not.toContain('advisor_id')
    expect(Object.keys(onCall.mock.calls[0][0])).not.toContain('status')
  })

  it('will not send twice while a call is in flight', async () => {
    renderWithQuery(
      <ToolCallPanel tool={search} onCall={vi.fn()} pending values={{ firm_id: 'f' }} />,
    )

    expect(screen.getByRole('button', { name: /^Call search_billing_runs/ })).toBeDisabled()
  })
})
