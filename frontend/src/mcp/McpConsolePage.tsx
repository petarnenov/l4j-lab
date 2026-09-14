import { Alert, Space, Typography, theme } from 'antd'
import { DEV_ONLY_MARKER } from './devOnlyMarker'

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

  return (
    <div data-dev-only={DEV_ONLY_MARKER}>
      <Typography.Title level={1} style={{ fontSize: 22, marginBottom: token.marginXS }}>
        MCP console
      </Typography.Title>
      <Typography.Paragraph type="secondary" style={{ maxWidth: '68ch' }}>
        A development tool for driving the MCP billing server by hand and watching the protocol while
        it happens. Everything here shows the exact JSON-RPC that travelled, so a tool failure and a
        protocol failure can be told apart. It is not part of the product these pages otherwise
        serve.
      </Typography.Paragraph>

      <Alert
        type="info"
        showIcon
        style={{ marginBottom: token.marginLG }}
        message="Development only"
        description="This page is absent from the packaged build, and the token issuer it depends on does not exist outside development."
      />

      <Space direction="vertical" size="large" style={{ display: 'flex' }}>
        {/* Regions are composed in T032 (US1) onward; each is a named landmark. */}
      </Space>
    </div>
  )
}
