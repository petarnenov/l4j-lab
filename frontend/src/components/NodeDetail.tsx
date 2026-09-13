import { Alert, Descriptions, Tag, Typography, theme } from 'antd'
import type { CSSProperties } from 'react'
import type { NodeView } from '../api/client'

/** Wide or long content scrolls inside its own capped region and never widens the page (FR-017). */
function scrollRegion(token: ReturnType<typeof theme.useToken>['token']): CSSProperties {
  return {
    maxHeight: '24rem',
    overflow: 'auto',
    margin: 0,
    padding: token.paddingSM,
    background: token.colorFillQuaternary,
    border: `1px solid ${token.colorBorderSecondary}`,
    borderRadius: token.borderRadius,
    fontSize: 12,
    lineHeight: 1.5,
    whiteSpace: 'pre-wrap',
    wordBreak: 'break-word',
  }
}

function Section({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div style={{ marginTop: 16 }}>
      <Typography.Title level={4} style={{ fontSize: 13, marginBottom: 8 }}>
        {label}
      </Typography.Title>
      {children}
    </div>
  )
}

/**
 * One node's record in full: what it received, what it produced, how long it took, and for the summarizing
 * node the exact text sent to the model and the exact text returned (FR-010, FR-021).
 */
export function NodeDetail({ node }: { node: NodeView }) {
  const { token } = theme.useToken()
  const region = scrollRegion(token)
  const isSummarize = node.position === 4

  return (
    <article>
      <Typography.Title level={3} style={{ fontSize: 16, marginTop: 16 }}>
        {node.position}. {node.nodeName}
      </Typography.Title>

      <Descriptions size="small" column={{ xs: 1, sm: 3 }} bordered>
        <Descriptions.Item label="Duration">{node.durationMs} ms</Descriptions.Item>
        <Descriptions.Item label="Outcome">
          {node.succeeded ? <Tag color="success">succeeded</Tag> : <Tag color="error">failed</Tag>}
        </Descriptions.Item>
        {isSummarize && (node.inputTokens != null || node.outputTokens != null) && (
          <Descriptions.Item label="Tokens">
            Tokens in {node.inputTokens ?? 'n/a'}, out {node.outputTokens ?? 'n/a'}
          </Descriptions.Item>
        )}
      </Descriptions>

      {!node.succeeded && node.failureReason && (
        <Alert style={{ marginTop: 16 }} type="error" showIcon title={node.failureReason} />
      )}

      <Section label="Received">
        {node.inputPayload == null ? (
          <Typography.Text type="secondary">Nothing was received.</Typography.Text>
        ) : (
          <pre style={region}>{JSON.stringify(node.inputPayload, null, 2)}</pre>
        )}
      </Section>

      <Section label="Produced">
        {node.outputPayload == null ? (
          <Typography.Text type="secondary">
            Nothing was produced, because this node failed.
          </Typography.Text>
        ) : (
          <pre style={region}>{JSON.stringify(node.outputPayload, null, 2)}</pre>
        )}
      </Section>

      {isSummarize && (
        <>
          <Section label="Sent to the model">
            {node.modelRequestText ? (
              <pre style={region}>{node.modelRequestText}</pre>
            ) : (
              <Typography.Text type="secondary">The request was never sent.</Typography.Text>
            )}
          </Section>
          <Section label="Returned by the model">
            {node.modelResponseText ? (
              <pre style={region}>{node.modelResponseText}</pre>
            ) : (
              <Typography.Text type="secondary">Nothing came back.</Typography.Text>
            )}
          </Section>
        </>
      )}
    </article>
  )
}
