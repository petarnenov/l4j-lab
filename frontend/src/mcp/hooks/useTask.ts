import { useQuery } from '@tanstack/react-query'
import { type McpSession, callMcp, resultOrThrow } from '../transport'
import { type TaskResult, isTerminalTask } from '../wire'

/**
 * Polling a handle to completion (FR-013).
 *
 * The interval comes from the server's own `pollIntervalMs` rather than a number chosen here — the
 * same instinct as FR-008's derived argument fields: the server said what it wanted.
 *
 * TanStack Query does the polling because it already owns server state, and the stack rules forbid
 * copying that into a second store. An interval hook writing into useState would be exactly that.
 *
 * Every poll is recorded as an exchange like any other, which is what makes a poll landing on
 * another replica visible in the log rather than hidden inside this hook (US4-3).
 */
export function useTask(session: McpSession | null, initial: TaskResult | null) {
  return useQuery<TaskResult>({
    queryKey: ['mcp', 'task', initial?.taskId],
    enabled: session !== null && initial !== null && !isTerminalTask(initial?.status),
    initialData: initial ?? undefined,
    queryFn: async () =>
      resultOrThrow<TaskResult>(
        await callMcp(session!, { method: 'tasks/get', params: { taskId: initial!.taskId } }),
      ),
    refetchInterval: (query) =>
      isTerminalTask(query.state.data?.status) ? false : (initial?.pollIntervalMs ?? 2000),
    retry: false,
  })
}

/**
 * FR-014. Cancellation is cooperative and it acts: it asks the legacy API to cancel the run.
 *
 * A task already in a terminal state keeps its status and the request is still acknowledged (007
 * FR-031) — so this resolving is not evidence the run stopped, and the console says so rather than
 * implying it.
 */
export async function cancelTask(session: McpSession, taskId: string) {
  return callMcp(session, { method: 'tasks/cancel', params: { taskId } })
}
