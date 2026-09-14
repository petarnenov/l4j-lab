/**
 * One pass over a document, yielding every line with its number and whether it sits inside a fenced
 * block. Nothing else in this tool reads a file: every extractor consumes this.
 *
 * The fence flag matters because the grammar treats the two differently, and for a reason a first
 * draft of this check demonstrated the hard way. A markdown link inside a fence is literal text — a
 * reader cannot follow it, so it promises nothing. A file tree inside a fence *is* read as a
 * description of real files. Same delimiter, opposite meanings.
 */

export function scan(text) {
  const out = []
  let inFence = false
  for (const [index, line] of text.split('\n').entries()) {
    if (line.trimStart().startsWith('```')) {
      // The marker itself is punctuation, not content, and belongs to neither side.
      out.push({ line: index + 1, text: line, inFence: false })
      inFence = !inFence
      continue
    }
    out.push({ line: index + 1, text: line, inFence })
  }
  return out
}

/**
 * The contents of every closed backtick span on a line. An unclosed backtick yields nothing rather
 * than swallowing the rest of the line — a half-written span is a typo, not a claim.
 */
export function inlineCode(text) {
  return [...text.matchAll(/`([^`\n]+)`/g)].map((m) => m[1])
}
