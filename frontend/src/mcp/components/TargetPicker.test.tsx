import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { renderWithQuery } from '../../test/render'
import { type Target, absenceReason, devProxyTargets } from '../targets'
import { TargetPicker } from './TargetPicker'

function targets(reachable: string[]): Target[] {
  return (['proxy', 'a', 'b', 'c'] as const).map((id) => ({
    id,
    label: id === 'proxy' ? 'Proxy (round-robin)' : `mcp-${id}`,
    baseUrl: devProxyTargets.baseUrl(id),
    reachability: reachable.includes(id) ? ('reachable' as const) : ('unreachable' as const),
    absenceReason: reachable.includes(id) ? null : absenceReason(id),
  }))
}

/**
 * T051 (FR-016, FR-017, US4-3, US4-5).
 */
describe('choosing where a call goes', () => {
  it('always offers the shared entry point', () => {
    renderWithQuery(<TargetPicker targets={targets(['proxy'])} value="proxy" onChange={vi.fn()} />)

    expect(screen.getByRole('radio', { name: /Proxy/ })).toBeEnabled()
  })

  it('offers a replica that is reachable', () => {
    renderWithQuery(
      <TargetPicker targets={targets(['proxy', 'b'])} value="proxy" onChange={vi.fn()} />,
    )

    expect(screen.getByRole('radio', { name: /mcp-b/ })).toBeEnabled()
  })

  it('disables a replica that is not published, and says why', () => {
    renderWithQuery(<TargetPicker targets={targets(['proxy'])} value="proxy" onChange={vi.fn()} />)

    expect(screen.getByRole('radio', { name: /mcp-a/ })).toBeDisabled()
    expect(screen.getByRole('region', { name: /topology/i })).toHaveTextContent(
      'make mcp-up-topology',
    )
  })

  it('presents all three unpublished as normal rather than as a fault', () => {
    // A plain `make mcp-up` publishes exactly one port on purpose: that is the honest shape for a
    // deployment, and the console must not describe it as something being broken.
    renderWithQuery(<TargetPicker targets={targets(['proxy'])} value="proxy" onChange={vi.fn()} />)

    const note = screen.getByRole('region', { name: /topology/i })
    expect(note).toHaveTextContent(/not a fault/i)
    expect(note).not.toHaveTextContent(/error|failed|broken/i)
  })

  it('says nothing about the overlay when every replica is published', () => {
    renderWithQuery(
      <TargetPicker targets={targets(['proxy', 'a', 'b', 'c'])} value="a" onChange={vi.fn()} />,
    )

    expect(screen.queryByRole('region', { name: /topology/i })).not.toBeInTheDocument()
  })

  it('explains that each answer names the replica that produced it (F-003, closed)', () => {
    renderWithQuery(
      <TargetPicker targets={targets(['proxy', 'a', 'b', 'c'])} value="proxy" onChange={vi.fn()} />,
    )

    expect(screen.getByRole('region', { name: /which server/i })).toHaveTextContent(
      /serverInfo\.instance/i,
    )
  })

  it('reports a change of target', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    renderWithQuery(
      <TargetPicker targets={targets(['proxy', 'a'])} value="proxy" onChange={onChange} />,
    )

    // Clicked by its own label, as a person does: antd takes pointer events off the input itself
    // and lets the surrounding label carry them. Reached from the radio rather than by text,
    // because the explanation below the group names the replicas too.
    const label = screen.getByRole('radio', { name: /mcp-a/ }).closest('label')
    await user.click(label as HTMLElement)
    expect(onChange).toHaveBeenCalledWith('a')
  })
})
