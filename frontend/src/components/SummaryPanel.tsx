import { Alert, Card, Typography } from 'antd'

/**
 * The model's plain-language summary.
 *
 * The summary sits in a region capped at 24rem so a model that returns far more than expected cannot fill
 * the screen (FR-017). The fictional-data notice is an alert rather than small grey text: it was the easiest
 * thing in the old design to overlook and the most important one not to (FR-023, R-009).
 */
export function SummaryPanel({ summary }: { summary: string | null | undefined }) {
  return (
    <Card
      title={
        <Typography.Title level={2} style={{ margin: 0, fontSize: 16 }}>
          Summary
        </Typography.Title>
      }
    >
      {summary ? (
        <div style={{ maxHeight: '24rem', overflowY: 'auto', whiteSpace: 'pre-wrap', lineHeight: 1.6 }}>
          {summary}
        </div>
      ) : (
        <Typography.Text type="secondary">The summary appears once the fourth node has run.</Typography.Text>
      )}

      <Alert
        style={{ marginTop: 16 }}
        type="info"
        showIcon
        title="These companies and figures are fictional and committed to this repository for teaching. This is not investment advice. Where the summary and the indicator table disagree, the table is correct."
      />
    </Card>
  )
}
