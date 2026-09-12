import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { nodes } from '../test/handlers'
import { NodeDetail } from './NodeDetail'

describe('NodeDetail', () => {
  it('shows what a node received and what it produced', () => {
    render(<NodeDetail node={nodes()[0]} />)

    expect(screen.getByText('Received')).toBeInTheDocument()
    expect(screen.getByText('Produced')).toBeInTheDocument()
    // The identifier appears in both payloads, which is the point: it flowed between them.
    expect(screen.getAllByText(/northwind-lighting/)).toHaveLength(2)
  })

  it('shows the full model exchange for the fourth node', () => {
    // FR-010 and US2: the text sent and the text returned, both visible.
    render(<NodeDetail node={nodes()[3]} />)

    expect(screen.getByText('Sent to the model')).toBeInTheDocument()
    expect(screen.getByText('Returned by the model')).toBeInTheDocument()
    expect(screen.getByText(/revenueGrowth = 0.2500/)).toBeInTheDocument()
    expect(screen.getByText(/Tokens in 120, out 80/)).toBeInTheDocument()
  })

  it('shows no model exchange for the deterministic nodes', () => {
    // FR-007: only the fourth node talks to a model, and the screen should make that obvious.
    render(<NodeDetail node={nodes()[1]} />)
    expect(screen.queryByText('Sent to the model')).not.toBeInTheDocument()
  })

  it('shows a failed node its reason and no output', () => {
    render(<NodeDetail node={nodes(true)[3]} />)

    expect(screen.getByRole('alert')).toHaveTextContent('The model could not be reached.')
    expect(screen.getByText(/Nothing was produced/)).toBeInTheDocument()
    expect(screen.getByText(/Nothing came back/)).toBeInTheDocument()
  })

  it('still shows the prompt when the call failed', () => {
    render(<NodeDetail node={nodes(true)[3]} />)
    expect(screen.getByText(/revenueGrowth = 0.2500/)).toBeInTheDocument()
  })
})
