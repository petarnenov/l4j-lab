# Contract: Load Balancer

**Feature**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md) | **Research**: R-002, R-003, R-009

What `deploy/load-balancer/nginx.conf` promises to clients and to the instances behind it.

## Routing

| Request path | Upstream | Notes |
|--------------|----------|-------|
| `/api/` and below | `backend` | Path passed through unchanged, e.g. `/api/runs/{id}` |
| `/lb-health` | answered by the balancer itself, `200` | For the balancer's own container health check |
| anything else | `frontend` | The frontend instance falls back to `index.html` for client routes |

Not routed: `/health`, `/health/readiness`, `/healthz`. Instance health endpoints are internal only.

## Upstream behavior

| Setting | Value | Requirement |
|---------|-------|-------------|
| Algorithm | round robin | SC-003 |
| Membership | Docker DNS for `backend` / `frontend`, re-resolved every 5 s | FR-012, FR-016 |
| `max_fails` / `fail_timeout` | 1 / 10 s | FR-012, SC-006 |
| `proxy_next_upstream` | `error timeout http_502 http_503` | FR-012 |
| Retry of non-idempotent requests | never | a `POST /api/runs` is never sent twice |
| `proxy_connect_timeout` | 2 s | FR-013 |
| `proxy_read_timeout`, `proxy_send_timeout` | 75 s | FR-014, above the 45 s model timeout |

## Headers set toward instances

| Header | Value |
|--------|-------|
| `Host` | original `$host` |
| `X-Forwarded-For` | `$proxy_add_x_forwarded_for` |
| `X-Forwarded-Proto` | `$scheme` |

No header in responses reveals an instance address.

## Failure responses

| Condition | Status | Body |
|-----------|--------|------|
| No backend instance reachable, path under `/api/` | 502, within 5 s (FR-013) | JSON: `{"title":"Service unavailable","status":502,"detail":"No backend instance is available. …"}` |
| No frontend instance reachable | 502 | Short HTML page saying the application is unavailable |

The JSON body follows the shape of the backend's existing problem responses (`title`, `status`,
`detail`), so the frontend's error handling reads it unchanged.

## Access log

One JSON object per line (`log_format … escape=json`), because the constitution requires structured
logs (Principle V). Fields: `time`, `method`, `uri` (path only, `$uri`, so no query string), `status`,
`upstream` (`$upstream_addr`), `upstream_status`, `request_time`. No headers, bodies, or query strings are
logged. The quickstart's checks match on `"upstream":"<address>"`, so `docker compose -f compose.stack.yaml logs
load-balancer` shows which instance answered each request.
