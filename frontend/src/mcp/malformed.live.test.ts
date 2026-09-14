import { beforeAll, describe, expect, it } from 'vitest'
import { liveSession, requireStack } from './liveFixture'
import { MALFORMED_REQUESTS } from './malformed'
import { type McpSession, callMcp } from './transport'
import type { JsonRpcResponse } from './wire'

/**
 * T063 (FR-011a, SC-003a). Each of the three, built by malformed.ts and sent by transport.ts.
 *
 * The point is that the console's own builders provoke these refusals — not that the server can
 * produce them, which feature 007's suite already proves. A stub would have refused whatever this
 * repository told it to.
 */
describe('the deliberate refusals, against the running stack', () => {
  let session: McpSession

  beforeAll(async () => {
    await requireStack()
    session = (await liveSession('admin-alpha')).session
  })

  for (const request of MALFORMED_REQUESTS) {
    it(`produces ${request.expectedCode} for: ${request.label}`, async () => {
      const exchange = await callMcp(session, {
        method: request.method,
        params: request.params,
        mangle: request.mangle,
        deliberate: true,
      })

      expect(exchange.deliberate).toBe(true)
      // A protocol failure, never a tool failure: the tool is not reached, so no isError exists.
      expect(exchange.outcome).toBe('protocol-error')
      expect(exchange.httpStatus).toBe(400)

      const error = (exchange.responseBody as JsonRpcResponse).error
      expect(error?.code).toBe(request.expectedCode)
      expect(error?.message).toBeTruthy()
      expect(JSON.stringify(exchange.responseBody)).not.toMatch(/isError/)
    })
  }

  it('names the versions it does support when it refuses one', async () => {
    const request = MALFORMED_REQUESTS.find((each) => each.id === 'unsupported-version')!
    const exchange = await callMcp(session, {
      method: request.method,
      params: request.params,
      mangle: request.mangle,
      deliberate: true,
    })

    const error = (exchange.responseBody as JsonRpcResponse).error
    expect(error?.data?.supported).toContain('2026-07-28')
    expect(error?.data?.requested).toBe('2025-06-18')
  })

  it('leaks nothing about itself in any of the three refusals', async () => {
    // 007 SC-006. Checked here because a malformed request is where an unguarded error path would
    // most plausibly surface a stack trace.
    for (const request of MALFORMED_REQUESTS) {
      const exchange = await callMcp(session, {
        method: request.method,
        params: request.params,
        mangle: request.mangle,
        deliberate: true,
      })
      const text = JSON.stringify(exchange.responseBody)

      expect(text).not.toMatch(/\bat [a-z.]+\(|Exception|jdbc:|select .* from /i)
      expect(text).not.toMatch(/mcp-[abc]:8080|legacy-billing-api:8080|token-issuer:8080/)
    }
  })
})
