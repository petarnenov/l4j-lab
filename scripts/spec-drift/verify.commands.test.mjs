import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { verifyCommand } from './verify.mjs'

/** T026, T027 (FR-003, FR-010). Read from the manifests as text; nothing is ever executed. */
describe('verifying a command', () => {
  test('a make target that exists holds', () => {
    assert.equal(verifyCommand({ subject: 'make check-specs' }).verdict, 'holds')
  })

  test('a make target that does not exist is broken', () => {
    const v = verifyCommand({ subject: 'make no-such-target' })
    assert.equal(v.verdict, 'broken')
    assert.match(v.why, /Makefile/)
  })

  test('an npm script that exists holds', () => {
    assert.equal(verifyCommand({ subject: 'npm run test:mcp' }).verdict, 'holds')
  })

  test('an npm script that does not exist is broken', () => {
    assert.equal(verifyCommand({ subject: 'npm run nothing-here' }).verdict, 'broken')
  })

  test('a gradle task that exists holds', () => {
    assert.equal(verifyCommand({ subject: './gradlew specDrift' }).verdict, 'holds')
    assert.equal(verifyCommand({ subject: './gradlew :frontend:check' }).verdict, 'holds')
  })

  test('a gradle task that does not exist is broken', () => {
    assert.equal(verifyCommand({ subject: './gradlew nothingAtAll' }).verdict, 'broken')
  })

  test('a lifecycle task every Gradle project has is recognised without reading a build file', () => {
    for (const task of ['build', 'check', 'test', 'clean']) {
      assert.equal(verifyCommand({ subject: `./gradlew ${task}` }).verdict, 'holds', task)
    }
  })

  test('a dynamically registered task is missed rather than falsely reported', () => {
    // The grammar states this limit outright. Reading build files as text cannot see a task whose
    // name is computed, and a check that overclaims its reach is worse than one with a stated edge.
    const v = verifyCommand({ subject: './gradlew someTaskBuiltAtRuntime' })
    assert.equal(v.verdict, 'broken')
    assert.match(v.why, /registered dynamically|as text/)
  })
})
