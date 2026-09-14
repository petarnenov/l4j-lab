import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { exemptionOn } from './exemptions.mjs'

/**
 * T006 (FR-014). An exemption covers one claim, never a file: the rest of the document stays worth
 * checking.
 */
describe('inline exemptions', () => {
  test('reads the reason from a marker on the line', () => {
    const e = exemptionOn('see `nope.ts` <!-- drift-ok: illustrates the failure -->')
    assert.equal(e.reason, 'illustrates the failure')
  })

  test('a marker with no reason is an error, not an exemption', () => {
    assert.equal(exemptionOn('see `nope.ts` <!-- drift-ok -->').error, true)
    assert.equal(exemptionOn('see `nope.ts` <!-- drift-ok: -->').error, true)
  })

  test('a line with no marker has no exemption', () => {
    assert.equal(exemptionOn('an ordinary line with `a/b.ts`'), null)
  })

  test('the marker is invisible in rendered markdown', () => {
    // An HTML comment, so a reader of the rendered page never sees the bookkeeping.
    const e = exemptionOn('x <!-- drift-ok: because -->')
    assert.equal(e.reason, 'because')
  })
})
