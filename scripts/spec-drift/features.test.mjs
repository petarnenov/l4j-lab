import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { loadFeatures, isImplemented, ALWAYS_IN_SCOPE } from './features.mjs'

/**
 * T004 (FR-007, research R-001). Driven by this repository's real specs/, because the rule exists
 * because of what is actually in them: two delivered features carry a deliberately open task, and
 * three implemented ones still declare themselves drafts.
 */
describe('which features are held to their code', () => {
  const features = loadFeatures()

  test('finds every feature directory under specs/', () => {
    assert.ok(features.length >= 9, `expected at least 9 features, found ${features.length}`)
    assert.ok(features.some((f) => f.id === '008-mcp-console'))
  })

  test('reads the declared status from each spec', () => {
    for (const feature of features) {
      assert.equal(typeof feature.declaredStatus, 'string', `${feature.id} has no status`)
      assert.ok(feature.declaredStatus.length > 0, `${feature.id} has an empty status`)
    }
  })

  test('counts tasks without treating an unticked box as undelivered', () => {
    // The case that rules out counting checkboxes: 001 and 008 are delivered and each carries one
    // deliberately open task — a trace that needs a person, and a two-minute test that needs someone
    // who has never seen the page.
    const first = features.find((f) => f.id === '001-financial-agent-chain')
    const console = features.find((f) => f.id === '008-mcp-console')
    assert.ok(first.tasksTotal - first.tasksDone === 1, 'expected exactly one open task in 001')
    assert.ok(console.tasksTotal - console.tasksDone === 1, 'expected exactly one open task in 008')
  })

  test('a feature is in scope when it declares itself implemented, not when its boxes are ticked', () => {
    assert.equal(isImplemented('Implemented, 2026-09-12. All 37 tasks complete.'), true)
    assert.equal(isImplemented('Draft'), false)
    assert.equal(isImplemented('Approved, 2026-09-12. Planning complete.'), false)
  })

  test('the repository README belongs to no feature and is always in scope', () => {
    assert.ok(ALWAYS_IN_SCOPE.includes('README.md'))
  })

  test('a feature in scope exposes the documents that make claims about it', () => {
    const implemented = features.filter((f) => f.implemented)
    assert.ok(implemented.length > 0, 'no feature declares itself implemented')
    for (const feature of implemented) {
      assert.ok(feature.documents.length > 0, `${feature.id} has no documents`)
      assert.ok(feature.documents.every((d) => d.endsWith('.md')))
    }
  })
})
