import { Alert, Button, Card, Space, Tag, Typography, theme } from 'antd'
import { MALFORMED_REQUESTS, type MalformedRequest } from '../malformed'

/**
 * Producing each kind of protocol refusal on demand (FR-011a, SC-003a).
 *
 * Everything here is marked deliberate, and the marking travels with the exchange into the log, so
 * a refusal produced on purpose is never mistaken for a fault in the system.
 */
export interface MalformedPanelProps {
  onSend: (request: MalformedRequest) => void
  pending: boolean
}

export function MalformedPanel({ onSend, pending }: MalformedPanelProps) {
  const { token } = theme.useToken()

  return (
    <Card size="small" role="region" aria-label="Deliberately malformed requests">
      <Space orientation="vertical" size="middle" style={{ display: 'flex' }}>
        <Alert
          type="info"
          showIcon
          title="These are wrong on purpose"
          description="Each sends a request with exactly one thing wrong with it, as the currently chosen principal and to the currently chosen target. The refusals they produce are the system working, not failing, and each exchange is marked deliberate in the log below."
        />

        {MALFORMED_REQUESTS.map((request) => (
          <div key={request.id}>
            <Space size={4} wrap style={{ marginBottom: token.marginXXS }}>
              <Typography.Text strong>{request.label}</Typography.Text>
              <Tag color="purple">expects JSON-RPC {request.expectedCode}</Tag>
            </Space>
            <Typography.Paragraph
              type="secondary"
              style={{ fontSize: token.fontSizeSM, marginBottom: token.marginXS }}
            >
              {request.explanation}
            </Typography.Paragraph>
            <Button
              loading={pending}
              onClick={() => onSend(request)}
              aria-label={`Send: ${request.label}`}
            >
              Send: {request.label}
            </Button>
          </div>
        ))}

        <Typography.Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
          There is no free-form editing of headers or body here, and there is not going to be. That
          is a different tool with a different purpose, and it already exists — it is called{' '}
          <code>curl</code>. Three prepared cases reach the same demonstration in one action.
        </Typography.Text>
      </Space>
    </Card>
  )
}
