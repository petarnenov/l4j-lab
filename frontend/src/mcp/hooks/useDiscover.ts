import { useQuery } from '@tanstack/react-query'
import { type McpSession, callMcp, resultOrThrow } from '../transport'
import type { DiscoverResult } from '../wire'

/**
 * `server/discover`: what the server says it is and what it implements (US1-1).
 *
 * Its `ttlMs` is the server's own caching directive, so it becomes the query's staleTime. Showing a
 * directive the console then ignored would be its own small lie.
 */
export function useDiscover(session: McpSession | null) {
  return useQuery<DiscoverResult>({
    queryKey: ['mcp', 'discover', session?.target, session?.principalName],
    enabled: session !== null,
    queryFn: async () =>
      resultOrThrow<DiscoverResult>(await callMcp(session!, { method: 'server/discover' })),
    staleTime: 3_600_000,
    retry: false,
  })
}
