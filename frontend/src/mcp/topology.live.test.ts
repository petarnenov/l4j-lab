import { beforeAll, describe, expect, it } from 'vitest'
import {
  SKIP_WITHOUT_TOPOLOGY,
  liveSession,
  liveTargets,
  reachableReplicas,
  requireStack,
} from './liveFixture'
import type { TargetId } from './targets'
import { type Exchange, type McpSession, callMcp, resultOrThrow } from './transport'
import { type TaskResult, isTerminalTask } from './wire'

/**
 * T058 (FR-013, FR-014, FR-015, SC-005, US4). Through the console's own transport and targets.
 *
 * Two rules, and they differ on purpose. An unreachable **proxy** fails with `make mcp-up`: SC-008
 * requires an instruction rather than an unexplained error. Unpublished **replicas** skip with
 * `make mcp-up-topology`: SC-005 is conditional on them being reachable, and a plain `make mcp-up`
 * publishes only the proxy, which is not a fault.
 */
function structured<T>(exchange: Exchange): T {
  return (exchange.responseBody as { result: { structuredContent: T } }).result.structuredContent
}

async function sleep(ms: number) {
  await new Promise((resolve) => setTimeout(resolve, ms))
}

describe('a long operation, and the replicas behind it', () => {
  let session: McpSession
  let replicas: TargetId[] = []

  beforeAll(async () => {
    await requireStack()
    session = (await liveSession('admin-alpha')).session
    replicas = await reachableReplicas()
  })

  it('returns a handle at once rather than waiting for the work', async () => {
    const started = Date.now()
    const exchange = await callMcp(session, {
      method: 'tools/call',
      params: {
        name: 'start_billing_run',
        arguments: { firm_id: 'firm-alpha', executed_by_advisor_id: 'adv-101' },
      },
    })

    expect(exchange.outcome).toBe('ok')
    // 007 SC-003 puts this under a second. Measured here from the console's side, which is the
    // side that matters: a handle nobody receives promptly is not a handle.
    expect(Date.now() - started).toBeLessThan(5000)

    const task = resultOrThrow<TaskResult>(exchange)
    expect(task.resultType).toBe('task')
    expect(task.taskId).toBeTruthy()
    expect(task.pollIntervalMs).toBeGreaterThan(0)
  })

  it('polls to a final state at the interval the server asked for', async () => {
    const task = resultOrThrow<TaskResult>(
      await callMcp(session, {
        method: 'tools/call',
        params: {
          name: 'start_billing_run',
          arguments: { firm_id: 'firm-alpha', executed_by_advisor_id: 'adv-101' },
        },
      }),
    )

    let latest = task
    for (let attempt = 0; attempt < 40 && !isTerminalTask(latest.status); attempt += 1) {
      await sleep(latest.pollIntervalMs ?? 2000)
      latest = resultOrThrow<TaskResult>(
        await callMcp(session, { method: 'tasks/get', params: { taskId: task.taskId } }),
      )
    }

    expect(isTerminalTask(latest.status)).toBe(true)
    expect(latest.taskId).toBe(task.taskId)
  })

  it('drives a live run to cancelled', async () => {
    const task = resultOrThrow<TaskResult>(
      await callMcp(session, {
        method: 'tools/call',
        params: {
          name: 'start_billing_run',
          arguments: { firm_id: 'firm-alpha', executed_by_advisor_id: 'adv-101' },
        },
      }),
    )

    const cancelled = await callMcp(session, {
      method: 'tasks/cancel',
      params: { taskId: task.taskId },
    })
    expect(cancelled.outcome).toBe('ok')

    await sleep(1500)
    const after = resultOrThrow<TaskResult>(
      await callMcp(session, { method: 'tasks/get', params: { taskId: task.taskId } }),
    )
    expect(isTerminalTask(after.status)).toBe(true)
  })

  it('acknowledges cancelling work that has already finished, and does not call it an error', async () => {
    // 007 FR-031. The console shows this as acknowledged rather than as a failure, and this is
    // where that reading is checked against the server rather than against an assumption.
    const task = resultOrThrow<TaskResult>(
      await callMcp(session, {
        method: 'tools/call',
        params: {
          name: 'start_billing_run',
          arguments: { firm_id: 'firm-alpha', executed_by_advisor_id: 'adv-101' },
        },
      }),
    )
    await callMcp(session, { method: 'tasks/cancel', params: { taskId: task.taskId } })
    await sleep(1500)

    const again = await callMcp(session, {
      method: 'tasks/cancel',
      params: { taskId: task.taskId },
    })

    expect(again.outcome).toBe('ok')
  })

  it('continues a page begun on one replica from another, with no overlap', async () => {
    if (replicas.length < 2) {
      console.log(SKIP_WITHOUT_TOPOLOGY)
      return
    }

    const targets = liveTargets()
    const onA = await liveSession('admin-alpha', replicas[0], targets)
    const first = await callMcp(onA.session, {
      method: 'tools/call',
      params: {
        name: 'search_billing_runs',
        arguments: { firm_id: 'firm-alpha', page_size: 1 },
      },
    })
    const pageOne = structured<{
      runs: Array<{ run_id: string }>
      truncated: boolean
      next_cursor?: string
    }>(first)

    expect(pageOne.truncated).toBe(true)
    expect(pageOne.next_cursor).toBeTruthy()

    // The cursor the console carried, not one lifted out by the test and reshaped.
    const onB = await liveSession('admin-alpha', replicas[1], targets)
    const second = await callMcp(onB.session, {
      method: 'tools/call',
      params: {
        name: 'search_billing_runs',
        arguments: { firm_id: 'firm-alpha', page_size: 1, cursor: pageOne.next_cursor },
      },
    })
    const pageTwo = structured<{ runs: Array<{ run_id: string }> }>(second)

    expect(second.outcome).toBe('ok')
    expect(pageTwo.runs.length).toBeGreaterThan(0)

    const idsOne = new Set(pageOne.runs.map((run) => run.run_id))
    for (const run of pageTwo.runs) {
      expect(idsOne.has(run.run_id)).toBe(false)
    }
  })

  it('polls a handle from a different replica than started it', async () => {
    if (replicas.length < 2) {
      console.log(SKIP_WITHOUT_TOPOLOGY)
      return
    }

    const targets = liveTargets()
    const onA = await liveSession('admin-alpha', replicas[0], targets)
    const task = resultOrThrow<TaskResult>(
      await callMcp(onA.session, {
        method: 'tools/call',
        params: {
          name: 'start_billing_run',
          arguments: { firm_id: 'firm-alpha', executed_by_advisor_id: 'adv-101' },
        },
      }),
    )

    const onB = await liveSession('admin-alpha', replicas[1], targets)
    const polled = resultOrThrow<TaskResult>(
      await callMcp(onB.session, { method: 'tasks/get', params: { taskId: task.taskId } }),
    )

    expect(polled.taskId).toBe(task.taskId)
  })
})
