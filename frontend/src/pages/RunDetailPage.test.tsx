import { screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { POLL_INTERVAL_MS } from '../hooks/useRun'
import { nodes, runDetail, server } from '../test/handlers'
import { renderWithQuery } from '../test/render'
import { ThemeProvider } from '../theme/ThemeProvider'
import { ThemeToggle } from '../theme/ThemeToggle'
import userEvent from '@testing-library/user-event'
import { RunDetailPage } from './RunDetailPage'

describe('RunDetailPage', () => {
  it('shows the summary and the indicators it was built from on one screen', async () => {
    // FR-012, the whole point of User Story 1.
    renderWithQuery(<RunDetailPage runId="11111111-1111-1111-1111-111111111111" />)

    await waitFor(() => expect(screen.getByRole('heading', { name: 'Summary' })).toBeInTheDocument())

    expect(screen.getByText(/Revenue grew 0.2500/)).toBeInTheDocument()
    expect(screen.getByText('Revenue growth')).toBeInTheDocument()
    expect(screen.getByText('0.2500')).toBeInTheDocument()
  })

  it('names the provider mode and the model that produced the summary', async () => {
    renderWithQuery(<RunDetailPage runId="11111111-1111-1111-1111-111111111111" />)

    await waitFor(() => expect(screen.getByText('gpt-oss:120b')).toBeInTheDocument())
    expect(screen.getByText('CLOUD')).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 1, name: /Northwind Lighting/ })).toBeInTheDocument()
  })

  it('names the failing node and its reason, and keeps the earlier work visible', async () => {
    // SC-006: the learner must be able to say which node failed and why, from the screen alone.
    server.use(
      http.get('/api/runs/:runId', () =>
        HttpResponse.json(
          runDetail({
            status: 'FAILED',
            failedNode: 'Summarize',
            failureReason: 'The model could not be reached. The indicators above were computed.',
            summary: null,
            nodes: nodes(true),
          }),
        ),
      ),
    )

    renderWithQuery(<RunDetailPage runId="11111111-1111-1111-1111-111111111111" />)

    await waitFor(() =>
      expect(screen.getByRole('heading', { name: /stopped at Summarize/ })).toBeInTheDocument(),
    )
    expect(screen.getByText(/The model could not be reached/)).toBeInTheDocument()

    // FR-008 and FR-024: the failure is an alert carrying an icon, so it survives a colour-blind
    // reader and a monochrome projector, and it names the node.
    const failure = screen
      .getAllByRole('alert')
      .find((el) => el.textContent?.includes('stopped at Summarize'))
    expect(failure).toBeDefined()
    expect(failure!.querySelector('[role="img"]')).not.toBeNull()
    // The indicators the third node computed are still there.
    expect(screen.getByText('0.2500')).toBeInTheDocument()
  })

  it('names the timed-out node too', async () => {
    server.use(
      http.get('/api/runs/:runId', () =>
        HttpResponse.json(
          runDetail({
            status: 'TIMED_OUT',
            failedNode: 'Summarize',
            failureReason: 'The model did not answer within 45 seconds.',
            summary: null,
            nodes: nodes(true),
          }),
        ),
      ),
    )

    renderWithQuery(<RunDetailPage runId="11111111-1111-1111-1111-111111111111" />)

    await waitFor(() =>
      expect(screen.getByRole('heading', { name: /took too long/ })).toBeInTheDocument(),
    )
  })

  it('names the node currently running while the chain is in flight', async () => {
    // FR-020: never a frozen screen.
    server.use(
      http.get('/api/runs/:runId', () =>
        HttpResponse.json(
          runDetail({
            status: 'RUNNING',
            currentNode: 'Summarize',
            summary: null,
            endedAt: null,
            nodes: nodes().slice(0, 3),
          }),
        ),
      ),
    )

    renderWithQuery(<RunDetailPage runId="11111111-1111-1111-1111-111111111111" />)

    // The label appears twice by design: once as the live status, once in the step list.
    await waitFor(() =>
      expect(screen.getAllByText(/Asking the model to summarise/).length).toBeGreaterThan(0),
    )
    expect(screen.getByText(/This is the slow step/)).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /Northwind Lighting/ })).toBeInTheDocument()
  })

  it('keeps a running run exactly as it was when the theme is switched (FR-013)', async () => {
    let requests = 0
    server.use(
      http.get('/api/runs/:runId', () => {
        requests++
        return HttpResponse.json(
          runDetail({ status: 'RUNNING', currentNode: 'Summarize', summary: null, endedAt: null, nodes: nodes().slice(0, 3) }),
        )
      }),
    )
    const user = userEvent.setup()

    renderWithQuery(
      <ThemeProvider>
        <ThemeToggle />
        <RunDetailPage runId="11111111-1111-1111-1111-111111111111" />
      </ThemeProvider>,
    )

    await waitFor(() => expect(screen.getByText(/This is the slow step/)).toBeInTheDocument())
    const heading = screen.getByRole('heading', { level: 1, name: /Northwind Lighting/ })
    const before = requests
    const startedAt = performance.now()

    await user.click(screen.getByRole('radio', { name: 'Dark' }).closest('label') as HTMLElement)

    expect(screen.getByText(/This is the slow step/)).toBeInTheDocument()
    // A theme change re-renders; it must not remount. A remount replaces every DOM node, so the heading
    // being the very same element is the direct evidence.
    expect(screen.getByRole('heading', { level: 1, name: /Northwind Lighting/ })).toBe(heading)
    // A running run is polled every POLL_INTERVAL_MS, and on a loaded machine the click alone can outlast
    // one interval, so "no request at all" failed on a legitimate poll. What must not appear is a request
    // beyond the polls that fit in the time that passed.
    const pollsThatFit = Math.floor((performance.now() - startedAt) / POLL_INTERVAL_MS) + 1
    expect(requests - before).toBeLessThanOrEqual(pollsThatFit)
  })

  it('stacks summary and indicators below 768 pixels and sets them side by side from it (FR-020)', async () => {
    // jsdom performs no layout, and antd applies these widths through CSS media queries, so what can be
    // checked here is that both regions carry the full-width narrow span and the half-width md span. The
    // rendered result at each width is measured in a real browser in T057.
    renderWithQuery(<RunDetailPage runId="11111111-1111-1111-1111-111111111111" />)

    const summary = await screen.findByRole('heading', { name: 'Summary' })
    const indicators = screen.getByRole('heading', { name: 'Indicators' })
    for (const heading of [summary, indicators]) {
      const col = heading.closest('.ant-col') as HTMLElement
      expect(col.className).toMatch(/ant-col-xs-24/)
      expect(col.className).toMatch(/ant-col-md-12/)
    }
  })
})
