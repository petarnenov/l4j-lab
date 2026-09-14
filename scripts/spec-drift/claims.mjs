/**
 * Claim extraction.
 *
 * The patterns below are the whole definition of what this check can promise. They are deliberately
 * a short list of regular forms rather than a markdown parser: the claim surface is about a dozen
 * shapes, and a syntax tree would be effort spent on prose, which FR-006 says never to interpret.
 *
 * Contract: specs/009-spec-drift-check/contracts/claim-grammar.md.
 */

import { inlineCode } from './scanner.mjs'

/**
 * What this check does not attempt, declared once. `report.mjs` prints this list and must not keep
 * its own copy — a second hand-written list of the boundary would be exactly the drift this whole
 * feature exists to catch (FR-013).
 */
export const NOT_CHECKED = [
  'prose',
  'accuracy of descriptions',
  'external links',
  'code behaviour',
]

export const CLAIM_KINDS = ['path', 'command', 'requirement', 'reference', 'status']

/**
 * The status claim: one per feature, about its own specification (research R-001).
 *
 * The only kind that checks a document against another document rather than against code. It is here
 * because `Status` arms every other check, and the worst thing this tool could do is pass by not
 * looking.
 */
export function statusClaims(feature) {
  const spec = feature.documents.find((d) => d.endsWith('/spec.md'))
  if (!spec) return []
  return [
    {
      kind: 'status',
      document: spec,
      line: 1,
      raw: feature.declaredStatus,
      subject: feature.declaredStatus,
      feature: feature.id,
    },
  ]
}

const GENERATED_EARLY = /(^|\/)(node_modules|build|dist|out|\.gradle|\.cache|target|META-INF)(\/|$)/

/** A path that names nothing real: an illustration with a hole in it. */
const PLACEHOLDER = /[<>{}]|\u2026|\.\.\.|\*/
const EXTENSIONS = /\.(ts|tsx|mjs|js|java|kts|json|yaml|yml|md|sql|sh)$/

/**
 * What separates a path claim from something merely path-shaped.
 *
 * The first draft accepted anything containing a slash, and measuring it against this repository
 * produced 278 reports of which most were noise: HTTP endpoints (`/api/catalog`, `/health/readiness`),
 * a GitHub Action reference (`actions/setup-node`), globs (`frontend/**`), and bare filenames like
 * `package.json` that exist half a dozen times over. A check that reports that much noise is one
 * nobody reads, which is the failure mode research R-002 names.
 */
