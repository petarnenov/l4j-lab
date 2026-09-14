/**
 * TypeScript types for feature 007's JSON-RPC **envelope**, and nothing else.
 *
 * This file is the exception recorded in the plan's Complexity Tracking against Principle III's
 * "the same shape MUST NOT be declared twice by hand". It exists because feature 007 publishes no
 * machine-readable description of its HTTP surface — deliberately: its contract is the tool
 * declarations it serves, and those describe a tool's *arguments*, not the envelope around them.
 * The console must know these field names before it can branch on them.
 *
 * The exception is bounded two ways, and both are checkable rather than promised:
 *
 *   1. Only the envelope is typed here. A tool's arguments and results are never typed in this
 *      file — they stay derived from the served inputSchema and outputSchema (FR-008).
 *   2. The live suite asserts each field below against what the running server actually sends, so a
 *      field that moves fails a test instead of rendering blank.
 *
 * Authority for every shape: specs/007-mcp-billing-server/contracts/mcp-protocol.md.
 */

export const PROTOCOL_VERSION = '2026-07-28'

/** The four behaviours FR-007 requires the console to show for every tool. */
export interface ToolAnnotations {
  readOnlyHint: boolean
  destructiveHint: boolean
  idempotentHint: boolean
  openWorldHint: boolean
}

/** A JSON Schema, as far as the console is concerned: an untyped object it renders from. */
export type JsonSchema = Record<string, unknown>

export interface McpTool {
  name: string
  title?: string
  description: string
  inputSchema: JsonSchema
  outputSchema?: JsonSchema
  annotations: ToolAnnotations
}

export interface ServerInfo {
  name: string
  version: string
}

export interface ResultMeta {
  'io.modelcontextprotocol/serverInfo'?: ServerInfo
}

export interface DiscoverResult {
  resultType: 'complete'
  supportedVersions: string[]
  capabilities: Record<string, unknown>
  instructions?: string
  ttlMs?: number
  cacheScope?: string
  _meta?: ResultMeta
}

export interface ToolsListResult {
  resultType: 'complete'
  tools: McpTool[]
  ttlMs?: number
  cacheScope?: string
  _meta?: ResultMeta
}

/** One elicitation the server asked for before it would act (FR-012). */
export interface InputRequest {
  method: 'elicitation/create'
  params: {
    mode?: string
    message: string
    requestedSchema: JsonSchema
  }
}

export type TaskStatus = 'working' | 'input_required' | 'completed' | 'failed' | 'cancelled'

export const TERMINAL_TASK_STATUSES: readonly TaskStatus[] = ['completed', 'failed', 'cancelled']

export function isTerminalTask(status: string | undefined): boolean {
  return TERMINAL_TASK_STATUSES.includes(status as TaskStatus)
}

/**
 * The three shapes a tools/call answer takes, plus the task one. Discriminated by `resultType`,
 * which every result carries (007 FR-003).
 */
export interface CompleteResult {
  resultType: 'complete'
  content?: Array<{ type: string; text?: string }>
  structuredContent?: Record<string, unknown>
  isError?: boolean
  _meta?: ResultMeta
}

export interface InputRequiredResult {
  resultType: 'input_required'
  inputRequests: Record<string, InputRequest>
  /** Opaque. Echoed back untouched on the retry; never constructed, decoded, or displayed. */
  requestState: string
  _meta?: ResultMeta
}

export interface TaskResult {
  resultType: 'task'
  taskId: string
  status: TaskStatus
  statusMessage?: string
  createdAt?: string
  lastUpdatedAt?: string
  ttlMs?: number
  pollIntervalMs?: number
  result?: Record<string, unknown>
  error?: unknown
  _meta?: ResultMeta
}

export type ToolCallResult = CompleteResult | InputRequiredResult | TaskResult

export interface JsonRpcError {
  code: number
  message: string
  /** On -32022 this carries `supported` and `requested`, which the edge case requires be shown. */
  data?: { supported?: string[]; requested?: string } & Record<string, unknown>
}

export interface JsonRpcResponse {
  jsonrpc?: string
  id?: string | number
  result?: unknown
  error?: JsonRpcError
}
