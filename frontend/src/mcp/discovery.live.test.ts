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
   * It does not assert equality, because the served schema is a **subset**: the server drops
   * eighteen keywords on the way out — `enum`, `format`, `default`, `minimum`, `maximum`,
   * `minLength`, `maxLength`, `additionalProperties` — recorded as finding F-001. The console
   * therefore renders a free-text box where the contract declares a chooser, which is correct
   * behaviour against an impoverished declaration and the reason the finding exists.
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
        // `integer` arrives as `number`: the SDK's schema model does not keep the distinction.
        // schemaForm.tsx treats both as an integer field, so nothing is rendered wrongly — but it
        // is one more thing the contract says and the wire does not (finding F-001).
        const expectedType = property.type === 'integer' ? ['integer', 'number'] : [property.type]
        expect(expectedType).toContain(offered[name].type)
        // The console shows the description verbatim as help text, so what it needs is that there
        // *is* one. Equality is asserted separately below, where the two that have already drifted
        // are pinned — the server does not read these files at runtime, whatever its README says.
        expect(String(offered[name].description ?? '')).not.toBe('')
      }
      expect(served?.inputSchema.required ?? []).toEqual(contract.inputSchema.required ?? [])

      // The output shape is served as a *different document* than the committed one: it gains $id
      // and $schema and a top-level description, and loses the per-property descriptions and
      // additionalProperties. Same fields, different paperwork (finding F-001). The console only
      // renders structuredContent, so the field names are what it depends on.
      // start_billing_run is exempt, and the exemption is the finding: the committed contract's
      // outputSchema describes the *completed* run, while the server declares the *immediate*
      // handle. Both shapes are real in feature 007; they are just not the same shape, and the tool
      // declares only one of them (finding F-004). Pinned in its own test below.
      if (contract.name !== 'start_billing_run') {
        const declaredOut = (contract.outputSchema?.properties ?? {}) as Record<string, unknown>
        const offeredOut = (served?.outputSchema?.properties ?? {}) as Record<string, unknown>
        expect(Object.keys(offeredOut).sort()).toEqual(Object.keys(declaredOut).sort())
      }
      // The served `required` list is a subset of the contract's, never a superset: the server
      // never demands a field the contract does not (finding F-001). The count of what it stops
      // demanding is pinned in the test below.
      if (contract.name !== 'start_billing_run') {
        const declaredRequired = (contract.outputSchema?.required ?? []) as string[]
        const offeredRequired = (served?.outputSchema?.required ?? []) as string[]
        expect(declaredRequired).toEqual(expect.arrayContaining(offeredRequired))
      }
    }
  })

  it('still drops the keywords finding F-001 records, and no others', async () => {
    // Pinned deliberately. If this fails, the gap changed — either feature 007 fixed it, in which
    // case the console gets choosers and date fields for free and F-001 should be closed, or it
    // widened and F-001 needs updating. Either way somebody should look.
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

    expect(dropped).toHaveLength(18)
  })

  it('still drops the required output fields finding F-001 records, and no others', async () => {
    // Nineteen fields the contracts declare mandatory arrive optional. Pinned for the same reason
    // as the input keywords: a change in either direction is something somebody should look at.
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

    expect(relaxed.filter((field) => !field.startsWith('start_billing_run'))).toHaveLength(12)
  })

  it('declares the immediate handle for start_billing_run, not the completed run (F-004)', async () => {
    // The committed contract documents what a *finished* run looks like — status, phase, accounts
    // processed. The tool declaration on the wire documents what the *call* returns: a handle. A
    // client validating either against the other would reject a correct result.
    const result = resultOrThrow<ToolsListResult>(await callMcp(session, { method: 'tools/list' }))
    const served = result.tools.find((tool) => tool.name === 'start_billing_run')
    const offered = Object.keys((served?.outputSchema?.properties ?? {}) as object)

    expect(offered).toEqual(expect.arrayContaining(['run_id', 'task_id', 'poll_with']))
    expect(offered).not.toContain('accounts_processed')
  })

  it('still serves the two drifted descriptions finding F-001 records, and no others', async () => {
    // These two are not a serialisation artifact: they are differently *worded*. The server holds
    // its own copies of the tool declarations, and two of them have already drifted from the
    // committed contract — which is the failure Principle III's single-source rule exists to
    // prevent, caught here because this is the only test that compares the wire to the contract.
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

    expect(drifted).toEqual([
      'post_fee_adjustment.operation_id',
      'post_fee_adjustment.effective_date',
    ])
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
