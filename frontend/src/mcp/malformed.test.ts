import { describe, expect, it } from 'vitest'
import { MALFORMED_REQUESTS } from './malformed'
import { PROTOCOL_VERSION, buildRequest } from './transport'

/**
 * T059 (FR-011a). Three prepared requests, each wrong in exactly one way.
 *
 * The catalogue is fixed in code. There is no free-form editor for headers or body: that is a
 * different tool with a different purpose, and it already exists — it is called curl.
 */
function mangled(id: string) {
  const request = MALFORMED_REQUESTS.find((candidate) => candidate.id === id)!
  return request.mangle(buildRequest({ method: request.method, params: request.params }))
}

describe('the prepared malformed requests', () => {
  it('is a fixed set of three, and offers no way to compose a fourth', () => {
    expect(MALFORMED_REQUESTS).toHaveLength(3)
    expect(MALFORMED_REQUESTS.map((request) => request.id)).toEqual([
      'header-disagrees',
      'unsupported-version',
      'missing-header',
    ])
  })

  it('explains what is wrong before it is sent', () => {
    for (const request of MALFORMED_REQUESTS) {
      expect(request.label.length).toBeGreaterThan(8)
      expect(request.explanation.length).toBeGreaterThan(40)
      expect(request.expectedCode).toBeLessThan(0)
    }
  })

  describe('a header disagreeing with the body', () => {
    it('names one tool in the header and another in the body', () => {
      const { headers, body } = mangled('header-disagrees')

      expect(headers['Mcp-Name']).not.toBe(body.params.name)
    })

    it('leaves everything else correct, so the refusal is about the disagreement', () => {
      const { headers, body } = mangled('header-disagrees')

      expect(headers['Mcp-Method']).toBe(body.method)
      expect(headers['MCP-Protocol-Version']).toBe(PROTOCOL_VERSION)
      expect(headers['Content-Type']).toBe('application/json')
    })
  })

  describe('an unimplemented version', () => {
    it('announces the same old version in both the header and _meta', () => {
      // They must agree, or the server answers -32020 first and this case silently demonstrates
      // the previous one instead.
      const { headers, body } = mangled('unsupported-version')

      expect(headers['MCP-Protocol-Version']).toBe('2025-06-18')
      expect(body.params._meta['io.modelcontextprotocol/protocolVersion']).toBe('2025-06-18')
    })

    it('keeps the method header mirroring the body', () => {
      const { headers, body } = mangled('unsupported-version')

      expect(headers['Mcp-Method']).toBe(body.method)
    })
  })

  describe('a required header omitted', () => {
    it('omits Mcp-Method and nothing else', () => {
      const { headers } = mangled('missing-header')

      expect(headers).not.toHaveProperty('Mcp-Method')
      expect(headers['MCP-Protocol-Version']).toBe(PROTOCOL_VERSION)
      expect(headers['Content-Type']).toBe('application/json')
      expect(headers['Accept']).toBe('application/json, text/event-stream')
    })

    it('still sends a well-formed body', () => {
      const { body } = mangled('missing-header')

      expect(body.jsonrpc).toBe('2.0')
      expect(body.method).toBe('tools/list')
    })
  })
})
