import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { scan, inlineCode } from './scanner.mjs'

/**
 * T005. One pass per document; nothing else may read a file. The fence distinction is the grammar's,
 * and it exists because a first draft of this check flagged its own examples.
 */
describe('scanning a document', () => {
  test('numbers lines from 1', () => {
    const lines = scan('first\nsecond\nthird')
    assert.deepEqual(
      lines.map((l) => l.line),
      [1, 2, 3],
    )
  })

  test('marks lines inside a fenced block', () => {
    const lines = scan(['outside', '```text', 'inside', '```', 'outside again'].join('\n'))
    assert.deepEqual(
      lines.map((l) => l.inFence),
      [false, false, true, false, false],
    )
  })

  test('treats an unterminated fence as running to the end, rather than guessing', () => {
    const lines = scan(['before', '```', 'after', 'and after'].join('\n'))
    assert.deepEqual(
      lines.slice(2).map((l) => l.inFence),
      [true, true],
    )
  })

  test('the fence markers themselves are not content', () => {
    const lines = scan(['```js', 'code', '```'].join('\n'))
    assert.equal(lines[0].inFence, false)
    assert.equal(lines[2].inFence, false)
  })

  test('extracts inline code spans', () => {
    assert.deepEqual(inlineCode('see `a/b.ts` and `make up` here'), ['a/b.ts', 'make up'])
  })

  test('ignores an unclosed backtick rather than swallowing the rest of the line', () => {
    assert.deepEqual(inlineCode('a `b.ts` then ` dangling'), ['b.ts'])
  })
})
