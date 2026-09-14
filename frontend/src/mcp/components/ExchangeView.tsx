import { Alert, Card, Space, Tag, Typography, theme } from 'antd'
import { TARGET_LABELS, START_COMMAND } from '../targets'
import type { Exchange, OutcomeKind } from '../transport'
import type { JsonRpcResponse } from '../wire'

/**
 * One call and its answer (FR-010, FR-011).
 *
 * The readable result and the exact bytes sit side by side, so nobody has to open a developer tool
 * to see what travelled (SC-003). The `Authorization` value arrives here already redacted — done in
 * transport.ts rather than at the point of display, so no caller can hold the real header and
 * render it by accident.
 *
 * A tool failure and a protocol failure are rendered by different branches, deliberately. Not
 * confusing them is the thing this console exists to demonstrate, and the server is careful never
 * to confuse them either.
 */

const OUTCOMES: Record<OutcomeKind, { label: string; colour: string }> = {
  ok: { label: 'Result', colour: 'green' },
  'tool-error': { label: 'Tool failure', colour: 'orange' },
  'protocol-error': { label: 'Protocol failure', colour: 'red' },
  'transport-error': { label: 'Transport failure', colour: 'red' },
}

function pretty(value: unknown): string {
  if (typeof value === 'string') return value
  return JSON.stringify(value, null, 2)
}

function headerBlock(headers: Record<string, string>): string {
  return Object.entries(headers)
    .map(([name, value]) => `${name}: ${value}`)
    .join('\n')
}

function Pre({ children }: { children: string }) {
  const { token } = theme.useToken()
  return (
    <pre
      style={{
        margin: 0,
        padding: token.paddingSM,
        background: token.colorFillQuaternary,
        borderRadius: token.borderRadius,
        fontSize: token.fontSizeSM,
        overflowX: 'auto',
        whiteSpace: 'pre-wrap',
        wordBreak: 'break-word',
      }}
    >
      {children}
    </pre>
  )
}

function Outcome({ exchange }: { exchange: Exchange }) {
  const body = exchange.responseBody as JsonRpcResponse | null
  const error = body?.error
  const result = body?.result as
    { content?: Array<{ text?: string }>; isError?: boolean } | undefined
  const text = result?.content
    ?.map((part) => part.text)
    .filter(Boolean)
    .join('\n')

  if (exchange.outcome === 'transport-error') {
    return (
      <Alert
        type="error"
        showIcon
        title={
          exchange.networkError || exchange.httpStatus === 0
            ? 'The request never arrived'
            : `Rejected before the protocol was reached (HTTP ${exchange.httpStatus})`
        }
        description={
          exchange.networkError || exchange.httpStatus === 0
            ? `Nothing answered at this target. The stack may be stopping or may not be running: ${START_COMMAND} starts it. This is not a protocol refusal — the server never saw the request.`
            : 'The token was rejected. It is minted for the mcp-billing-server audience and lasts an hour; the console re-mints it, so this usually means the issuer is unreachable.'
        }
      />
    )
  }

  if (exchange.outcome === 'protocol-error' && error) {
    return (
      <Alert
        type="error"
        showIcon
        title={`Protocol failure — JSON-RPC ${error.code}, HTTP ${exchange.httpStatus}`}
        description={
          <div>
            <Typography.Paragraph style={{ marginBottom: 4 }}>{error.message}</Typography.Paragraph>
            {error.data?.supported && (
              <Typography.Paragraph style={{ marginBottom: 4 }}>
                The server supported: <code>{error.data.supported.join(', ')}</code>
                {error.data.requested ? (
                  <>
                    {' '}
                    — this request asked for <code>{error.data.requested}</code>.
                  </>
                ) : null}
              </Typography.Paragraph>
            )}
            <Typography.Text type="secondary">
              The tool was never reached, so there is no isError anywhere in this answer.
            </Typography.Text>
          </div>
        }
      />
    )
  }

  if (exchange.outcome === 'tool-error') {
    return (
      <Alert
        type="warning"
        showIcon
        title="Tool failure"
        description={
          <div>
            <Typography.Paragraph style={{ marginBottom: 4 }}>{text}</Typography.Paragraph>
            <Typography.Text type="secondary">
              This arrived as a successful response carrying an error flag — HTTP{' '}
              {exchange.httpStatus}, a JSON-RPC result with <code>isError: true</code>. The protocol
              worked; the tool declined.
            </Typography.Text>
          </div>
        }
      />
    )
  }

  return (
    <Typography.Text type="secondary">{text || 'No text rendering was returned.'}</Typography.Text>
  )
}

export function ExchangeView({ exchange }: { exchange: Exchange }) {
  const { token } = theme.useToken()
  const outcome = OUTCOMES[exchange.outcome]
  const body = exchange.responseBody as JsonRpcResponse | null
  const structured = (body?.result as { structuredContent?: unknown } | undefined)
    ?.structuredContent

  return (
    <Card size="small" style={{ borderColor: token.colorBorderSecondary }}>
      <Space orientation="vertical" size="middle" style={{ display: 'flex' }}>
        <div role="region" aria-label="Call attribution">
          <Space wrap size={4}>
            <Tag color={outcome.colour}>{outcome.label}</Tag>
            <code>{exchange.method}</code>
            {exchange.toolName && <code>{exchange.toolName}</code>}
            <Tag>as {exchange.principalName}</Tag>
            <Tag>
              {exchange.targetId === 'proxy'
                ? `${TARGET_LABELS.proxy} — a replica answered; the proxy does not report which`
                : TARGET_LABELS[exchange.targetId]}
            </Tag>
            <Tag>HTTP {exchange.httpStatus || '—'}</Tag>
            <Tag>{exchange.durationMs} ms</Tag>
            {exchange.deliberate && <Tag color="purple">deliberate</Tag>}
          </Space>
        </div>

        <div role="region" aria-label="Outcome">
          <Outcome exchange={exchange} />
        </div>

        {structured !== undefined && (
          <div role="region" aria-label="Structured result">
            <Typography.Text strong style={{ fontSize: token.fontSizeSM }}>
              Structured result
            </Typography.Text>
            <Pre>{pretty(structured)}</Pre>
          </div>
        )}

        <div role="region" aria-label="Request as it travelled">
          <Typography.Text strong style={{ fontSize: token.fontSizeSM }}>
            Request
          </Typography.Text>
          <Pre>{`${headerBlock(exchange.requestHeaders)}\n\n${pretty(exchange.requestBody)}`}</Pre>
        </div>

        <div role="region" aria-label="Response as it travelled">
          <Typography.Text strong style={{ fontSize: token.fontSizeSM }}>
            Response
          </Typography.Text>
          <Pre>
            {`${headerBlock(exchange.responseHeaders)}\n\n${
              exchange.responseBody === null
                ? exchange.networkError
                  ? `(nothing arrived: ${exchange.networkError})`
                  : '(empty)'
                : pretty(exchange.responseBody)
            }`}
          </Pre>
        </div>
      </Space>
    </Card>
  )
}
