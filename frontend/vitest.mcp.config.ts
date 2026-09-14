import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vitest/config'

const here = path.dirname(fileURLToPath(import.meta.url))

/**
 * The live suite (FR-003a, SC-008). Separate from the deterministic one by *task*, never by a flag
 * someone has to remember — the split feature 007 already uses between `test` and `topologyTest`.
 *
 *   npm run test:mcp                     here
 *   ./gradlew :frontend:mcpConsoleTest   through the build
 *   make test-console                    through the launcher
 *
 * `environment: 'node'` is correct because every test here drives the console's own modules —
 * transport.ts, targets.ts, principals.ts — rather than rendering them. A hand-written fetch in one
 * of these files would assert things about the server while proving nothing about the console, which
 * is the exact failure feature 007 recorded in its R-017 and the reason SC-008 asks for two suites.
 *
 * `globals: true` matches the default project, so a test reads the same in either suite.
 *
 * No setup file: no MSW, no jsdom, no stubs. That is the point.
 */
export default defineConfig({
  resolve: {
    alias: {
      '@contracts007': path.resolve(here, '../specs/007-mcp-billing-server/contracts/tools'),
    },
  },
  test: {
    environment: 'node',
    globals: true,
    include: ['src/mcp/**/*.live.test.ts'],
    // A billing run takes 30-90s unless the stack was started with LEGACY_RUN_DURATION_MS (007
    // FR-024), and the poll-to-completion test really does wait one out.
    testTimeout: 180_000,
    hookTimeout: 60_000,
    // The outcome depends on a running stack, so there is nothing to cache between runs.
    fileParallelism: false,
  },
})
