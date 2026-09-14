import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { requirementClaims } from './claims.mjs'
import { scan } from './scanner.mjs'

/** T031 (FR-004). Identifiers are claims only where they are declared. */
function claims(text, document = 'specs/00X/spec.md') {
  return requirementClaims({ document, lines: scan(text), feature: { id: '00X' } })
}

describe('extracting requirement identifiers', () => {
  test('takes a declared functional requirement', () => {
    assert.deepEqual(claims('- **FR-012**: The system MUST do a thing.').map((c) => c.subject), ['FR-012'])
  })

  test('takes a declared success criterion', () => {
    assert.deepEqual(claims('- **SC-003**: Something measurable.').map((c) => c.subject), ['SC-003'])
  })

  test('takes a lettered identifier, which the template allows', () => {
    assert.deepEqual(claims('- **FR-011a**: A refinement.').map((c) => c.subject), ['FR-011a'])
  })

  test('a mention is not a declaration', () => {
    // Other documents refer to requirements constantly; each reference is not a separate promise.
    assert.deepEqual(claims('this satisfies FR-012 and `FR-013`').map((c) => c.subject), [])
  })

  test('only spec.md declares requirements', () => {
    assert.deepEqual(claims('- **FR-012**: x', 'specs/00X/plan.md').map((c) => c.subject), [])
  })
})
