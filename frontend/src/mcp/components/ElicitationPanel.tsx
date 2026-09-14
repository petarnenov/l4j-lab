import { Alert, Button, Card, Descriptions, Space, Tag, Typography, theme } from 'antd'
import { RESET_COMMAND } from '../targets'
import type { CompleteResult, InputRequiredResult } from '../wire'

/**
 * The change that asks before it acts (FR-012, FR-012a, FR-012b).
 *
 * Three calls: the first applies nothing and returns a question, the second carries the answer and
 * the state the server issued, and a third with the same `operation_id` returns the original result
 * without acting again. The hard part is not the mechanism but not misrepresenting it — so while a
 * question is outstanding this shows no result panel at all, because there is no result.
 */

export interface ElicitationAnswer {
  key: string
  confirmed: boolean
  requestState: string
}

export interface ElicitationPanelProps {
  asked: InputRequiredResult | null
  applied: CompleteResult | null
  onAnswer: (answer: ElicitationAnswer) => void
  pending: boolean
  /** True when the answer sent back was "no". The outcome is then expected, not a failure. */
  declined?: boolean
}

interface FeeChange {
  accountId: string
  deltaBps: number
  newFeeBps: number
  previousFeeBps: number
  legacyReferenceId: string
  confirmedByUserId: string
  replayed: boolean
}

/**
 * FR-012a: the fee before and after.
 *
 * The output schema carries `new_fee_bps` and `delta_bps` and no previous fee (finding F-002), so
 * the before value is computed by subtraction. That is exact, and it stays correct on the replay
 * path: a replayed result returns the original `new_fee_bps` alongside the original `delta_bps`.
 *
 * Not parsed out of the elicitation message, which does contain it — that message is prose written
 * for a person, the server is free to reword it, and arithmetic that cannot fail is available.
 */
export function feeChange(structured: Record<string, unknown> | undefined): FeeChange | null {
  if (!structured || structured.new_fee_bps === undefined) return null
  const newFeeBps = Number(structured.new_fee_bps)
  const deltaBps = Number(structured.delta_bps)
  return {
    accountId: String(structured.account_id ?? ''),
    deltaBps,
    newFeeBps,
    previousFeeBps: newFeeBps - deltaBps,
    legacyReferenceId: String(structured.legacy_reference_id ?? ''),
    confirmedByUserId: String(structured.confirmed_by_user_id ?? ''),
    replayed: Boolean(structured.replayed),
  }
}

function basisPoints(bps: number): string {
  return `${bps} bps (${(bps / 100).toFixed(2)}%)`
}

export function ElicitationPanel({
  asked,
  applied,
  onAnswer,
  pending,
  declined = false,
}: ElicitationPanelProps) {
  const { token } = theme.useToken()

  if (asked) {
    const [key, request] = Object.entries(asked.inputRequests)[0]
    return (
      <Card size="small" role="region" aria-label="Confirmation the server asked for">
        <Space orientation="vertical" size="middle" style={{ display: 'flex' }}>
          <Alert
            type="warning"
            showIcon
            title="Nothing has been applied"
            description="The first call did not execute. The server returned the question below and an opaque state; the change happens only when that answer and that state are sent back."
          />
          {/* Verbatim. Not paraphrased, not restructured — FR-012 asks for the server's phrasing. */}
          <Typography.Paragraph strong style={{ marginBottom: 0 }}>
            {request.params.message}
          </Typography.Paragraph>
          <Space>
            <Button
              type="primary"
              loading={pending}
              onClick={() => onAnswer({ key, confirmed: true, requestState: asked.requestState })}
            >
              Confirm and apply
            </Button>
            <Button
              loading={pending}
              onClick={() => onAnswer({ key, confirmed: false, requestState: asked.requestState })}
            >
              Decline
            </Button>
          </Space>
          <Typography.Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
            Declining is an answer, not a refusal to answer: the server replies with a result saying
            the change was not applied, and that is not an error.
          </Typography.Text>
        </Space>
      </Card>
    )
  }

  if (!applied) return null

  const change = feeChange(applied.structuredContent)
  const text = applied.content
    ?.map((part) => part.text)
    .filter(Boolean)
    .join('\n')

  if (declined) {
    return (
      <Card size="small" role="region" aria-label="Applied change">
        <Alert
          type="info"
          showIcon
          title="Nothing was applied"
          description={
            <div>
              <Typography.Paragraph style={{ marginBottom: 4 }}>{text}</Typography.Paragraph>
              <Typography.Text type="secondary">
                Declining is an answer, and this is the outcome it was supposed to produce. The
                server nevertheless marked this result <code>isError: true</code>, so the exchange
                below shows it as a tool failure — its own contract says a declined confirmation
                should not be one. Both are shown rather than one being tidied away (finding F-005).
              </Typography.Text>
            </div>
          }
        />
      </Card>
    )
  }

  return (
    <Card size="small" role="region" aria-label="Applied change">
      <Space orientation="vertical" size="middle" style={{ display: 'flex' }}>
        {change ? (
          <>
            {change.replayed && (
              <Alert
                type="info"
                showIcon
                title="Nothing happened a second time"
                description="The same operation_id was seen before, so this is the original result returned from the idempotency record. The fee was not adjusted again."
              />
            )}
            <Descriptions size="small" column={1} colon={false}>
              <Descriptions.Item label="Account">
                <code>{change.accountId}</code>
              </Descriptions.Item>
              <Descriptions.Item label="Fee before">
                {basisPoints(change.previousFeeBps)}
              </Descriptions.Item>
              <Descriptions.Item label="Fee after">
                {basisPoints(change.newFeeBps)}
              </Descriptions.Item>
              <Descriptions.Item label="Change">
                <Tag color={change.deltaBps >= 0 ? 'orange' : 'blue'}>
                  {change.deltaBps >= 0 ? '+' : ''}
                  {change.deltaBps} bps
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Billing system reference">
                <code>{change.legacyReferenceId}</code>
              </Descriptions.Item>
              <Descriptions.Item label="Confirmed by">
                <code>{change.confirmedByUserId}</code>
              </Descriptions.Item>
            </Descriptions>
            <Typography.Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
              The fee before is computed as <code>new_fee_bps − delta_bps</code>: the result does
              not carry a previous value, and this arithmetic is exact.
            </Typography.Text>
          </>
        ) : (
          <Typography.Paragraph style={{ marginBottom: 0 }}>{text}</Typography.Paragraph>
        )}

        {/* FR-012b. There is no undo button, and there is not going to be one. */}
        <Alert
          type="info"
          showIcon
          title="There is no undo, on purpose"
          description={
            <Typography.Text>
              An undo would have to be a second, opposite adjustment, leaving the audit log
              describing two events where one happened. To put the seeded data back, discard the
              stack&apos;s stored data instead: <code>{RESET_COMMAND}</code>. Until then the fee
              stays where this left it, and that is the honest state of a system of record.
            </Typography.Text>
          }
        />
      </Space>
    </Card>
  )
}
