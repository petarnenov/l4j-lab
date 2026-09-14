import { type MintedToken, type PrincipalName, mintToken } from './principals'
import { TARGET_IDS, type TargetId, type TargetResolver, absoluteTargets, probe } from './targets'
import type { Exchange, McpSession } from './transport'

/**
 * The live suite's fixture. Requires a running stack; deliberately does not start one.
 *
 * Every request the live suite makes goes through the console's own `transport.ts`, using these
 * targets and this minting. A hand-written fetch here would assert things about the server while
 * proving nothing about the console — the exact failure feature 007 recorded in its R-017, and the
 * reason SC-008 asks for a second suite at all.
 *
 * The environment variable names are `mcp-server/src/topologyTest/.../TopologyFixture.java`'s, so a
 * shell that runs one suite runs the other.
 */

/**
 * Declared locally rather than by adding @types/node. This is the only file under `src/` that reads
 * the environment, and the alternative pulls Node's global typings over the whole frontend, where
 * they would quietly change what `setTimeout` returns. One line, one file, no dependency.
 */
declare const process: { env: Record<string, string | undefined> }

function origin(variable: string, fallback: string): string {
  return process.env[variable] ?? fallback
}

export function liveTargets(): TargetResolver {
  return absoluteTargets({
    proxy: origin('MCP_PROXY_URL', 'http://localhost:8877'),
    a: origin('MCP_REPLICA_A_URL', 'http://localhost:8881'),
    b: origin('MCP_REPLICA_B_URL', 'http://localhost:8882'),
    c: origin('MCP_REPLICA_C_URL', 'http://localhost:8883'),
  })
}

/**
 * SC-008: a missing stack fails with an instruction, never an unexplained connection error. The
 * wording follows TopologyFixture's, because a developer who has seen one should recognise the
 * other.
 */
export async function requireStack(targets: TargetResolver = liveTargets()): Promise<void> {
  if ((await probe('proxy', targets)) === 'reachable') return
  throw new Error(
    `The MCP stack is not reachable at ${targets.baseUrl('proxy')}.\n\n` +
      'Run `make mcp-up` first, or `make mcp-verify` to start it, run the scenarios and stop it\n' +
      'again. For the cross-replica scenarios use `make mcp-up-topology`, which publishes the\n' +
      'replicas individually. These tests deliberately do not start containers themselves.',
  )
}

/**
 * The other half of the rule: unpublished replicas are a *skip*, not a failure. SC-005 is
 * conditional on the individual replicas being reachable, and a plain `make mcp-up` publishes only
 * the proxy on purpose. Returns the replicas that answered.
 */
export async function reachableReplicas(
  targets: TargetResolver = liveTargets(),
): Promise<TargetId[]> {
  const replicas = TARGET_IDS.filter((id) => id !== 'proxy')
  const states = await Promise.all(replicas.map((id) => probe(id, targets)))
  return replicas.filter((_, index) => states[index] === 'reachable')
}

export const SKIP_WITHOUT_TOPOLOGY =
  'Skipped: the individual replicas are not published. `make mcp-up-topology` publishes them; a ' +
  'plain `make mcp-up` publishes only the proxy, which is not a fault.'

export interface LiveSession {
  session: McpSession
  exchanges: Exchange[]
  credential: MintedToken
}

/** A session built exactly as the console builds one, so the live suite exercises that code. */
export async function liveSession(
  principal: PrincipalName = 'admin-alpha',
  target: TargetId = 'proxy',
  targets: TargetResolver = liveTargets(),
): Promise<LiveSession> {
  const credential = await mintToken(principal, targets)
  const exchanges: Exchange[] = []
  return {
    credential,
    exchanges,
    session: {
      target,
      targets,
      token: credential.token,
      principalName: principal,
      expiresAt: credential.claims.expiresAt,
      record: (exchange) => exchanges.push(exchange),
    },
  }
}
