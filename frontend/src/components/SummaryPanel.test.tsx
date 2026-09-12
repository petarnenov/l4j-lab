import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderWithQuery } from '../test/render'
import { SummaryPanel } from './SummaryPanel'

describe('SummaryPanel', () => {
  it('renders the summary text', () => {
    render(<SummaryPanel summary="Revenue grew 0.2500 over the prior quarter." />)
    expect(screen.getByText(/Revenue grew 0.2500/)).toBeInTheDocument()
  })

  it('always carries the fictional-data and not-advice note', () => {
    // R-009: a model summarising financial figures drifts toward advice, and this audience must
    // never read it as such.
    render(<SummaryPanel summary="Anything at all." />)

    expect(screen.getByText(/fictional/)).toBeInTheDocument()
    expect(screen.getByText(/not investment advice/)).toBeInTheDocument()
  })

  it('presents the notice as an alert, not as incidental text (FR-023)', () => {
    render(<SummaryPanel summary="Anything at all." />)
    const alert = screen.getByRole('alert')
    expect(alert).toHaveTextContent(/fictional/)
    expect(alert).toHaveTextContent(/not investment advice/)
  })

  it('caps the summary in a scrolling region so a long response cannot fill the screen (FR-017)', () => {
    render(<SummaryPanel summary={'A very long model response. '.repeat(400)} />)
    const region = screen.getByText(/A very long model response/).closest('[style*="max-height"]')
    expect(region).not.toBeNull()
    expect(region).toHaveStyle({ maxHeight: '24rem', overflowY: 'auto' })
  })

  it('says the indicator table wins when the two disagree', () => {
    render(<SummaryPanel summary="Anything at all." />)
    expect(screen.getByText(/the table is\s+correct/)).toBeInTheDocument()
  })

  it('explains itself before the fourth node has run', () => {
    render(<SummaryPanel summary={null} />)
    expect(screen.getByText(/appears once the fourth node has run/)).toBeInTheDocument()
  })

  it('keeps the fictional-data notice as an alert in the dark theme too', () => {
    renderWithQuery(<SummaryPanel summary="Anything at all." />, { theme: 'dark' })
    expect(screen.getByRole('alert')).toHaveTextContent(/not investment advice/)
  })
})
