import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { indicators } from '../test/handlers'
import { renderWithQuery } from '../test/render'
import { setViewportWidth } from '../test/setup'
import { IndicatorTable } from './IndicatorTable'

describe('IndicatorTable', () => {
  it('renders all five indicators', () => {
    render(<IndicatorTable indicators={indicators} />)

    expect(screen.getByText('Revenue growth')).toBeInTheDocument()
    expect(screen.getByText('Gross margin')).toBeInTheDocument()
    expect(screen.getByText('Net margin')).toBeInTheDocument()
    expect(screen.getByText('Current ratio')).toBeInTheDocument()
    expect(screen.getByText('Debt to equity')).toBeInTheDocument()
  })

  it('renders values exactly as the backend sent them', () => {
    // SC-005: reformatting here would break the one property the whole node was written to protect.
    render(<IndicatorTable indicators={indicators} />)

    expect(screen.getByText('0.2500')).toBeInTheDocument()
    expect(screen.getByText('2.0000')).toBeInTheDocument()
  })

  it('shows a not-applicable indicator with its reason rather than a number', () => {
    render(<IndicatorTable indicators={indicators} />)

    expect(screen.getByText('not applicable')).toBeInTheDocument()
    expect(screen.getByText(/Equity is zero/)).toBeInTheDocument()
  })

  it('names the record fields each indicator came from', () => {
    render(<IndicatorTable indicators={indicators} />)
    expect(screen.getByText('totalDebt, equity')).toBeInTheDocument()
  })

  it('keeps trailing zeros and negative signs exactly as supplied (FR-022)', () => {
    // Exact full-text queries only. `0.34` is a substring of the correct `0.3400`, so a regex query
    // would reject the right value and invite someone to loosen this guard.
    render(
      <IndicatorTable
        indicators={[
          {
            name: 'revenueGrowth',
            value: '-0.0500',
            notApplicableReason: undefined,
            derivedFrom: ['revenue'],
          },
          {
            name: 'grossMargin',
            value: '0.3400',
            notApplicableReason: undefined,
            derivedFrom: ['revenue'],
          },
          {
            name: 'currentRatio',
            value: '2.0000',
            notApplicableReason: undefined,
            derivedFrom: ['currentAssets'],
          },
        ]}
      />,
    )

    expect(screen.getByText('-0.0500')).toBeInTheDocument()
    expect(screen.getByText('0.3400')).toBeInTheDocument()
    expect(screen.getByText('2.0000')).toBeInTheDocument()
    expect(screen.queryByText('0.34')).not.toBeInTheDocument()
    expect(screen.queryByText('34%')).not.toBeInTheDocument()
    expect(screen.queryByText('-0.05')).not.toBeInTheDocument()
  })

  it('presents the indicators as a table with column headers', () => {
    render(<IndicatorTable indicators={indicators} />)
    expect(screen.getByRole('table')).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: 'Value' })).toBeInTheDocument()
  })

  it('explains itself before the third node has run', () => {
    render(<IndicatorTable indicators={null} />)
    expect(screen.getByText(/appear once the third node has run/)).toBeInTheDocument()
  })

  it('keeps values verbatim in the dark theme too', () => {
    renderWithQuery(<IndicatorTable indicators={indicators} />, { theme: 'dark' })
    expect(screen.getByText('0.2500')).toBeInTheDocument()
    expect(screen.getByText('2.0000')).toBeInTheDocument()
    expect(screen.getByText(/Equity is zero/)).toBeInTheDocument()
  })

  describe('on a narrow screen (research R-004)', () => {
    it('renders one card per indicator instead of a table below 768 pixels', async () => {
      setViewportWidth(375)
      renderWithQuery(<IndicatorTable indicators={indicators} />)

      expect(await screen.findAllByRole('article')).toHaveLength(5)
      expect(screen.queryByRole('table')).not.toBeInTheDocument()
    })

    it('keeps every value verbatim in the card layout', async () => {
      setViewportWidth(375)
      renderWithQuery(<IndicatorTable indicators={indicators} />)

      await screen.findAllByRole('article')
      expect(screen.getByText('0.2500')).toBeInTheDocument()
      expect(screen.getByText('2.0000')).toBeInTheDocument()
      expect(screen.getByText(/Equity is zero/)).toBeInTheDocument()
      expect(screen.queryByText('0.25')).not.toBeInTheDocument()
    })

    it('uses the table from 768 pixels', async () => {
      setViewportWidth(768)
      renderWithQuery(<IndicatorTable indicators={indicators} />)
      expect(await screen.findByRole('table')).toBeInTheDocument()
      expect(screen.queryAllByRole('article')).toHaveLength(0)
    })
  })
})
