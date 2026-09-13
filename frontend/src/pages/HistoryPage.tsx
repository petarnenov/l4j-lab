import { RunHistory } from '../components/RunHistory'
import { RunDetailPage } from './RunDetailPage'

export function HistoryPage({
  runId,
  onOpen,
}: {
  runId: string | null
  onOpen: (runId: string) => void
}) {
  return (
    <div>
      <RunHistory onOpen={onOpen} />
      {runId && <RunDetailPage runId={runId} />}
    </div>
  )
}
