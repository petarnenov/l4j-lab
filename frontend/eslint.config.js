import js from '@eslint/js'

export default [
  { ignores: ['node_modules/', 'dist/', 'coverage/', 'build/', '.gradle/', 'src/api/schema.d.ts'] },
  js.configs.recommended,
  // scripts/ runs under Node, not in the browser, so it may use Node's console.
  { files: ['scripts/**/*.mjs'], languageOptions: { globals: { console: 'readonly' } } },
]
