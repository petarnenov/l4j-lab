import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { renderWithQuery } from '../../test/render'
import type { Exchange } from '../transport'
import { ExchangeView } from './ExchangeView'

function exchange(overrides: Partial<Exchange> = {}): Exchange {
  return {
    id: 'x1',
    principalName: 'advisor-alpha-101',
    targetId: 'proxy',
    method: 'tools/call',
    toolName: 'search_billing_runs',
    requestHeaders: {
      Authorization: 'Bearer «token for advisor-alpha-101 — redacted, not omitted»',
      'Mcp-Method': 'tools/call',
      'Mcp-Name': 'search_billing_runs',
      'MCP-Protocol-Version': '2026-07-28',
    },
    requestBody: { jsonrpc: '2.0', id: '1', method: 'tools/call' },
    httpStatus: 200,
    responseHeaders: { 'content-type': 'application/json' },
    responseBody: {
      jsonrpc: '2.0',
      id: '1',
      result: { resultType: 'complete', structuredContent: { total_match_count: 3 } },
    },
    durationMs: 42,
    outcome: 'ok',
    deliberate: false,
    ...overrides,
  }
}

/**
 * T024 (FR-010, SC-003) and T035 (FR-011, US2-3).
 */
describe('showing what travelled', () => {
  it('shows the structured result, readable', () => {
    renderWithQuery(<ExchangeView exchange={exchange()} />)

    expect(screen.getByRole('region', { name: /result/i })).toHaveTextContent('total_match_count')
  })

  it('shows the request and the response as they travelled, without leaving the page', () => {
    renderWithQuery(<ExchangeView exchange={exchange()} />)

    const request = screen.getByRole('region', { name: /request/i })
    expect(request).toHaveTextContent('Mcp-Method')
    expect(request).toHaveTextContent('tools/call')
    expect(request).toHaveTextContent('MCP-Protocol-Version')

    expect(screen.getByRole('region', { name: /response/i })).toHaveTextContent('resultType')
  })

  it('reports how long the call took', () => {
    renderWithQuery(<ExchangeView exchange={exchange()} />)

    expect(screen.getByRole('region', { name: /attribution/i })).toHaveTextContent(/42\s*ms/)
  })

  it('shows the bearer as a labelled redaction, not as a header it failed to send', () => {
    // Principle II forbids a credential on screen; FR-010 requires the request as it travelled.
    // Both hold: the label says redaction, so nobody reads it as an omission.
    const request =
      (renderWithQuery(<ExchangeView exchange={exchange()} />),
      screen.getByRole('region', { name: /request/i }))

    expect(request).toHaveTextContent('Authorization')
    expect(request).toHaveTextContent('redacted, not omitted')
  })
})

describe('telling the two kinds of failure apart', () => {
  it('calls a tool failure a tool failure, and says it arrived as a successful response', () => {
    renderWithQuery(
      <ExchangeView
        exchange={exchange({
          outcome: 'tool-error',
          httpStatus: 200,
          responseBody: {
            jsonrpc: '2.0',
            id: '1',
            result: {
              resultType: 'complete',
              isError: true,
              content: [{ type: 'text', text: 'No access to firm-beta.' }],
            },
          },
        })}
      />,
    )

    const outcome = screen.getByRole('region', { name: /outcome/i })
    expect(outcome).toHaveTextContent(/Tool failure/i)
    expect(outcome).toHaveTextContent('No access to firm-beta.')
    // The distinction US2-3 asks to be made visible.
    expect(outcome).toHaveTextContent(/successful response carrying an error flag/i)
  })

  it('calls a protocol failure a protocol failure, and shows its code', () => {
    renderWithQuery(
      <ExchangeView
        exchange={exchange({
          outcome: 'protocol-error',
          httpStatus: 400,
          responseBody: {
            jsonrpc: '2.0',
            id: '1',
            error: { code: -32020, message: 'Mcp-Name disagrees with params.name.' },
          },
        })}
      />,
    )

    const outcome = screen.getByRole('region', { name: /outcome/i })
    expect(outcome).toHaveTextContent(/Protocol failure/i)
    expect(outcome).toHaveTextContent('-32020')
    expect(outcome).not.toHaveTextContent(/Tool failure/i)
  })

  it('shows which versions the server does support when it refuses one', () => {
    renderWithQuery(
      <ExchangeView
        exchange={exchange({
          outcome: 'protocol-error',
          httpStatus: 400,
          responseBody: {
            jsonrpc: '2.0',
            id: '1',
            error: {
              code: -32022,
              message: 'Unsupported protocol version.',
              data: { supported: ['2026-07-28'], requested: '2025-06-18' },
            },
          },
        })}
      />,
    )

    const outcome = screen.getByRole('region', { name: /outcome/i })
    expect(outcome).toHaveTextContent('-32022')
    expect(outcome).toHaveTextContent('2026-07-28')
    expect(outcome).toHaveTextContent(/supported/i)
  })

  it('explains a request that never arrived rather than showing a blank result', () => {
    // A call made while the stack is stopping. The edge case asks for an explanation, not a void.
    renderWithQuery(
      <ExchangeView
        exchange={exchange({
          outcome: 'transport-error',
          httpStatus: 0,
          responseBody: null,
          responseHeaders: {},
          networkError: 'fetch failed',
        })}
      />,
    )

    const outcome = screen.getByRole('region', { name: /outcome/i })
    expect(outcome).toHaveTextContent(/never arrived/i)
    expect(outcome).toHaveTextContent('make mcp-up')
  })

  it('marks a deliberately malformed request as deliberate', () => {
    renderWithQuery(
      <ExchangeView
        exchange={exchange({ outcome: 'protocol-error', deliberate: true, httpStatus: 400 })}
      />,
    )

    expect(screen.getByRole('region', { name: /attribution/i })).toHaveTextContent(/deliberate/i)
  })
})

describe('attribution', () => {
  it('names the principal the result was obtained as', () => {
    renderWithQuery(<ExchangeView exchange={exchange()} />)

    expect(screen.getByRole('region', { name: /attribution/i })).toHaveTextContent(
      'advisor-alpha-101',
    )
  })

  it('names the target, and says the proxy does not report which replica answered', () => {
    // serverInfo carries {name, version} and no instance identity, and nginx adds no upstream
    // header. Via the proxy the information does not exist, so the console must not invent it.
    renderWithQuery(<ExchangeView exchange={exchange({ targetId: 'proxy' })} />)

    expect(screen.getByRole('region', { name: /attribution/i })).toHaveTextContent(
      /does not report which/i,
    )
  })

  it('names the replica when one was addressed directly', () => {
    renderWithQuery(<ExchangeView exchange={exchange({ targetId: 'b' })} />)

    const attribution = screen.getByRole('region', { name: /attribution/i })
    expect(attribution).toHaveTextContent('mcp-b')
    expect(attribution).not.toHaveTextContent(/does not report which/i)
  })
})
