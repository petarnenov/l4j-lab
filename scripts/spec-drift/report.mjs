/**
 * What one run prints, and what it exits with.
 *
 * Contract: specs/009-spec-drift-check/contracts/report-format.md. The counts are not decoration: an
 * extractor that silently stops matching is otherwise identical to a clean repository, and this is
 * the only thing that would show it (FR-012).
 */

function plural(n, one, many) {
  return `${n} ${n === 1 ? one : many}`
}

export function formatReport(report) {
  const lines = []
  const total = Object.values(report.checked).reduce((sum, n) => sum + n, 0)
  lines.push(
    `spec-drift: ${plural(total, 'claim', 'claims')} checked across ` +
      `${plural(report.features.length, 'implemented feature', 'implemented features')}`,
  )
  for (const [kind, count] of Object.entries(report.checked)) {
    lines.push(`  ${kind.padEnd(13)} ${String(count).padStart(5)} checked`)
  }
  lines.push(`  not checked: ${report.notChecked.join(', ')}`)

  for (const failure of report.broken) {
    lines.push('')
    lines.push(`BROKEN  ${failure.document}:${failure.line}`)
    lines.push(`        ${failure.kind}   ${failure.subject}`)
    lines.push(`        ${failure.why}`)
  }

  // Printed even on a passing run. This is the section most likely to be deleted as noise, and the
  // one that must not be: an excuse nobody sees becomes permanent (FR-015).
  if (report.exempt.length > 0) {
    lines.push('')
    lines.push(`exempt (${report.exempt.length})`)
    for (const e of report.exempt) {
      lines.push(`  ${e.document}:${e.line}`)
      lines.push(`    reason: ${e.reason}`)
    }
  }
  if (report.baselined.length > 0) {
    lines.push('')
    lines.push(`baselined (${report.baselined.length})`)
    for (const b of report.baselined) {
      lines.push(`  ${b.document} → ${b.subject}`)
      lines.push(`    reason: ${b.reason}`)
      lines.push(`    recorded: ${b.recorded}`)
    }
  }
  for (const s of report.stale) {
    lines.push('')
    lines.push(`STALE   ${s.where}`)
    lines.push(`        ${s.subject}`)
    lines.push(`        ${s.why}`)
  }
  return lines.join('\n')
}

export function exitCodeFor(report) {
  return report.broken.length > 0 || report.stale.length > 0 ? 1 : 0
}

export function printReport(report) {
  console.log(formatReport(report))
  return exitCodeFor(report)
}
