/**
 * FR-001a: the MCP console must not be carried in the packaged build.
 *
 *   node scripts/check-dev-only.mjs              build the app, fail if the console is in the output
 *   node scripts/check-dev-only.mjs --self-test  prove this check can actually fail
 *
 * The console's nav item and page sit behind `import.meta.env.DEV`, which Vite replaces with the
 * literal `false` during `vite build`, so Rollup eliminates the branch and the dynamic import inside
 * it. That is how it works. This script is why anyone should believe it.
 *
 * FR-001a is the kind of requirement that silently stops being true — a refactor that hoists an
 * import above the guard, a Vite version that treats the branch differently — and the failure is
 * invisible, because the development build still behaves correctly. A check that reads the actual
 * build output cannot be fooled by either.
 *
 * It is a build check rather than a Vitest test on purpose: Vitest runs with DEV === true, so it is
 * structurally the wrong place to ask this question.
 */
import {
  mkdtempSync,
  readdirSync,
  readFileSync,
  realpathSync,
  rmSync,
  statSync,
  writeFileSync,
} from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'
import { build } from 'vite'

const frontendDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

/**
 * The one string that must never reach a production bundle.
 *
 * Read from the source that declares it rather than copied, so the check and the thing it checks
 * for cannot drift: renaming it in src/mcp/devOnlyMarker.ts changes what this looks for, in the
 * same commit. A second copy here would go on being believed after it went stale.
 */
export const MARKER = (() => {
  const declaration = path.join(frontendDir, 'src/mcp/devOnlyMarker.ts')
  const match = /DEV_ONLY_MARKER = '([^']+)'/.exec(readFileSync(declaration, 'utf8'))
  if (!match) {
    throw new Error(`Could not read DEV_ONLY_MARKER from ${declaration}. Has it been renamed?`)
  }
  return match[1]
})()

/** Every file under `dir` whose bytes contain `marker`. */
export function filesContaining(dir, marker) {
  const found = []
  for (const entry of readdirSync(dir)) {
    const full = path.join(dir, entry)
    if (statSync(full).isDirectory()) {
      found.push(...filesContaining(full, marker))
    } else if (readFileSync(full, 'utf8').includes(marker)) {
      found.push(full)
    }
  }
  return found
}

/**
 * `outDir` is relative to `root`, which is what Vite expects. An absolute path outside the root
 * makes Rollup compute a relative asset name with `..` segments in it and refuse to emit, and a
 * macOS temp directory reaches it through a symlink, so realpath is not optional either.
 */
async function buildInto(root, outDir) {
  await build({
    root,
    logLevel: 'silent',
    configFile: root === frontendDir ? undefined : false,
    build: { outDir, emptyOutDir: true, write: true },
  })
  return path.join(root, outDir)
}

async function checkRealBuild() {
  // Inside node_modules/.cache, as scripts/api-contract.mjs already does: gitignored, on the same
  // filesystem as the project, and never confused with the real `dist/`.
  const relative = path.join('node_modules', '.cache', 'dev-only-dist')
  const outDir = path.join(frontendDir, relative)
  try {
    await buildInto(frontendDir, relative)
    const leaked = filesContaining(outDir, MARKER)
    if (leaked.length > 0) {
      const names = leaked.map((f) => path.relative(outDir, f)).join(', ')
      throw new Error(
        `FR-001a: the MCP console reached the packaged build.\n\n` +
          `The marker "${MARKER}" appears in: ${names}\n\n` +
          `The console must sit behind import.meta.env.DEV so Rollup eliminates it. A static import ` +
          `above the guard, or a reference outside the guarded branch, defeats that. Check ` +
          `frontend/src/App.tsx.`,
      )
    }
    console.log('FR-001a: the MCP console is absent from the production build.')
  } finally {
    rmSync(outDir, { recursive: true, force: true })
  }
}

/**
 * A check never seen to fail is not evidence. This builds the same guard two ways and asserts the
 * marker survives one and not the other — so it tests the *mechanism* (Rollup eliminating a branch
 * on a replaced `import.meta.env.DEV`), not merely this script's ability to read files.
 */
async function selfTest() {
  const root = realpathSync(mkdtempSync(path.join(tmpdir(), 'l4j-dev-only-selftest-')))
  try {
    writeFileSync(path.join(root, 'marker.js'), `export const M = '${MARKER}'\n`)
    writeFileSync(
      path.join(root, 'index.html'),
      '<!doctype html><html><body><script type="module" src="/main.js"></script></body></html>\n',
    )
    const guarded =
      "const page = import.meta.env.DEV ? () => import('./marker.js') : null\n" +
      'if (page) page().then((m) => console.log(m.M))\n'
    const unguarded =
      "const page = () => import('./marker.js')\n" + 'page().then((m) => console.log(m.M))\n'

    writeFileSync(path.join(root, 'main.js'), unguarded)
    const dirty = await buildInto(root, 'dist-unguarded')
    if (filesContaining(dirty, MARKER).length === 0) {
      throw new Error(
        'Self-test failed: an UNGUARDED import did not leave the marker in the build, so this ' +
          'check would pass whether or not the console were shipped. It is proving nothing.',
      )
    }

    writeFileSync(path.join(root, 'main.js'), guarded)
    const clean = await buildInto(root, 'dist-guarded')
    if (filesContaining(clean, MARKER).length > 0) {
      throw new Error(
        'Self-test failed: a GUARDED import still left the marker in the build. The ' +
          'import.meta.env.DEV mechanism FR-001a relies on is not eliminating the branch.',
      )
    }
    console.log('Self-test: the guard removes the marker, and its absence removes the check.')
  } finally {
    rmSync(root, { recursive: true, force: true })
  }
}

async function main() {
  if (process.argv.includes('--self-test')) {
    await selfTest()
    return
  }
  await selfTest()
  await checkRealBuild()
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  main().catch((error) => {
    console.error(error.message)
    process.exit(1)
  })
}
