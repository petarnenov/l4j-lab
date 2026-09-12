import HourglassOutlined from '@ant-design/icons/HourglassOutlined'
import LoadingOutlined from '@ant-design/icons/LoadingOutlined'
import { Card, Steps, Typography } from 'antd'
import { isTerminal } from '../api/client'
import { useTheme } from '../theme/useTheme'

const NODE_ORDER = ['PrepareRequest', 'RetrieveRecords', 'ComputeIndicators', 'Summarize']

const NODE_LABELS: Record<string, string> = {
  PrepareRequest: 'Preparing the request',
  RetrieveRecords: 'Retrieving the records',
  ComputeIndicators: 'Computing the indicators',
  Summarize: 'Asking the model to summarise',
}

/**
 * What the chain is doing right now (FR-020, FR-025). Completed steps carry a check icon and the running
 * step a spinner, so progress reads without relying on colour (FR-008).
 */
export function RunProgress({
  status,
  currentNode,
}: {
  status: string | undefined
  currentNode: string | null | undefined
}) {
  const { reducedMotion } = useTheme()

  if (!status || isTerminal(status)) return null

  const active = currentNode ? NODE_ORDER.indexOf(currentNode) : -1

  return (
    <Card size="small" style={{ marginBottom: 16 }}>
      <Typography.Paragraph role="status" aria-live="polite" style={{ marginBottom: 12 }}>
        {status === 'PENDING'
          ? 'Queued, the chain is about to start.'
          : (NODE_LABELS[currentNode ?? ''] ?? 'Running')}
        {currentNode === 'Summarize' && ' This is the slow step; the model can take a while.'}
      </Typography.Paragraph>

      <Steps
        size="small"
        current={Math.max(active, 0)}
        items={NODE_ORDER.map((node, index) => ({
          title: NODE_LABELS[node],
          status: index < active ? 'finish' : index === active ? 'process' : 'wait',
          // A model call can take the better part of a minute. For a learner who asked for less motion the
          // active step shows a static hourglass instead (FR-018). `spin={false}` would not do it: antd's
          // icon component spins anything named "loading" regardless of that prop.
          icon: index === active ? reducedMotion ? <HourglassOutlined /> : <LoadingOutlined /> : undefined,
        }))}
      />
    </Card>
  )
}
