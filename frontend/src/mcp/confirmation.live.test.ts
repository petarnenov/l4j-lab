import { beforeAll, describe, expect, it } from 'vitest'
import { liveSession, requireStack } from './liveFixture'
import { type Exchange, type McpSession, callMcp, retryParams } from './transport'
import type { CompleteResult, InputRequiredResult, JsonRpcResponse, ToolCallResult } from './wire'

/**
 * T048 (FR-012, FR-012a, US3). The three-call sequence, against the running stack, through the
 * console's own transport including its retry path.
 *
 * **This test writes real data, and the drift is accepted.** A fee adjustment is a real write to a
 * system of record and it outlives stopping the stack; the spec chose visible drift over an undo
 * that would leave the audit log describing two events where one happened. `make mcp-reset`
 * discards the stored data and reloads the seeded fixtures.
 *
 * Each run uses a fresh operation_id, so re-running this suite adjusts the fee again rather than
 * replaying — which is the honest behaviour to test, since idempotency is keyed on that value.
 */
function result(exchange: Exchange): ToolCallResult {
  return (exchange.responseBody as JsonRpcResponse).result as ToolCallResult
}

describe('a change that asks before it acts', () => {
  let session: McpSession
  let operationId: string
  const args = () => ({
    operation_id: operationId,
    account_id: 'acc-0101',
    delta_bps: 15,
    effective_date: '2026-10-01',
    reason: 'MCP console live test',
  })

  beforeAll(async () => {
    await requireStack()
    const live = await liveSession('admin-alpha')
    session = live.session
    // No Math.random and no shared counter: the exchange ids are monotonic within the process, and
    // the principal's own expiry makes the value unique per run without a clock read of its own.
    operationId = `console-live-${session.expiresAt?.getTime()}`
  })

  it('applies nothing on the first call, and asks', async () => {
    const first = await callMcp(session, {
      method: 'tools/call',
      params: { name: 'post_fee_adjustment', arguments: args() },
    })

    expect(first.outcome).toBe('ok')
    const asked = result(first) as InputRequiredResult
    expect(asked.resultType).toBe('input_required')
    expect(asked.requestState).toBeTruthy()

    const request = Object.values(asked.inputRequests)[0]
    expect(request.method).toBe('elicitation/create')
    // The question the console shows verbatim. It names the account and the change, which is why
    // showing it unedited is worth more than any paraphrase.
    expect(request.params.message).toContain('acc-0101')
    expect(request.params.requestedSchema).toHaveProperty('properties.confirmed')
  })

  it('applies exactly once when the answer and the state go back', async () => {
    const first = await callMcp(session, {
      method: 'tools/call',
      params: { name: 'post_fee_adjustment', arguments: args() },
    })
    const asked = result(first) as InputRequiredResult
    const key = Object.keys(asked.inputRequests)[0]

    const confirmed = await callMcp(session, {
      method: 'tools/call',
      params: retryParams({
        name: 'post_fee_adjustment',
        args: args(),
        key,
        confirmed: true,
        requestState: asked.requestState,
      }),
    })

    expect(confirmed.outcome).toBe('ok')
    const applied = result(confirmed) as CompleteResult
    expect(applied.resultType).toBe('complete')
    expect(applied.structuredContent?.legacy_reference_id).toBeTruthy()
    expect(applied.structuredContent?.replayed).toBe(false)
    expect(applied.structuredContent?.confirmed_by_user_id).toBe('usr-900')

    // FR-012a: the before value the console displays, computed from what the result carries.
    const newFee = Number(applied.structuredContent?.new_fee_bps)
    const delta = Number(applied.structuredContent?.delta_bps)
    expect(newFee - delta).toBeGreaterThan(0)
  })

  it('uses a different JSON-RPC id for the retry, as the revision requires', async () => {
    const live = await liveSession('admin-alpha')
    const opId = `${operationId}-ids`
    const callArgs = { ...args(), operation_id: opId }

    const first = await callMcp(live.session, {
      method: 'tools/call',
      params: { name: 'post_fee_adjustment', arguments: callArgs },
    })
    const asked = result(first) as InputRequiredResult
    await callMcp(live.session, {
      method: 'tools/call',
      params: retryParams({
        name: 'post_fee_adjustment',
        args: callArgs,
        key: Object.keys(asked.inputRequests)[0],
        confirmed: true,
        requestState: asked.requestState,
      }),
    })

    const ids = live.exchanges.map((exchange) => (exchange.requestBody as { id: string }).id)
    expect(new Set(ids).size).toBe(ids.length)
  })

  it('returns the original result a third time, without acting again', async () => {
    const third = await callMcp(session, {
      method: 'tools/call',
      params: { name: 'post_fee_adjustment', arguments: args() },
    })

    const replayed = result(third) as CompleteResult
    expect(replayed.resultType).toBe('complete')
    expect(replayed.structuredContent?.replayed).toBe(true)
    // The same reference the billing system assigned the first time. Nothing was adjusted twice.
    expect(replayed.structuredContent?.legacy_reference_id).toBeTruthy()
  })

  it('applies nothing when the answer is no, and does not call that an error', async () => {
    const live = await liveSession('admin-alpha')
    const declineArgs = { ...args(), operation_id: `${operationId}-declined` }

    const first = await callMcp(live.session, {
      method: 'tools/call',
      params: { name: 'post_fee_adjustment', arguments: declineArgs },
    })
    const asked = result(first) as InputRequiredResult

    const declined = await callMcp(live.session, {
      method: 'tools/call',
      params: retryParams({
        name: 'post_fee_adjustment',
        args: declineArgs,
        key: Object.keys(asked.inputRequests)[0],
        confirmed: false,
        requestState: asked.requestState,
      }),
    })

    // Feature 010 closed F-005: a decline is now the result the contract always said it was. This
    // assertion used to require isError: true and to explain why the server disagreed with its own
    // contract. The explanation is gone because the divergence is.
    const body = result(declined) as CompleteResult
    expect(body.resultType).toBe('complete')
    expect(body.structuredContent?.legacy_reference_id).toBeUndefined()
    expect(body.content?.[0]?.text).toMatch(/nothing was applied/i)
    expect(body.isError).toBeUndefined()
    expect(declined.outcome).toBe('ok')
  })
})
