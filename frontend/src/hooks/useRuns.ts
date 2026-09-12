import { useInfiniteQuery } from '@tanstack/react-query'
import { api } from '../api/client'

/** The history list, newest first, paged by the opaque cursor the API issues. */
export function useRuns() {
  return useInfiniteQuery({
    queryKey: ['runs'],
    queryFn: ({ pageParam }) => api.runs(pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
  })
}
