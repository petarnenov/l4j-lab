import { existsSync, readdirSync, statSync } from 'node:fs'
import path from 'node:path'
import { completeDirectories } from './claims.mjs'
import { ROOT } from './features.mjs'

/**
 * One verifier per claim kind. Each returns a verdict from data-model.md and, when it fails, a
 * sentence that can be acted on without re-deriving anything (FR-011).
 */

/** The statuses the flow produces. Anything else, and nothing downstream knows what to do with it. */
export const KNOWN_STATUSES = ['Draft', 'Approved', 'Implemented']

function holds() {
  return { verdict: 'holds', why: '' }
}

function broken(why) {
  return { verdict: 'broken', why }
}

export function verifyStatus(feature) {
  const declared = feature.declaredStatus ?? ''
  const leading = declared.split(/[,.\s]/)[0]
  if (!KNOWN_STATUSES.includes(leading)) {
    return broken(
      `status "${declared}" is not one of ${KNOWN_STATUSES.join(', ')}; the check cannot tell ` +
        'whether this feature should be held to its code',
    )
  }

  if (feature.implemented && !feature.hasTasks) {
    return broken('declares itself implemented but has no tasks.md to have completed')
  }

  // A count written in prose, beside the file it counts. Feature 005 says 46 where its file holds 54.
  const stated = /\ball\s+(\d+)\s+tasks?\b/i.exec(declared)
  if (stated && feature.hasTasks && Number(stated[1]) !== feature.tasksTotal) {
    return broken(
      `the status line says ${stated[1]} tasks; tasks.md holds ${feature.tasksTotal}`,
    )
  }

  // The skip has to be visible. A feature that is finished but does not say so would otherwise be
  // passed over in silence, which is this tool passing by not looking.
  const outstanding = feature.tasksTotal - feature.tasksDone
  if (!feature.implemented && feature.hasTasks && feature.tasksTotal > 0 && outstanding <= 1) {
    const work =
      outstanding === 0
        ? `all ${feature.tasksTotal} tasks are complete`
        : `${feature.tasksDone} of ${feature.tasksTotal} tasks are complete, the one remaining left open deliberately`
    return broken(
      `${work}, but the status says "${leading}" rather than Implemented, so this feature's ` +
        'documents are not being held to its code',
    )
  }

  return holds()
}

/**
 * Every path in the repository worth matching against, built once.
 *
 * A document writes a path relative to whatever root its reader has in mind: the repository, its own
 * directory, a module, or a Java package. Rather than guess which, a claim holds when the index
 * contains it or contains a path ending in it — so `agent/FinancialChainFactory.java` finds the file
 * inside backend's package tree, and `core/ChainRunner.java`, which feature 005 deleted, finds
 * nothing and is reported.
 */
const SKIP = new Set(['node_modules', 'build', 'dist', 'out', '.gradle', '.cache', 'target', '.git'])

function indexRepository(dir = ROOT, prefix = '') {
  const found = []
  for (const entry of readdirSync(dir)) {
    if (SKIP.has(entry)) continue
    const relative = prefix ? `${prefix}/${entry}` : entry
    if (statSync(path.join(dir, entry)).isDirectory()) {
      found.push(`${relative}/`)
      found.push(...indexRepository(path.join(dir, entry), relative))
    } else {
      found.push(relative)
    }
  }
  return found
}

let index = null
function repositoryIndex() {
  if (index === null) index = indexRepository()
  return index
}

/** FR-001. Existence, nothing more: whether a document *describes* it correctly is not checkable. */
export function verifyPath(claim) {
  const relative = claim.subject.replace(/\/$/, '')
  if (existsSync(path.join(ROOT, relative))) return holds()
  // A claim always carries its document in a real run; without one there is simply no "beside".
  if (claim.document) {
    const beside = path.posix.join(path.posix.dirname(claim.document), relative)
    if (existsSync(path.join(ROOT, beside))) return holds()
  }
  // Matched with and without a trailing slash: the index records directories as `frontend/src/api/`,
  // and a claim arrives here stripped, so a bare suffix test misses every directory.
  const suffixes = [`/${relative}`, `/${relative}/`]
  const index = repositoryIndex()
  if (index.some((p) => p === relative || p === `${relative}/` || suffixes.some((x) => p.endsWith(x)))) {
    return holds()
  }
  return broken(
    'the document names it; it does not exist — not at the repository root, beside the document, ' +
      'or inside any module',
  )
}

/**
 * FR-002, bounded by research R-009: only a directory whose tree line carries `[complete]` claims
 * that its contents are accounted for. Naming a directory is not that claim — inferring it demanded
 * that feature 008's plan account for node_modules, and six false failures would have had this check
 * switched off inside a week.
 *
 * `named` is every path the feature's documents mention, so a file named in the quickstart counts as
 * accounted for even when the plan's tree omits it.
 */
export function completenessClaims({ document, lines, feature, named }) {
  const claims = []
  for (const marked of completeDirectories(lines)) {
    const full = path.join(ROOT, marked.directory)
    if (!existsSync(full) || !statSync(full).isDirectory()) continue
    for (const entry of readdirSync(full)) {
      const relative = `${marked.directory}/${entry}`
      if (statSync(path.join(full, entry)).isDirectory()) continue
      if (named.has(relative) || named.has(`${relative}/`)) continue
      claims.push({
        kind: 'path',
        document,
        line: marked.line,
        raw: marked.lineText,
        subject: relative,
        feature: feature.id,
        lineText: marked.lineText,
        why:
          `${marked.directory}/ is marked [complete], and this file beneath it is named nowhere ` +
          "in this feature's documents",
      })
    }
  }
  return claims
}
