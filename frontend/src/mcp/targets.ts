/**
 * Where a call can be aimed, and what the console says when it cannot be.
 *
 * Three of feature 007's most distinctive properties exist only across replicas — a cursor minted
 * by one read by another, a confirmation retry landing elsewhere, task polls spread over three. The
 * base stack publishes only the proxy, so the console addresses replicas when they are reachable
 * and says plainly when they are not (FR-016, FR-017).
 *
 * Contract: specs/008-mcp-console/contracts/dev-proxy.md.
 */

export const TARGET_IDS = ['proxy', 'a', 'b', 'c'] as const

export type TargetId = (typeof TARGET_IDS)[number]

export type Reachability = 'unknown' | 'reachable' | 'unreachable'

export interface Target {
  id: TargetId
  label: string
  baseUrl: string
  reachability: Reachability
  /** Set only when unreachable. Names the command that would publish it. */
  absenceReason: string | null
}

export const TARGET_LABELS: Record<TargetId, string> = {
  proxy: 'Proxy (round-robin)',
  a: 'mcp-a',
  b: 'mcp-b',
  c: 'mcp-c',
}

/**
 * Resolves a target to the base URL a request is sent to.
 *
 * This seam exists because targets are plural and their reachability is discovered at runtime — it
 * would exist with no tests at all. The browser reaches them through the development forwarder; the
 * live suite, which runs outside `vite dev` where nothing rewrites the prefix, reaches them
 * directly. Both supply the same kind of value through the same interface (research R-005).
 */
export interface TargetResolver {
  baseUrl(id: TargetId): string
}

export const devProxyTargets: TargetResolver = {
  baseUrl: (id) => `/mcp-dev/${id}`,
}

export function absoluteTargets(origins: Record<TargetId, string>): TargetResolver {
  return { baseUrl: (id) => origins[id] }
}

/**
 * What to ask a target to find out whether it is there.
 *
 * nginx serves /lb-health and routes /dev/ and /.well-known/ to the issuer. A replica serves
 * neither, but does serve the readiness endpoint its own Compose health check already uses. This
 * asymmetry is feature 007's, not the console's.
 */
export function healthPath(id: TargetId): string {
  return id === 'proxy' ? '/lb-health' : '/health/readiness'
}

/** The command that starts the whole system (FR-003). */
export const START_COMMAND = 'make mcp-up'

/** The command that publishes the individual replicas as well (FR-017). */
export const START_TOPOLOGY_COMMAND = 'make mcp-up-topology'

/** The command that discards the stored data and reloads the seeded fixtures (FR-012b). */
export const RESET_COMMAND = 'make mcp-reset'

export function absenceReason(id: TargetId): string {
  if (id === 'proxy') {
    return `The MCP stack is not reachable. Run \`${START_COMMAND}\` to start it.`
  }
  // Not a fault: the base stack publishes exactly one port on purpose, which is the honest shape
  // for a deployment. Only the acceptance scenarios need to address a replica by name.
  return (
    `${TARGET_LABELS[id]} is not published. \`${START_COMMAND}\` publishes only the proxy; ` +
    `run \`${START_TOPOLOGY_COMMAND}\` to publish the replicas as well.`
  )
}

export function initialTargets(targets: TargetResolver): Target[] {
  return TARGET_IDS.map((id) => ({
    id,
    label: TARGET_LABELS[id],
    baseUrl: targets.baseUrl(id),
    reachability: 'unknown' as Reachability,
    absenceReason: null,
  }))
}

/** True when the proxy answered. Everything else the console shows depends on it. */
export async function probe(
  id: TargetId,
  targets: TargetResolver,
  fetchImpl: typeof fetch = fetch,
): Promise<Reachability> {
  try {
    const response = await fetchImpl(`${targets.baseUrl(id)}${healthPath(id)}`, { method: 'GET' })
    return response.ok ? 'reachable' : 'unreachable'
  } catch {
    // A refused connection. Distinct from a target that answered with an error, which would have
    // returned above — the console must not conflate "not published" with "the server spoke".
    return 'unreachable'
  }
}
