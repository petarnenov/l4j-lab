import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { referenceClaims } from './claims.mjs'
import { scan } from './scanner.mjs'

/** T034 (FR-005). A link is a promise only where a reader can follow it. */
function claims(text) {
  return referenceClaims({ document: 'specs/00X/plan.md', lines: scan(text), feature: { id: '00X' } })
}

describe('extracting references', () => {
  test('takes a relative link', () => {
    assert.deepEqual(claims('see [research](./research.md) for why').map((c) => c.subject), ['./research.md'])
  })

  test('takes a link into another directory', () => {
    assert.deepEqual(claims('[grammar](contracts/claim-grammar.md)').map((c) => c.subject), [
      'contracts/claim-grammar.md',
    ])
  })

  test('ignores an external link, because following one needs a network', () => {
    for (const url of ['https://example.com/a.md', 'http://x.test/', 'mailto:a@b.test']) {
      assert.deepEqual(claims(`[x](${url})`).map((c) => c.subject), [], url)
    }
  })

  test('ignores an anchor within the same document', () => {
    assert.deepEqual(claims('[top](#overview)').map((c) => c.subject), [])
  })

  test('ignores a link inside a fenced block', () => {
    // Rendered markdown makes that literal text, so it promises nothing. This file's own grammar
    // contains such lines as examples, and a first draft of this check flagged them.
    const fenced = ['```text', '[example](./nowhere.md)', '```'].join('\n')
    assert.deepEqual(claims(fenced).map((c) => c.subject), [])
  })
})
