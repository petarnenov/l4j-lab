import { useQuery } from '@tanstack/react-query'
import { type McpSession, callMcp, resultOrThrow } from '../transport'
import type { ToolsListResult } from '../wire'

/**
 * `tools/list` (FR-007). The order is the server's and is never re-sorted here.
 *
 * `cacheScope` is `public` and `ttlMs` is five minutes on this result: the tool list is identical
 * for every caller, because entitlements filter *results* and never the tool set. So the query is
 * keyed by target rather than by principal — keying it by principal would suggest the set varies
 * with who asks, which is exactly what the server's cacheScope says it does not.
 */
export function useTools(session: McpSession | null) {
  return useQuery<ToolsListResult>({
    queryKey: ['mcp', 'tools', session?.target],
    enabled: session !== null,
    queryFn: async () =>
      resultOrThrow<ToolsListResult>(await callMcp(session!, { method: 'tools/list' })),
    staleTime: 300_000,
    retry: false,
  })
}
