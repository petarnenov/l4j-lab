import { Card, Space, Tag, Typography, theme } from 'antd'
import type { McpTool, ToolAnnotations } from '../wire'

/**
 * The tools the server offers, in the order it returned them (FR-007).
 *
 * The order is never sorted here: it is fixed in the server for a stated reason — reads before
 * writes, and within reads the order an agent would naturally walk. Re-sorting would discard that.
 *
 * All four declared behaviours are shown for every tool, including the ones that are false. "Not
 * idempotent" is information; its absence is not.
 */

export function annotationTags(annotations: ToolAnnotations): Array<{ text: string; ok: boolean }> {
  return [
    { text: annotations.readOnlyHint ? 'read-only' : 'writes', ok: annotations.readOnlyHint },
    {
      text: annotations.destructiveHint ? 'destructive' : 'non-destructive',
      ok: !annotations.destructiveHint,
    },
    {
      text: annotations.idempotentHint ? 'idempotent' : 'not idempotent',
      ok: annotations.idempotentHint,
    },
    { text: annotations.openWorldHint ? 'open world' : 'closed world', ok: false },
  ]
}

export interface ToolListProps {
  tools: McpTool[]
  selected: string | null
  onSelect: (name: string) => void
}

export function ToolList({ tools, selected, onSelect }: ToolListProps) {
  const { token } = theme.useToken()

  return (
    <div style={{ display: 'grid', gap: token.marginSM }}>
      {tools.map((tool) => (
        <Card
          key={tool.name}
          size="small"
          role="button"
          tabIndex={0}
          aria-label={`Select tool ${tool.name}`}
          aria-pressed={selected === tool.name}
          data-tool={tool.name}
          onClick={() => onSelect(tool.name)}
          onKeyDown={(event) => {
            if (event.key === 'Enter' || event.key === ' ') {
              event.preventDefault()
              onSelect(tool.name)
            }
          }}
          style={{
            cursor: 'pointer',
            borderColor: selected === tool.name ? token.colorPrimary : undefined,
          }}
        >
          <Space orientation="vertical" size={4} style={{ display: 'flex' }}>
            <Space wrap size={4}>
              <code style={{ fontWeight: 600 }}>{tool.name}</code>
              {tool.title && <Typography.Text type="secondary">{tool.title}</Typography.Text>}
            </Space>
            <Space wrap size={4}>
              {annotationTags(tool.annotations).map((tag) => (
                <Tag key={tag.text} color={tag.ok ? 'green' : 'orange'}>
                  {tag.text}
                </Tag>
              ))}
            </Space>
            <Typography.Paragraph
              type="secondary"
              style={{ marginBottom: 0, fontSize: token.fontSizeSM }}
            >
              {tool.description}
            </Typography.Paragraph>
          </Space>
        </Card>
      ))}
    </div>
  )
}
