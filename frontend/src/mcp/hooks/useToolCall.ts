import { useMutation } from '@tanstack/react-query'
import {
  type Exchange,
  type McpSession,
  type RetryOptions,
  callMcp,
  retryParams,
} from '../transport'

/**
 * Calling a tool.
 *
 * The whole `Exchange` is returned rather than a result, because a tool failure is something to
 * show and not something to throw: it arrived as a successful response carrying a flag, and US2-3
 * asks for exactly that to be visible. Only the caller can decide what to do with each shape.
 */
export function useToolCall(session: McpSession | null) {
  return useMutation<Exchange, Error, { name: string; args: Record<string, unknown> }>({
    mutationFn: ({ name, args }) =>
      callMcp(session!, { method: 'tools/call', params: { name, arguments: args } }),
  })
}

/**
 * The second call of a Multi Round-Trip Request (FR-012).
 *
 * A separate mutation rather than a flag on the first, because they are genuinely different
 * requests: this one repeats the arguments, carries the answer, echoes the state the server issued,
 * and takes a new JSON-RPC id. Folding them together would hide the very thing US3 exists to show.
 */
export function useToolRetry(session: McpSession | null) {
  return useMutation<Exchange, Error, RetryOptions>({
    mutationFn: (options) =>
      callMcp(session!, { method: 'tools/call', params: retryParams(options) }),
  })
}
