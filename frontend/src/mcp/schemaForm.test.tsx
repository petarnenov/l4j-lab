import { screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { renderWithQuery } from '../test/render'
import { SchemaForm, missingRequired, properties, prunedArguments } from './schemaForm'
import { TOOL_CONTRACTS } from './test/mcpHandlers'
import type { JsonSchema } from './wire'

const search = TOOL_CONTRACTS[0]
const feeAdjustment = TOOL_CONTRACTS[3]

/**
 * T022 (FR-008). Fields are derived from what the server declares, so a tool that changes is
 * reflected without the console changing. The schemas below are the committed ones, which is what
 * makes this a test of derivation rather than of a list written here.
 */
describe('fields derived from the declared input shape', () => {
  it('offers one field per declared property', () => {
    renderWithQuery(<SchemaForm schema={search.inputSchema} values={{}} onChange={vi.fn()} />)

    for (const name of Object.keys(search.inputSchema.properties as object)) {
      expect(screen.getByLabelText(new RegExp(`^${name}`))).toBeInTheDocument()
    }
  })

  it('marks the required ones, and only those', () => {
    renderWithQuery(<SchemaForm schema={search.inputSchema} values={{}} onChange={vi.fn()} />)

    expect(screen.getByLabelText(/^firm_id/)).toBeRequired()
    expect(screen.getByLabelText(/^advisor_id/)).not.toBeRequired()
  })

  it('renders a declared enum as a chooser rather than free text', () => {
    renderWithQuery(<SchemaForm schema={search.inputSchema} values={{}} onChange={vi.fn()} />)

    expect(screen.getByLabelText(/^status/)).toHaveAttribute('role', 'combobox')
  })

  it('renders a date-formatted string as a date field', () => {
    renderWithQuery(<SchemaForm schema={search.inputSchema} values={{}} onChange={vi.fn()} />)

    expect(screen.getByLabelText(/^started_from/)).toHaveAttribute('data-field-kind', 'date')
  })

  it('honours an integer bound the server declared', () => {
    renderWithQuery(<SchemaForm schema={search.inputSchema} values={{}} onChange={vi.fn()} />)

    const pageSize = screen.getByLabelText(/^page_size/)
    expect(pageSize).toHaveAttribute('aria-valuemin', '1')
    expect(pageSize).toHaveAttribute('aria-valuemax', '20')
  })

  it('prefills a declared default', () => {
    renderWithQuery(
      <SchemaForm schema={search.inputSchema} values={{ page_size: 20 }} onChange={vi.fn()} />,
    )

    expect(screen.getByLabelText(/^page_size/)).toHaveValue('20')
  })

  it('shows each description as the server wrote it, as help text', () => {
    renderWithQuery(<SchemaForm schema={search.inputSchema} values={{}} onChange={vi.fn()} />)

    expect(
      screen.getByText('Opaque cursor from a previous result. Do not construct or modify it.'),
    ).toBeInTheDocument()
  })

  it('derives the elicitation schema the same way, because it is the same kind of thing', () => {
    const requested: JsonSchema = {
      type: 'object',
      properties: { confirmed: { type: 'boolean' } },
      required: ['confirmed'],
    }
    renderWithQuery(<SchemaForm schema={requested} values={{}} onChange={vi.fn()} />)

    expect(screen.getByLabelText(/^confirmed/)).toBeInTheDocument()
  })
})

describe('the order the fields appear in', () => {
  it('puts the required ones first, whatever order the server sent', () => {
    // The server does not preserve the declared order (finding F-001): search_billing_runs arrives
    // with firm_id fifth of seven, so its only required field sits below four optional ones. That
    // is the tool SC-001 is measured on, and scrolling past four fields to find the one you must
    // fill is most of a first-timer's two minutes.
    //
    // Ordering is presentation, not derivation: every declared field is still rendered and none is
    // invented, which is all FR-008 asks.
    const scrambled: JsonSchema = {
      type: 'object',
      properties: {
        started_from: { type: 'string' },
        cursor: { type: 'string' },
        firm_id: { type: 'string' },
        page_size: { type: 'integer' },
      },
      required: ['firm_id'],
    }

    expect(properties(scrambled).map((property) => property.name)).toEqual([
      'firm_id',
      'started_from',
      'cursor',
      'page_size',
    ])
  })

  it('keeps the server order within each group, because it has nothing better to go on', () => {
    const schema: JsonSchema = {
      type: 'object',
      properties: {
        b: { type: 'string' },
        a: { type: 'string' },
        d: { type: 'string' },
        c: { type: 'string' },
      },
      required: ['d', 'a'],
    }

    // Not alphabetical, and not the order of `required` either: the declared order, partitioned.
    expect(properties(schema).map((property) => property.name)).toEqual(['a', 'd', 'b', 'c'])
  })

  it('renders the required ones first on screen, not merely in the model', () => {
    renderWithQuery(<SchemaForm schema={search.inputSchema} values={{}} onChange={vi.fn()} />)

    const labels = screen.getAllByText(/^(firm_id|status|advisor_id|cursor|page_size)$/)
    expect(labels[0]).toHaveTextContent('firm_id')
  })
})

describe('a keyword the console does not render natively', () => {
  it('offers a raw JSON field and says so, rather than silently dropping it', () => {
    // A silently missing field looks identical to a tool that does not have one. The fallback
    // exists for a running server that moved ahead of this console; the committed contracts are
    // covered by the tests above, so this is never reached by them.
    const schema: JsonSchema = {
      type: 'object',
      properties: { nested: { oneOf: [{ type: 'string' }, { type: 'integer' }] } },
    }
    renderWithQuery(<SchemaForm schema={schema} values={{}} onChange={vi.fn()} />)

    expect(screen.getByLabelText(/^nested/)).toBeInTheDocument()
    expect(screen.getByText(/does not render natively/i)).toBeInTheDocument()
  })
})

describe('what the console can already tell about the arguments', () => {
  it('names every required field still empty', () => {
    expect(missingRequired(feeAdjustment.inputSchema, {})).toEqual([
      'operation_id',
      'account_id',
      'delta_bps',
      'effective_date',
    ])
  })

  it('is satisfied once they are filled', () => {
    const values = {
      operation_id: 'op-12345678',
      account_id: 'acc-0101',
      delta_bps: 15,
      effective_date: '2026-10-01',
    }
    expect(missingRequired(feeAdjustment.inputSchema, values)).toEqual([])
  })

  it('treats whitespace as empty, because the server will', () => {
    expect(missingRequired(search.inputSchema, { firm_id: '   ' })).toEqual(['firm_id'])
  })

  it('omits an empty optional field rather than sending it as an empty string', () => {
    // Every tool schema is additionalProperties: false with typed properties, and "" is not the
    // same statement as an absent field.
    expect(prunedArguments({ firm_id: 'firm-alpha', advisor_id: '', page_size: 20 })).toEqual({
      firm_id: 'firm-alpha',
      page_size: 20,
    })
  })
})
