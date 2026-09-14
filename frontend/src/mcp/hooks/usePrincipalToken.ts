import { useQuery } from '@tanstack/react-query'
import { type MintedToken, type PrincipalName, mintToken } from '../principals'
import { type TargetResolver, devProxyTargets } from '../targets'

/**
 * FR-005: the console obtains the credential itself; nobody mints or pastes one.
 *
 * The token lives in the query cache, which is memory for the life of the tab, and nowhere else.
 * Principle II forbids persisting a credential, and it is also what keeps the "two people using the
 * console at once" edge case true without a line of code being written for it.
 *
 * A token is good for an hour. Refetching after fifty minutes means the first call of the second
 * hour succeeds rather than failing once and being corrected — expiry is read from the token, not
 * discovered from a rejection.
 */
export function usePrincipalToken(principal: PrincipalName, targets: TargetResolver = devProxyTargets) {
  return useQuery<MintedToken>({
    queryKey: ['mcp', 'token', principal],
    queryFn: () => mintToken(principal, targets),
    staleTime: 50 * 60 * 1000,
    gcTime: 60 * 60 * 1000,
    retry: false,
  })
}
