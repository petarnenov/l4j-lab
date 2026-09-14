import { Alert, Radio, Space, Tooltip, Typography, theme } from 'antd'
import { START_TOPOLOGY_COMMAND, type Target, type TargetId } from '../targets'

/**
 * Where a call goes (FR-016, FR-017).
 *
 * Three of feature 007's headline properties exist only across replicas — a cursor minted by one
 * read by another, a confirmation retry landing elsewhere, polls spread over three. The base stack
 * publishes only the proxy, which is the honest shape for a deployment, so the console addresses
 * replicas when they are reachable and explains their absence when they are not. Absence is the
 * normal case, not a fault, and the wording here has to carry that.
 */
export interface TargetPickerProps {
  targets: Target[]
  value: TargetId
  onChange: (target: TargetId) => void
}

export function TargetPicker({ targets, value, onChange }: TargetPickerProps) {
  const { token } = theme.useToken()
  const replicasMissing = targets.some(
    (target) => target.id !== 'proxy' && target.reachability !== 'reachable',
  )

  return (
    <section aria-labelledby="target-heading">
      <Typography.Title id="target-heading" level={2} style={{ fontSize: token.fontSizeLG }}>
        Target
      </Typography.Title>

      <Space orientation="vertical" size="middle" style={{ display: 'flex' }}>
        <Radio.Group value={value} onChange={(event) => onChange(event.target.value as TargetId)}>
          {targets.map((target) => {
            const unreachable = target.id !== 'proxy' && target.reachability !== 'reachable'
            // A disabled radio still reports its name, so the reason stays reachable rather than
            // the option merely vanishing. The tooltip wraps only the disabled ones: antd's wrapper
            // takes pointer events off the child, which would make a reachable replica unclickable.
            const button = (
              <Radio.Button value={target.id} disabled={unreachable}>
                {target.label}
              </Radio.Button>
            )
            return unreachable ? (
              <Tooltip key={target.id} title={target.absenceReason ?? undefined}>
                {button}
              </Tooltip>
            ) : (
              <span key={target.id}>{button}</span>
            )
          })}
        </Radio.Group>

        {value === 'proxy' && (
          <div role="region" aria-label="Which server answered">
            <Typography.Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
              The proxy round-robins over the three replicas and does not report which one answered:
              nothing in <code>serverInfo</code> or the response headers identifies an instance.
              Address a replica by name to see the hop.
            </Typography.Text>
          </div>
        )}

        {replicasMissing && (
          <Alert
            type="info"
            showIcon
            role="region"
            aria-label="Replica topology"
            title="The individual replicas are not published"
            description={
              <Typography.Text>
                This is not a fault: the stack publishes one port on purpose, which is what a real
                deployment would do. To address <code>mcp-a</code>, <code>mcp-b</code> and{' '}
                <code>mcp-c</code> by name — and so to demonstrate a cursor minted by one being
                honoured by another — start it with <code>{START_TOPOLOGY_COMMAND}</code> instead.
              </Typography.Text>
            }
          />
        )}
      </Space>
    </section>
  )
}
