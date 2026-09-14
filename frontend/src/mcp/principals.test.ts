import { describe, expect, it } from 'vitest'
import { PRINCIPAL_NAMES, decodeClaims, mintToken, tokenEndpoint } from './principals'
import { devProxyTargets } from './targets'
import { fixtureToken } from './test/mcpHandlers'

/**
 * T011: who the console can act as, and where the four facts about them come from.
 */
describe('the fixture principals', () => {
  it('offers the six the issuer defines, and adds none', () => {
    expect(PRINCIPAL_NAMES).toEqual([
      'advisor-alpha-101',
      'advisor-alpha-102',
      'admin-alpha',
      'ops-alpha',
      'readonly-alpha',
      'admin-beta',
    ])
  })

  it('mints through the proxy target rather than a hardcoded path', () => {
    // A replica serves neither /dev/ nor /.well-known/ — only nginx routes those to the issuer. The
    // literal browser path would also leave the live suite unable to mint a token at all.
    expect(tokenEndpoint(devProxyTargets)).toBe('/mcp-dev/proxy/dev/token')
  })
})

describe('what the console knows about a principal', () => {
  it('reads user, firm, role and permitted advisors from the token it was given', () => {
    // Not from a table copied out of the issuer's contract: a copy would be believed after it went
    // stale, and these are the claims the server will actually act on.
    const claims = decodeClaims(fixtureToken('admin-alpha'))

    expect(claims.userId).toBe('usr-900')
    expect(claims.firmId).toBe('firm-alpha')
    expect(claims.role).toBe('FIRM_ADMIN')
    expect(claims.advisorIds).toEqual(['adv-101', 'adv-102'])
  })

  it('narrows to one advisor for an advisor principal', () => {
    expect(decodeClaims(fixtureToken('advisor-alpha-101')).advisorIds).toEqual(['adv-101'])
  })

  it('takes expiry from the token rather than from a call that failed', () => {
    const claims = decodeClaims(fixtureToken('ops-alpha', 60))

    expect(claims.expiresAt.getTime()).toBeGreaterThan(Date.now())
    expect(claims.expiresAt.getTime()).toBeLessThan(Date.now() + 61_000)
  })

  it('refuses a token it cannot read rather than showing invented claims', () => {
    expect(() => decodeClaims('not-a-jwt')).toThrow()
  })
})

describe('minting', () => {
  it('obtains the credential itself, so nobody has to paste one', async () => {
    const minted = await mintToken('advisor-alpha-102', devProxyTargets)

    expect(minted.token.split('.')).toHaveLength(3)
    expect(minted.claims.userId).toBe('usr-102')
  })

  it('keeps the token out of localStorage and every other shared store', async () => {
    // Principle II forbids persisting a credential. It is also what keeps the "two people using the
    // console at once" edge case true: the choice and its token stay in tab-local memory.
    await mintToken('admin-beta', devProxyTargets)

    const stored = Object.keys(window.localStorage)
    expect(stored).toHaveLength(0)
    expect(window.sessionStorage.length).toBe(0)
  })
})
