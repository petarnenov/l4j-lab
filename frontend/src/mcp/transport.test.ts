import { describe, expect, it } from 'vitest'
import { PROTOCOL_VERSION, buildRequest, nextJsonRpcId, redactAuthorization } from './transport'

/**
 * T008: the envelope the console puts on the wire.
 *
 * The mirroring below is the whole reason feature 007's server can reject a request without reading
 * it, so it is built per request from the body rather than from a stored template: a copy-pasted
 * header set agrees with the body until the day someone edits one of the two.
 */
describe('the request envelope', () => {
  it('mirrors the protocol version in both the header and _meta', () => {
    const { headers, body } = buildRequest({ method: 'tools/list', params: {} })

    expect(headers['MCP-Protocol-Version']).toBe(PROTOCOL_VERSION)
    expect(body.params._meta['io.modelcontextprotocol/protocolVersion']).toBe(PROTOCOL_VERSION)
    expect(headers['MCP-Protocol-Version']).toBe(
      body.params._meta['io.modelcontextprotocol/protocolVersion'],
    )
  })

  it('mirrors the method in Mcp-Method', () => {
    expect(buildRequest({ method: 'tasks/get', params: {} }).headers['Mcp-Method']).toBe('tasks/get')
  })

  it('sends Mcp-Name on tools/call, mirroring params.name', () => {
    const { headers, body } = buildRequest({
      method: 'tools/call',
      params: { name: 'search_billing_runs', arguments: { firm_id: 'firm-alpha' } },
    })

    expect(headers['Mcp-Name']).toBe('search_billing_runs')
    expect(headers['Mcp-Name']).toBe(body.params.name)
  })

  it('omits Mcp-Name when there is no tool to name', () => {
    expect(buildRequest({ method: 'tools/list', params: {} }).headers).not.toHaveProperty('Mcp-Name')
  })

  it('carries the content type and accept the revision requires', () => {
    const { headers } = buildRequest({ method: 'tools/list', params: {} })

    expect(headers['Content-Type']).toBe('application/json')
    expect(headers['Accept']).toBe('application/json, text/event-stream')
  })

  it('declares both capabilities it actually implements, and identifies itself', () => {
    const { body } = buildRequest({ method: 'tools/list', params: {} })
    const meta = body.params._meta

    // Declaring a capability the console cannot honour is the one thing the client contract forbids
    // outright. elicitation is declared because the confirmation round trip is implemented; the
    // tasks extension because tasks/get is polled.
    expect(meta['io.modelcontextprotocol/clientCapabilities']).toEqual({
      elicitation: {},
      extensions: { 'io.modelcontextprotocol/tasks': {} },
    })
    expect(meta['io.modelcontextprotocol/clientInfo'].name).toBe('l4j-mcp-console')
    expect(meta['io.modelcontextprotocol/clientInfo'].version).toMatch(/^\d+\.\d+\.\d+/)
  })

  it('is JSON-RPC 2.0 with the id it was given', () => {
    const { body } = buildRequest({ method: 'tools/list', params: {}, id: 'x1' })

    expect(body.jsonrpc).toBe('2.0')
    expect(body.id).toBe('x1')
  })
})

describe('JSON-RPC ids', () => {
  it('are monotonic strings', () => {
    const first = nextJsonRpcId()
    const second = nextJsonRpcId()

    expect(typeof first).toBe('string')
    expect(Number(second)).toBeGreaterThan(Number(first))
  })

  it('give a confirmation retry a different id from the call it retries', () => {
    // 007's contract requires it, and the exchange view shows both so the difference is visible
    // rather than asserted.
    const first = buildRequest({ method: 'tools/call', params: { name: 'post_fee_adjustment' } })
    const retry = buildRequest({
      method: 'tools/call',
      params: { name: 'post_fee_adjustment', requestState: 'opaque' },
    })

    expect(retry.body.id).not.toBe(first.body.id)
  })
})

describe('the bearer token', () => {
  it('is replaced by a labelled redaction naming the principal, not by a missing header', () => {
    // Principle II forbids a credential on screen; FR-010 requires the request as it travelled.
    // Everything that decides what the server did stays visible; the secret does not.
    const redacted = redactAuthorization(
      { Authorization: 'Bearer eyJhbGciOi.realpayload.signature', 'Mcp-Method': 'tools/list' },
      'advisor-alpha-101',
    )

    expect(redacted.Authorization).toContain('Bearer')
    expect(redacted.Authorization).toContain('advisor-alpha-101')
    expect(redacted.Authorization).not.toContain('realpayload')
    expect(redacted.Authorization).not.toContain('signature')
  })

  it('leaves every other header byte-for-byte', () => {
    const headers = {
      Authorization: 'Bearer a.b.c',
      'Mcp-Method': 'tools/call',
      'Mcp-Name': 'search_billing_runs',
      'MCP-Protocol-Version': PROTOCOL_VERSION,
    }

    const redacted = redactAuthorization(headers, 'admin-alpha')

    expect(redacted['Mcp-Method']).toBe('tools/call')
    expect(redacted['Mcp-Name']).toBe('search_billing_runs')
    expect(redacted['MCP-Protocol-Version']).toBe(PROTOCOL_VERSION)
  })
})
