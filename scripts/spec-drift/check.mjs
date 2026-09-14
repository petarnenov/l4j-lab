/**
 * spec-drift: fails when a specification document says something about the code that is no longer
 * true (feature 009).
 *
 *   node scripts/spec-drift/check.mjs
 *   make check-specs
 *
 * It reads documents and the filesystem. It starts nothing, opens no connection, and writes no file.
 * What it checks and — with equal prominence — what it does not are in
 * specs/009-spec-drift-check/contracts/claim-grammar.md, and printed on every run.
 */
import { readFileSync } from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { loadBaseline, matchBaseline, validateBaseline } from './baseline.mjs'
import { NOT_CHECKED, pathClaims, statusClaims } from './claims.mjs'
import { exemptionOn } from './exemptions.mjs'
import { ROOT, loadFeatures } from './features.mjs'
import { printReport } from './report.mjs'
import { scan } from './scanner.mjs'
import { completenessClaims, verifyPath, verifyStatus } from './verify.mjs'

const BASELINE_PATH = path.join(ROOT, 'scripts/spec-drift/baseline.json')

/**
 * Claim kinds registered by the four user stories. Each takes a document and yields claims; the
 * `status` kind is different and runs per feature rather than per document, because it is about the
 * feature's own declaration rather than about anything in the text.
 */
const DOCUMENT_KINDS = [{ name: 'path', extract: pathClaims, verify: verifyPath }]

function read(relative) {
  return readFileSync(path.join(ROOT, relative), 'utf8')
}

function claimsFor(feature) {
  const scanned = feature.documents.map((document) => ({ document, lines: scan(read(document)) }))
  const claims = []
  for (const { document, lines } of scanned) {
    for (const kind of DOCUMENT_KINDS) {
      claims.push(...kind.extract({ document, lines, feature }))
    }
  }

  // Completeness needs every path the feature mentions anywhere, not just the one document: a file
  // named in a quickstart is accounted for even when a plan's tree omits it.
  const named = new Set(claims.filter((c) => c.kind === 'path').map((c) => c.subject))
  const missing = []
  for (const { document, lines } of scanned) {
    missing.push(...completenessClaims({ document, lines, feature, named }))
  }
  return { claims, missing }
}

export function run() {
  const features = loadFeatures()
  const baseline = loadBaseline(BASELINE_PATH)
  const baselineProblems = validateBaseline(baseline)
  if (baselineProblems.length > 0) {
    throw new Error(`the baseline is malformed:\n  ${baselineProblems.join('\n  ')}`)
  }

  const checked = Object.fromEntries([...DOCUMENT_KINDS.map((k) => k.name), 'status'].map((k) => [k, 0]))
  const broken = []
  const exempt = []
  const baselined = []
  const stale = []
  const usedBaseline = new Set()

  // The status kind runs for *every* feature, implemented or not. It is what decides whether the
  // others run at all, and a feature that is finished while declaring itself a draft would otherwise
  // be skipped in silence — this tool passing by not looking.
  for (const feature of features) {
    for (const claim of statusClaims(feature)) {
      checked.status += 1
      record(claim, verifyStatus(feature))
    }
  }

  // Everything else applies only once a feature declares itself implemented. Before that its plan is
  // supposed to describe files that do not exist yet — that is what writing one first means.
  for (const feature of features.filter((f) => f.implemented)) {
    const { claims, missing } = claimsFor(feature)
    for (const claim of claims) {
      checked[claim.kind] += 1
      record(claim, verifyClaim(claim))
    }
    // Already carrying their own `why`: these are not claims that failed verification but files the
    // documents never accounted for.
    for (const claim of missing) {
      checked[claim.kind] += 1
      record(claim, { verdict: 'broken', why: claim.why })
    }
  }

  function record(claim, result) {
    if (result.verdict === 'holds') return

    const line = claim.lineText ?? ''
    const exemption = exemptionOn(line)
    if (exemption?.error) {
      stale.push({
        where: `${claim.document}:${claim.line}`,
        subject: claim.subject,
        why: 'a drift-ok marker with no reason is not an exemption',
      })
      return
    }
    if (exemption) {
      exempt.push({ document: claim.document, line: claim.line, reason: exemption.reason })
      return
    }

    const entry = matchBaseline(baseline, claim)
    if (entry) {
      usedBaseline.add(entry)
      baselined.push(entry)
      return
    }

    broken.push({ ...claim, why: result.why })
  }

  // An entry that excuses nothing has outlived its cause. This is what lets the baseline shrink by
  // itself, and stops it becoming permanent by being forgotten.
  for (const entry of baseline) {
    if (!usedBaseline.has(entry)) {
      stale.push({
        where: 'scripts/spec-drift/baseline.json',
        subject: `${entry.document} → ${entry.subject}`,
        why: 'this claim now holds; remove the baseline entry',
      })
    }
  }

  return {
    features: features.filter((f) => f.implemented),
    checked,
    broken,
    exempt,
    baselined,
    stale,
    notChecked: NOT_CHECKED,
  }
}

function verifyClaim(claim) {
  const kind = DOCUMENT_KINDS.find((k) => k.name === claim.kind)
  if (!kind) throw new Error(`no verifier registered for claim kind "${claim.kind}"`)
  return kind.verify(claim)
}

if (import.meta.url === `file://${process.argv[1]}`) {
  try {
    process.exit(printReport(run()))
  } catch (error) {
    // Exit 2, never 1: a check that could not run must not be mistaken for one that found nothing.
    console.error(`spec-drift: could not run — ${error.message}`)
    process.exit(2)
  }
}
