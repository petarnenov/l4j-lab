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
interface SearchResult {
  runs: Array<{ executed_by_advisor_id: string }>
  total_match_count: number
}

function searchResult(exchange: { responseBody: unknown }): SearchResult {
  return (exchange.responseBody as { result: { structuredContent: SearchResult } }).result
    .structuredContent
}

function runs(exchange: { responseBody: unknown }) {
  return searchResult(exchange).runs
}

async function filterByAdvisor(session: McpSession, advisorId: string) {
  return callMcp(session, {
    method: 'tools/call',
    params: {
      name: 'search_billing_runs',
      arguments: { firm_id: 'firm-alpha', advisor_id: advisorId, page_size: 5 },
    },
  })
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
    // Compared on the total, not on the page: a page is capped at twenty, and the seeded data
    // grows every time the long-operation suite starts a run, so two full pages would tie.
    expect(searchResult(asAdvisor).total_match_count).toBeLessThan(
      searchResult(asAdmin).total_match_count,
    )
  })

  it('shows an advisor only its own advisor runs', async () => {
    const asAdvisor = await search(advisor, 'firm-alpha')

    expect(runs(asAdvisor).length).toBeGreaterThan(0)
    for (const run of runs(asAdvisor)) {
      expect(run.executed_by_advisor_id).toBe('adv-101')
    }
  })

  it('lets a firm administrator reach the other advisor', async () => {
    // Asked as a filter rather than by counting advisors on page one. The seeded data drifts as
    // the console is used — the long-operation suite starts runs for adv-101 — so a page of twenty
    // is no longer guaranteed to contain both. The entitlement question is not about page shape.
    const adminSeesOther = await filterByAdvisor(admin, 'adv-102')

    expect(adminSeesOther.outcome).toBe('ok')
    expect(searchResult(adminSeesOther).total_match_count).toBeGreaterThan(0)
  })

  /**
   * Finding F-006, pinned. An advisor asking for another advisor's runs — inside its own firm —
   * gets HTTP 500 and JSON-RPC -32603 "message must not be empty", instead of the tool error the
   * cross-firm path correctly returns.
   *
   * -32603 is not in 007's error mapping table at all, the status contradicts its rule that an
   * entitlement refusal is a tool failure at HTTP 200, and the message is an internal validation
   * complaint, which SC-006 says must never reach a caller.
   *
   * Asserted as it behaves rather than as it should, so the console's rendering is tested against
   * reality. When the server is fixed this fails, and that is the notification.
   */
  it('still answers a cross-advisor search with an internal error (F-006)', async () => {
    const refused = await filterByAdvisor(advisor, 'adv-102')

    expect(refused.httpStatus).toBe(500)
    expect(refused.outcome).toBe('protocol-error')
    expect((refused.responseBody as { error: { code: number } }).error.code).toBe(-32603)
  })

  it('answers an unknown advisor the same way, so it is the path and not the entitlement (F-006)', async () => {
    const unknown = await filterByAdvisor(advisor, 'adv-does-not-exist')

    expect(unknown.httpStatus).toBe(500)
    expect((unknown.responseBody as { error: { code: number } }).error.code).toBe(-32603)
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
