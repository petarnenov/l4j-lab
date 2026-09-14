import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { verifyReference } from './verify.mjs'

/** T035 (FR-005, FR-009). */
describe('verifying a reference', () => {
  test('a link that resolves beside its document holds', () => {
    assert.equal(
      verifyReference({ document: 'specs/009-spec-drift-check/plan.md', subject: './research.md' })
        .verdict,
      'holds',
    )
  })

  test('a link into a subdirectory holds', () => {
    assert.equal(
      verifyReference({
        document: 'specs/009-spec-drift-check/plan.md',
        subject: 'contracts/claim-grammar.md',
      }).verdict,
      'holds',
    )
  })

  test('a link to a moved file is broken, naming both ends', () => {
    const v = verifyReference({
      document: 'specs/009-spec-drift-check/plan.md',
      subject: './moved-away.md',
    })
    assert.equal(v.verdict, 'broken')
    assert.match(v.why, /moved-away\.md/)
  })

  test('resolution is relative to the document, not to the working directory', () => {
    // A link is written from where it sits. Resolving from anywhere else would make the verdict
    // depend on where the check happened to be started.
    assert.equal(
      verifyReference({ document: 'README.md', subject: './specs/009-spec-drift-check/spec.md' })
        .verdict,
      'holds',
    )
  })
})
