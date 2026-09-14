import react from '@vitejs/plugin-react'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vite'
import { configDefaults } from 'vitest/config'

const here = path.dirname(fileURLToPath(import.meta.url))

/**
 * Feature 008: the browser never addresses the MCP stack — this dev server forwards to it.
 *
 * That is what keeps the request same-origin (so feature 007's server needs no CORS change), keeps
 * the two Compose networks apart (feature 004's promise), and makes the console structurally absent
 * from `vite build`: the forwarder exists only while this server is running.
 *
 * The variable names and defaults are the ones compose.mcp.yaml and compose.mcp.topology.yaml
 * already read, so a stack started with `MCP_HTTP_PORT=9001 make mcp-up` needs the same variable
 * exported here and nothing else changes.
 *
 * Contract: specs/008-mcp-console/contracts/dev-proxy.md.
 */
const mcpPorts: Record<string, string> = {
  proxy: process.env.MCP_HTTP_PORT ?? '8877',
  a: process.env.MCP_REPLICA_A_PORT ?? '8881',
  b: process.env.MCP_REPLICA_B_PORT ?? '8882',
  c: process.env.MCP_REPLICA_C_PORT ?? '8883',
}

const mcpForwarder = Object.fromEntries(
  Object.entries(mcpPorts).map(([name, port]) => [
    `/mcp-dev/${name}`,
    {
      target: `http://localhost:${port}`,
      // Deliberately NOT changeOrigin. FR-010 promises the console shows what travelled, so the
      // forwarder adds, removes, and rewrites nothing but the path prefix it owns. Every target is a
      // port on this machine, so no target depends on the Host header to route.
      changeOrigin: false,
      rewrite: (requestPath: string) => requestPath.replace(`/mcp-dev/${name}`, ''),
      // A streamed result must not arrive all at once, the same reason deploy/mcp/nginx.conf turns
      // buffering off. A billing run poll can also outlive the default timeout.
      timeout: 300_000,
    },
  ]),
)

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      // R-004: the deterministic suite reads the *committed* tool declarations rather than a copy of
      // them — the same five files mcp-server's build takes as its own test oracle. A fixture that
      // drifts from the contract is worse than no fixture: it makes the suite pass while the console
      // renders the wrong thing.
      '@contracts007': path.resolve(here, '../specs/007-mcp-billing-server/contracts/tools'),
    },
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      ...mcpForwarder,
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    // Vitest's `exclude` REPLACES the default list rather than extending it. Without the spread,
    // node_modules and dist join the run. The live suite is a separate project (vitest.mcp.config.ts)
    // because it needs a running stack, which this one must never need.
    exclude: [...configDefaults.exclude, '**/*.live.test.ts'],
    // The first antd render in each file is several times slower on a cold machine: 3.0 s against 0.75 s
    // measured right after `npm ci`, which is every CI run and every `./gradlew check --rerun-tasks`. The
    // 5 s default then failed tests that were correct, only slow. A broken test still fails, later.
    testTimeout: 20_000,
  },
})
