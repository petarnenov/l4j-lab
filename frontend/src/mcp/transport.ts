import { version as APP_VERSION } from '../../package.json'
import type { TargetId, TargetResolver } from './targets'
import { PROTOCOL_VERSION } from './wire'
import type { JsonRpcResponse } from './wire'

/**
 * What the console puts on the wire, and how it reads what comes back.
 *
 * Contract: specs/008-mcp-console/contracts/mcp-client.md. Authority for every shape:
 * specs/007-mcp-billing-server/contracts/mcp-protocol.md.
 */

export { PROTOCOL_VERSION }

export const CLIENT_NAME = 'l4j-mcp-console'

/**
 * Both capabilities are declared because both are implemented: `elicitation` because the
 * confirmation round trip is driven here (without it, post_fee_adjustment is answered -32021), and
 * the tasks extension because tasks/get is polled. Declaring one the console could not honour is
 * the single thing the client contract forbids outright.
 */
export const CLIENT_CAPABILITIES = {
  elicitation: {},
  extensions: { 'io.modelcontextprotocol/tasks': {} },
} as const

let idCounter = 0

/** Monotonic within the tab, as strings. Shown in the exchange view so a retry's id is visible. */
export function nextJsonRpcId(): string {
  idCounter += 1
  return String(idCounter)
}

export type OutcomeKind = 'ok' | 'tool-error' | 'protocol-error' | 'transport-error'

export interface Exchange {
  id: string
  principalName: string
  targetId: TargetId
  method: string
  toolName: string | null
  requestHeaders: Record<string, string>
  requestBody: unknown
  httpStatus: number
  responseHeaders: Record<string, string>
  responseBody: unknown
  durationMs: number
  outcome: OutcomeKind
  /** True for the malformed catalogue, so a refusal is never read as a fault in the system. */
  deliberate: boolean
  /** Set only on a transport failure whose request never arrived. */
  networkError?: string
}

export interface BuiltRequest {
  headers: Record<string, string>
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  body: any
}

export interface BuildOptions {
  method: string
  params?: Record<string, unknown>
  id?: string
}

/**
 * Builds the headers **from the body**, never from a stored template, so the mirroring feature
 * 007's server checks is a property of this function rather than of a copy-paste that agreed with
 * the body until someone edited one of the two.
 *
 * The only code path permitted to break the mirroring is the malformed catalogue, which breaks it
 * deliberately and marks the exchange as such.
 */
export function buildRequest({ method, params = {}, id }: BuildOptions): BuiltRequest {
  const body = {
    jsonrpc: '2.0',
    id: id ?? nextJsonRpcId(),
    method,
    params: {
      ...params,
      _meta: {
        'io.modelcontextprotocol/protocolVersion': PROTOCOL_VERSION,
        'io.modelcontextprotocol/clientInfo': { name: CLIENT_NAME, version: APP_VERSION },
        'io.modelcontextprotocol/clientCapabilities': CLIENT_CAPABILITIES,
        ...((params._meta as Record<string, unknown> | undefined) ?? {}),
      },
    },
  }

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'application/json, text/event-stream',
    'MCP-Protocol-Version': String(body.params._meta['io.modelcontextprotocol/protocolVersion']),
    'Mcp-Method': body.method,
  }
  if (typeof params.name === 'string') {
    headers['Mcp-Name'] = params.name
  }
  return { headers, body }
}

export interface RetryOptions {
  name: string
  args: Record<string, unknown>
  /** The key the server asked under, e.g. `confirm_adjustment`. Not chosen by the console. */
  key: string
  confirmed: boolean
  /** Opaque. Echoed exactly as received; never constructed, decoded, or edited. */
  requestState: string
}

/**
 * The second call of the Multi Round-Trip Request (FR-012).
 *
 * It repeats the arguments because the server verifies its sealed digest against them, echoes
 * `requestState` untouched, and takes a **new** JSON-RPC id — 007's contract requires the id to
 * differ, and the exchange log shows both so that difference is visible rather than asserted.
 */
export function retryParams({
  name,
  args,
  key,
  confirmed,
  requestState,
}: RetryOptions): Record<string, unknown> {
  return {
    name,
    arguments: args,
    /**
     * The MCP `ElicitResult` envelope: `{ action, content }`, with the requested schema's fields
     * inside `content`. Not `{ confirmed }` directly, which is what 007's protocol contract reads
     * as if it said — it names `params.inputResponses.confirm_adjustment` and stops there, and the
     * elicitation's `requestedSchema` is `{ confirmed: boolean }`, so the flat shape looks right.
     *
     * The server reads `content.confirmed` and treats anything else as a refusal, so a client
     * following the contract alone sends a confirmation that is silently understood as "no".
     * Recorded as finding F-005; caught by the live suite, invisible to any stub.
     */
    inputResponses: {
      [key]: { action: confirmed ? 'accept' : 'decline', content: { confirmed } },
    },
    requestState,
  }
}

export function buildRetryRequest(options: RetryOptions): BuiltRequest {
  return buildRequest({ method: 'tools/call', params: retryParams(options) })
}

/**
 * The classification FR-011 requires, derived from the response rather than from the HTTP status
 * alone. The status is not enough on its own: 007's error table puts -32602 for an unknown tool
 * name at HTTP 200, and a tool *execution* failure arrives at 200 as a successful response carrying
 * a flag.
 */
