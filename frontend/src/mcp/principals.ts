import type { TargetResolver } from './targets'

/**
 * Who the console can act as, and where the four facts about them come from.
 *
 * Only the six **names** are written down here. Everything FR-004 requires the console to show —
 * user, firm, role, permitted advisors — is read from the claims of the token the issuer mints,
 * because those are the claims the server will actually act on. Copying the table out of
 * `007/contracts/token-issuer.md` would put a second declaration in the console, and the copy would
 * go on being believed after it went stale.
 *
 * The names have to be written down somewhere: the issuer offers no endpoint that lists them. A
 * list of strings is the minimum, and it is not a table of claims.
 */

export const PRINCIPAL_NAMES = [
  'advisor-alpha-101',
  'advisor-alpha-102',
  'admin-alpha',
  'ops-alpha',
  'readonly-alpha',
  'admin-beta',
] as const

export type PrincipalName = (typeof PRINCIPAL_NAMES)[number]

/** The audience the MCP server checks. A token minted for anything else is rejected with 401. */
export const MCP_AUDIENCE = 'mcp-billing-server'

export interface PrincipalClaims {
  userId: string
  firmId: string
  role: string
  advisorIds: string[]
  expiresAt: Date
}

export interface MintedToken {
  token: string
  claims: PrincipalClaims
}

/**
 * The issuer is reachable **only** through the proxy: deploy/mcp/nginx.conf routes /dev/ and
 * /.well-known/ to it, and an individual replica serves neither. So every token is minted through
 * the proxy target whatever target the subsequent call is aimed at — which is also honest, since
 * minting is not part of the protocol being demonstrated.
 *
 * Resolved through the target resolver rather than written as a literal path: the literal is a
 * browser-only path, and nothing rewrites the prefix outside `vite dev`.
 */
export function tokenEndpoint(targets: TargetResolver): string {
  return `${targets.baseUrl('proxy')}/dev/token`
}

function decodeSegment(segment: string): Record<string, unknown> {
  const padded = segment.replace(/-/g, '+').replace(/_/g, '/')
  return JSON.parse(atob(padded)) as Record<string, unknown>
}

/**
 * Reads a token's claims **without verifying it**, for display only.
 *
 * This is not authentication and must never be mistaken for it: no signature is checked, no issuer
 * is consulted, and nothing here decides what the console may do. The server verifies; this only
 * shows a person which identity they are about to act as. Feature 007's server and legacy API are
 * the only things that validate a token, and they use the issuer's public key to do it.
 */
export function decodeClaims(token: string): PrincipalClaims {
  const segments = token.split('.')
  if (segments.length !== 3) {
    throw new Error('That is not a JWT: a token has three dot-separated segments.')
  }
  const payload = decodeSegment(segments[1])
  const advisorIds = payload.advisor_ids
  return {
    userId: String(payload.sub ?? ''),
    firmId: String(payload.firm_id ?? ''),
    role: String(payload.role ?? ''),
    advisorIds: Array.isArray(advisorIds) ? advisorIds.map(String) : [],
    expiresAt: new Date(Number(payload.exp ?? 0) * 1000),
  }
}

/**
 * FR-005: the console obtains the credential itself. Nobody mints or pastes one.
 *
 * The token is returned, never stored anywhere that outlives the tab. Principle II forbids
 * persisting a credential, and keeping it in memory is also what keeps the "two people using the
 * console at once" edge case true.
 */
export async function mintToken(
  principal: PrincipalName | string,
  targets: TargetResolver,
  fetchImpl: typeof fetch = fetch,
): Promise<MintedToken> {
  const response = await fetchImpl(tokenEndpoint(targets), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ principal, audience: MCP_AUDIENCE }),
  })
  if (!response.ok) {
    throw new Error(
      `The token issuer refused to mint for ${principal} (HTTP ${response.status}). It is ` +
        `development-only and reachable through the proxy; check that the stack is running.`,
    )
  }
  const body = (await response.json()) as { token: string }
  return { token: body.token, claims: decodeClaims(body.token) }
}

/** True while the token has more than a minute left, so a call never fails merely for being late. */
export function isUsable(claims: PrincipalClaims, now: number = Date.now()): boolean {
  return claims.expiresAt.getTime() - now > 60_000
}
