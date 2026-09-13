import { Alert, Card, Col, Row, Space, Spin, Tag, Typography } from 'antd'
import { IndicatorTable } from '../components/IndicatorTable'
import { NodeTimeline } from '../components/NodeTimeline'
import { RunProgress } from '../components/RunProgress'
import { SummaryPanel } from '../components/SummaryPanel'
import { useRun } from '../hooks/useRun'

/**
 * One run, in full. The summary and the indicators it was built from share a screen (FR-020), and the node
 * timeline sits below them (FR-021). A failure names its node and says the earlier work survived (FR-024).
 */
export function RunDetailPage({ runId }: { runId: string }) {
  const run = useRun(runId)

  if (run.isLoading) return <Spin description="Loading the run" />
  if (run.isError) return <Alert type="error" showIcon title={(run.error as Error).message} />
  if (!run.data) return null

  const detail = run.data
  const failed = detail.status === 'FAILED' || detail.status === 'TIMED_OUT'

  return (
    <div style={{ marginTop: 24 }}>
      <header style={{ marginBottom: 16 }}>
        <Typography.Title level={1} style={{ fontSize: 22, marginBottom: 8 }}>
          {detail.companyName ?? detail.companyId} · {detail.period}
        </Typography.Title>
        <Space wrap size={[8, 8]}>
          <Tag>{detail.status}</Tag>
          <Tag>{detail.providerMode}</Tag>
          <Tag>{detail.modelId}</Tag>
        </Space>
      </header>

      <RunProgress status={detail.status} currentNode={detail.currentNode} />

      {failed && (
        <Alert
          style={{ marginBottom: 16 }}
          type="error"
          showIcon
          title={
            <Typography.Title level={2} style={{ fontSize: 16, margin: 0 }}>
              This run stopped at {detail.failedNode}
              {detail.status === 'TIMED_OUT' && ' because the model took too long'}
            </Typography.Title>
          }
          description={
            <>
              <Typography.Paragraph style={{ marginBottom: 4 }}>
                {detail.failureReason}
              </Typography.Paragraph>
              <Typography.Text type="secondary">
                Everything the earlier nodes produced is still below, including the indicators,
                which were computed before the failure.
              </Typography.Text>
            </>
          }
        />
      )}

      <Row gutter={[16, 16]}>
        <Col xs={24} md={12}>
          <SummaryPanel summary={detail.summary} />
        </Col>
        <Col xs={24} md={12}>
          <Card
            title={
              <Typography.Title level={2} style={{ margin: 0, fontSize: 16 }}>
                Indicators
              </Typography.Title>
            }
          >
            <IndicatorTable indicators={detail.indicators} />
          </Card>
        </Col>
      </Row>

      <Card
        style={{ marginTop: 16 }}
        title={
          <Typography.Title level={2} style={{ margin: 0, fontSize: 16 }}>
            What each node did
          </Typography.Title>
        }
      >
        <NodeTimeline nodes={detail.nodes} />
      </Card>
    </div>
  )
}
