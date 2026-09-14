import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { statusClaims } from './claims.mjs'
import { verifyStatus } from './verify.mjs'

/**
 * T015 (research R-001). The one claim kind that checks a document against another document.
 *
 * It exists because `Status` is what arms every other check, and an arming switch nobody watches is
 * one nobody maintains. At the time of writing, three implemented features in this repository still
 * declare themselves drafts — so this is not a hypothetical failure mode.
 */
function feature(overrides = {}) {
  return {
    id: '00X-example',
    directory: 'specs/00X-example',
    documents: ['specs/00X-example/spec.md'],
    declaredStatus: 'Implemented, 2026-09-14. All 10 tasks complete.',
    implemented: true,
    tasksTotal: 10,
    tasksDone: 10,
    hasTasks: true,
    ...overrides,
  }
}

describe('the status claim', () => {
  test('is one claim per feature, drawn from its own spec', () => {
    const claims = statusClaims(feature())
    assert.equal(claims.length, 1)
    assert.equal(claims[0].kind, 'status')
    assert.equal(claims[0].document, 'specs/00X-example/spec.md')
  })

  test('a known status holds', () => {
    // Each fixture must also be *coherent*: the default has every task complete, so pairing it with
    // `Draft` would be the finished-but-undeclared case two tests below, not a recognised-value case.
    assert.equal(verifyStatus(feature()).verdict, 'holds')
    assert.equal(
      verifyStatus(feature({ declaredStatus: 'Draft', implemented: false, tasksDone: 2 })).verdict,
      'holds',
    )
    assert.equal(
      verifyStatus(feature({ declaredStatus: 'Approved, 2026-09-12.', implemented: false, tasksDone: 3 }))
        .verdict,
      'holds',
    )
  })

  test('an unrecognised status is broken, because nothing downstream knows what to do with it', () => {
    assert.equal(verifyStatus(feature({ declaredStatus: 'Mostly done' })).verdict, 'broken')
  })

  test('declaring implemented without a task list is broken', () => {
    const v = verifyStatus(feature({ hasTasks: false, tasksTotal: 0, tasksDone: 0 }))
    assert.equal(v.verdict, 'broken')
    assert.match(v.why, /tasks\.md/)
  })

  test('a task count stated in the status line must match the file', () => {
    // Feature 005 says "All 46 tasks complete" where its file holds 54. Harmless in substance, and
    // exactly the class of claim this tool checks: a number written about a file, beside the file.
    const v = verifyStatus(
      feature({ declaredStatus: 'Implemented, 2026-09-13. All 46 tasks complete.', tasksTotal: 54, tasksDone: 54 }),
    )
    assert.equal(v.verdict, 'broken')
    assert.match(v.why, /46/)
    assert.match(v.why, /54/)
  })

  test('a finished feature still calling itself a draft is broken, not ignored', () => {
    // The worst failure available to this tool is passing by not looking. A feature that is done but
    // says otherwise would be skipped silently, so the skip itself has to be reported.
    const v = verifyStatus(feature({ declaredStatus: 'Draft', implemented: false, tasksDone: 10 }))
    assert.equal(v.verdict, 'broken')
    assert.match(v.why, /draft/i)
  })

  test('a draft with work genuinely outstanding is fine', () => {
    const v = verifyStatus(
      feature({ declaredStatus: 'Draft', implemented: false, tasksTotal: 10, tasksDone: 2 }),
    )
    assert.equal(v.verdict, 'holds')
  })

  test('one deliberately open task does not make a feature a draft', () => {
    // 001 and 008 each carry one, on purpose, and both are delivered.
    const v = verifyStatus(feature({ tasksTotal: 71, tasksDone: 70, declaredStatus: 'Implemented, 2026-09-14.' }))
    assert.equal(v.verdict, 'holds')
  })
})
