import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { formatReport, exitCodeFor } from './report.mjs'
import { NOT_CHECKED } from './claims.mjs'

/**
 * T008. The whole of contracts/report-format.md. The counts and the boundary line are the parts most
 * likely to be trimmed as noise, and the parts that must not be.
 */
const clean = {
  features: [{ id: 'a' }, { id: 'b' }],
  checked: { path: 912, command: 118 },
  broken: [],
  exempt: [],
  baselined: [],
  stale: [],
  notChecked: NOT_CHECKED,
}

describe('the report', () => {
  test('prints a count per kind, even when everything passes', () => {
    const out = formatReport(clean)
    assert.match(out, /path\s+912 checked/)
    assert.match(out, /command\s+118 checked/)
  })

  test('prints the total and the number of features it held to their code', () => {
    assert.match(formatReport(clean), /1030 claims checked across 2 implemented features/)
  })

  test('always names what it does not check', () => {
    const out = formatReport(clean)
    for (const kind of NOT_CHECKED) assert.ok(out.includes(kind), `missing: ${kind}`)
  })

  test('a run that checked nothing does not read like a run that found nothing', () => {
    const empty = formatReport({ ...clean, checked: {}, features: [] })
    assert.match(empty, /0 claims checked across 0 implemented features/)
  })

  test('a broken claim carries its document, line, kind and subject', () => {
    const out = formatReport({
      ...clean,
      broken: [
        {
          document: 'specs/008-mcp-console/plan.md',
          line: 127,
          kind: 'path',
          subject: 'frontend/src/mcp/gone.ts',
          why: 'the document names it; it does not exist',
        },
      ],
    })
    assert.match(out, /BROKEN {2}specs\/008-mcp-console\/plan\.md:127/)
    assert.match(out, /path {3}frontend\/src\/mcp\/gone\.ts/)
    assert.match(out, /the document names it; it does not exist/)
  })

  test('prints exemptions and baseline entries on a passing run', () => {
    const out = formatReport({
      ...clean,
      exempt: [{ document: 'a.md', line: 7, reason: 'illustrates the failure' }],
      baselined: [
        { document: 'b.md', subject: 'x', reason: 'predates the check', recorded: '2026-09-14' },
      ],
    })
    assert.match(out, /exempt \(1\)/)
    assert.match(out, /illustrates the failure/)
    assert.match(out, /baselined \(1\)/)
    assert.match(out, /predates the check/)
  })

  test('exits 0 when everything holds', () => {
    assert.equal(exitCodeFor(clean), 0)
  })

  test('exits 1 on a broken claim', () => {
    assert.equal(exitCodeFor({ ...clean, broken: [{}] }), 1)
  })

  test('exits 1 on an excuse that has outlived its cause', () => {
    assert.equal(exitCodeFor({ ...clean, stale: [{}] }), 1)
  })
})
