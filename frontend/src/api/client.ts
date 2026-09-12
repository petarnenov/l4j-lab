import type { components } from './schema'

/**
 * Every shape here comes from the generated schema. Nothing in the frontend declares a request or
 * response type by hand, which is what Principle III requires: the Java records are the single
 * source, and `npm run check:api` fails the build if this file drifts from them.
 */
export type CatalogResponse = components['schemas']['CatalogResponse']
export type CompanyEntry = components['schemas']['CatalogResponse.CompanyEntry']
export type RunDetailResponse = components['schemas']['RunDetailResponse']
export type RunListResponse = components['schemas']['RunListResponse']
export type RunListEntry = components['schemas']['RunListResponse.RunListEntry']
export type StartRunResponse = components['schemas']['StartRunResponse']
export type IndicatorView = components['schemas']['IndicatorView']
export type NodeView = components['schemas']['NodeView']

export type RunStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'TIMED_OUT'

export const TERMINAL_STATUSES: readonly RunStatus[] = ['SUCCEEDED', 'FAILED', 'TIMED_OUT']

export function isTerminal(status: string | undefined): boolean {
  return TERMINAL_STATUSES.includes(status as RunStatus)
}

/** An RFC 9457 problem detail, which is the only error shape this API returns. */
export interface Problem {
  title?: string
  status?: number
  detail?: string
}

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, detail: string) {
    super(detail)
    this.status = status
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
  })

  if (!response.ok) {
    let detail = `Request to ${path} failed with status ${response.status}.`
    try {
      const problem = (await response.json()) as Problem
      if (problem.detail) detail = problem.detail
    } catch {
      // A non-JSON error body is still an error; the status-based message above stands.
    }
    throw new ApiError(response.status, detail)
  }

  return (await response.json()) as T
}

export const api = {
  catalog: () => request<CatalogResponse>('/api/catalog'),

  startRun: (companyId: string, period: string) =>
    request<StartRunResponse>('/api/runs', {
      method: 'POST',
      body: JSON.stringify({ companyId, period }),
    }),

  run: (runId: string) => request<RunDetailResponse>(`/api/runs/${runId}`),

  runs: (cursor?: string | null) =>
    request<RunListResponse>(`/api/runs${cursor ? `?cursor=${encodeURIComponent(cursor)}` : ''}`),
}
