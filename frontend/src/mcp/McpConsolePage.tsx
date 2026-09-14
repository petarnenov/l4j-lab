import { Alert, Card, Col, Row, Space, Spin, Typography, theme } from 'antd'
import { useCallback, useMemo, useState } from 'react'
import { ElicitationPanel } from './components/ElicitationPanel'
import { ExchangeLog } from './components/ExchangeLog'
import { MalformedPanel } from './components/MalformedPanel'
import { PageCursor, type SearchPage } from './components/PageCursor'
import { PrincipalPicker } from './components/PrincipalPicker'
import { TargetPicker } from './components/TargetPicker'
import { TaskWatcher } from './components/TaskWatcher'
import { StackOffline } from './components/StackOffline'
import { ToolCallPanel } from './components/ToolCallPanel'
import { ToolList } from './components/ToolList'
import { DEV_ONLY_MARKER } from './devOnlyMarker'
import { useDiscover } from './hooks/useDiscover'
import { usePrincipalToken } from './hooks/usePrincipalToken'
import { proxyIsDown, useReachableTargets } from './hooks/useReachableTargets'
import { useTools } from './hooks/useTools'
import { cancelTask, useTask } from './hooks/useTask'
import { callMcp } from './transport'
import { useToolCall, useToolRetry } from './hooks/useToolCall'
import type { MalformedRequest } from './malformed'
import type { PrincipalName } from './principals'
import { devProxyTargets } from './targets'
import type { Exchange, McpSession } from './transport'
import type {
  CompleteResult,
  InputRequiredResult,
  JsonRpcResponse,
  TaskResult,
  ToolCallResult,
} from './wire'
import { isTerminalTask } from './wire'
import type { TargetId } from './targets'

/**
 * The MCP console (feature 008).
 *
 * A window onto feature 007's protocol, not a client that hides it. The existing pages abstract the
 * agent chain away because a user of that feature wants the answer and not the mechanism; here the
 * mechanism *is* the subject. Someone opening this wants to see that a request carried its own
 * protocol version, that a cursor minted by one replica was honoured by another, that a fee change
 * asked before it acted.
 *
 * Development only, and structurally so: this module is imported from App.tsx only inside an
 * `import.meta.env.DEV` branch, which Vite replaces with `false` during `vite build` so Rollup
 * eliminates the branch and this chunk with it. `npm run check:dev-only` reads the actual build
 * output and fails if DEV_ONLY_MARKER below survives (FR-001a).
 *
 * Surface contract: specs/008-mcp-console/contracts/console-surface.md.
 */
