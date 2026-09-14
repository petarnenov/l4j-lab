import { Empty, Space, Typography, theme } from 'antd'
import type { Exchange } from '../transport'
import { ExchangeView } from './ExchangeView'

/**
 * Every call this session made, newest first.
 *
 * Not persisted, not editable, not replayable — the spec excludes all three. It shows what happened
 * in the current session; it is not a history. Task polls appear here like any other call, which is
 * how a poll landing on another replica becomes visible rather than hidden inside a hook.
 */
export function ExchangeLog({ exchanges }: { exchanges: Exchange[] }) {
  const { token } = theme.useToken()

  return (
    <section aria-labelledby="exchange-log-heading">
      <Typography.Title id="exchange-log-heading" level={2} style={{ fontSize: token.fontSizeLG }}>
        Exchanges
      </Typography.Title>
      {exchanges.length === 0 ? (
        <Empty description="Nothing has been called yet." image={Empty.PRESENTED_IMAGE_SIMPLE} />
      ) : (
        <Space orientation="vertical" size="middle" style={{ display: 'flex' }}>
          {exchanges.map((exchange) => (
            <ExchangeView key={exchange.id} exchange={exchange} />
          ))}
        </Space>
      )}
    </section>
  )
}
