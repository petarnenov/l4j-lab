import type { BuiltRequest } from './transport'
import { PROTOCOL_VERSION } from './wire'

/**
 * The three prepared, deliberately wrong requests (FR-011a).
 *
 * The two refusals that make this protocol recognisable happen only when a request is wrong: a
 * header that disagrees with the body, and a version the server does not implement. A console that
 * always builds correct requests could never show them.
 *
 * Fixed in code, and no free-form editing of headers or body: that is a different tool with a
 * different purpose, and it already exists — it is called curl. Three prepared cases reach the same
 * demonstration in one action and keep the console light, which is what was asked for.
 *
 * Contract: specs/008-mcp-console/contracts/malformed-requests.md.
 */
export interface MalformedRequest {
  id: string
  label: string
  explanation: string
  expectedCode: number
  method: string
  params: Record<string, unknown>
  mangle: (built: BuiltRequest) => BuiltRequest
}

export const MALFORMED_REQUESTS: MalformedRequest[] = [
  {
    id: 'header-disagrees',
    label: 'A header that disagrees with the body',
    explanation:
      'A well-formed call to search_billing_runs, with Mcp-Name naming get_run_failures instead. ' +
      'The revision requires the routing headers to mirror the body, and the server checks rather ' +
      'than trusts. The tool is never reached, so there is no isError anywhere in the answer — ' +
      'which is the clearest way to see what separates a protocol failure from a tool failure.',
    expectedCode: -32020,
    method: 'tools/call',
    params: { name: 'search_billing_runs', arguments: { firm_id: 'firm-alpha' } },
    mangle: ({ headers, body }) => ({
      headers: { ...headers, 'Mcp-Name': 'get_run_failures' },
      body,
    }),
  },
  {
    id: 'unsupported-version',
    label: 'A version the server does not implement',
    explanation:
      'A tools/list announcing 2025-06-18 in both the header and _meta. They agree deliberately: ' +
      'making them disagree would produce -32020 first and demonstrate the previous case instead. ' +
      'The refusal names the versions the server does support.',
    expectedCode: -32022,
    method: 'tools/list',
    params: {},
    mangle: ({ headers, body }) => ({
      headers: { ...headers, 'MCP-Protocol-Version': '2025-06-18' },
      body: {
        ...body,
        params: {
          ...body.params,
          _meta: {
            ...body.params._meta,
            'io.modelcontextprotocol/protocolVersion': '2025-06-18',
          },
        },
      },
    }),
  },
  {
    id: 'missing-header',
    label: 'A required header omitted',
    explanation:
      'A well-formed tools/list with Mcp-Method absent and every other header present. Next to ' +
      'the first case, it shows that "missing" and "disagreeing" are the same refusal — worth ' +
      'seeing rather than assuming.',
    expectedCode: -32020,
    method: 'tools/list',
    params: {},
    mangle: ({ headers, body }) => {
      const withoutMethod = { ...headers }
      delete withoutMethod['Mcp-Method']
      return { headers: withoutMethod, body }
    },
  },
]

/** The version the console really speaks, for the panel to contrast with the refused one. */
export const CONSOLE_PROTOCOL_VERSION = PROTOCOL_VERSION
