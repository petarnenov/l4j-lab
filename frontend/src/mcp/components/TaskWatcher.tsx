import { Alert, Button, Card, Descriptions, Space, Tag, Typography, theme } from 'antd'
import { type TaskResult, isTerminalTask } from '../wire'

/**
 * Work that outlives the call that started it (FR-013, FR-014).
 *
 * The handle appears the moment the first result returns, before any poll: that a long operation
 * hands back a reference immediately is the property this exists to show, and waiting for a poll to
 * render it would hide exactly that.
 */
export interface TaskWatcherProps {
  task: TaskResult
  onCancel: (taskId: string) => void
  cancelling: boolean
  /** Set when a cancellation was acknowledged for work that had already finished (007 FR-031). */
  cancelAcknowledged?: boolean
}

const STATUS_COLOURS: Record<string, string> = {
  working: 'processing',
  input_required: 'warning',
  completed: 'green',
  failed: 'red',
  cancelled: 'default',
}

export function TaskWatcher({
  task,
  onCancel,
  cancelling,
  cancelAcknowledged = false,
}: TaskWatcherProps) {
  const { token } = theme.useToken()
  const terminal = isTerminalTask(task.status)

  return (
    <Card size="small" role="region" aria-label="Long operation">
      <Space orientation="vertical" size="middle" style={{ display: 'flex' }}>
        <Space wrap size={4}>
          <Tag color={STATUS_COLOURS[task.status] ?? 'default'}>{task.status}</Tag>
          <code>{task.taskId}</code>
        </Space>

        <Descriptions size="small" column={1} colon={false}>
          {task.statusMessage && (
            <Descriptions.Item label="Progress">{task.statusMessage}</Descriptions.Item>
          )}
          <Descriptions.Item label="Polling">
            {terminal ? (
              <Typography.Text type="secondary">Finished, so no longer polling.</Typography.Text>
            ) : (
              <Typography.Text type="secondary">
                Every {task.pollIntervalMs ?? 2000} ms — the interval the server asked for, not one
                chosen by the console. Each poll appears in the log below.
              </Typography.Text>
            )}
          </Descriptions.Item>
        </Descriptions>

        {cancelAcknowledged && (
          <Alert
            type="info"
            showIcon
            title="The cancellation was acknowledged"
            description="The work had already reached a final state, so its status is unchanged. The server accepts the request rather than treating it as an error, which is why this is not a failure."
          />
        )}

        {task.result !== undefined && (
          <pre
            style={{
              margin: 0,
              padding: token.paddingSM,
              background: token.colorFillQuaternary,
              borderRadius: token.borderRadius,
              fontSize: token.fontSizeSM,
              overflowX: 'auto',
              whiteSpace: 'pre-wrap',
            }}
          >
            {JSON.stringify(task.result, null, 2)}
          </pre>
        )}

        {!terminal && (
          <Button danger loading={cancelling} onClick={() => onCancel(task.taskId)}>
            Cancel this run
          </Button>
        )}
      </Space>
    </Card>
  )
}
