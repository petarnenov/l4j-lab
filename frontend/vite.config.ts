import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    // The first antd render in each file is several times slower on a cold machine: 3.0 s against 0.75 s
    // measured right after `npm ci`, which is every CI run and every `./gradlew check --rerun-tasks`. The
    // 5 s default then failed tests that were correct, only slow. A broken test still fails, later.
    testTimeout: 20_000,
  },
})
