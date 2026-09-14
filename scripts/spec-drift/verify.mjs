import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs'
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

/**
 * The three manifests, read as text (FR-010).
 *
 * Nothing is executed, not even to list Gradle tasks: `./gradlew tasks` configures every project,
 * which is a side effect and a wait for a question about existence.
 */
let manifests = null
function readManifests() {
  if (manifests) return manifests
  const makeTargets = new Set()
  for (const line of readFileSync(path.join(ROOT, 'Makefile'), 'utf8').split('\n')) {
    const target = /^([a-zA-Z0-9_-]+):/.exec(line)
    if (target) makeTargets.add(target[1])
  }

  const npmScripts = new Set()
  // Tasks the Gradle plugins this build applies contribute, which no build file names in text. The
  // `java` and `application` plugins add most of these; `base` adds the lifecycle ones. Listed rather
  // than discovered because discovering them means running Gradle, and FR-010 forbids executing what
  // is being checked. The cost is that a typo matching one of these names would be missed.
  const gradleTasks = new Set([
    'build', 'check', 'test', 'clean', 'assemble', 'classes', 'testClasses', 'jar', 'javadoc',
    'run', 'installDist', 'distZip', 'distTar', 'processResources', 'processTestResources',
    'compileJava', 'compileTestJava', 'wrapper', 'tasks', 'projects', 'dependencies',
  ])
  for (const relative of repositoryIndex()) {
    if (relative.endsWith('package.json') && !relative.includes('node_modules')) {
      try {
        const json = JSON.parse(readFileSync(path.join(ROOT, relative), 'utf8'))
        for (const name of Object.keys(json.scripts ?? {})) npmScripts.add(name)
      } catch {
        // A package.json that will not parse is the JSON's problem, not this check's.
      }
    }
    if (relative.endsWith('build.gradle.kts')) {
      const text = readFileSync(path.join(ROOT, relative), 'utf8')
      for (const m of text.matchAll(/tasks\.register(?:<[^>]+>)?\(\s*"([\w:.-]+)"/g)) gradleTasks.add(m[1])
      for (const m of text.matchAll(/val\s+(\w+)\s+by\s+tasks\.registering/g)) gradleTasks.add(m[1])
      for (const m of text.matchAll(/tasks\.named(?:<[^>]+>)?\(\s*"([\w:.-]+)"/g)) gradleTasks.add(m[1])
    }
  }
  manifests = { makeTargets, npmScripts, gradleTasks }
  return manifests
}

export function verifyCommand(claim) {
  const { makeTargets, npmScripts, gradleTasks } = readManifests()
  const [tool, ...rest] = claim.subject.split(/\s+/)

  if (tool === 'make') {
    return makeTargets.has(rest[0])
      ? holds()
      : broken(`the Makefile declares no target named "${rest[0]}"`)
  }
  if (tool === 'npm') {
    const script = rest[1]
    return npmScripts.has(script)
      ? holds()
      : broken(`no package.json in this repository declares a script named "${script}"`)
  }
  const task = rest[0].replace(/^.*:/, '')
  return gradleTasks.has(task) || gradleTasks.has(rest[0])
    ? holds()
    : broken(
        `no build file registers a task named "${task}". Build files are read as text, so a task ` +
          'registered dynamically is missed rather than falsely reported — the grammar states that limit',
      )
}

/**
 * FR-004. Coverage is about whether anyone planned for a requirement, not whether the work is
 * finished: a requirement cited only by a task left deliberately open is still accounted for.
 * Feature 008's SC-001 is the live example — it needs a person who has never seen the page.
 */
export function verifyRequirement(claim, { tasksText }) {
  // A feature with no task list is a different problem, and the status claim already reports it.
  // Repeating it once per requirement would bury the one report that matters under twenty copies.
  if (!tasksText) return holds()
  const cited = new RegExp(`\\b${claim.subject}\\b`).test(tasksText)
  return cited
    ? holds()
    : broken(`declared in the specification, and no task in tasks.md cites it`)
}

/**
 * FR-005, FR-009. Resolved from the document's own directory, because that is where the link was
 * written — resolving from anywhere else would make the verdict depend on where the check was
 * started. Nothing external is fetched; the extractor never offers one.
 */
export function verifyReference(claim) {
  const target = claim.subject.split('#')[0]
  if (target.length === 0) return holds()
  const resolved = path.join(ROOT, path.posix.dirname(claim.document), target)
  return existsSync(resolved)
    ? holds()
    : broken(`the link points at ${claim.subject}, which does not exist beside ${claim.document}`)
}
