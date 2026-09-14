import { Alert, Card, Col, Row, Space, Spin, Typography, theme } from 'antd'
import { useCallback, useMemo, useState } from 'react'
import { ElicitationPanel } from './components/ElicitationPanel'
import { ExchangeLog } from './components/ExchangeLog'
import { PrincipalPicker } from './components/PrincipalPicker'
import { StackOffline } from './components/StackOffline'
import { ToolCallPanel } from './components/ToolCallPanel'
import { ToolList } from './components/ToolList'
import { DEV_ONLY_MARKER } from './devOnlyMarker'
import { useDiscover } from './hooks/useDiscover'
import { usePrincipalToken } from './hooks/usePrincipalToken'
import { proxyIsDown, useReachableTargets } from './hooks/useReachableTargets'
import { useTools } from './hooks/useTools'
import { useToolCall, useToolRetry } from './hooks/useToolCall'
import type { PrincipalName } from './principals'
import { devProxyTargets } from './targets'
import type { Exchange, McpSession } from './transport'
import type { CompleteResult, InputRequiredResult, JsonRpcResponse, ToolCallResult } from './wire'
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
  const [target] = useState<TargetId>('proxy')
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

  const onCall = (name: string, args: Record<string, unknown>) => {
    setAsked(null)
    setApplied(null)
    setDeclined(false)
    call.mutate(
      { name, args },
      {
        onSuccess: (exchange) => {
          const result = readResult(exchange)
          if (result?.resultType === 'input_required') {
            setAsked({ result, toolName: name, args })
          } else if (result?.resultType === 'complete' && !result.isError) {
            setApplied(result)
          }
        },
      },
    )
  }

  const tool = tools.data?.tools.find((candidate) => candidate.name === selectedTool) ?? null

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

          <ExchangeLog exchanges={exchanges} />
        </Space>
      )}
    </div>
  )
}
