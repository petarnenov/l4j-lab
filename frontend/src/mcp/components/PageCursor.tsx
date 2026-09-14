import { Button, Card, Space, Table, Tag, Typography, theme } from 'antd'
import { TARGET_LABELS, type TargetId } from '../targets'

/**
 * Carrying a page cursor from one call to the next (FR-015).
 *
 * Pages stack rather than replace each other, so that the absence of overlap between page 1 and
 * page 2 is something a person can see rather than take on trust (SC-005). Each page records the
 * target it came from, which is how the cross-replica continuation becomes visible: begin on
 * mcp-a, switch the target, take page 2 from mcp-b.
 *
 * The cursor itself is never displayed. The tool description says "do not construct or modify it",
 * and a console demonstrating the protocol while showing an opaque value as though it meant
 * something would be teaching the opposite.
 */
export interface SearchPage {
  targetId: TargetId
  runs: Array<{ run_id: string; executed_by_advisor_id: string; status: string }>
  totalMatchCount: number
  truncated: boolean
  nextCursor?: string
  refineHint?: string
}

export interface PageCursorProps {
  pages: SearchPage[]
  onNextPage: (cursor: string) => void
  pending: boolean
}

export function PageCursor({ pages, onNextPage, pending }: PageCursorProps) {
  const { token } = theme.useToken()
  const last = pages[pages.length - 1]
  if (!last) return null

  return (
    <Card size="small" role="region" aria-label="Search pages">
      <Space orientation="vertical" size="middle" style={{ display: 'flex' }}>
        <Typography.Text type="secondary">
          {last.totalMatchCount} runs match this search in total.
        </Typography.Text>

        {pages.map((page, index) => (
          <div key={`${page.targetId}-${index}`}>
            <Space size={4} style={{ marginBottom: token.marginXXS }}>
              <Typography.Text strong style={{ fontSize: token.fontSizeSM }}>
                Page {index + 1}
              </Typography.Text>
              <Tag>from {TARGET_LABELS[page.targetId]}</Tag>
            </Space>
            <Table
              size="small"
              pagination={false}
              rowKey="run_id"
              dataSource={page.runs}
              columns={[
                { title: 'Run', dataIndex: 'run_id' },
                { title: 'Advisor', dataIndex: 'executed_by_advisor_id' },
                { title: 'Status', dataIndex: 'status' },
              ]}
            />
          </div>
        ))}

        {last.truncated && last.nextCursor ? (
          <Space orientation="vertical" size={4} style={{ display: 'flex' }}>
            <Button loading={pending} onClick={() => onNextPage(last.nextCursor!)}>
              Next page
            </Button>
            {last.refineHint && (
              <Typography.Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
                {last.refineHint}
              </Typography.Text>
            )}
            <Typography.Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
              The cursor travels with the request; nothing needs copying. Change the target first to
              take the next page from another replica.
            </Typography.Text>
          </Space>
        ) : (
          <Typography.Text type="secondary">No more results remain.</Typography.Text>
        )}
      </Space>
    </Card>
  )
}
