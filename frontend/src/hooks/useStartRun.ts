import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'

export function useStartRun() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ companyId, period }: { companyId: string; period: string }) =>
      api.startRun(companyId, period),
    onSuccess: () => {
      // A new run belongs at the top of the history list.
      void queryClient.invalidateQueries({ queryKey: ['runs'] })
    },
  })
}
