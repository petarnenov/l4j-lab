import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { renderWithQuery } from '../../test/render'
import { RESET_COMMAND } from '../targets'
import type { CompleteResult, InputRequiredResult } from '../wire'
import { ElicitationPanel } from './ElicitationPanel'

const asked: InputRequiredResult = {
  resultType: 'input_required',
  requestState: 'opaque-sealed-payload',
  inputRequests: {
    confirm_adjustment: {
      method: 'elicitation/create',
      params: {
        mode: 'form',
        message:
          'Confirm: change account acc-0101 fee by +15 bps (1.00% → 1.15%), effective 2026-10-01.',
        requestedSchema: {
          type: 'object',
          properties: { confirmed: { type: 'boolean' } },
          required: ['confirmed'],
        },
      },
    },
  },
}

const applied: CompleteResult = {
  resultType: 'complete',
  isError: false,
  structuredContent: {
    operation_id: 'op-12345678',
    account_id: 'acc-0101',
    delta_bps: 15,
    effective_date: '2026-10-01',
    legacy_reference_id: 'adj-77',
    new_fee_bps: 115,
    confirmed_by_user_id: 'usr-900',
    replayed: false,
  },
}

/**
 * T041, T043, T044 (FR-012, FR-012a, FR-012b, US3).
 */
describe('when the server asks before it acts', () => {
  it('shows the question as the server phrased it', () => {
    renderWithQuery(
      <ElicitationPanel asked={asked} applied={null} onAnswer={vi.fn()} pending={false} />,
    )

    expect(
      screen.getByText(asked.inputRequests.confirm_adjustment.params.message),
    ).toBeInTheDocument()
  })

  it('states that nothing has been applied', () => {
    renderWithQuery(
      <ElicitationPanel asked={asked} applied={null} onAnswer={vi.fn()} pending={false} />,
    )

    expect(screen.getByRole('region', { name: /confirmation/i })).toHaveTextContent(
      /nothing has been applied/i,
    )
  })

  it('presents no result, because there is not one yet', () => {
    renderWithQuery(
      <ElicitationPanel asked={asked} applied={null} onAnswer={vi.fn()} pending={false} />,
    )

    expect(screen.queryByRole('region', { name: /applied change/i })).not.toBeInTheDocument()
  })

  it('sends the answer and the state the server issued', async () => {
    const user = userEvent.setup()
    const onAnswer = vi.fn()
    renderWithQuery(
      <ElicitationPanel asked={asked} applied={null} onAnswer={onAnswer} pending={false} />,
    )

    await user.click(screen.getByRole('button', { name: /^Confirm/ }))

    expect(onAnswer).toHaveBeenCalledWith({
      key: 'confirm_adjustment',
      confirmed: true,
      requestState: 'opaque-sealed-payload',
    })
  })

  it('can decline, which is an answer and not a refusal to answer', async () => {
    const user = userEvent.setup()
    const onAnswer = vi.fn()
    renderWithQuery(
      <ElicitationPanel asked={asked} applied={null} onAnswer={onAnswer} pending={false} />,
    )

    await user.click(screen.getByRole('button', { name: /^Decline/ }))

    expect(onAnswer).toHaveBeenCalledWith(
      expect.objectContaining({ confirmed: false, requestState: 'opaque-sealed-payload' }),
    )
  })

  it('never shows the opaque state as though it meant something', () => {
    renderWithQuery(
      <ElicitationPanel asked={asked} applied={null} onAnswer={vi.fn()} pending={false} />,
    )

    const region = screen.getByRole('region', { name: /confirmation/i })
    expect(region).not.toHaveTextContent('opaque-sealed-payload')
  })
})

describe('after the change is applied', () => {
  it('shows the identifier the billing system assigned', () => {
    renderWithQuery(
      <ElicitationPanel asked={null} applied={applied} onAnswer={vi.fn()} pending={false} />,
    )

    expect(screen.getByRole('region', { name: /applied change/i })).toHaveTextContent('adj-77')
  })

  it('shows the fee before and after, whatever the account started from', () => {
    // The output schema carries new_fee_bps and delta_bps and no previous fee, so the before value
    // is computed: exact, and correct on the replay path too (finding F-002).
    renderWithQuery(
      <ElicitationPanel asked={null} applied={applied} onAnswer={vi.fn()} pending={false} />,
    )

    const region = screen.getByRole('region', { name: /applied change/i })
    expect(region).toHaveTextContent('100')
    expect(region).toHaveTextContent('115')
  })

  it('says plainly when nothing happened a second time', () => {
    renderWithQuery(
      <ElicitationPanel
        asked={null}
        applied={{
          ...applied,
          structuredContent: { ...applied.structuredContent, replayed: true },
        }}
        onAnswer={vi.fn()}
        pending={false}
      />,
    )

    const region = screen.getByRole('region', { name: /applied change/i })
    expect(region).toHaveTextContent(/nothing happened a second time/i)
    // Still the original result, not a fresh one.
    expect(region).toHaveTextContent('adj-77')
  })

  it('shows a declined change as nothing applied, not as a failure', () => {
    // The server really does return isError: true here, against its own contract (finding F-005).
    // The console shows the outcome for what it is and names the divergence rather than either
    // hiding it or calling a correct outcome a failure.
    renderWithQuery(
      <ElicitationPanel
        asked={null}
        declined
        applied={{
          resultType: 'complete',
          isError: true,
          content: [
            { type: 'text', text: 'The change was not confirmed, so nothing was applied.' },
          ],
        }}
        onAnswer={vi.fn()}
        pending={false}
      />,
    )

    const region = screen.getByRole('region', { name: /applied change/i })
    expect(region).toHaveTextContent(/nothing was applied/i)
    expect(region).toHaveTextContent(/isError/)
    expect(region).not.toHaveTextContent(/undo/i)
  })

  it('offers no undo, and names the reset instead', () => {
    renderWithQuery(
      <ElicitationPanel asked={null} applied={applied} onAnswer={vi.fn()} pending={false} />,
    )

    expect(
      screen.queryByRole('button', { name: /undo|revert|put it back/i }),
    ).not.toBeInTheDocument()
    expect(screen.getByText(RESET_COMMAND)).toBeInTheDocument()
    // The reason matters as much as the command: an undo would be a second, opposite change.
    expect(screen.getByRole('region', { name: /applied change/i })).toHaveTextContent(
      /two events where one happened/i,
    )
  })
})
