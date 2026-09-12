import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { compareGenerated, descriptionPath, missingDescriptionMessage } from './api-contract.mjs'

const frontendDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

describe('descriptionPath', () => {
  it('points at the version-free description the backend compile writes (R-001)', () => {
    expect(descriptionPath()).toBe(
      path.resolve(frontendDir, '../backend/build/classes/java/main/META-INF/swagger/openapi.yml'),
    )
  })

  it('carries no version in the file name, so a version bump cannot move it', () => {
    expect(path.basename(descriptionPath())).toBe('openapi.yml')
  })
})

describe('missingDescriptionMessage', () => {
  it('names the file it expected and the command that produces it (FR-003)', () => {
    const message = missingDescriptionMessage('/somewhere/openapi.yml')

    expect(message).toContain('/somewhere/openapi.yml')
    expect(message).toContain('./gradlew :backend:classes')
  })
})

describe('compareGenerated', () => {
  it('passes when the committed types equal a fresh generation', () => {
    expect(compareGenerated('export interface A {}\n', 'export interface A {}\n')).toEqual({
      ok: true,
    })
  })

  it('fails on any difference and names the stale file and the fix', () => {
    const result = compareGenerated('export interface A {}\n', 'export interface A { b: string }\n')

    expect(result.ok).toBe(false)
    expect(result.message).toContain('src/api/schema.d.ts')
    expect(result.message).toContain('npm run generate:api')
  })

  it('treats a formatting-only difference as drift, because the check is byte for byte', () => {
    const result = compareGenerated('export interface A {}\n', 'export interface A {}\r\n')

    expect(result.ok).toBe(false)
  })
})
