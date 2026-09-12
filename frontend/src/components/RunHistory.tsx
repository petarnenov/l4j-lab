import CheckCircleOutlined from '@ant-design/icons/CheckCircleOutlined'
import ClockCircleOutlined from '@ant-design/icons/ClockCircleOutlined'
import CloseCircleOutlined from '@ant-design/icons/CloseCircleOutlined'
import { Alert, Button, Spin, Tag, Typography, theme } from 'antd'
import { useRuns } from '../hooks/useRuns'

/** Outcome by icon and label as well as colour, so it survives a monochrome projector (FR-008). */
function OutcomeTag({ status }: { status: string | undefined }) {
  switch (status) {
    case 'SUCCEEDED':
      return <Tag icon={<CheckCircleOutlined />} color="success">SUCCEEDED</Tag>
    case 'FAILED':
      return <Tag icon={<CloseCircleOutlined />} color="error">FAILED</Tag>
    case 'TIMED_OUT':
      return <Tag icon={<CloseCircleOutlined />} color="error">TIMED_OUT</Tag>
    default:
      return <Tag icon={<ClockCircleOutlined />} color="processing">{status}</Tag>
  }
}

function formatTime(value: string | undefined) {
  return value ? new Date(value).toLocaleString() : ''
}

/**
 * Previous runs, newest first (FR-014). Running the same selection twice and reading both summaries is how
 * the model's non-determinism becomes visible next to indicators that never change.
 *
 * Each row is a native button in a semantic list rather than antd's List, which antd 6.6 deprecates. Its
 * suggested replacement, Listy, is a virtualised scrolling primitive meant for very long lists, and would
 * cost accessibility for a list capped at fifty rows. A native button also gives keyboard support for free.
 */
export function RunHistory({ onOpen }: { onOpen: (runId: string) => void }) {
  const history = useRuns()
  const { token } = theme.useToken()

  if (history.isLoading) return <Spin description="Loading previous runs" />
  if (history.isError) return <Alert type="error" showIcon title="The history could not be loaded." />

  const runs = history.data?.pages.flatMap((page) => page.runs ?? []) ?? []

  return (
    <section aria-labelledby="history-heading">
      <Typography.Title level={1} id="history-heading" style={{ fontSize: 22 }}>
        Previous runs
      </Typography.Title>

      {runs.length === 0 ? (
        <Typography.Text type="secondary">No runs yet. Start one and it will appear here.</Typography.Text>
      ) : (
        <ul
          style={{
            listStyle: 'none',
            margin: 0,
            padding: 0,
            background: token.colorBgContainer,
            border: `1px solid ${token.colorBorderSecondary}`,
            borderRadius: token.borderRadiusLG,
          }}
        >
          {runs.map((run, index) => (
            <li
              key={run.runId}
              style={{ borderTop: index === 0 ? 'none' : `1px solid ${token.colorBorderSecondary}` }}
            >
              <button
                type="button"
                onClick={() => run.runId && onOpen(run.runId)}
                style={{
                  display: 'block',
                  width: '100%',
                  textAlign: 'left',
                  padding: `${token.paddingSM}px ${token.padding}px`,
                  background: 'transparent',
                  border: 'none',
                  cursor: 'pointer',
                  color: token.colorText,
                  font: 'inherit',
                }}
              >
                <span style={{ display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'baseline' }}>
                  <Typography.Text strong style={{ overflowWrap: 'anywhere' }}>
                    {run.companyName ?? run.companyId}
                  </Typography.Text>
                  <Typography.Text>{run.period}</Typography.Text>
                  <OutcomeTag status={run.status} />
                  <Typography.Text type="secondary">{formatTime(run.startedAt)}</Typography.Text>
                </span>
                {run.summaryPreview && (
                  <Typography.Text
                    type="secondary"
                    style={{ display: 'block', marginTop: 4, overflowWrap: 'anywhere' }}
                  >
                    {run.summaryPreview}
                  </Typography.Text>
                )}
              </button>
            </li>
          ))}
        </ul>
      )}

      {history.hasNextPage && (
        <Button style={{ marginTop: 16 }} onClick={() => void history.fetchNextPage()}>
          Load more
        </Button>
      )}
    </section>
  )
}
