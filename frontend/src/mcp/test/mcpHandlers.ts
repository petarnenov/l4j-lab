import { http, HttpResponse } from 'msw'
import getBillingRunStatus from '@contracts007/get_billing_run_status.json'
import getRunFailures from '@contracts007/get_run_failures.json'
import postFeeAdjustment from '@contracts007/post_fee_adjustment.json'
import searchBillingRuns from '@contracts007/search_billing_runs.json'
import startBillingRun from '@contracts007/start_billing_run.json'
import type { McpTool } from '../wire'

/**
 * The deterministic suite's stand-in for the MCP stack.
 *
 * `tools/list` serves the **committed** tool declarations from
 * `specs/007-mcp-billing-server/contracts/tools/` — the same five files mcp-server's build takes as
 * its own test oracle (research R-004). A copied fixture would be worse than none: it would make
 * this suite pass while the console rendered the wrong thing, and nothing would say when it went
 * stale.
 *
 * The order is fixed here because it is fixed in the server, for the reason its contracts/README
 * gives: a directory listing is alphabetical on one filesystem and arbitrary on another.
 */
export const TOOL_CONTRACTS = [
  searchBillingRuns,
  getBillingRunStatus,
  getRunFailures,
  postFeeAdjustment,
  startBillingRun,
] as unknown as McpTool[]

export const PROTOCOL_VERSION = '2026-07-28'

/**
 * The fixture principals from `007/contracts/token-issuer.md`. Encoded here because the issuer
 * offers no machine-readable description of them — unlike the tool declarations, which is why those
 * are read from the contract and these are not.
 */
export const FIXTURE_CLAIMS: Record<
  string,
  { sub: string; firm_id: string; role: string; advisor_ids: string[] }
> = {
  'advisor-alpha-101': {
    sub: 'usr-101',
    firm_id: 'firm-alpha',
    role: 'ADVISOR',
    advisor_ids: ['adv-101'],
  },
  'advisor-alpha-102': {
    sub: 'usr-102',
    firm_id: 'firm-alpha',
    role: 'ADVISOR',
    advisor_ids: ['adv-102'],
  },
  'admin-alpha': {
    sub: 'usr-900',
    firm_id: 'firm-alpha',
    role: 'FIRM_ADMIN',
    advisor_ids: ['adv-101', 'adv-102'],
  },
  'ops-alpha': {
    sub: 'usr-901',
    firm_id: 'firm-alpha',
    role: 'OPS',
    advisor_ids: ['adv-101', 'adv-102'],
  },
  'readonly-alpha': {
    sub: 'usr-902',
    firm_id: 'firm-alpha',
    role: 'READ_ONLY',
    advisor_ids: ['adv-101', 'adv-102'],
  },
  'admin-beta': {
    sub: 'usr-800',
    firm_id: 'firm-beta',
    role: 'FIRM_ADMIN',
    advisor_ids: ['adv-201'],
  },
}

