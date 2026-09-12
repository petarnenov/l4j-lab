/**
 * The frontend end of the API contract (R-006).
 *
 *   node scripts/api-contract.mjs generate   rewrite src/api/schema.d.ts from the backend's description
 *   node scripts/api-contract.mjs check      fail if src/api/schema.d.ts differs from a fresh generation
 *
 * The Java records are the single source. The backend compile writes their OpenAPI description, and
 * openapi-typescript turns that into the types this frontend compiles against. This script only adds
 * two things the bare CLI could not: a failure that says what to run, and a comparison that works on
 * every operating system, which `diff -q` did not.
 */
import { spawnSync } from 'node:child_process'
import { existsSync, mkdirSync, readFileSync } from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath, pathToFileURL } from 'node:url'

const frontendDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const committedTypes = path.join(frontendDir, 'src/api/schema.d.ts')
const checkOutput = path.join(frontendDir, 'node_modules/.cache/schema.check.d.ts')

/** Where the backend compile writes the description. No version in the name, so a bump cannot move it. */
export function descriptionPath() {
  return path.resolve(
    frontendDir,
    '../backend/build/classes/java/main/META-INF/swagger/openapi.yml',
  )
}

export function missingDescriptionMessage(expectedPath) {
  return (
    `The API description was not found at ${expectedPath}. ` +
    'It is written when the backend compiles. Run `./gradlew :backend:classes` from the repository ' +
    'root, or run `./gradlew :frontend:checkApi`, which compiles the backend first.'
  )
}

/** Byte for byte on purpose: formatting drift is still drift, and the fix is to regenerate, never to hand-edit. */
export function compareGenerated(committed, fresh) {
  if (committed === fresh) {
    return { ok: true }
  }
  return {
    ok: false,
    message:
      'src/api/schema.d.ts does not match the backend API description. A Java type changed without ' +
      'the frontend types being regenerated, or the file was edited by hand. Run `npm run generate:api` ' +
      'in frontend/ and commit the result.',
  }
}

function generate(description, output) {
  // Runs the CLI's own entry file with this Node, which avoids the .cmd shim problem on Windows.
  const cli = path.join(frontendDir, 'node_modules/openapi-typescript/bin/cli.js')
  const result = spawnSync(process.execPath, [cli, description, '-o', output], { stdio: 'inherit' })
  if (result.status !== 0) {
    process.exit(result.status ?? 1)
  }
}

function main(mode) {
  if (mode !== 'generate' && mode !== 'check') {
    console.error('Usage: node scripts/api-contract.mjs <generate|check>')
    process.exit(2)
  }

  const description = descriptionPath()
  if (!existsSync(description)) {
    console.error(missingDescriptionMessage(description))
    process.exit(1)
  }

  if (mode === 'generate') {
    generate(description, committedTypes)
    return
  }

  mkdirSync(path.dirname(checkOutput), { recursive: true })
  generate(description, checkOutput)
  const result = compareGenerated(
    readFileSync(committedTypes, 'utf8'),
    readFileSync(checkOutput, 'utf8'),
  )
  if (!result.ok) {
    console.error(result.message)
    process.exit(1)
  }
  console.log('src/api/schema.d.ts matches the backend API description.')
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  main(process.argv[2])
}
