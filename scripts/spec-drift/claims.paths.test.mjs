import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { pathClaims } from './claims.mjs'
import { scan } from './scanner.mjs'

/**
 * T017 (FR-001, FR-008). Path extraction per contracts/claim-grammar.md.
 */
function claims(text, document = 'specs/00X/plan.md') {
  return pathClaims({ document, lines: scan(text), feature: { id: '00X' } })
}

describe('extracting paths', () => {
  test('takes a path from inline code', () => {
    const found = claims('see `frontend/src/mcp/transport.ts` for the envelope')
    assert.deepEqual(
      found.map((c) => c.subject),
      ['frontend/src/mcp/transport.ts'],
    )
    assert.equal(found[0].line, 1)
    assert.equal(found[0].kind, 'path')
  })

  test('takes a directory from inline code', () => {
    assert.deepEqual(
      claims('lives in `scripts/spec-drift/`').map((c) => c.subject),
      ['scripts/spec-drift/'],
    )
  })

  test('resolves a filename in a fenced tree against the tree root', () => {
    const tree = ['```text', 'scripts/spec-drift/', '├── check.mjs', '└── report.mjs', '```'].join('\n')
    const subjects = claims(tree).map((c) => c.subject)
    assert.ok(subjects.includes('scripts/spec-drift/check.mjs'), subjects.join(', '))
    assert.ok(subjects.includes('scripts/spec-drift/report.mjs'), subjects.join(', '))
  })

  test('ignores a bare word', () => {
    assert.deepEqual(claims('the `envelope` is built per request').map((c) => c.subject), [])
  })

  test('ignores a path inside a URL', () => {
    assert.deepEqual(claims('see https://example.com/a/b.ts for context').map((c) => c.subject), [])
  })

  test('ignores a path with a placeholder segment', () => {
    // Illustrations, not claims. `specs/<feature>/spec.md` names no file.
    for (const placeholder of ['specs/<feature>/spec.md', 'specs/{id}/spec.md', 'specs/…/spec.md']) {
      assert.deepEqual(claims(`see \`${placeholder}\``).map((c) => c.subject), [], placeholder)
    }
  })

  test('ignores prose that merely mentions a filename without marking it as code', () => {
    // FR-006. A sentence is never a claim, however file-shaped the words in it look.
    assert.deepEqual(claims('the report format lives in report-format.md somewhere').map((c) => c.subject), [])
  })

  test('carries the line text, so an exemption on that line can be found', () => {
    const found = claims('see `a/b.ts` <!-- drift-ok: illustration -->')
    assert.match(found[0].lineText, /drift-ok/)
  })
})