function looksLikePath(text) {
  if (PLACEHOLDER.test(text)) return false
  if (GENERATED_EARLY.test(text)) return false
  if (/^[a-z]+:\/\//i.test(text)) return false
  if (/\s/.test(text)) return false
  // A leading slash is an HTTP path, not a repository path. Nothing in this repository is addressed
  // from the filesystem root, and every document that writes one means a URL.
  if (text.startsWith('/')) return false
  // A name with no directory part is too ambiguous to hold anyone to: `package.json` exists five
  // times over, and a document naming it is not promising which.
  if (!text.includes('/')) return false
  // A single-segment directory — `agent/`, `domain/`, `dist/` — is almost always a package or a
  // build output being described, not a path being claimed. Two segments is the point at which a
  // document is pointing somewhere rather than naming a concept.
  if (text.endsWith('/') && text.replace(/\/$/, '').split('/').length < 2) return false
  return EXTENSIONS.test(text) || text.endsWith('/')
}

/**
 * Paths whose absence means nothing: build outputs and installed dependencies. A document naming
 * `frontend/build/` is describing something generated, not promising a committed file, and failing a
 * clean checkout for it would teach people to ignore this check.
 */
const GENERATED = /(^|\/)(node_modules|build|dist|out|\.gradle|\.cache|target)(\/|$)/

export function isGenerated(text) {
  return GENERATED.test(text)
}

/**
 * A fenced file tree describes real files, so its lines are claims — unlike a markdown link inside a
 * fence, which is literal text a reader cannot follow. Same delimiter, opposite meanings; the
 * grammar says why.
 *
 * Depth comes from the position of the tree marker: these trees indent four characters per level.
 */
function treePaths(lines) {
  const found = []
  const stack = []
  let root = null
  for (const { line, text, inFence } of lines) {
    if (!inFence) {
      root = null
      stack.length = 0
      continue
    }
    const bare = text.split('#')[0].trimEnd()
    // A blank line ends a tree section. These documents put two trees in one fence, separated by a
    // blank line, and without this every entry after the gap is joined to the first tree's root —
    // which is how `.github/workflows/build.gradle.kts` appeared out of nowhere.
    if (bare.trim().length === 0) {
      root = null
      stack.length = 0
      continue
    }

    const branch = /^([\s\u2502]*)[\u251c\u2514][\u2500-]{1,2}\s*(.+)$/.exec(bare)
    if (!branch) {
      // A line with no tree marker at all: the root of the tree, if it looks like a directory.
      // A tree root is a structural question, not a claim: `backend/` is one segment and would fail
      // looksLikePath, but it still roots everything indented under it. Conflating the two left a
      // stale root in place and produced `.github/workflows/build.gradle.kts`, a path from two
      // different parts of one tree.
      const candidate = bare.trim()
      if (/^[\w.@/-]+\/$/.test(candidate)) {
        root = candidate.replace(/\/$/, '')
        stack.length = 0
      }
      continue
    }
    const depth = Math.floor(branch[1].length / 4)
    const name = branch[2].trim()
    stack.length = depth
    stack[depth] = name.replace(/\/$/, '')
    const joined = [root, ...stack.slice(0, depth + 1)].filter(Boolean).join('/')
    if (!looksLikePath(joined)) continue
    found.push({ line, subject: name.endsWith('/') ? `${joined}/` : joined, lineText: text })
  }
  return found
}

/** Paths a document claims exist: inline code spans, and the lines of a fenced file tree. */
export function pathClaims({ document, lines, feature }) {
  const claims = []
  for (const { line, text, inFence } of lines) {
    if (inFence) continue
    for (const span of inlineCode(text)) {
      if (!looksLikePath(span)) continue
      claims.push({ kind: 'path', document, line, raw: span, subject: span, feature: feature.id, lineText: text })
    }
  }
  for (const entry of treePaths(lines)) {
    claims.push({
      kind: 'path',
      document,
      line: entry.line,
      raw: entry.subject,
      subject: entry.subject,
      feature: feature.id,
      lineText: entry.lineText,
    })
  }
  return claims
}

/** Directory lines carrying the completeness marker, with the directory they refer to. */
export function completeDirectories(lines) {
  const marked = []
  for (const entry of treePaths(lines)) {
    if (/\[complete\]/.test(entry.lineText)) {
      marked.push({ line: entry.line, directory: entry.subject.replace(/\/$/, ''), lineText: entry.lineText })
    }
  }
  for (const { line, text, inFence } of lines) {
    if (!inFence || !/\[complete\]/.test(text)) continue
    const bare = text.split('#')[0].trim()
    if (/^[\w./-]+\/$/.test(bare)) marked.push({ line, directory: bare.replace(/\/$/, ''), lineText: text })
  }
  return marked
}

/**
 * Commands this project defines (FR-003, research R-004).
 *
 * Three prefixes and no others. `docker`, `curl`, `git` and the rest describe the reader's machine;
 * failing a build because someone's container runtime is absent is not drift.
 */
// A flag is not a target: `make --version` and `make -qp` ask make about itself, and this project
// promises neither. Every name here must start with a word character.
const COMMAND = /^(make\s+\w[\w:.-]*|npm\s+run\s+\w[\w:.-]*|\.\/gradlew\s+[:\w][\w:.-]*)$/

export function commandClaims({ document, lines, feature }) {
  const claims = []
  const add = (line, text, subject) =>
    claims.push({ kind: 'command', document, line, raw: subject, subject, feature: feature.id, lineText: text })

  for (const { line, text, inFence } of lines) {
    if (inFence) {
      // A quickstart puts its commands in a fenced block, one per line. A leading variable
      // assignment means the line is demonstrating configuration, not naming a target.
      const bare = text.split('#')[0].trim()
      if (COMMAND.test(bare)) add(line, text, bare)
      continue
    }
    for (const span of inlineCode(text)) {
      const bare = span.trim()
      if (COMMAND.test(bare)) add(line, text, bare)
    }
  }
  return claims
}

/**
 * Requirement identifiers, declared (FR-004).
 *
 * Only in spec.md, and only in the bold form the template uses. Other documents refer to
 * requirements constantly, and a reference is not a second promise to keep.
 */
const DECLARED = /^\s*-\s+\*\*((?:FR|SC)-\d+[a-z]?)\*\*/

export function requirementClaims({ document, lines, feature }) {
  if (!document.endsWith('/spec.md')) return []
  const claims = []
  for (const { line, text, inFence } of lines) {
    if (inFence) continue
    const found = DECLARED.exec(text)
    if (!found) continue
    claims.push({
      kind: 'requirement',
      document,
      line,
      raw: found[0].trim(),
      subject: found[1],
      feature: feature.id,
      lineText: text,
    })
  }
  return claims
}

/**
 * Markdown links into this repository (FR-005).
 *
 * Outside a fence only. Inside one, `[a](b)` is literal text a reader cannot follow, so it promises
 * nothing — this repository's own grammar contains such lines as examples, and a first draft of this
 * check flagged them. A file tree inside a fence *is* a claim, because a reader takes it as a
 * description of real files. Same delimiter, opposite meanings.
 */
const LINK = /\[[^\]]*\]\(([^)\s]+)\)/g

export function referenceClaims({ document, lines, feature }) {
  const claims = []
  for (const { line, text, inFence } of lines) {
    if (inFence) continue
    // Inline code is literal for the same reason a fence is: `[a](b)` written inside backticks is
    // shown, not offered. This file's own grammar writes exactly that, and the check found it.
    const outsideCode = text.replace(/`[^`\n]*`/g, '')
    for (const found of outsideCode.matchAll(LINK)) {
      const target = found[1]
      if (/^[a-z]+:/i.test(target)) continue
      if (target.startsWith('#')) continue
      claims.push({
        kind: 'reference',
        document,
        line,
        raw: found[0],
        subject: target,
        feature: feature.id,
        lineText: text,
      })
    }
  }
  return claims
}
