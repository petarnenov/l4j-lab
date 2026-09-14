/**
 * Drift that existed before this check did (research R-005).
 *
 * Matched by document and subject rather than by line: a line number moves whenever a document is
 * reformatted, and an entry that quietly moved onto a different claim would excuse the wrong thing.
 *
 * An entry that matches no broken claim fails the run. So the list can shrink by itself, and cannot
 * grow without someone writing a reason down.
 */
import { readFileSync } from 'node:fs'

export function loadBaseline(path) {
  try {
    return JSON.parse(readFileSync(path, 'utf8'))
  } catch (error) {
    if (error.code === 'ENOENT') return []
    throw new Error(`${path} is not readable JSON: ${error.message}`)
  }
}

export function matchBaseline(entries, claim) {
  return (
    entries.find((e) => e.document === claim.document && e.subject === claim.subject) ?? null
  )
}

export function validateBaseline(entries) {
  const problems = []
  for (const [index, entry] of entries.entries()) {
    const where = `baseline entry ${index + 1}`
    if (!entry.document) problems.push(`${where}: no document`)
    if (!entry.subject) problems.push(`${where}: no subject`)
    if (!entry.reason) problems.push(`${where}: no reason — an excuse without one is not an excuse`)
    if (!entry.recorded) problems.push(`${where}: no recorded date, so an old entry cannot read as old`)
  }
  return problems
}
