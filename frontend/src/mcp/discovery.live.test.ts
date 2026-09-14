import { beforeAll, describe, expect, it } from 'vitest'
import { liveSession, requireStack } from './liveFixture'
import { TOOL_CONTRACTS } from './test/mcpHandlers'
import { type McpSession, callMcp, resultOrThrow } from './transport'
import type { DiscoverResult, ToolsListResult } from './wire'

/**
 * T033 (SC-002, SC-008). Against the running stack, through the console's own transport.
 *
 * Nothing here builds a request by hand. The point is not that the server answers — feature 007's
 * own suite proves that — but that *this console* speaks to it: the headers it builds are accepted,
 * the result shapes it branches on are the ones that arrive, and the envelope fields wire.ts
 * declares are really there. A hand-written fetch would prove none of it.
 */
describe('discovery and the tool list, against the running stack', () => {
  let session: McpSession

  beforeAll(async () => {
    await requireStack()
    session = (await liveSession('admin-alpha')).session
  })

  it('accepts the headers the console builds', async () => {
    const exchange = await callMcp(session, { method: 'server/discover' })

    // If the mirroring in transport.ts were wrong, this would be -32020 and nothing else here
    // would be worth reading.
    expect(exchange.outcome).toBe('ok')
    expect(exchange.httpStatus).toBe(200)
  })

  it('says which versions it speaks, and identifies itself', async () => {
    const result = resultOrThrow<DiscoverResult>(
      await callMcp(session, { method: 'server/discover' }),
    )

    expect(result.resultType).toBe('complete')
    expect(result.supportedVersions).toContain('2026-07-28')
    expect(result._meta?.['io.modelcontextprotocol/serverInfo']?.name).toBe('mcp-billing-server')
    expect(result.capabilities).toHaveProperty('tools')
  })

  it('returns the five tools in the documented order', async () => {
    const result = resultOrThrow<ToolsListResult>(await callMcp(session, { method: 'tools/list' }))

    expect(result.tools.map((tool) => tool.name)).toEqual(TOOL_CONTRACTS.map((tool) => tool.name))
  })

  it('returns the four declared hints the committed contracts state', async () => {
    // The committed files are the oracle for the four hints FR-007 requires the console to show. A
    // hint flipped in the server without the contract changing fails here, which is the only place
    // in this repository that would notice.
    //
    // Deliberately not a deep equality on `annotations`: the served object is a *superset*. The MCP
    // Java SDK's ToolAnnotations record adds `title: ""` and `returnDirect: false` of its own, so
    // the contracts are not served quite as verbatim as 007's contracts/README says. Harmless for
    // the console — it reads the four hints — and recorded as a finding rather than worked around
    // silently.
    const result = resultOrThrow<ToolsListResult>(await callMcp(session, { method: 'tools/list' }))

    for (const contract of TOOL_CONTRACTS) {
      const served = result.tools.find((tool) => tool.name === contract.name)
      expect(served, `the server did not serve ${contract.name}`).toBeDefined()
      expect(served?.annotations).toMatchObject(contract.annotations)
    }
  })

  /**
   * FR-008 builds every argument field from the served input shape, so what matters is that the
   * server declares every property the contract declares, with the same type and the same
   * description a person reads as help text.
   *
   * It once could not assert equality, because the served schema was a **subset**: eighteen keywords
   * were dropped on the way out and the console rendered a free-text box where the contract declared
   * a chooser. Feature 010 closed that (F-001) by making the Java the single source and generating
   * the committed files from what ships, so the two are now the same declaration rather than two
   * that happen to agree. The tests below assert the absence of drift directly.
   *
   * The deterministic suite cannot see any of this: it serves the committed files itself, so both
   * sides of that comparison are the same object.
   */
  it('declares every property the contracts declare, with the same type and description', async () => {
    const result = resultOrThrow<ToolsListResult>(await callMcp(session, { method: 'tools/list' }))

    for (const contract of TOOL_CONTRACTS) {
      const served = result.tools.find((tool) => tool.name === contract.name)
      const declared = contract.inputSchema.properties as Record<string, Record<string, unknown>>
      const offered = served?.inputSchema.properties as Record<string, Record<string, unknown>>

      expect(Object.keys(offered).sort()).toEqual(Object.keys(declared).sort())
      for (const [name, property] of Object.entries(declared)) {
        // `integer` used to arrive as `number`, because the generator does not keep the distinction
        // and nothing restored it. It is restored now, so the tolerance below is kept only for a
        // property no constraint covers — where the generator is still the only source of the type.
        const expectedType = property.type === 'integer' ? ['integer', 'number'] : [property.type]
        expect(expectedType).toContain(offered[name].type)
        // The console shows the description verbatim as help text. Equality is asserted separately
        // below; here it only has to exist.
        expect(String(offered[name].description ?? '')).not.toBe('')
      }
      expect(served?.inputSchema.required ?? []).toEqual(contract.inputSchema.required ?? [])

      // start_billing_run was exempt, and the exemption was the finding: the committed contract
      // described the *completed* run while the server declared the *immediate* handle. Research
      // R-005 found the server right; the contract is generated from it now and both describe the
      // handle. The exemption is kept because the committed file is regenerated from the wire, so
      // asserting it here would be asserting that a file equals itself.
      if (contract.name !== 'start_billing_run') {
        const declaredOut = (contract.outputSchema?.properties ?? {}) as Record<string, unknown>
        const offeredOut = (served?.outputSchema?.properties ?? {}) as Record<string, unknown>
        expect(Object.keys(offeredOut).sort()).toEqual(Object.keys(declaredOut).sort())
      }
      // The served `required` list must never be a superset of the contract's: the server may not
      // demand a field the contract does not. It is now exactly equal, which the test below asserts.
      if (contract.name !== 'start_billing_run') {
        const declaredRequired = (contract.outputSchema?.required ?? []) as string[]
        const offeredRequired = (served?.outputSchema?.required ?? []) as string[]
        expect(declaredRequired).toEqual(expect.arrayContaining(offeredRequired))
      }
    }
  })

  it('drops no keyword the contracts declare (F-001, closed)', async () => {
    // This used to require exactly eighteen dropped keywords and explain why they were missing. The
    // explanation is gone because the gap is: feature 010 made the Java declarations the single
    // source, gave them the keywords the generator could not infer, and generates the committed
    // contracts from what ships. The console gets its choosers and date fields for free.
    const result = resultOrThrow<ToolsListResult>(await callMcp(session, { method: 'tools/list' }))
    const keywords = ['enum', 'format', 'default', 'minimum', 'maximum', 'minLength', 'maxLength']
    const dropped: string[] = []

    for (const contract of TOOL_CONTRACTS) {
      const served = result.tools.find((tool) => tool.name === contract.name)
      if (contract.inputSchema.additionalProperties !== undefined) {
        if (served?.inputSchema.additionalProperties === undefined) {
          dropped.push(`${contract.name}.additionalProperties`)
        }
      }
      const declared = contract.inputSchema.properties as Record<string, Record<string, unknown>>
      const offered = served?.inputSchema.properties as Record<string, Record<string, unknown>>
      for (const [name, property] of Object.entries(declared)) {
        for (const keyword of keywords) {
          if (property[keyword] !== undefined && offered[name]?.[keyword] === undefined) {
            dropped.push(`${contract.name}.${name}.${keyword}`)
          }
        }
      }
    }

    expect(dropped).toEqual([])
  })

  it('relaxes no required output field the contracts declare (F-001, closed)', async () => {
    // Twelve fields declared mandatory used to arrive optional. Both sides now come from one
    // declaration, so "declared" and "served" are the same list rather than two that agree.
    const result = resultOrThrow<ToolsListResult>(await callMcp(session, { method: 'tools/list' }))
    const relaxed: string[] = []

    for (const contract of TOOL_CONTRACTS) {
      const served = result.tools.find((tool) => tool.name === contract.name)
      const declared = (contract.outputSchema?.required ?? []) as string[]
      const offered = new Set((served?.outputSchema?.required ?? []) as string[])
      relaxed.push(
        ...declared.filter((field) => !offered.has(field)).map((f) => `${contract.name}.${f}`),
      )
    }

    expect(relaxed).toEqual([])
  })

  it('declares the immediate handle for start_billing_run, not the completed run (F-004, closed)', async () => {
    // The committed contract used to document what a *finished* run looks like — status, phase,
    // accounts processed — while the wire documented what the *call* returns: a handle. Research
    // R-005 found the server right and the contract wrong, and the contract is now generated from
    // the server, so both describe the handle. The assertion is unchanged: it was always correct.
    const result = resultOrThrow<ToolsListResult>(await callMcp(session, { method: 'tools/list' }))
    const served = result.tools.find((tool) => tool.name === 'start_billing_run')
    const offered = Object.keys((served?.outputSchema?.properties ?? {}) as object)

    expect(offered).toEqual(expect.arrayContaining(['run_id', 'task_id', 'poll_with']))
    expect(offered).not.toContain('accounts_processed')
  })

  it('serves no description that has drifted from the contract (F-001, closed)', async () => {
    // Two were differently *worded* — not a serialisation artifact but two hand-written texts, which
    // is exactly what Principle III's single-source rule exists to prevent. There is now one text
    // per description, in the Java, and the committed file is written from it.
    const result = resultOrThrow<ToolsListResult>(await callMcp(session, { method: 'tools/list' }))
    const drifted: string[] = []

    for (const contract of TOOL_CONTRACTS) {
      const served = result.tools.find((tool) => tool.name === contract.name)
      const declared = contract.inputSchema.properties as Record<string, Record<string, unknown>>
      const offered = served?.inputSchema.properties as Record<string, Record<string, unknown>>
      for (const [name, property] of Object.entries(declared)) {
        if (offered[name]?.description !== property.description) {
          drifted.push(`${contract.name}.${name}`)
        }
      }
    }

    expect(drifted).toEqual([])
  })

  it('returns the same order twice, and says the list is cacheable', async () => {
    const first = resultOrThrow<ToolsListResult>(await callMcp(session, { method: 'tools/list' }))
    const second = resultOrThrow<ToolsListResult>(await callMcp(session, { method: 'tools/list' }))

    expect(second.tools.map((t) => t.name)).toEqual(first.tools.map((t) => t.name))
    expect(first.ttlMs).toBeGreaterThan(0)
    // public, because entitlements filter results and never the tool set.
    expect(first.cacheScope).toBe('public')
  })

  /**
   * SC-002 says *every* one of the five can be exercised. This is the only place that is checked:
   * get_billing_run_status and get_run_failures appear in no other test, and "covered by the
   * generic path" is an argument, not evidence.
   */
  it('can call all five tools and render what each returns', async () => {
    const list = resultOrThrow<ToolsListResult>(await callMcp(session, { method: 'tools/list' }))
    const search = await callMcp(session, {
      method: 'tools/call',
      params: { name: 'search_billing_runs', arguments: { firm_id: 'firm-alpha', page_size: 1 } },
    })
    expect(search.outcome).toBe('ok')
    const runId = (
      search.responseBody as { result: { structuredContent: { runs: Array<{ run_id: string }> } } }
    ).result.structuredContent.runs[0].run_id

    const args: Record<string, Record<string, unknown>> = {
      search_billing_runs: { firm_id: 'firm-alpha', page_size: 1 },
      get_billing_run_status: { run_id: runId },
      get_run_failures: { run_id: runId },
      post_fee_adjustment: {
        operation_id: `console-list-${runId}`,
        account_id: 'acc-0101',
        delta_bps: 5,
        effective_date: '2026-10-01',
      },
      start_billing_run: { firm_id: 'firm-alpha', executed_by_advisor_id: 'adv-101' },
    }

    for (const tool of list.tools) {
      const exchange = await callMcp(session, {
        method: 'tools/call',
        params: { name: tool.name, arguments: args[tool.name] },
      })

      // ok, a tool failure, an elicitation or a task handle are all answers the console renders.
      // A *protocol* failure is not: it would mean the console built the request wrongly.
      expect(exchange.outcome).not.toBe('protocol-error')
      expect(exchange.outcome).not.toBe('transport-error')
    }
  })
})
