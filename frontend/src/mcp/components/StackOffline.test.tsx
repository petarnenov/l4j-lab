import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderWithQuery } from '../../test/render'
import { StackOffline } from './StackOffline'

/**
 * T025 (FR-003, US1-4, SC-006). Opening the console with the stack stopped must produce an
 * explanation and a command to run, and never an empty page or an unexplained failure.
 */
describe('when the MCP stack is not reachable', () => {
  it('says so plainly', () => {
    renderWithQuery(<StackOffline />)

    expect(screen.getByRole('heading', { name: /not reachable/i })).toBeInTheDocument()
  })

  it('names the command that starts it', () => {
    renderWithQuery(<StackOffline />)

    expect(screen.getByText('make mcp-up')).toBeInTheDocument()
  })

  it('names the overlay too, since the cross-replica demonstrations need it', () => {
    renderWithQuery(<StackOffline />)

    expect(screen.getByText('make mcp-up-topology')).toBeInTheDocument()
  })

  it('is not an empty page', () => {
    const { container } = renderWithQuery(<StackOffline />)

    expect(container.textContent?.length ?? 0).toBeGreaterThan(80)
  })
})
