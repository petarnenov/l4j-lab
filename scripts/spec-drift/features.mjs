/**
 * Which features are held to their code, and why that is a declaration rather than a count.
 *
 * Counting ticked tasks was the obvious rule and is wrong here: 001 and 008 are both delivered and
 * each carries one deliberately open task — a recorded trace that needs a person, and a two-minute
 * usability check that needs someone who has never seen the page. A rule that treated an unticked
 * box as "not delivered" would exclude the two features with the most carefully reasoned open tasks.
 *
 * So the signal is the specification's own `Status` line. That makes it exactly the kind of field
 * that rots, which is why `status` is one of the claim kinds: the check watches its own switch
 * (research R-001).
 */
import { readdirSync, readFileSync, statSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

export const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')

/** Documents belonging to no feature. README names more commands than any specification does. */
export const ALWAYS_IN_SCOPE = ['README.md']

export function isImplemented(declaredStatus) {
  return /\bimplemented\b/i.test(declaredStatus ?? '')
}

function markdownUnder(dir) {
  const found = []
  for (const entry of readdirSync(dir)) {
    const full = path.join(dir, entry)
    if (statSync(full).isDirectory()) found.push(...markdownUnder(full))
    else if (entry.endsWith('.md')) found.push(path.relative(ROOT, full))
  }
  return found.sort()
}

function statusOf(specText) {
  const line = /^\*\*Status\*\*:\s*(.+)$/m.exec(specText)
  return line ? line[1].trim() : ''
}

function countTasks(dir) {
  try {
    const text = readFileSync(path.join(dir, 'tasks.md'), 'utf8')
    return {
      tasksTotal: (text.match(/^- \[[ xX]\] T/gm) ?? []).length,
      tasksDone: (text.match(/^- \[[xX]\] T/gm) ?? []).length,
      hasTasks: true,
    }
  } catch {
    return { tasksTotal: 0, tasksDone: 0, hasTasks: false }
  }
}

export function loadFeatures(root = ROOT) {
  const specsDir = path.join(root, 'specs')
  return readdirSync(specsDir)
    .filter((name) => statSync(path.join(specsDir, name)).isDirectory())
    .sort()
    .map((id) => {
      const dir = path.join(specsDir, id)
      let specText = ''
      try {
        specText = readFileSync(path.join(dir, 'spec.md'), 'utf8')
      } catch {
        specText = ''
      }
      const declaredStatus = statusOf(specText)
      return {
        id,
        directory: path.relative(root, dir),
        documents: markdownUnder(dir),
        declaredStatus,
        implemented: isImplemented(declaredStatus),
        ...countTasks(dir),
      }
    })
}
