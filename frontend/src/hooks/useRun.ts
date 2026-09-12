import { useQuery } from '@tanstack/react-query'
import { api, isTerminal } from '../api/client'

/** How often a running chain is polled. R-004 chose polling over a streaming transport. */
export const POLL_INTERVAL_MS = 1000

/**
 * One run, polled while it is in flight and left alone once it is terminal. TanStack Query owns the
 * refetch behaviour, so the run's state is never copied into a second store.
 */
export function useRun(runId: string | undefined) {
  return useQuery({
    queryKey: ['run', runId],
    queryFn: () => api.run(runId as string),
    enabled: Boolean(runId),
    refetchInterval: (query) => (isTerminal(query.state.data?.status) ? false : POLL_INTERVAL_MS),
  })
}
