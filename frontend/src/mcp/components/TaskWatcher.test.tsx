import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { renderWithQuery } from '../../test/render'
import type { TaskResult } from '../wire'
import { TaskWatcher } from './TaskWatcher'

function handle(overrides: Partial<TaskResult> = {}): TaskResult {
  return {
    resultType: 'task',
    taskId: 'tsk_abc123',
    status: 'working',
    statusMessage: 'DATA_COLLECTION, 0/240 accounts',
    createdAt: '2026-09-14T10:00:00Z',
    lastUpdatedAt: '2026-09-14T10:00:00Z',
    ttlMs: 900_000,
    pollIntervalMs: 2000,
    ...overrides,
  }
}

/**
 * T049, T050 (FR-013, FR-014, US4-1, US4-2).
 */
describe('a handle for work that outlives the call', () => {
  it('shows the handle from the first result, before any poll', () => {
    renderWithQuery(<TaskWatcher task={handle()} onCancel={vi.fn()} cancelling={false} />)

    const region = screen.getByRole('region', { name: /long operation/i })
    expect(region).toHaveTextContent('tsk_abc123')
    expect(region).toHaveTextContent('working')
  })

  it('reports progress in the server words', () => {
    renderWithQuery(<TaskWatcher task={handle()} onCancel={vi.fn()} cancelling={false} />)

    expect(screen.getByRole('region', { name: /long operation/i })).toHaveTextContent(
      'DATA_COLLECTION, 0/240 accounts',
    )
  })

  it('says it is polling at the interval the server asked for, not one chosen here', () => {
    renderWithQuery(<TaskWatcher task={handle()} onCancel={vi.fn()} cancelling={false} />)

    expect(screen.getByRole('region', { name: /long operation/i })).toHaveTextContent('2000')
  })

  it('offers cancellation while the work is not finished', async () => {
    const user = userEvent.setup()
    const onCancel = vi.fn()
    renderWithQuery(<TaskWatcher task={handle()} onCancel={onCancel} cancelling={false} />)

    await user.click(screen.getByRole('button', { name: /^Cancel/ }))
    expect(onCancel).toHaveBeenCalledWith('tsk_abc123')
  })

  it('shows a cancelled operation reaching a cancelled state', () => {
    renderWithQuery(
      <TaskWatcher task={handle({ status: 'cancelled' })} onCancel={vi.fn()} cancelling={false} />,
    )

    const region = screen.getByRole('region', { name: /long operation/i })
    expect(region).toHaveTextContent('cancelled')
    expect(region).toHaveTextContent(/no longer polling/i)
  })

  it('stops offering cancellation once the work is finished', () => {
    for (const status of ['completed', 'failed', 'cancelled'] as const) {
      const { unmount } = renderWithQuery(
        <TaskWatcher task={handle({ status })} onCancel={vi.fn()} cancelling={false} />,
      )
      expect(screen.queryByRole('button', { name: /^Cancel/ })).not.toBeInTheDocument()
      unmount()
    }
  })

  it('says an already-finished cancellation was acknowledged, not that it failed', async () => {
    // 007 FR-031: a run already terminal keeps its status and the request is still acknowledged.
    renderWithQuery(
      <TaskWatcher
        task={handle({ status: 'completed' })}
        onCancel={vi.fn()}
        cancelling={false}
        cancelAcknowledged
      />,
    )

    const region = screen.getByRole('region', { name: /long operation/i })
    expect(region).toHaveTextContent(/acknowledged/i)
    expect(region).not.toHaveTextContent(/failed to cancel/i)
  })

  it('shows the result once the work completes', () => {
    renderWithQuery(
      <TaskWatcher
        task={handle({ status: 'completed', result: { run_id: 'run-9', status: 'COMPLETED' } })}
        onCancel={vi.fn()}
        cancelling={false}
      />,
    )

    expect(screen.getByRole('region', { name: /long operation/i })).toHaveTextContent('run-9')
  })

  it('waits for nothing when it already has a handle', async () => {
    // FR-013: the handle is shown at once. Nothing here may depend on a poll having returned.
    renderWithQuery(<TaskWatcher task={handle()} onCancel={vi.fn()} cancelling={false} />)

    await waitFor(() => expect(screen.getByText('tsk_abc123')).toBeInTheDocument())
  })
})
