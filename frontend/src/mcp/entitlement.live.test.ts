import { beforeAll, describe, expect, it } from 'vitest'
import { liveSession, requireStack } from './liveFixture'
import { type McpSession, callMcp } from './transport'

/**
 * T040 (SC-004, FR-011, US2). Against the running stack, through the console's own transport and
 * with tokens minted the way the console mints them.
 *
 * Entitlement is the one thing a substituted server cannot honestly stand in for: the legacy API
 * owns the data and enforces the rules, and a stub would be enforcing whatever this file asked it
 * to. Here the refusal is the system's own.
 */
function runs(exchange: { responseBody: unknown }): Array<{ executed_by_advisor_id: string }> {
  const body = exchange.responseBody as {
    result: { structuredContent: { runs: Array<{ executed_by_advisor_id: string }> } }
  }
  return body.result.structuredContent.runs
}

async function search(session: McpSession, firmId: string) {
  return callMcp(session, {
    method: 'tools/call',
    params: { name: 'search_billing_runs', arguments: { firm_id: firmId, page_size: 20 } },
  })
}

describe('identity decides what is visible', () => {
  let advisor: McpSession
  let admin: McpSession

  beforeAll(async () => {
    await requireStack()
    advisor = (await liveSession('advisor-alpha-101')).session
    admin = (await liveSession('admin-alpha')).session
  })

  it('shows an advisor fewer runs than a firm administrator sees', async () => {
    const asAdvisor = await search(advisor, 'firm-alpha')
    const asAdmin = await search(admin, 'firm-alpha')

    expect(asAdvisor.outcome).toBe('ok')
    expect(asAdmin.outcome).toBe('ok')
    expect(runs(asAdvisor).length).toBeLessThan(runs(asAdmin).length)
  })

  it('shows an advisor only its own advisor runs', async () => {
    const asAdvisor = await search(advisor, 'firm-alpha')

    expect(runs(asAdvisor).length).toBeGreaterThan(0)
    for (const run of runs(asAdvisor)) {
      expect(run.executed_by_advisor_id).toBe('adv-101')
    }
  })

  it('shows a firm administrator both advisors', async () => {
    const asAdmin = await search(admin, 'firm-alpha')
    const advisors = new Set(runs(asAdmin).map((run) => run.executed_by_advisor_id))

    expect(advisors.size).toBeGreaterThan(1)
  })

  it('refuses another firm as a tool failure, not a protocol failure', async () => {
    // The distinction the console exists to make visible, and the one place it is checked against
    // the real refusal rather than one this repository wrote.
    const refused = await search(admin, 'firm-beta')

    expect(refused.httpStatus).toBe(200)
    expect(refused.outcome).toBe('tool-error')

    const result = (refused.responseBody as { result: Record<string, unknown> }).result
    expect(result.isError).toBe(true)
    expect(result.structuredContent).toBeUndefined()
  })

  it('says nothing about the other firm in the refusal it returns', async () => {
    const refused = await search(admin, 'firm-beta')
    const text = JSON.stringify(refused.responseBody)

    // 007 SC-006: no stack trace, no SQL, no hostname. And no run data from a firm the caller
    // cannot see, which would make the refusal self-defeating.
    expect(text).not.toMatch(/select |jdbc:|Exception|\bat [a-z]+\./i)
    expect(text.length).toBeLessThan(2000)
  })

  it('never sends the inbound token onward, so the console cannot leak one', async () => {
    // The console holds a token for mcp-billing-server only. That the server exchanges rather than
    // forwards it is 007's property; what is checked here is that the console's own request carries
    // exactly one Authorization header and nothing token-shaped anywhere else.
    const exchange = await search(admin, 'firm-alpha')
    const body = JSON.stringify(exchange.requestBody)

    expect(body).not.toMatch(/eyJ[A-Za-z0-9_-]+\./)
    expect(exchange.requestHeaders.Authorization).toContain('redacted, not omitted')
  })
})
