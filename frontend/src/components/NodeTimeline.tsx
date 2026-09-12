import { Grid, Steps, Typography } from 'antd'
import { useState } from 'react'
import type { NodeView } from '../api/client'
import { NodeDetail } from './NodeDetail'

/**
 * The four node boundaries, in execution order. Stepping through them is what turns a working demo into a
 * lesson (US2 of feature 001, FR-021). No boundary is ever collapsed or hidden to save space.
 */
export function NodeTimeline({ nodes }: { nodes: NodeView[] | undefined }) {
  // Selection lives in state that a layout change does not reset (US3 scenario 5).
  const [selected, setSelected] = useState(0)
  const vertical = Grid.useBreakpoint().md === false

  if (!nodes || nodes.length === 0) {
    return <Typography.Text type="secondary">Node records appear as the chain advances.</Typography.Text>
  }

  const index = Math.min(selected, nodes.length - 1)

  return (
    <div>
      <div role="group" aria-label="Node steps">
        <Steps
          size="small"
          // Stated explicitly rather than left to antd's responsive default, so the behaviour is visible here.
          orientation={vertical ? 'vertical' : 'horizontal'}
          responsive={false}
          current={index}
          onChange={setSelected}
          items={nodes.map((node) => ({
            title: node.nodeName,
            content: node.succeeded ? `${node.durationMs} ms` : `${node.durationMs} ms · failed`,
            status: node.succeeded ? 'finish' : 'error',
          }))}
        />
      </div>

      <NodeDetail node={nodes[index]} />
    </div>
  )
}
