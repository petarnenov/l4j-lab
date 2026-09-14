import { Card, Descriptions, Select, Space, Tag, Typography, theme } from 'antd'
import { PRINCIPAL_NAMES, type PrincipalClaims, type PrincipalName } from '../principals'

/**
 * Who the console acts as (FR-004, FR-005, FR-006).
 *
 * The four facts below are read from the claims of the token the issuer minted, not from a table
 * written here. Only the six names are written down, because the issuer offers no endpoint that
 * lists them — and a list of strings is not a table of claims that can quietly go stale.
 *
 * The token itself is never rendered. Principle II is unconditional about that, and the fact that
 * this issuer's key is committed and worthless is an argument for why showing it would be harmless,
 * not for why it would be a good thing to demonstrate.
 */
export interface PrincipalPickerProps {
  value: PrincipalName
  onChange: (principal: PrincipalName) => void
  claims: PrincipalClaims | null
}

export function PrincipalPicker({ value, onChange, claims }: PrincipalPickerProps) {
  const { token } = theme.useToken()

  return (
    <section aria-labelledby="principal-heading">
      <Typography.Title id="principal-heading" level={2} style={{ fontSize: token.fontSizeLG }}>
        Principal
      </Typography.Title>

      <Space orientation="vertical" size="middle" style={{ display: 'flex' }}>
        <Select
          aria-label="Acting as"
          // Six options never need windowing, and virtualising them means the list a person sees
          // depends on the size of a box jsdom cannot measure. Off, so the whole set is always real.
          virtual={false}
          style={{ minWidth: 260 }}
          value={value}
          // antd hands the chosen option as a second argument; this component's contract is one.
          onChange={(next) => onChange(next)}
          options={PRINCIPAL_NAMES.map((name) => ({ value: name, label: name }))}
        />

        <Card size="small" role="region" aria-label="Principal identity">
          {claims ? (
            <>
              <Descriptions size="small" column={1} colon={false}>
                <Descriptions.Item label="User">
                  <code>{claims.userId}</code>
                </Descriptions.Item>
                <Descriptions.Item label="Firm">
                  <code>{claims.firmId}</code>
                </Descriptions.Item>
                <Descriptions.Item label="Role">
                  <Tag>{claims.role}</Tag>
                </Descriptions.Item>
                <Descriptions.Item label="May act for">
                  <Space wrap size={4}>
                    {claims.advisorIds.map((advisorId) => (
                      <Tag key={advisorId}>{advisorId}</Tag>
                    ))}
                  </Space>
                </Descriptions.Item>
              </Descriptions>
              <Typography.Text type="secondary" style={{ fontSize: token.fontSizeSM }}>
                Read from the token minted by the console — nothing to paste. The token is not
                shown: every request carries it, and the exchange log renders that header as a
                redaction.
              </Typography.Text>
            </>
          ) : (
            <Typography.Text type="secondary">
              Minting a token for <code>{value}</code>. Its user, firm, role and permitted advisors
              appear once it arrives; the console does not guess them in the meantime.
            </Typography.Text>
          )}
        </Card>
      </Space>
    </section>
  )
}
