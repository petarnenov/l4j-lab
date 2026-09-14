import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { server } from '../test/handlers'
import { renderWithQuery } from '../test/render'
import McpConsolePage from './McpConsolePage'
import { mcpHandlers, toolResult } from './test/mcpHandlers'

/**
 * T036 (FR-006). Switching principal affects only subsequent calls, and a result already on screen
 * keeps the principal it was obtained as. A result whose identity is unknown is worse than no
 * result: the whole point of US2 is that identity decides what comes back.
 */
/**
 * The log holds every call, server/discover and tools/list included — that is what makes a task
 * poll landing on another replica visible later. So a test about a search must find the search
 * rather than assume it is the newest thing in the list.
 */
function searchExchange() {
  return screen
    .getAllByRole('region', { name: /attribution/i })
    .find((region) => region.textContent?.includes('search_billing_runs'))
}

async function callSearchAs(user: ReturnType<typeof userEvent.setup>, firmId: string) {
  await user.click(await screen.findByRole('button', { name: /^Select tool search_billing_runs/ }))
  await user.type(await screen.findByLabelText(/^firm_id/), firmId)
  const call = screen.getByRole('button', { name: /^Call search_billing_runs/ })
  await waitFor(() => expect(call).toBeEnabled())
  await user.click(call)
}

describe('switching principal', () => {
  it('labels each result with the principal it was obtained as, and keeps it after a switch', async () => {
    server.use(
      ...mcpHandlers({
        onToolCall: (args) =>
          toolResult({ runs: [], total_match_count: 0, truncated: false, echo: args.firm_id }),
      }),
    )
    const user = userEvent.setup()
    renderWithQuery(<McpConsolePage />)

    await callSearchAs(user, 'firm-alpha')
    await waitFor(() => expect(searchExchange()).toBeDefined())
    expect(searchExchange()).toHaveTextContent('as admin-alpha')

    await user.click(screen.getByLabelText('Acting as'))
    await user.click(within(await screen.findByRole('listbox')).getByText('advisor-alpha-101'))

    // The earlier exchange still says who it was made as. Nothing is relabelled retrospectively,
    // even though the switch does re-read the server's own description as the new principal.
    await waitFor(() => expect(screen.getByLabelText('Acting as')).toBeInTheDocument())
    expect(searchExchange()).toHaveTextContent('as admin-alpha')
  })

  it('uses the new principal for the next call', async () => {
    server.use(...mcpHandlers({ onToolCall: () => toolResult({ runs: [], total_match_count: 0 }) }))
    const user = userEvent.setup()
    renderWithQuery(<McpConsolePage />)

    await screen.findByRole('button', { name: /^Select tool search_billing_runs/ })
    await user.click(screen.getByLabelText('Acting as'))
    await user.click(within(await screen.findByRole('listbox')).getByText('advisor-alpha-101'))

    await callSearchAs(user, 'firm-alpha')

    await waitFor(() => expect(searchExchange()).toBeDefined())
    expect(searchExchange()).toHaveTextContent('as advisor-alpha-101')
  })
})
