import { describe, expect, it } from 'vitest'
import { classify } from './transport'

/**
 * T009: the four-way classification.
 *
 * Not confusing a tool failure with a protocol failure is the thing the console exists to
 * demonstrate, and the server is careful never to confuse them either. The classification is named
 * for transport.ts because that is where it lives; there is no separate outcome.ts.
 */
describe('classifying what came back', () => {
  it('calls a plain result ok', () => {
    expect(classify({ httpStatus: 200, body: { result: { resultType: 'complete' } } })).toBe('ok')
  })

  it('calls isError on a successful response a tool failure', () => {
    // The shape US2-3 asks to be made visible: the call succeeded, the tool did not.
    expect(
      classify({
        httpStatus: 200,
        body: { result: { resultType: 'complete', isError: true, content: [] } },
      }),
    ).toBe('tool-error')
  })

  it('calls a JSON-RPC error a protocol failure', () => {
    expect(classify({ httpStatus: 400, body: { error: { code: -32020, message: 'no' } } })).toBe(
      'protocol-error',
    )
  })

  it('calls an unknown tool name a protocol failure even though it arrives at HTTP 200', () => {
    // The case that breaks any classifier reading only the status. 007's error table puts -32602
    // for an unknown tool at HTTP 200 deliberately.
    expect(
      classify({ httpStatus: 200, body: { error: { code: -32602, message: 'unknown' } } }),
    ).toBe('protocol-error')
  })

  it('calls a rejected token a transport failure', () => {
    expect(classify({ httpStatus: 401, body: null })).toBe('transport-error')
  })

  it('calls a request that never arrived a transport failure', () => {
    // What a call made while the stack is shutting down looks like from here. It must reach the
    // exchange view as something explainable, never a blank result.
    expect(classify({ httpStatus: 0, body: null, networkError: 'fetch failed' })).toBe(
      'transport-error',
    )
  })
})
