# Quickstart: Validating the Containerized Deployment

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

Run after implementation. `S` abbreviates `docker compose -f compose.stack.yaml`. Commands are in
[contracts/operations.md](./contracts/operations.md); ports and variables in
[contracts/stack-topology.md](./contracts/stack-topology.md); routing in
[contracts/load-balancer.md](./contracts/load-balancer.md).

## Prerequisites

- Docker Engine with Compose v2 or later. No JDK or Node.js is needed for the stack itself.
- For a completed summary: either `OLLAMA_API_KEY` with `L4J_PROVIDER=cloud` and
  `L4J_MODEL_BASE_URL=https://ollama.com`, or `--profile local` and a pulled model.
- Scenarios 2 to 7 do not need a model; they check packaging.

## Scenario 1: One command, one address, a completed run (US1, SC-001)

```bash
S up -d --build            # add --profile local for local mode
S ps                       # wait until every service is healthy
```

Open `http://localhost:8000`, pick a company and period, start a run, and follow it to the summary. Open
Previous runs and open the run. Then request a path the application does not define:
`curl -s http://localhost:8000/any/path/at/all | grep -c '<div id="root"'`.

**Expect**: the start screen loads with populated lists; the run completes with four node records and opens
from Previous runs; the arbitrary path returns the application's HTML (count 1), not a "not found" page.
A browser reload returns to the start screen, because the application has no per-screen addresses; that is
unchanged behavior.

## Scenario 2: Exactly one published port (US2, SC-002)

```bash
S ps --format '{{.Service}} {{.Publishers}}'
for port in 5432 8080 11434 5173; do nc -z -w 2 localhost $port && echo "OPEN $port" || echo "closed $port"; done
curl -fsS http://localhost:8000/api/catalog >/dev/null && echo "api via balancer ok"
```

**Expect**: only `load-balancer` lists a publisher, on 8000. Every other probe prints `closed` (run with the
development workflow stopped, since it legitimately uses those ports). The catalog is served through the
balancer.

## Scenario 3: A different published port (US2)

```bash
S down && L4J_HTTP_PORT=9000 S up -d
curl -fsS http://localhost:9000/ >/dev/null && echo ok
```

**Expect**: `ok`, with no file edited.

## Scenario 4: Two of each, both answering (US3, SC-003)

```bash
S ps --format '{{.Service}}' | sort | uniq -c
for i in $(seq 20); do curl -fsS http://localhost:8000/api/catalog >/dev/null; curl -fsS http://localhost:8000/ >/dev/null; done
S logs --no-log-prefix load-balancer | grep -oE '"upstream":"[^"]*"' | grep -oE '[0-9.]+:8080' | sort | uniq -c
S logs backend | grep -oE '^backend-[0-9]+' | sort -u
```

**Expect**: 2 `frontend` and 2 `backend`. The access log shows four distinct upstream addresses, two for
`/api/` requests and two for the rest, each serving several of the 20 requests. The backend logs carry two
distinct instance prefixes, `backend-1` and `backend-2` (FR-018).

The two-step match reads every address in the `upstream` field. When nginx retries a request on a second
instance, that field holds both addresses separated by a comma, and a single pattern anchored on the closing
quote would silently skip the line.

## Scenario 5: Migrations exactly once (US3)

```bash
S down -v && S up -d
S logs backend | grep -E 'Successfully applied|is up to date'
```

**Expect**: one instance reports applying 2 migrations; the other reports the schema up to date. Both become
healthy.

## Scenario 6: A run survives polls to different instances (US3, SC-004)

With a model configured, start a run through the browser or `curl -X POST` to `/api/runs`, then poll
`/api/runs/{id}` through the balancer until it is terminal.

**Expect**: a terminal status with four node records, while the access log shows polls answered by both
backend instances.

## Scenario 7: Scale up, scale down, lose an instance (US4, SC-005, SC-006)

```bash
S up -d --scale backend=3
sleep 6; for i in $(seq 30); do curl -fsS http://localhost:8000/api/catalog >/dev/null; done
S logs --since 1m --no-log-prefix load-balancer | grep '"uri":"/api/' | grep -oE '"upstream":"[^"]*"' | grep -oE '[0-9.]+:8080' | sort -u   # three backend addresses
S up -d --scale backend=1
curl -fsS http://localhost:8000/api/catalog >/dev/null && echo "works at 1"
S up -d --scale backend=2
docker stop $(S ps -q backend | head -1)   # stop one of the two
for i in $(seq 20); do curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8000/api/catalog; sleep 0.5; done
```

**Expect**: three distinct backend upstreams after scaling to 3; the application still works at 1. After
stopping one of two instances, every response is `200` within 10 seconds of the stop.

## Scenario 8: No backend at all (edge case, FR-013)

```bash
S up -d --scale backend=0
curl -s -w '\n%{http_code} %{time_total}s\n' http://localhost:8000/api/catalog
```

**Expect**: `502` within about 2 seconds, with the JSON body from the load balancer contract. Restore with
`S up -d`.

## Scenario 8b: A frontend with no instances (edge case, FR-013)

```bash
S up -d --scale frontend=0
curl -s -w '\n%{http_code} %{time_total}s\n' http://localhost:8000/
```

**Expect**: `502` within 5 seconds with the load balancer's short HTML page; `/api/catalog` still answers.
Restore with `S up -d`.

## Scenario 8c: Cloud mode without a credential (edge case)

```bash
L4J_PROVIDER=cloud L4J_MODEL_BASE_URL=https://ollama.com OLLAMA_API_KEY= S up -d backend
S logs backend | grep -m1 OLLAMA_API_KEY
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8000/api/catalog
```

**Expect**: backend instances fail at startup with a message naming `OLLAMA_API_KEY` and never become
healthy; `/api/catalog` returns 502 with the JSON body. Restore with `S up -d`.

## Scenario 9: Data survives a restart (US1, SC-007)

Complete a run (Scenario 1), then `S down && S up -d`, open Previous runs.

**Expect**: the run is listed and opens with its node records.

## Scenario 10: The development workflow and verification are unchanged (FR-022, FR-023, SC-008)

```bash
./gradlew check
```

**Expect**: success, with 121 backend tests (the 120 baseline plus the readiness test) and 126 frontend
tests, no existing test removed or skipped, and no image built. Separately, `docker compose
up -d` still publishes 5432 for `./gradlew :backend:run`.

## Scenario 11: The README alone is enough (US5)

A reader who has not seen this feature follows only the README to run Scenario 1, scale the backend to three,
and stop the stack with its data kept.
