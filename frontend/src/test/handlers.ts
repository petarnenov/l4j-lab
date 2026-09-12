import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'

export const catalog = {
  companies: [
    {
      companyId: 'northwind-lighting',
      companyName: 'Northwind Lighting (fictional)',
      periods: ['2024-Q1', '2024-Q2', '2025-Q1', '2025-Q2'],
    },
    {
      companyId: 'harbor-foods',
      companyName: 'Harbor Foods (fictional)',
      periods: ['2024-Q1', '2024-Q2'],
    },
  ],
}

export const indicators = [
  { name: 'revenueGrowth', value: '0.2500', notApplicableReason: null, derivedFrom: ['revenue'] },
  { name: 'grossMargin', value: '0.4000', notApplicableReason: null, derivedFrom: ['revenue'] },
  { name: 'netMargin', value: '0.1200', notApplicableReason: null, derivedFrom: ['netIncome'] },
  { name: 'currentRatio', value: '2.0000', notApplicableReason: null, derivedFrom: ['currentAssets'] },
  {
    name: 'debtToEquity',
    value: null,
    notApplicableReason: 'Equity is zero, so debt to equity has no defined value',
    derivedFrom: ['totalDebt', 'equity'],
  },
]

export function nodes(failAtSummarize = false) {
  const base = [
    { position: 1, nodeName: 'PrepareRequest', succeeded: true, failureReason: null, inputPayload: { companyId: 'northwind-lighting' }, outputPayload: { companyId: 'northwind-lighting', period: '2025-Q2' }, startedAt: '2026-09-12T10:00:00Z', durationMs: 1, modelRequestText: null, modelResponseText: null, inputTokens: null, outputTokens: null },
    { position: 2, nodeName: 'RetrieveRecords', succeeded: true, failureReason: null, inputPayload: { companyId: 'northwind-lighting' }, outputPayload: { current: { revenue: '4200' } }, startedAt: '2026-09-12T10:00:01Z', durationMs: 2, modelRequestText: null, modelResponseText: null, inputTokens: null, outputTokens: null },
    { position: 3, nodeName: 'ComputeIndicators', succeeded: true, failureReason: null, inputPayload: { current: { revenue: '4200' } }, outputPayload: { indicators }, startedAt: '2026-09-12T10:00:02Z', durationMs: 3, modelRequestText: null, modelResponseText: null, inputTokens: null, outputTokens: null },
  ]
  return [
    ...base,
    failAtSummarize
      ? { position: 4, nodeName: 'Summarize', succeeded: false, failureReason: 'The model could not be reached.', inputPayload: { indicators }, outputPayload: null, startedAt: '2026-09-12T10:00:03Z', durationMs: 45000, modelRequestText: 'Indicators:\n- revenueGrowth = 0.2500', modelResponseText: null, inputTokens: null, outputTokens: null }
      : { position: 4, nodeName: 'Summarize', succeeded: true, failureReason: null, inputPayload: { indicators }, outputPayload: { text: 'Revenue grew 0.2500.' }, startedAt: '2026-09-12T10:00:03Z', durationMs: 900, modelRequestText: 'Indicators:\n- revenueGrowth = 0.2500', modelResponseText: 'Revenue grew 0.2500.', inputTokens: 120, outputTokens: 80 },
  ]
}

export function runDetail(overrides: Record<string, unknown> = {}) {
  return {
    runId: '11111111-1111-1111-1111-111111111111',
    companyId: 'northwind-lighting',
    companyName: 'Northwind Lighting (fictional)',
    period: '2025-Q2',
    status: 'SUCCEEDED',
    currentNode: null,
    failedNode: null,
    failureReason: null,
    providerMode: 'CLOUD',
    modelId: 'gpt-oss:120b',
    summary: 'Revenue grew 0.2500 over the prior quarter.',
    indicators,
    startedAt: '2026-09-12T10:00:00Z',
    endedAt: '2026-09-12T10:00:04Z',
    nodes: nodes(),
    ...overrides,
  }
}

export const server = setupServer(
  http.get('/api/catalog', () => HttpResponse.json(catalog)),
  http.post('/api/runs', () =>
    HttpResponse.json({ runId: '11111111-1111-1111-1111-111111111111', status: 'PENDING' }, { status: 202 }),
  ),
  http.get('/api/runs/:runId', () => HttpResponse.json(runDetail())),
  http.get('/api/runs', () =>
    HttpResponse.json({
      runs: [
        { runId: '11111111-1111-1111-1111-111111111111', companyId: 'northwind-lighting', companyName: 'Northwind Lighting (fictional)', period: '2025-Q2', status: 'SUCCEEDED', summaryPreview: 'Revenue grew 0.2500', startedAt: '2026-09-12T10:00:00Z', endedAt: '2026-09-12T10:00:04Z' },
        { runId: '22222222-2222-2222-2222-222222222222', companyId: 'harbor-foods', companyName: 'Harbor Foods (fictional)', period: '2024-Q2', status: 'FAILED', summaryPreview: null, startedAt: '2026-09-12T09:00:00Z', endedAt: '2026-09-12T09:00:02Z' },
      ],
      nextCursor: null,
    }),
  ),
)
