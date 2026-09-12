import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderWithQuery } from '../test/render'
import { setReducedMotion } from '../test/setup'
import { RunProgress } from './RunProgress'

describe('RunProgress', () => {
  it('renders all four steps', () => {
    renderWithQuery(<RunProgress status="RUNNING" currentNode="RetrieveRecords" />)

    for (const label of [
      'Preparing the request',
      'Retrieving the records',
      'Computing the indicators',
      'Asking the model to summarise',
    ]) {
      expect(screen.getAllByText(label).length).toBeGreaterThan(0)
    }
  })

  it('names the node currently executing (FR-025)', () => {
    renderWithQuery(<RunProgress status="RUNNING" currentNode="ComputeIndicators" />)
    expect(screen.getByRole('status')).toHaveTextContent('Computing the indicators')
  })

  it('says the summarizing step is the slow one while it runs', () => {
    renderWithQuery(<RunProgress status="RUNNING" currentNode="Summarize" />)
    expect(screen.getByText(/slow step/)).toBeInTheDocument()
  })

  it('marks completed steps with an icon, not colour alone (FR-008)', () => {
    renderWithQuery(<RunProgress status="RUNNING" currentNode="ComputeIndicators" />)
    // Two steps are finished before ComputeIndicators.
    expect(screen.getAllByRole('img', { name: 'check' })).toHaveLength(2)
  })

  it('says it is queued before the first node starts', () => {
    renderWithQuery(<RunProgress status="PENDING" currentNode={null} />)
    expect(screen.getByRole('status')).toHaveTextContent(/Queued/)
  })

  it('renders nothing once the run is terminal', () => {
    const { container } = renderWithQuery(<RunProgress status="SUCCEEDED" currentNode={null} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('does not spin the in-progress indicator when reduced motion is requested (FR-018)', () => {
    setReducedMotion(true)
    const { container } = renderWithQuery(<RunProgress status="RUNNING" currentNode="Summarize" />)
    expect(container.querySelector('.anticon-spin')).toBeNull()
    expect(screen.getByRole('status')).toHaveTextContent('Asking the model to summarise')
  })

  it('does spin it otherwise', () => {
    const { container } = renderWithQuery(<RunProgress status="RUNNING" currentNode="Summarize" />)
    expect(container.querySelector('.anticon-spin')).not.toBeNull()
  })
})
