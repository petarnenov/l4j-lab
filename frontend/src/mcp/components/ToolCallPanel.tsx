import { Alert, Button, Space, Typography, theme } from 'antd'
import { useEffect, useState } from 'react'
import { SchemaForm, initialValues, missingRequired, prunedArguments } from '../schemaForm'
import type { McpTool } from '../wire'

/**
 * Fill a tool's arguments and call it.
 *
 * FR-009: the console does not send a call it can already tell is incomplete, and it says which
 * argument is missing rather than only refusing. Learning a field was empty from the server's
 * refusal is a worse way to learn it.
 */
export interface ToolCallPanelProps {
  tool: McpTool
  onCall: (args: Record<string, unknown>) => void
  pending: boolean
  /** Seeds the form. The page uses it to carry a cursor from one page to the next (FR-015). */
  values?: Record<string, unknown>
}

export function ToolCallPanel({ tool, onCall, pending, values }: ToolCallPanelProps) {
  const { token } = theme.useToken()
  const [current, setCurrent] = useState<Record<string, unknown>>(
    values ?? initialValues(tool.inputSchema),
  )

  // A different tool has different arguments; keeping the old ones would silently send a field the
  // new tool never declared, which its additionalProperties: false would reject.
  useEffect(() => {
    setCurrent(values ?? initialValues(tool.inputSchema))
  }, [tool.name, values])

  const missing = missingRequired(tool.inputSchema, current)

  return (
    <Space orientation="vertical" size="middle" style={{ display: 'flex' }}>
      <SchemaForm
        schema={tool.inputSchema}
        values={current}
        onChange={setCurrent}
        idPrefix={`arg-${tool.name}`}
        disabled={pending}
      />

      {missing.length > 0 && (
        <Alert
          type="info"
          showIcon
          role="region"
          aria-label="Missing arguments"
          title={
            <Typography.Text style={{ fontSize: token.fontSizeSM }}>
              Still needed:{' '}
              {missing.map((name) => (
                <code key={name}>{name} </code>
              ))}
            </Typography.Text>
          }
        />
      )}

      <Button
        type="primary"
        aria-label={`Call ${tool.name}`}
        disabled={missing.length > 0 || pending}
        loading={pending}
        onClick={() => onCall(prunedArguments(current))}
      >
        Call {tool.name}
      </Button>
    </Space>
  )
}
