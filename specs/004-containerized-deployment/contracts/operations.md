# Contract: Operating the Stack

**Feature**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md)

The commands the README documents. All run from the repository root. `S` below abbreviates
`docker compose -f compose.stack.yaml`.

| Task | Command | Result |
|------|---------|--------|
| Start, cloud mode | set `L4J_PROVIDER=cloud`, `L4J_MODEL_BASE_URL=https://ollama.com`, `L4J_MODEL_ID`, `OLLAMA_API_KEY` (shell or `.env`), then `S up -d --build` | 2 frontend, 2 backend, balancer, database; open `http://localhost:8866` |
| Start, local mode | `S --profile local up -d --build` (the stack's default provider is `local`) | as above plus `ollama`; pull a model before the first run |
| Pull a local model (once) | `S --profile local exec ollama ollama pull llama3.2` | model stored in `ollamadata` |
| Status | `S ps` | every service healthy; one published port |
| Scale | `S up -d --scale backend=3` or `BACKEND_REPLICAS=3 S up -d` | three backend instances in rotation within 5 s |
| Which instance served | `S logs --no-log-prefix load-balancer` | one JSON line per request with `"upstream"` |
| Stop, keep data | `S down` | containers and network removed, `pgdata` kept |
| Stop, delete data | `S down -v` | also removes `pgdata` and `ollamadata` |
| Use a different port | `L4J_HTTP_PORT=9000 S up -d` | balancer on `http://localhost:9000` |

## Development workflow (unchanged)

| Task | Command |
|------|---------|
| Database and model runtime for development | `docker compose up -d` |
| Backend from source | `./gradlew :backend:run` |
| Frontend dev server | `cd frontend && npm run dev` |
| Verify everything | `./gradlew check` (does not build images) |

The stack and the development workflow can run at the same time: the stack publishes only 8866, the
development workflow uses 5432, 11434, 8080, and 5173.
