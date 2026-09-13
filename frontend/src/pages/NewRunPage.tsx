import { Card, Typography } from 'antd'
import { RunLauncher } from '../components/RunLauncher'
import { RunDetailPage } from './RunDetailPage'

/** Pick a company and a period, start the chain, and watch it run on the same screen. */
export function NewRunPage({
  runId,
  onStarted,
}: {
  runId: string | null
  onStarted: (runId: string) => void
}) {
  return (
    <div>
      <section aria-labelledby="launcher-heading">
        <Typography.Title level={1} id="launcher-heading" style={{ fontSize: 22 }}>
          Run the chain
        </Typography.Title>
        <Typography.Paragraph type="secondary">
          Four nodes run in order. The first three are deterministic. Only the fourth calls a
          language model.
        </Typography.Paragraph>
        <Card>
          <RunLauncher onStarted={onStarted} />
        </Card>
      </section>

      {runId && <RunDetailPage runId={runId} />}
    </div>
  )
}
