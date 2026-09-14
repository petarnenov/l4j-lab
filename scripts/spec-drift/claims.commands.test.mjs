import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { commandClaims } from './claims.mjs'
import { scan } from './scanner.mjs'

/** T025 (FR-003, research R-004). Only what this project promises is a claim. */
function claims(text) {
  return commandClaims({ document: 'd.md', lines: scan(text), feature: { id: '00X' } })
}

describe('extracting commands', () => {
  test('takes a make target', () => {
    assert.deepEqual(claims('run `make mcp-up` first').map((c) => c.subject), ['make mcp-up'])
  })

  test('takes an npm script', () => {
    assert.deepEqual(claims('`npm run test:mcp`').map((c) => c.subject), ['npm run test:mcp'])
  })

  test('takes a gradle task', () => {
    assert.deepEqual(claims('`./gradlew :frontend:check`').map((c) => c.subject), [
      './gradlew :frontend:check',
    ])
  })

  test('takes commands from inside a fenced block, which is where quickstarts put them', () => {
    const fenced = ['```bash', 'make mcp-up', 'npm run build', '```'].join('\n')
    assert.deepEqual(claims(fenced).map((c) => c.subject), ['make mcp-up', 'npm run build'])
  })

  test('ignores everything this project does not define', () => {
    // These describe the reader's machine, not a promise anyone here can keep.
    for (const other of ['docker compose up', 'curl -s localhost', 'git status', 'jq .token', 'node --test x']) {
      assert.deepEqual(claims(`\`${other}\``).map((c) => c.subject), [], other)
    }
  })

  test('ignores a make invocation carrying a variable, which names no target', () => {
    assert.deepEqual(claims('`make help`').map((c) => c.subject), ['make help'])
    assert.deepEqual(claims('`MCP_HTTP_PORT=9001 make mcp-up`').map((c) => c.subject), [])
  })
})