export default function McpConsolePage() {
  const { token } = theme.useToken()
  const targets = devProxyTargets

  const [principal, setPrincipal] = useState<PrincipalName>('admin-alpha')
  const [target, setTarget] = useState<TargetId>('proxy')
  const [selectedTool, setSelectedTool] = useState<string | null>(null)
  const [exchanges, setExchanges] = useState<Exchange[]>([])
  // The outstanding question, and the arguments it was asked about: the server verifies its sealed
  // digest against them, so the retry must repeat exactly what the first call sent.
  const [asked, setAsked] = useState<{
    result: InputRequiredResult
    toolName: string
    args: Record<string, unknown>
  } | null>(null)
  const [applied, setApplied] = useState<CompleteResult | null>(null)
  const [declined, setDeclined] = useState(false)
  const [handle, setHandle] = useState<TaskResult | null>(null)
  const [cancelling, setCancelling] = useState(false)
  const [cancelAcknowledged, setCancelAcknowledged] = useState(false)
  // Pages stack rather than replace, so the absence of overlap between them is visible (SC-005).
  const [pages, setPages] = useState<SearchPage[]>([])
  const [searchArgs, setSearchArgs] = useState<Record<string, unknown> | null>(null)

  const record = useCallback((exchange: Exchange) => {
    setExchanges((previous) => [exchange, ...previous])
  }, [])

  const reachability = useReachableTargets(targets)
  const credential = usePrincipalToken(principal, targets)

  const session = useMemo<McpSession | null>(
    () =>
      credential.data
        ? {
            target,
            targets,
            token: credential.data.token,
            principalName: principal,
            expiresAt: credential.data.claims.expiresAt,
            record,
          }
        : null,
    [credential.data, principal, record, target, targets],
  )

  const discover = useDiscover(session)
  const tools = useTools(session)
  const call = useToolCall(session)
  const retry = useToolRetry(session)

  // A union, not an intersection: the three result shapes are discriminated by resultType and a
  // value is exactly one of them.
  const readResult = (exchange: Exchange) =>
    (exchange.responseBody as JsonRpcResponse | null)?.result as ToolCallResult | undefined

  const absorb = (
    name: string,
    args: Record<string, unknown>,
    result: ToolCallResult | undefined,
  ) => {
    if (!result) return
    if (result.resultType === 'input_required') {
      setAsked({ result, toolName: name, args })
      return
    }
    if (result.resultType === 'task') {
      // FR-013: shown at once, before any poll. The handle is the whole point of this shape.
      setHandle(result)
      return
    }
    if (result.resultType === 'complete' && !result.isError) {
      if (name === 'search_billing_runs' && result.structuredContent?.runs) {
        const structured = result.structuredContent as unknown as {
          runs: SearchPage['runs']
          total_match_count: number
          truncated: boolean
          next_cursor?: string
          refine_hint?: string
        }
        const page: SearchPage = {
          targetId: target,
          runs: structured.runs,
          totalMatchCount: structured.total_match_count,
          truncated: structured.truncated,
          nextCursor: structured.next_cursor,
          refineHint: structured.refine_hint,
        }
        setPages((previous) => (args.cursor ? [...previous, page] : [page]))
        setSearchArgs(args)
        return
      }
      setApplied(result)
    }
  }

  const onCall = (name: string, args: Record<string, unknown>) => {
    setAsked(null)
    setApplied(null)
    setDeclined(false)
    if (name !== 'search_billing_runs') setPages([])
    if (name === 'start_billing_run') {
      setHandle(null)
      setCancelAcknowledged(false)
    }
    call.mutate(
      { name, args },
      { onSuccess: (exchange) => absorb(name, args, readResult(exchange)) },
    )
  }

  // FR-011a. Sent through the same transport as everything else, with the one thing that is wrong
  // applied by the catalogue's own mangle — so the request is otherwise exactly what the console
  // would have built, and the refusal is about the named defect and nothing else.
  const [sendingMalformed, setSendingMalformed] = useState(false)
  const onMalformed = async (request: MalformedRequest) => {
    if (!session) return
    setSendingMalformed(true)
    try {
      await callMcp(session, {
        method: request.method,
        params: request.params,
        mangle: request.mangle,
        deliberate: true,
      })
    } finally {
      setSendingMalformed(false)
    }
  }

  const onNextPage = (cursor: string) => {
    // FR-015: the cursor travels with the request, and nothing needs copying. The target is left
    // free between pages, which is how the cross-replica continuation is demonstrated (US4-4).
    const args = { ...(searchArgs ?? {}), cursor }
    call.mutate(
      { name: 'search_billing_runs', args },
      { onSuccess: (exchange) => absorb('search_billing_runs', args, readResult(exchange)) },
    )
  }

  const task = useTask(session, handle)
  const liveTask = task.data ?? handle
  const tool = tools.data?.tools.find((candidate) => candidate.name === selectedTool) ?? null

  const onCancel = async (taskId: string) => {
    if (!session) return
    setCancelling(true)
    try {
      await cancelTask(session, taskId)
      // Acknowledged is not the same as stopped: work already in a final state keeps its status,
      // and the server accepts the request anyway (007 FR-031).
      setCancelAcknowledged(isTerminalTask(liveTask?.status))
    } finally {
      setCancelling(false)
    }
  }

  return (
    <div data-dev-only={DEV_ONLY_MARKER}>
      <Typography.Title level={1} style={{ fontSize: 22, marginBottom: token.marginXS }}>
        MCP console
      </Typography.Title>
      <Typography.Paragraph type="secondary" style={{ maxWidth: '68ch' }}>
        A development tool for driving the MCP billing server by hand and watching the protocol
        while it happens. Everything here shows the exact JSON-RPC that travelled, so a tool failure
        and a protocol failure can be told apart. It is not part of the product these pages
        otherwise serve.
      </Typography.Paragraph>

      <Alert
        type="info"
        showIcon
        style={{ marginBottom: token.marginLG }}
        title="Development only"
        description="This page is absent from the packaged build, and the token issuer it depends on does not exist outside development."
      />

      {proxyIsDown(reachability.data) ? (
        <StackOffline />
      ) : (
        <Space orientation="vertical" size="large" style={{ display: 'flex' }}>
          {/* FR-006: a change here affects only subsequent calls. Exchanges already in the log
              keep the principal they were obtained as, because each records its own. */}
          <PrincipalPicker
            value={principal}
            onChange={setPrincipal}
            claims={credential.data?.claims ?? null}
          />

          <TargetPicker targets={reachability.data ?? []} value={target} onChange={setTarget} />

          <section aria-labelledby="server-heading">
            <Typography.Title id="server-heading" level={2} style={{ fontSize: token.fontSizeLG }}>
              The server
            </Typography.Title>
            {discover.isPending && <Spin />}
            {discover.isError && (
              <Alert type="error" showIcon title={(discover.error as Error).message} />
            )}
            {discover.data && (
              <Card size="small">
                <Space orientation="vertical" size={4} style={{ display: 'flex' }}>
                  <Typography.Text>
                    <code>{discover.data._meta?.['io.modelcontextprotocol/serverInfo']?.name}</code>{' '}
                    version{' '}
                    <code>
                      {discover.data._meta?.['io.modelcontextprotocol/serverInfo']?.version}
                    </code>
                  </Typography.Text>
                  <Typography.Text type="secondary">
                    Speaks: <code>{discover.data.supportedVersions.join(', ')}</code>
                  </Typography.Text>
                  {discover.data.instructions && (
                    <Typography.Text type="secondary">{discover.data.instructions}</Typography.Text>
                  )}
                </Space>
              </Card>
            )}
          </section>

          <Row gutter={[token.marginLG, token.marginLG]}>
            <Col xs={24} lg={11}>
              <section aria-labelledby="tools-heading">
                <Typography.Title
                  id="tools-heading"
                  level={2}
                  style={{ fontSize: token.fontSizeLG }}
                >
                  Tools
                </Typography.Title>
                {tools.isPending && <Spin />}
                {tools.isError && (
                  <Alert type="error" showIcon title={(tools.error as Error).message} />
                )}
                {tools.data && (
                  <ToolList
                    tools={tools.data.tools}
                    selected={selectedTool}
                    onSelect={setSelectedTool}
                  />
                )}
              </section>
            </Col>

            <Col xs={24} lg={13}>
              <section aria-labelledby="call-heading">
                <Typography.Title
                  id="call-heading"
                  level={2}
                  style={{ fontSize: token.fontSizeLG }}
                >
                  Call
                </Typography.Title>
                {tool ? (
                  <Card size="small">
                    <ToolCallPanel
                      tool={tool}
                      pending={call.isPending}
                      onCall={(args) => onCall(tool.name, args)}
                    />
                  </Card>
                ) : (
                  <Typography.Text type="secondary">
                    Choose a tool to see the arguments it declares.
                  </Typography.Text>
                )}
              </section>
            </Col>
          </Row>

          {pages.length > 0 && (
            <section aria-labelledby="pages-heading">
              <Typography.Title id="pages-heading" level={2} style={{ fontSize: token.fontSizeLG }}>
                Search pages
              </Typography.Title>
              <PageCursor pages={pages} onNextPage={onNextPage} pending={call.isPending} />
            </section>
          )}

          {liveTask && (
            <section aria-labelledby="task-heading">
              <Typography.Title id="task-heading" level={2} style={{ fontSize: token.fontSizeLG }}>
                Long operation
              </Typography.Title>
              <TaskWatcher
                task={liveTask}
                onCancel={onCancel}
                cancelling={cancelling}
                cancelAcknowledged={cancelAcknowledged}
              />
            </section>
          )}

          {(asked || applied) && (
            <section aria-labelledby="round-trip-heading">
              <Typography.Title
                id="round-trip-heading"
                level={2}
                style={{ fontSize: token.fontSizeLG }}
              >
                {asked ? 'The server asked before acting' : 'Applied'}
              </Typography.Title>
              <ElicitationPanel
                asked={asked?.result ?? null}
                applied={applied}
                declined={declined}
                pending={retry.isPending}
                onAnswer={(answer) =>
                  retry.mutate(
                    {
                      name: asked!.toolName,
                      args: asked!.args,
                      key: answer.key,
                      confirmed: answer.confirmed,
                      requestState: answer.requestState,
                    },
                    {
                      onSuccess: (exchange) => {
                        const result = readResult(exchange)
                        setAsked(null)
                        setDeclined(!answer.confirmed)
                        if (result?.resultType === 'complete') setApplied(result)
                      },
                    },
                  )
                }
              />
            </section>
          )}

          <section aria-labelledby="malformed-heading">
            <Typography.Title
              id="malformed-heading"
              level={2}
              style={{ fontSize: token.fontSizeLG }}
            >
              Deliberate refusals
            </Typography.Title>
            <MalformedPanel onSend={onMalformed} pending={sendingMalformed} />
          </section>

          <ExchangeLog exchanges={exchanges} />
        </Space>
      )}
    </div>
  )
}
