import { useQuery } from '@tanstack/react-query'
import {
  TARGET_IDS,
  type Target,
  type TargetResolver,
  absenceReason,
  devProxyTargets,
  probe,
} from '../targets'
import { TARGET_LABELS } from '../targets'

/**
 * FR-016 and FR-017: which of the four places a call can be aimed at are actually there.
 *
 * Nothing in the protocol advertises the topology, and nothing should — so the console asks. The
 * proxy answers /lb-health; a replica answers the readiness endpoint its own Compose health check
 * uses. All three replicas unreachable is the normal state of a plain `make mcp-up`, not a fault.
 *
 * Re-probed periodically so someone who starts the topology overlay mid-session sees the replicas
 * appear, rather than having to reload the page to be believed.
 */
export function useReachableTargets(targets: TargetResolver = devProxyTargets) {
  return useQuery<Target[]>({
    queryKey: ['mcp', 'reachability'],
    queryFn: async () => {
      const states = await Promise.all(TARGET_IDS.map((id) => probe(id, targets)))
      return TARGET_IDS.map((id, index) => ({
        id,
        label: TARGET_LABELS[id],
        baseUrl: targets.baseUrl(id),
        reachability: states[index],
        absenceReason: states[index] === 'unreachable' ? absenceReason(id) : null,
      }))
    },
    staleTime: 10_000,
    refetchInterval: 30_000,
    retry: false,
  })
}

/** True when the whole console has nothing to talk to. Everything else depends on the proxy. */
export function proxyIsDown(targets: Target[] | undefined): boolean {
  return targets?.find((t) => t.id === 'proxy')?.reachability === 'unreachable'
}
