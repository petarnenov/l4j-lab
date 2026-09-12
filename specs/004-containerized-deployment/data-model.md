# Data Model: Containerized Deployment

**Date**: 2026-09-13 | **Plan**: [plan.md](./plan.md)

No application data changes: no table, migration, or API type. The entities here are the runtime
components of the packaged system and the rules between them. The feature is correct exactly when these
rules hold.

## Components

| Component | Compose service | Image | Replicas | Internal port | Published to host | Health signal |
|-----------|-----------------|-------|----------|---------------|-------------------|---------------|
| Load balancer | `load-balancer` | `nginx:1.28-alpine` + `deploy/load-balancer/nginx.conf` | 1 | 8080 | `${L4J_HTTP_PORT:-8000}` | `GET /lb-health` → 200 |
| Frontend instance | `frontend` | built from `frontend/Dockerfile` | `${FRONTEND_REPLICAS:-2}` | 8080 | no | `GET /healthz` → 200 |
| Backend instance | `backend` | built from `backend/Dockerfile` | `${BACKEND_REPLICAS:-2}` | 8080 | no | `GET /health/readiness` → 200 |
| Database | `postgres` | `pgvector/pgvector:pg17` | 1 | 5432 | no | `pg_isready` |
| Model runtime | `ollama` (profile `local`) | `ollama/ollama` | 1 | 11434 | no | none; not a startup dependency |

## Relationships and startup order

```text
                     host :${L4J_HTTP_PORT:-8000}
                                 │
                        ┌────────▼────────┐
                        │  load-balancer  │  the only published port
                        └───┬─────────┬───┘
               /api/*       │         │   everything else
                 ┌──────────▼──┐   ┌──▼───────────┐
                 │ backend × N │   │ frontend × N │
                 └──┬───────┬──┘   └──────────────┘
                    │       │ (local provider mode only)
             ┌──────▼───┐ ┌─▼──────┐
             │ postgres │ │ ollama │
             └──────────┘ └────────┘
          ── all on the private network `internal` ──
```

| Service | `depends_on` | Condition |
|---------|-------------|-----------|
| `backend` | `postgres` | `service_healthy` |
| `frontend` | none | — |
| `load-balancer` | `backend`, `frontend` | `service_healthy` |

## Rules

- **Exposure** (FR-010): only `load-balancer` has a `ports:` entry. Every other service has none, and no
  service uses host networking.
- **Routing** (FR-008): a request path starting with `/api/` goes to the backend upstream; every other
  path goes to the frontend upstream. Health paths of instances are not routed.
- **Membership** (FR-012, FR-016): upstream membership is whatever Docker DNS returns for the service name,
  re-resolved every 5 seconds. Scaling needs no balancer change.
- **Interchangeability** (FR-017): backend instances hold no per-run state in memory beyond the executing
  thread; run and node records live in `postgres`. Frontend instances serve identical static files.
- **Persistence** (FR-006): `postgres` data lives in the named volume `pgdata` of the stack project. `down`
  keeps it; `down -v` removes it.
- **Configuration** (FR-020, FR-021): backend instances receive `L4J_*`, `OLLAMA_API_KEY`, and
  `DATASOURCE_*` from the environment with the defaults in [contracts/stack-topology.md](./contracts/stack-topology.md).
  No value is baked into an image.

## Instance lifecycle

| From | Event | To | Balancer effect |
|------|-------|----|-----------------|
| starting | readiness 200 | healthy | added on next DNS refresh (≤ 5 s) |
| healthy | connection refused or 502/503 | failed | skipped for `fail_timeout` (10 s), request retried on another instance if idempotent |
| healthy | container stopped or scaled away | gone | removed on next DNS refresh (≤ 5 s) |
| backend healthy | database unavailable | unhealthy (readiness ≠ 200) | Compose restarts per `restart: unless-stopped` if the process exits |

**Known limitation** (spec edge case): a run executing on a backend instance that stops stays in its last
non-terminal state. No component reclaims it.
