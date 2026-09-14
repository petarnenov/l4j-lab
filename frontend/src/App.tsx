import { ConfigProvider, Grid, Layout, Menu, Tag, theme } from 'antd'
import { Suspense, lazy, useState } from 'react'
import { HistoryPage } from './pages/HistoryPage'
import { NewRunPage } from './pages/NewRunPage'
import { ThemeToggle } from './theme/ThemeToggle'

type Tab = 'run' | 'history' | 'mcp'

/**
 * Feature 008: the MCP console, and the guard that keeps it out of the packaged build (FR-001a).
 *
 * `import.meta.env.DEV` is replaced with the literal `false` by `vite build`, so this becomes
 * `const mcpConsole = null`, the branch below is eliminated, and the dynamic import inside it is
 * never referenced — Rollup emits no chunk for it. `npm run check:dev-only` reads the real build
 * output and fails if it ever does. The import must stay *inside* the conditional: hoisting it to a
 * static import at the top of this file would defeat the whole arrangement.
 */
const mcpConsole = import.meta.env.DEV ? lazy(() => import('./mcp/McpConsolePage')) : null

/** The widest the content grows. Past this, lines get too long to read comfortably (FR-005). */
export const CONTENT_MAX_WIDTH = 1200

export function App() {
  const [tab, setTab] = useState<Tab>('run')
  const McpConsole = mcpConsole ?? (() => null)
  const [runId, setRunId] = useState<string | null>(null)
  const { token } = theme.useToken()
  // Below md the side gutter shrinks and the header wraps, so a 320 pixel phone never scrolls sideways.
  const narrow = Grid.useBreakpoint().md === false
  const gutter = narrow ? token.padding : token.paddingLG

  return (
    <Layout style={{ minHeight: '100vh', background: token.colorBgLayout }}>
      <Layout.Header
        style={{
          background: token.colorBgContainer,
          borderBottom: `1px solid ${token.colorBorderSecondary}`,
          paddingInline: 0,
          height: 'auto',
          lineHeight: 'normal',
        }}
      >
        <div
          style={{
            maxWidth: CONTENT_MAX_WIDTH,
            marginLeft: 'auto',
            marginRight: 'auto',
            paddingInline: gutter,
            paddingBlock: narrow ? token.paddingXS : 0,
            display: 'flex',
            flexWrap: 'wrap',
            alignItems: 'center',
            columnGap: token.marginLG,
            rowGap: token.marginXS,
            minHeight: 56,
          }}
        >
          <span style={{ fontWeight: 600, fontSize: token.fontSizeLG, color: token.colorText }}>
            Financial Agent Chain
          </span>
          <nav
            aria-label="Main"
            style={{ flex: narrow ? '1 1 100%' : 1, minWidth: 0, order: narrow ? 3 : 0 }}
          >
            {/*
              Both items stay visible on a phone rather than folding into an overflow menu, so they must
              fit: antd's default item padding pushed "Previous runs" 3 pixels past a 320 pixel viewport.
              Measured in Chromium, which jsdom cannot do.
            */}
            <ConfigProvider
              theme={{ components: { Menu: { itemPaddingInline: narrow ? 8 : 20 } } }}
            >
              <Menu
                mode="horizontal"
                disabledOverflow
                selectedKeys={[tab]}
                onClick={({ key }) => setTab(key as Tab)}
                style={{ borderBottom: 'none', background: 'transparent' }}
                items={[
                  { key: 'run', label: 'New run' },
                  { key: 'history', label: 'Previous runs' },
                  // FR-002: visibly a development tool, never presented as part of the product.
                  ...(mcpConsole
                    ? [
                        {
                          key: 'mcp',
                          label: (
                            <span>
                              MCP console{' '}
                              <Tag color="warning" style={{ marginInlineEnd: 0 }}>
                                dev
                              </Tag>
                            </span>
                          ),
                        },
                      ]
                    : []),
                ]}
              />
            </ConfigProvider>
          </nav>
          {/* Reachable from every screen, because the header is (FR-011). */}
          <ThemeToggle />
        </div>
      </Layout.Header>

      <Layout.Content>
        <div
          style={{
            maxWidth: CONTENT_MAX_WIDTH,
            marginLeft: 'auto',
            marginRight: 'auto',
            padding: gutter,
          }}
        >
          {tab === 'mcp' && mcpConsole ? (
            <Suspense fallback={null}>
              {/* eslint-disable-next-line react/jsx-pascal-case */}
              <McpConsole />
            </Suspense>
          ) : tab === 'run' ? (
            <NewRunPage runId={runId} onStarted={setRunId} />
          ) : (
            <HistoryPage runId={runId} onOpen={setRunId} />
          )}
        </div>
      </Layout.Content>
    </Layout>
  )
}
