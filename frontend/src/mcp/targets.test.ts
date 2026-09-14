import { describe, expect, it } from 'vitest'
import { TARGET_IDS, absenceReason, absoluteTargets, devProxyTargets, healthPath } from './targets'

/**
 * T010: where a call can be aimed, and what the console says when it cannot be.
 */
describe('target addresses', () => {
  it('offers the proxy and the three replicas', () => {
    expect(TARGET_IDS).toEqual(['proxy', 'a', 'b', 'c'])
  })

  it('addresses each through the development forwarder in the browser', () => {
    expect(devProxyTargets.baseUrl('proxy')).toBe('/mcp-dev/proxy')
    expect(devProxyTargets.baseUrl('b')).toBe('/mcp-dev/b')
  })

  it('addresses each absolutely when there is no forwarder', () => {
    // The live suite runs outside `vite dev`, where nothing rewrites the prefix. The seam exists
    // because targets are plural and discovered at runtime, not because tests wanted one.
    const targets = absoluteTargets({
      proxy: 'http://localhost:8877',
      a: 'http://localhost:8881',
      b: 'http://localhost:8882',
      c: 'http://localhost:8883',
    })

    expect(targets.baseUrl('proxy')).toBe('http://localhost:8877')
    expect(targets.baseUrl('a')).toBe('http://localhost:8881')
  })

  it('probes the proxy and a replica at the endpoints each one actually serves', () => {
    // nginx serves /lb-health and routes /dev/ to the issuer; a replica serves neither, but does
    // serve the readiness endpoint its Compose health check already uses.
    expect(healthPath('proxy')).toBe('/lb-health')
    expect(healthPath('a')).toBe('/health/readiness')
  })
})

describe('explaining an unreachable target', () => {
  it('tells someone how to start the stack when the proxy does not answer', () => {
    expect(absenceReason('proxy')).toContain('make mcp-up')
  })

  it('names the overlay when a replica is simply not published', () => {
    // All three unreachable is the normal state of a plain `make mcp-up`, not a fault: the base
    // stack publishes one port on purpose.
    const reason = absenceReason('a')

    expect(reason).toContain('make mcp-up-topology')
    expect(reason).toContain('mcp-a')
  })

  it('does not tell someone to start a stack that is already running', () => {
    expect(absenceReason('a')).not.toContain('make mcp-up ')
  })
})
