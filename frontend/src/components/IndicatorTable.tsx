import { Card, Grid, Table, Typography } from 'antd'
import type { IndicatorView } from '../api/client'

const LABELS: Record<string, string> = {
  revenueGrowth: 'Revenue growth',
  grossMargin: 'Gross margin',
  netMargin: 'Net margin',
  currentRatio: 'Current ratio',
  debtToEquity: 'Debt to equity',
}

/**
 * The computed indicators, the source of truth on screen.
 *
 * Values are rendered exactly as the backend supplied them, as strings. There is deliberately no numeric
 * column type, no formatter, and no `toFixed` anywhere near them: every one of those turns `0.3400` into
 * something that is no longer byte-identical to what was computed and stored (FR-022, SC-005).
 */
/** The one place a value is rendered, shared by the table and the cards so they cannot diverge. */
function renderValue(row: IndicatorView) {
  return row.value ? (
    <Typography.Text style={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace' }}>{row.value}</Typography.Text>
  ) : (
    <span>
      <Typography.Text type="secondary" italic>
        not applicable
      </Typography.Text>
      <br />
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        {row.notApplicableReason}
      </Typography.Text>
    </span>
  )
}

export function IndicatorTable({ indicators }: { indicators: IndicatorView[] | null | undefined }) {
  if (!indicators || indicators.length === 0) {
    return <Typography.Text type="secondary">The indicators appear once the third node has run.</Typography.Text>
  }

  // Below the md breakpoint each indicator becomes a card. A phone user should not scroll sideways to see
  // a value next to its name (research R-004). The value is still the supplied string, untouched.
  const screens = Grid.useBreakpoint()
  if (screens.md === false) {
    return (
      <div style={{ display: 'grid', gap: 8 }}>
        {indicators.map((row) => (
          <article key={row.name} aria-label={LABELS[row.name ?? ''] ?? row.name}>
            <Card size="small">
              <Typography.Text strong>{LABELS[row.name ?? ''] ?? row.name}</Typography.Text>
              <div style={{ marginTop: 4 }}>{renderValue(row)}</div>
              <Typography.Text type="secondary" style={{ fontSize: 12, overflowWrap: 'anywhere' }}>
                {(row.derivedFrom ?? []).join(', ')}
              </Typography.Text>
            </Card>
          </article>
        ))}
      </div>
    )
  }

  return (
    <Table<IndicatorView>
      size="small"
      pagination={false}
      rowKey={(row) => row.name ?? ''}
      dataSource={indicators}
      columns={[
        {
          title: 'Indicator',
          dataIndex: 'name',
          render: (name: string) => <Typography.Text strong>{LABELS[name] ?? name}</Typography.Text>,
        },
        {
          title: 'Value',
          key: 'value',
          render: (_, row) => renderValue(row),
        },
        {
          title: 'Derived from',
          dataIndex: 'derivedFrom',
          render: (fields: string[] | undefined) => (
            <Typography.Text type="secondary">{(fields ?? []).join(', ')}</Typography.Text>
          ),
        },
      ]}
    />
  )
}
