# Data Model: Monorepo Integration

**Date**: 2026-09-12 | **Plan**: [plan.md](./plan.md)

This feature adds no runtime data, no database table, and no API type. Its entities are build-time
artifacts and the relationships between them. They are recorded here because the correctness of the
feature is exactly the correctness of these relationships.

## Project identity

| Field | Value | Declared in | Read by |
|-------|-------|-------------|---------|
| Base name | `financial-agent-chain` | `settings.gradle.kts` (`rootProject.name`) | Gradle, IDE |
| API title | `financial-agent-chain` | `@Info(title = …)` in `Application.java` | API description |
| Frontend package name | `financial-agent-chain-frontend` | `frontend/package.json` | npm |
| Version | `0.1.0` | `backend/build.gradle.kts` (`version`) | processor via `api.version`, `:frontend:checkVersion` |
| Frontend manifest version | `0.1.0` | `frontend/package.json` | npm; must equal Version |
| Node major version | `24` | `frontend/.nvmrc` | `:frontend:checkNode`, `actions/setup-node` |

**Rules**

- Version has exactly one declaration. The frontend manifest version is a required duplicate that
  `:frontend:checkVersion` compares, failing with both values when they differ (FR-013).
- Frontend package name starts with the base name (FR-012).

## API description

| Attribute | Value |
|-----------|-------|
| Source | Java web DTOs and controllers in `backend/src/main/java/dev/l4jlab/chain/web/` |
| Producer | Micronaut OpenAPI annotation processor during `:backend:compileJava` |
| Location | `backend/build/classes/java/main/META-INF/swagger/openapi.yml` (fixed, version-free) |
| Committed | No |
| Title / version | Project identity base name / Version |

**Rules**: generated, never hand-edited (constitution, Application framework). Absent until
`:backend:classes` has run; every consumer either depends on that task or fails naming it.

## Committed frontend API types

| Attribute | Value |
|-----------|-------|
| Location | `frontend/src/api/schema.d.ts` |
| Producer | `openapi-typescript` via `npm run generate:api` |
| Committed | Yes |

**Rules**: must be byte-identical to a fresh generation from the current API description.
`npm run check:api` enforces it. Hand edits are drift.

## Verification task graph

```text
./gradlew check
├── :backend:check
│   └── :backend:test                       (120 tests; liveTest not attached)
└── :frontend:check
    ├── :frontend:test ─────────► :frontend:npmCi ─► :frontend:checkNode
    ├── :frontend:checkApi ─────► :frontend:npmCi
    │                      └────► :backend:classes ─► (writes API description)
    └── :frontend:checkVersion
```

**State transitions of a verification run**

| From | Event | To |
|------|-------|----|
| Clean clone | `./gradlew check` | Node checked → dependencies installed → backend compiled → three checks run |
| Any check fails | — | Run fails; Gradle names the failing task path |
| All pass | — | Run succeeds |

The live model test (`:backend:liveTest`) has no edge into this graph (FR-008).
