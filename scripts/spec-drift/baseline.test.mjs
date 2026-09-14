import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { matchBaseline, validateBaseline } from './baseline.mjs'

/**
 * T007 (FR-015, research R-005). The list can shrink by itself and cannot grow quietly.
 */
const entries = [
  {
    document: 'specs/003-monorepo-integration/plan.md',
    subject: 'gradle/old.properties',
    reason: 'predates this check; 003 is a historical record and may not be edited',
    recorded: '2026-09-14',
  },
]

describe('the baseline', () => {
  test('matches by document and subject, never by line', () => {
    // A line number moves when a document is reformatted, and an entry that moved onto a different
    // claim would excuse the wrong thing silently.
    const claim = { document: entries[0].document, subject: entries[0].subject, line: 999 }
    assert.ok(matchBaseline(entries, claim))
  })

  test('does not match a different subject in the same document', () => {
    assert.equal(matchBaseline(entries, { document: entries[0].document, subject: 'other' }), null)
  })

  test('requires a reason on every entry', () => {
    const bad = [{ document: 'a.md', subject: 'b', recorded: '2026-09-14' }]
    assert.ok(validateBaseline(bad).some((p) => /reason/.test(p)))
  })

  test('requires a date on every entry, so an old entry reads as old', () => {
    const bad = [{ document: 'a.md', subject: 'b', reason: 'because' }]
    assert.ok(validateBaseline(bad).some((p) => /recorded/.test(p)))
  })

  test('accepts a well-formed entry', () => {
    assert.deepEqual(validateBaseline(entries), [])
  })
})
