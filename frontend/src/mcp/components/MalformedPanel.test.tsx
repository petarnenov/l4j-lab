import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { renderWithQuery } from '../../test/render'
import { MALFORMED_REQUESTS } from '../malformed'
import { MalformedPanel } from './MalformedPanel'

/**
 * T060 (FR-011a, SC-003a).
 */
describe('producing a refusal on demand', () => {
  it('offers all three, each sendable in one action', () => {
    renderWithQuery(<MalformedPanel onSend={vi.fn()} pending={false} />)

    for (const request of MALFORMED_REQUESTS) {
      expect(
        screen.getByRole('button', { name: new RegExp(`^Send: ${request.label}`) }),
      ).toBeEnabled()
    }
  })

  it('explains what is wrong before anything is sent', () => {
    renderWithQuery(<MalformedPanel onSend={vi.fn()} pending={false} />)

    const region = screen.getByRole('region', { name: /deliberate/i })
    for (const request of MALFORMED_REQUESTS) {
      expect(region).toHaveTextContent(request.explanation.slice(0, 40))
    }
  })

  it('names the code each one should produce, so the answer can be checked against the claim', () => {
    renderWithQuery(<MalformedPanel onSend={vi.fn()} pending={false} />)

    const region = screen.getByRole('region', { name: /deliberate/i })
    expect(region).toHaveTextContent('-32020')
    expect(region).toHaveTextContent('-32022')
  })

  it('says plainly that these are on purpose', () => {
    // A refusal produced deliberately must not be mistaken for a fault in the system.
    renderWithQuery(<MalformedPanel onSend={vi.fn()} pending={false} />)

    expect(screen.getByRole('region', { name: /deliberate/i })).toHaveTextContent(/on purpose/i)
  })

  it('sends the chosen one', async () => {
    const user = userEvent.setup()
    const onSend = vi.fn()
    renderWithQuery(<MalformedPanel onSend={onSend} pending={false} />)

    await user.click(
      screen.getByRole('button', { name: new RegExp(`^Send: ${MALFORMED_REQUESTS[1].label}`) }),
    )

    expect(onSend).toHaveBeenCalledWith(MALFORMED_REQUESTS[1])
  })

  it('offers no way to edit headers or a body', () => {
    renderWithQuery(<MalformedPanel onSend={vi.fn()} pending={false} />)

    const region = screen.getByRole('region', { name: /deliberate/i })
    expect(within(region).queryAllByRole('textbox')).toHaveLength(0)
    expect(within(region).queryAllByRole('combobox')).toHaveLength(0)
  })

  it('points at curl rather than pretending a request editor is missing', () => {
    renderWithQuery(<MalformedPanel onSend={vi.fn()} pending={false} />)

    expect(screen.getByRole('region', { name: /deliberate/i })).toHaveTextContent('curl')
  })
})
