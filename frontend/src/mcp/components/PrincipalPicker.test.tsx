import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { renderWithQuery } from '../../test/render'
import { decodeClaims } from '../principals'
import { fixtureToken } from '../test/mcpHandlers'
import { PrincipalPicker } from './PrincipalPicker'

/**
 * T034 (FR-004, FR-005, US2-1). The four facts shown come from the token's claims, not from a table
 * copied out of the issuer's contract: a copy would go on being believed after it went stale, and
 * these are the claims the server will actually act on.
 */
describe('choosing who to act as', () => {
  it('offers the six seeded principals and adds none', async () => {
    const user = userEvent.setup()
    renderWithQuery(<PrincipalPicker value="admin-alpha" onChange={vi.fn()} claims={null} />)

    await user.click(screen.getByLabelText('Acting as'))
    const options = await screen.findAllByRole('option')

    expect(options.map((option) => option.textContent)).toEqual([
      'advisor-alpha-101',
      'advisor-alpha-102',
      'admin-alpha',
      'ops-alpha',
      'readonly-alpha',
      'admin-beta',
    ])
  })

  it('shows the chosen principal user, firm, role and permitted advisors', () => {
    renderWithQuery(
      <PrincipalPicker
        value="admin-alpha"
        onChange={vi.fn()}
        claims={decodeClaims(fixtureToken('admin-alpha'))}
      />,
    )

    const identity = screen.getByRole('region', { name: /identity/i })
    expect(identity).toHaveTextContent('usr-900')
    expect(identity).toHaveTextContent('firm-alpha')
    expect(identity).toHaveTextContent('FIRM_ADMIN')
    expect(identity).toHaveTextContent('adv-101')
    expect(identity).toHaveTextContent('adv-102')
  })

  it('narrows to one advisor for an advisor principal', () => {
    renderWithQuery(
      <PrincipalPicker
        value="advisor-alpha-101"
        onChange={vi.fn()}
        claims={decodeClaims(fixtureToken('advisor-alpha-101'))}
      />,
    )

    const identity = screen.getByRole('region', { name: /identity/i })
    expect(identity).toHaveTextContent('adv-101')
    expect(identity).not.toHaveTextContent('adv-102')
  })

  it('says the console obtained the credential, so nobody looks for somewhere to paste one', () => {
    renderWithQuery(
      <PrincipalPicker
        value="ops-alpha"
        onChange={vi.fn()}
        claims={decodeClaims(fixtureToken('ops-alpha'))}
      />,
    )

    expect(screen.getByRole('region', { name: /identity/i })).toHaveTextContent(
      /minted by the console/i,
    )
  })

  it('shows the name without inventing claims while the token is still being minted', () => {
    renderWithQuery(<PrincipalPicker value="admin-beta" onChange={vi.fn()} claims={null} />)

    const identity = screen.getByRole('region', { name: /identity/i })
    expect(identity).toHaveTextContent(/minting/i)
    expect(identity).not.toHaveTextContent('usr-')
  })

  it('never shows the token itself', () => {
    const token = fixtureToken('admin-alpha')
    const { container } = renderWithQuery(
      <PrincipalPicker value="admin-alpha" onChange={vi.fn()} claims={decodeClaims(token)} />,
    )

    expect(container.textContent).not.toContain(token.split('.')[1])
  })

  it('reports a change of principal', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    renderWithQuery(<PrincipalPicker value="admin-alpha" onChange={onChange} claims={null} />)

    await user.click(screen.getByLabelText('Acting as'))
    await user.click(within(await screen.findByRole('listbox')).getByText('admin-beta'))

    expect(onChange).toHaveBeenCalledWith('admin-beta')
  })
})
