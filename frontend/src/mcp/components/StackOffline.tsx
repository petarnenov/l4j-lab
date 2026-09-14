import { Alert, Typography, theme } from 'antd'
import { START_COMMAND, START_TOPOLOGY_COMMAND } from '../targets'

/**
 * FR-003, US1-4, SC-006: opening the console with the stack stopped produces an explanation and a
 * command to run, never an empty page or a generic failure.
 *
 * The second command is named too. Most of the console works against a plain `make mcp-up`, but the
 * cross-replica demonstrations — the ones feature 007 exists to show — need the individual replicas
 * published, and finding that out after starting the stack the other way is a wasted minute.
 */
export function StackOffline() {
  const { token } = theme.useToken()

  return (
    <Alert
      type="warning"
      showIcon
      role="region"
      aria-label="MCP stack state"
      title={
        <Typography.Title level={2} style={{ fontSize: token.fontSizeLG, margin: 0 }}>
          The MCP stack is not reachable
        </Typography.Title>
      }
      description={
        <div>
          <Typography.Paragraph style={{ marginBottom: token.marginXS }}>
            Nothing answered on the proxy this console forwards to. The stack runs as its own
            Compose project and is not started by the application.
          </Typography.Paragraph>
          <Typography.Paragraph style={{ marginBottom: token.marginXS }}>
            Start it with <code>{START_COMMAND}</code>, then reload.
          </Typography.Paragraph>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
            To demonstrate the cross-replica scenarios as well, start it with{' '}
            <code>{START_TOPOLOGY_COMMAND}</code> instead: that publishes <code>mcp-a</code>,{' '}
            <code>mcp-b</code> and <code>mcp-c</code> individually, which a plain start deliberately
            does not.
          </Typography.Paragraph>
        </div>
      }
    />
  )
}
