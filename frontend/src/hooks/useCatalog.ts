import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'

/** The selectable companies and periods. Stable across restarts, so it is cached generously. */
export function useCatalog() {
  return useQuery({
    queryKey: ['catalog'],
    queryFn: api.catalog,
    staleTime: Infinity,
  })
}
