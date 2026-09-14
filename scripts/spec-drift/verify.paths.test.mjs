import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { verifyPath, completenessClaims } from './verify.mjs'
import { readdirSync } from 'node:fs'
import { scan } from './scanner.mjs'

/**
 * T018, T019, T020 (FR-001, FR-002, FR-007).
 */
describe('verifying a path', () => {
  test('a path that exists holds', () => {
    assert.equal(verifyPath({ subject: 'scripts/spec-drift/check.mjs' }).verdict, 'holds')
  })

  test('a directory that exists holds', () => {
    assert.equal(verifyPath({ subject: 'scripts/spec-drift/' }).verdict, 'holds')
  })

  test('a path that does not exist is broken, and says so plainly', () => {
    const v = verifyPath({ subject: 'scripts/spec-drift/not-here.mjs' })
    assert.equal(v.verdict, 'broken')
    assert.match(v.why, /does not exist/)
  })
})

describe('completeness is claimed, never inferred (research R-009)', () => {
  const marked = [
    '```text',
    'scripts/spec-drift/          # [complete] every file here belongs to this feature',
    '├── check.mjs',
    '```',
  ].join('\n')

  test('a directory marked [complete] demands its files be named', () => {
    const claims = completenessClaims({
      document: 'specs/00X/plan.md',
      lines: scan(marked),
      feature: { id: '00X', documents: [] },
      named: new Set(['scripts/spec-drift/check.mjs']),
    })
    const subjects = claims.map((c) => c.subject)
    // report.mjs exists on disk and is not named by the document, so it is a missing mention.
    assert.ok(subjects.some((s) => s.endsWith('report.mjs')), subjects.join(', '))
  })

  test('an unmarked directory demands nothing', () => {
    // The rule that replaced inference. Applied to `frontend/`, which feature 008's plan names as its
    // tree root, inference demanded that plan account for node_modules and package-lock.json — six
    // false failures in the one case the rule was designed from.
    const unmarked = ['```text', 'scripts/spec-drift/', '├── check.mjs', '```'].join('\n')
    const claims = completenessClaims({
      document: 'specs/00X/plan.md',
      lines: scan(unmarked),
      feature: { id: '00X', documents: [] },
      named: new Set(['scripts/spec-drift/check.mjs']),
    })
    assert.deepEqual(claims, [])
  })

  test('a marked directory whose files are every one named produces no claim', () => {
    const everything = new Set(
      readdirSync('scripts/spec-drift').map((f) => `scripts/spec-drift/${f}`),
    )
    const claims = completenessClaims({
      document: 'specs/00X/plan.md',
      lines: scan(marked),
      feature: { id: '00X', documents: [] },
      named: everything,
    })
    assert.deepEqual(claims, [])
  })

  test('names the file that is missing, not merely the directory', () => {
    const claims = completenessClaims({
      document: 'specs/00X/plan.md',
      lines: scan(marked),
      feature: { id: '00X', documents: [] },
      named: new Set(['scripts/spec-drift/check.mjs']),
    })
    assert.ok(claims.every((c) => c.subject.includes('.')), 'every claim should name a file')
    assert.ok(claims.every((c) => c.kind === 'path'))
  })
})
