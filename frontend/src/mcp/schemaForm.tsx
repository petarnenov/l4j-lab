import { Input, InputNumber, Select, Switch, Typography, theme } from 'antd'
import type { JsonSchema } from './wire'

/**
 * Argument fields built from what the server declares, never from a list written here (FR-008).
 *
 * The subset below is what the five committed tool contracts actually use. A general JSON Schema
 * form library is roughly the size of the rest of this page put together and styles against its own
 * design system; sixty lines of switch is the smaller claim, and the constitution asks a dependency
 * to justify itself against what is already available (research R-003).
 *
 * Anything outside the subset renders as a raw-JSON field with a visible note. It is never dropped:
 * a silently missing field looks identical to a tool that does not have one. The deterministic
 * suite drives this from the committed contracts, so a future tool introducing `oneOf` or a nested
 * object fails a test here the day the contract changes.
 */

type FieldKind = 'string' | 'enum' | 'date' | 'integer' | 'boolean' | 'raw'

interface Property {
  name: string
  kind: FieldKind
  required: boolean
  schema: Record<string, unknown>
}

function kindOf(schema: Record<string, unknown>): FieldKind {
  if (Array.isArray(schema.enum)) return 'enum'
  if (schema.type === 'string') return schema.format === 'date' ? 'date' : 'string'
  if (schema.type === 'integer' || schema.type === 'number') return 'integer'
  if (schema.type === 'boolean') return 'boolean'
  return 'raw'
}

/**
 * Every declared property, required ones first.
 *
 * The ordering is a presentation decision forced by the server: it does not preserve the order the
 * contracts declare (finding F-001), and `search_billing_runs` arrives with its *only* required
 * field fifth of seven. That is the tool SC-001 is measured on, and scrolling past four optional
 * fields to find the one you must fill is most of a first-timer's two minutes.
 *
 * Within each group the server's order is kept, because the console has nothing better to go on —
 * it cannot see the contract, only what was served. Alphabetical would be inventing an order;
 * partitioning by what the server itself marked required is not.
 *
 * This is ordering, not derivation: every declared field is still rendered and none is invented,
 * which is all FR-008 asks.
 */
export function properties(schema: JsonSchema): Property[] {
  const declared = (schema.properties ?? {}) as Record<string, Record<string, unknown>>
  const required = (schema.required ?? []) as string[]
  const all = Object.entries(declared).map(([name, property]) => ({
    name,
    kind: kindOf(property),
    required: required.includes(name),
    schema: property,
  }))
  return [...all.filter((property) => property.required), ...all.filter((p) => !p.required)]
}

/** Seeds the form from the defaults the server declared, so a default is offered rather than typed. */
export function initialValues(schema: JsonSchema): Record<string, unknown> {
  const values: Record<string, unknown> = {}
  for (const property of properties(schema)) {
    if (property.schema.default !== undefined) values[property.name] = property.schema.default
  }
  return values
}

function isEmpty(value: unknown): boolean {
  if (value === undefined || value === null) return true
  if (typeof value === 'string') return value.trim() === ''
  return false
}

/** FR-009: what the console can already tell is missing, before it sends anything. */
export function missingRequired(schema: JsonSchema, values: Record<string, unknown>): string[] {
  return ((schema.required ?? []) as string[]).filter((name) => isEmpty(values[name]))
}

/**
 * Empty optional fields are omitted entirely rather than sent as `""` or `null`. Every tool schema
 * is `additionalProperties: false` with typed properties, and an empty string is not the same
 * statement as an absent field.
 */
export function prunedArguments(values: Record<string, unknown>): Record<string, unknown> {
  return Object.fromEntries(Object.entries(values).filter(([, value]) => !isEmpty(value)))
}

export interface SchemaFormProps {
  schema: JsonSchema
  values: Record<string, unknown>
  onChange: (values: Record<string, unknown>) => void
  idPrefix?: string
  disabled?: boolean
}

export function SchemaForm({
  schema,
  values,
  onChange,
  idPrefix = 'arg',
  disabled = false,
}: SchemaFormProps) {
  const { token } = theme.useToken()
  const set = (name: string, value: unknown) => onChange({ ...values, [name]: value })

  return (
    <div style={{ display: 'grid', gap: token.margin }}>
      {properties(schema).map((property) => {
        const id = `${idPrefix}-${property.name}`
        const description = property.schema.description as string | undefined
        const value = values[property.name]

        return (
          <div key={property.name}>
            <label htmlFor={id} style={{ display: 'block', marginBottom: token.marginXXS }}>
              <code>{property.name}</code>
              {property.required && (
                <span style={{ color: token.colorError }} aria-hidden>
                  {' *'}
                </span>
              )}
            </label>

            {property.kind === 'enum' && (
              <Select
                id={id}
                disabled={disabled}
                allowClear
                style={{ width: '100%' }}
                value={(value as string) ?? undefined}
                onChange={(next) => set(property.name, next)}
                options={(property.schema.enum as string[]).map((option) => ({
                  value: option,
                  label: option,
                }))}
              />
            )}

            {property.kind === 'date' && (
              <Input
                id={id}
                type="date"
                data-field-kind="date"
                required={property.required}
                disabled={disabled}
                value={(value as string) ?? ''}
                onChange={(event) => set(property.name, event.target.value)}
              />
            )}

            {property.kind === 'string' && (
              <Input
                id={id}
                data-field-kind="string"
                required={property.required}
                disabled={disabled}
                maxLength={property.schema.maxLength as number | undefined}
                value={(value as string) ?? ''}
                onChange={(event) => set(property.name, event.target.value)}
              />
            )}

            {property.kind === 'integer' && (
              <InputNumber
                id={id}
                data-field-kind="integer"
                required={property.required}
                disabled={disabled}
                style={{ width: '100%' }}
                min={property.schema.minimum as number | undefined}
                max={property.schema.maximum as number | undefined}
                value={(value as number) ?? null}
                onChange={(next) => set(property.name, next ?? undefined)}
              />
            )}

            {property.kind === 'boolean' && (
              <Switch
                id={id}
                aria-label={property.name}
                disabled={disabled}
                checked={Boolean(value)}
                onChange={(next) => set(property.name, next)}
              />
            )}

            {property.kind === 'raw' && (
              <>
                <Input.TextArea
                  id={id}
                  data-field-kind="raw"
                  rows={3}
                  disabled={disabled}
                  value={(value as string) ?? ''}
                  onChange={(event) => set(property.name, event.target.value)}
                />
                <Typography.Text type="warning" style={{ fontSize: token.fontSizeSM }}>
                  This tool declares a shape the console does not render natively. Type the value as
                  JSON; nothing is dropped.
                </Typography.Text>
              </>
            )}

            {description && (
              <Typography.Text
                type="secondary"
                style={{ display: 'block', fontSize: token.fontSizeSM }}
              >
                {description}
              </Typography.Text>
            )}
          </div>
        )
      })}
    </div>
  )
}