export function classify(response: {
  httpStatus: number
  body: unknown
  networkError?: string
}): OutcomeKind {
  if (response.networkError || response.httpStatus === 0) return 'transport-error'
  if (response.httpStatus === 401 || response.httpStatus === 403) return 'transport-error'
  const body = response.body as JsonRpcResponse | null
  if (body?.error) return 'protocol-error'
  const result = body?.result as { isError?: boolean } | undefined
  if (result?.isError) return 'tool-error'
  if (!body?.result) return 'transport-error'
  return 'ok'
}

/**
 * Principle II forbids a credential being shown on screen; FR-010 requires the request exactly as
 * it travelled. Both are honoured: the bearer value becomes a labelled redaction naming the
 * principal, and every other header and the whole body stay byte-for-byte.
 *
 * That the issuer's key is committed and worthless is an argument for why showing the token would
 * be harmless, not for why it would be a good thing to demonstrate in a repository whose purpose is
 * teaching. The label says it is a redaction, so nobody reads it as a header the console failed to
 * send.
 */
export function redactAuthorization(
  headers: Record<string, string>,
  principalName: string,
  expiresAt?: Date,
): Record<string, string> {
  if (!headers.Authorization) return { ...headers }
  const expiry = expiresAt
    ? `, expires ${expiresAt.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`
    : ''
  return {
    ...headers,
    Authorization: `Bearer «token for ${principalName}${expiry} — redacted, not omitted»`,
  }
}

export interface SendOptions extends BuildOptions {
  target: TargetId
  targets: TargetResolver
  token: string
  principalName: string
  expiresAt?: Date
  /** Only the malformed catalogue passes these, and it marks the exchange deliberate. */
  mangle?: (built: BuiltRequest) => BuiltRequest
  deliberate?: boolean
  fetchImpl?: typeof fetch
}

/**
 * One call and its answer, recorded whole. Every request the console makes — including each task
 * poll — goes through here, so nothing that happened is missing from the log.
 */
export async function send(options: SendOptions): Promise<Exchange> {
  const {
    target,
    targets,
    token,
    principalName,
    expiresAt,
    mangle,
    deliberate = false,
    fetchImpl = fetch,
  } = options

  const base = buildRequest(options)
  const built = mangle ? mangle(base) : base
  const sentHeaders = { ...built.headers, Authorization: `Bearer ${token}` }
  const startedAt = performance.now()

  let httpStatus = 0
  let responseBody: unknown = null
  let responseHeaders: Record<string, string> = {}
  let networkError: string | undefined

  try {
    const response = await fetchImpl(`${targets.baseUrl(target)}/mcp`, {
      method: 'POST',
      headers: sentHeaders,
      body: JSON.stringify(built.body),
    })
    httpStatus = response.status
    response.headers.forEach((value, key) => {
      responseHeaders[key] = value
    })
    const text = await response.text()
    try {
      responseBody = text ? JSON.parse(text) : null
    } catch {
      responseBody = text
    }
  } catch (error) {
    // The request never arrived: the stack is stopping, or the replica is not published. It must
    // reach the exchange view as something explainable, never as a blank result panel.
    networkError = error instanceof Error ? error.message : String(error)
    responseHeaders = {}
  }

  return {
    id: `x${built.body.id}`,
    principalName,
    targetId: target,
    method: built.body.method,
    toolName: typeof built.body.params?.name === 'string' ? built.body.params.name : null,
    // Redacted here rather than at the point of display, so no caller can accidentally hold the
    // real header and render it.
    requestHeaders: redactAuthorization(sentHeaders, principalName, expiresAt),
    requestBody: built.body,
    httpStatus,
    responseHeaders,
    responseBody,
    durationMs: Math.round(performance.now() - startedAt),
    outcome: classify({ httpStatus, body: responseBody, networkError }),
    deliberate,
    networkError,
  }
}

/**
 * Everything one call needs to know about who and where. Passed to every hook rather than pulled
 * from a context, so a live test can assemble one without rendering anything.
 */
export interface McpSession {
  target: TargetId
  targets: TargetResolver
  token: string
  principalName: string
  expiresAt?: Date
  /** Every call is recorded, task polls included, so nothing that happened is missing from the log. */
  record?: (exchange: Exchange) => void
}

export interface CallOptions extends BuildOptions {
  mangle?: (built: BuiltRequest) => BuiltRequest
  deliberate?: boolean
}

/** Sends through the session and records the exchange. The single door every request goes through. */
export async function callMcp(session: McpSession, options: CallOptions): Promise<Exchange> {
  const exchange = await send({ ...options, ...session })
  session.record?.(exchange)
  return exchange
}

/**
 * The result of a call, or a thrown error for the two kinds of failure that are not results.
 *
 * A *tool* failure is a result: it is a successful response carrying a flag, and the console shows
 * it as one. A protocol or transport failure is not, so queries built on this surface it as an
 * error state rather than rendering a half-empty panel.
 */
export function resultOrThrow<T>(exchange: Exchange): T {
  if (exchange.outcome === 'protocol-error') {
    const error = (exchange.responseBody as JsonRpcResponse | null)?.error
    throw new Error(`JSON-RPC ${error?.code}: ${error?.message}`)
  }
  if (exchange.outcome === 'transport-error') {
    throw new Error(
      exchange.networkError
        ? `The request never arrived: ${exchange.networkError}`
        : `Rejected with HTTP ${exchange.httpStatus} before the protocol was reached.`,
    )
  }
  return (exchange.responseBody as JsonRpcResponse).result as T
}
