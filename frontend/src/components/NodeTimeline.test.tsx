import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { nodes } from '../test/handlers'
import { setViewportWidth } from '../test/setup'
import { NodeTimeline } from './NodeTimeline'

/**
 * Rewritten for antd's Steps. Steps are found by node name inside the named step group rather than by
 * counting every button on the page. Every fact the original asserted is still asserted: four entries in
 * execution order, durations shown, the failing node marked, and selection switching the detail.
 */

function steps() {
  return within(screen.getByRole('group', { name: 'Node steps' })).getAllByRole('button')
}

function step(name: RegExp) {
  return steps().find((s) => name.test(s.textContent ?? ''))!
}

describe('NodeTimeline', () => {
  it('lists exactly four node entries in execution order', () => {
    render(<NodeTimeline nodes={nodes()} />)
    const order = ['PrepareRequest', 'RetrieveRecords', 'ComputeIndicators', 'Summarize']
    expect(steps()).toHaveLength(4)
    steps().forEach((s, i) => expect(s).toHaveTextContent(order[i]))
  })

  it('shows each node its duration', () => {
    render(<NodeTimeline nodes={nodes()} />)
    expect(step(/PrepareRequest/)).toHaveTextContent('1 ms')
  })

  it('marks the failing node in words and with an icon', () => {
    render(<NodeTimeline nodes={nodes(true)} />)
    const failed = step(/Summarize/)
    expect(failed).toHaveTextContent('failed')
    expect(within(failed).getByRole('img', { name: 'close' })).toBeInTheDocument()
  })

  it('steps to another node when its entry is clicked', async () => {
    const user = userEvent.setup()
    render(<NodeTimeline nodes={nodes()} />)

    await user.click(step(/ComputeIndicators/))

    expect(screen.getByRole('heading', { name: '3. ComputeIndicators' })).toBeInTheDocument()
  })

  it('keeps payloads and the model exchange inside capped scrolling regions (FR-017)', async () => {
    const user = userEvent.setup()
    render(<NodeTimeline nodes={nodes()} />)
    await user.click(step(/Summarize/))

    const regions = Array.from(document.querySelectorAll('pre')).filter((pre) =>
      (pre.getAttribute('style') ?? '').includes('max-height'),
    )
    // Received, produced, sent to the model, returned by the model.
    expect(regions).toHaveLength(4)
    regions.forEach((pre) => expect(pre).toHaveStyle({ maxHeight: '24rem', overflow: 'auto' }))
  })

  it('explains itself before any node has run', () => {
    render(<NodeTimeline nodes={[]} />)
    expect(screen.getByText(/appear as the chain advances/)).toBeInTheDocument()
  })

  describe('responsive layout', () => {
    const orientation = () =>
      screen.getByRole('group', { name: 'Node steps' }).querySelector('.ant-steps')!.className

    it('lays the steps out vertically below 768 pixels', async () => {
      setViewportWidth(375)
      render(<NodeTimeline nodes={nodes()} />)
      await waitFor(() => expect(orientation()).toMatch(/vertical/))
    })

    it('lays the steps out horizontally from 768 pixels', async () => {
      setViewportWidth(1024)
      render(<NodeTimeline nodes={nodes()} />)
      await waitFor(() => expect(orientation()).not.toMatch(/vertical/))
    })

    it('keeps the selected node when the viewport crosses the breakpoint (US3 scenario 5)', async () => {
      setViewportWidth(1024)
      const user = userEvent.setup()
      render(<NodeTimeline nodes={nodes()} />)

      await user.click(step(/Summarize/))
      expect(screen.getByRole('heading', { name: '4. Summarize' })).toBeInTheDocument()

      act(() => setViewportWidth(375))
      await waitFor(() => expect(orientation()).toMatch(/vertical/))
      expect(screen.getByRole('heading', { name: '4. Summarize' })).toBeInTheDocument()
    })
  })
})