function base64url(value: object): string {
  return btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

/** A JWT-shaped token with real claims and a meaningless signature. Nothing here verifies one. */
export function fixtureToken(principal: string, expiresInSeconds = 3600): string {
  const claims = FIXTURE_CLAIMS[principal]
  if (!claims) throw new Error(`No fixture principal named ${principal}`)
  const payload = {
    ...claims,
    aud: 'mcp-billing-server',
    exp: Math.floor(Date.now() / 1000) + expiresInSeconds,
  }
  return `${base64url({ alg: 'RS256', typ: 'JWT' })}.${base64url(payload)}.not-a-signature`
}

const serverInfo = {
  'io.modelcontextprotocol/serverInfo': { name: 'mcp-billing-server', version: '0.1.0' },
}

export function discoverResult() {
  return {
    resultType: 'complete',
    supportedVersions: [PROTOCOL_VERSION],
    capabilities: {
      tools: { listChanged: false },
      extensions: { 'io.modelcontextprotocol/tasks': {} },
    },
    instructions:
      'Billing runs and fee adjustments for wealth-management firms. Search runs before opening one.',
    ttlMs: 3_600_000,
    cacheScope: 'public',
    _meta: serverInfo,
  }
}

export function toolsListResult() {
  return {
    resultType: 'complete',
    tools: TOOL_CONTRACTS,
    ttlMs: 300_000,
    cacheScope: 'public',
    _meta: serverInfo,
  }
}

/** A `complete` tool result: text rendering plus validated structured payload. */
export function toolResult(structuredContent: unknown, text = 'A readable rendering.') {
  return {
    resultType: 'complete',
    content: [{ type: 'text', text }],
    structuredContent,
    isError: false,
    _meta: serverInfo,
  }
}

/** A tool *execution* failure: a successful response carrying a flag. Never a JSON-RPC error. */
export function toolError(message: string) {
  return {
    resultType: 'complete',
    content: [{ type: 'text', text: message }],
    isError: true,
    _meta: serverInfo,
  }
}

/** A *protocol* failure: a JSON-RPC error with one of the codes in 007's error table. */
export function protocolError(code: number, message: string, data?: unknown) {
  return { code, message, ...(data === undefined ? {} : { data }) }
}

export type ToolResponder = (args: Record<string, unknown>, name: string) => unknown

const defaultToolResponder: ToolResponder = (args, name) => {
  if (name === 'search_billing_runs') {
    return toolResult({
      runs: [
        {
          run_id: 'run-0001',
          firm_id: String(args.firm_id ?? 'firm-alpha'),
          executed_by_advisor_id: 'adv-101',
          status: 'COMPLETED',
          started_at: '2026-09-01T09:00:00Z',
        },
      ],
      total_match_count: 1,
      truncated: false,
    })
  }
  return toolResult({ ok: true })
}

export interface McpHandlerOptions {
  /** Answers `tools/call`. Return a JSON-RPC result, or throw a protocol error object. */
  onToolCall?: ToolResponder
  /** Targets that answer their health probe. Everything else refuses the connection. */
  reachable?: string[]
}

/**
 * MSW handlers for the forwarder's paths. Every request the console makes in the deterministic
 * suite lands here, and `onUnhandledRequest: 'error'` in src/test/setup.ts means one that does not
 * is a test failure rather than a silent hole.
 */
export function mcpHandlers(options: McpHandlerOptions = {}) {
  const onToolCall = options.onToolCall ?? defaultToolResponder
  const reachable = options.reachable ?? ['proxy']

  return [
    http.post('/mcp-dev/proxy/dev/token', async ({ request }) => {
      const body = (await request.json()) as { principal: string }
      if (!FIXTURE_CLAIMS[body.principal]) {
        return new HttpResponse(null, { status: 404 })
      }
      return HttpResponse.json({ token: fixtureToken(body.principal), expiresInSeconds: 3600 })
    }),

    http.get('/mcp-dev/proxy/lb-health', () =>
      reachable.includes('proxy')
        ? new HttpResponse('ok\n', { status: 200 })
        : HttpResponse.error(),
    ),

    http.get('/mcp-dev/:target/health/readiness', ({ params }) =>
      reachable.includes(String(params.target))
        ? HttpResponse.json({ status: 'UP' })
        : HttpResponse.error(),
    ),

    http.post('/mcp-dev/:target/mcp', async ({ request }) => {
      const body = (await request.json()) as {
        id: string
        method: string
        params?: Record<string, unknown>
      }
      const answer = (result: unknown) =>
        HttpResponse.json({ jsonrpc: '2.0', id: body.id, result })
      const refuse = (code: number, message: string, status: number, data?: unknown) =>
        HttpResponse.json(
          { jsonrpc: '2.0', id: body.id, error: protocolError(code, message, data) },
          { status },
        )

      // The header checks the server actually performs, in the order it performs them, so a
      // deliberately malformed request produces the same refusal here as it does over the wire.
      const headerVersion = request.headers.get('MCP-Protocol-Version')
      const metaVersion = (body.params?._meta as Record<string, unknown> | undefined)?.[
        'io.modelcontextprotocol/protocolVersion'
      ]
      if (request.headers.get('Mcp-Method') !== body.method) {
        return refuse(-32020, 'Mcp-Method is missing or disagrees with the body.', 400)
      }
      if (headerVersion !== metaVersion) {
        return refuse(-32020, 'MCP-Protocol-Version disagrees with the body.', 400)
      }
      if (headerVersion !== PROTOCOL_VERSION) {
        return refuse(-32022, 'Unsupported protocol version.', 400, {
          supported: [PROTOCOL_VERSION],
          requested: headerVersion,
        })
      }
      if (body.method === 'tools/call') {
        const name = String(body.params?.name ?? '')
        if (request.headers.get('Mcp-Name') !== name) {
          return refuse(-32020, 'Mcp-Name disagrees with params.name.', 400)
        }
        const args = (body.params?.arguments ?? {}) as Record<string, unknown>
        try {
          return answer(onToolCall(args, name))
        } catch (error) {
          const e = error as { code: number; message: string; data?: unknown; status?: number }
          return refuse(e.code, e.message, e.status ?? 400, e.data)
        }
      }
      if (body.method === 'server/discover') return answer(discoverResult())
      if (body.method === 'tools/list') return answer(toolsListResult())
      return refuse(-32601, `Unknown method ${body.method}.`, 404)
    }),
  ]
}
