# Contract: CI Workflows

**Feature**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md) | **Research**: R-007

Host: GitHub Actions. Three workflows under `.github/workflows/`, each triggered on `push` and
`pull_request` with the path set below. No workflow receives a model credential or runs
`:backend:liveTest` (FR-015).

## Shared paths

Every workflow includes these, so a change to shared build configuration runs everything (FR-014):

```text
settings.gradle.kts
gradle/**
gradlew
gradlew.bat
.github/workflows/**
```

## Workflows

| Workflow | Additional paths | Command | Toolchains |
|----------|------------------|---------|------------|
| `backend.yml` | `backend/**` | `./gradlew :backend:check` | JDK 25 |
| `frontend.yml` | `frontend/**` | `npm ci` then `npm test`, in `frontend/` | Node from `frontend/.nvmrc` |
| `contract.yml` | `backend/**`, `frontend/**` | `./gradlew :frontend:checkApi :frontend:checkVersion` | JDK 25, Node from `frontend/.nvmrc` |

## Expected selection

| Change touches only | backend | frontend | contract |
|---------------------|:-------:|:--------:|:--------:|
| `backend/src/**` | runs | — | runs |
| `frontend/src/**` | — | runs | runs |
| `settings.gradle.kts` | runs | runs | runs |
| `README.md`, `specs/**`, `.specify/**` | — | — | — |

The contract workflow runs on any change to either half rather than a narrower set (web DTOs,
`src/api/`), because a narrower filter can miss a type reached indirectly. The cost is one JDK and
Node setup per change, which is small at this project's size.
