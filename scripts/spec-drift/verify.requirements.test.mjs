import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { verifyRequirement } from './verify.mjs'

/** T032 (FR-004, US3). */
const tasks = [
  '- [X] T001 Do a thing (FR-012).',
  '- [ ] T002 Do another, deliberately left open — see SC-009 below.',
].join('\n')

describe('verifying a requirement', () => {
  test('an identifier some task cites holds', () => {
    assert.equal(verifyRequirement({ subject: 'FR-012' }, { tasksText: tasks }).verdict, 'holds')
  })

  test('an identifier no task cites is broken', () => {
    const v = verifyRequirement({ subject: 'FR-099' }, { tasksText: tasks })
    assert.equal(v.verdict, 'broken')
    assert.match(v.why, /no task/)
  })

  test('a requirement cited only by an unfinished task still counts as covered', () => {
    // Coverage is about whether anyone planned for it, not whether the work is done. Feature 008's
    // SC-001 is the live example: cited by one task, left open because it needs a person who has
    // never seen the page.
    assert.equal(verifyRequirement({ subject: 'SC-009' }, { tasksText: tasks }).verdict, 'holds')
  })

  test('a feature with no task list at all is reported once, not per requirement', () => {
    const v = verifyRequirement({ subject: 'FR-001' }, { tasksText: null })
    assert.equal(v.verdict, 'holds')
  })
})
