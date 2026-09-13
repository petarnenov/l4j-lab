# Contract: Stack Topology and Configuration

**Feature**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md) | **Research**: R-001, R-008, R-010, R-011

What `compose.stack.yaml` promises. Changing a row is a change to this contract.

## Project

| Property | Value |
|----------|-------|
| File | `compose.stack.yaml` at the repository root |
| Compose project name | `financial-agent-chain-stack` |
| Network | `internal`, a bridge network; every service attaches to it |
| Volumes | `pgdata` (PostgreSQL), `ollamadata` (model runtime, profile `local`) |

## Published ports

| Host | Container | Service |
|------|-----------|---------|
| `${L4J_HTTP_PORT:-8866}` | 8080 | `load-balancer` |

No other service publishes a port. `docker compose -f compose.stack.yaml ps --format '{{.Publishers}}'`
lists exactly one published port across all services.

## Environment

Variables are read from the shell or from a `.env` file at the repository root (git-ignored).

| Variable | Used by | Default in the stack | Notes |
|----------|---------|----------------------|-------|
| `L4J_HTTP_PORT` | `load-balancer` | `8866` | The one published port |
| `FRONTEND_REPLICAS` | `frontend` | `2` | |
| `BACKEND_REPLICAS` | `backend` | `2` | |
| `L4J_PROVIDER` | `backend` | `local` | `local` or `cloud`, as in development |
| `L4J_MODEL_BASE_URL` | `backend` | `http://ollama:11434` | Set `https://ollama.com` for cloud mode |
| `L4J_MODEL_ID` | `backend` | `llama3.2` | Same default as `application.yml` |
| `L4J_MODEL_TIMEOUT_SECONDS` | `backend` | `45` | Must stay below the balancer's 75 s read timeout |
| `OLLAMA_API_KEY` | `backend` | empty | Required in cloud mode; never written to an image |
| `DATASOURCE_USER` | `backend`, `postgres` | `l4j` | Development value; change outside local use |
| `DATASOURCE_PASSWORD` | `backend`, `postgres` | `l4j` | Development value; change outside local use |

Set by the stack and not meant to be overridden: `DATASOURCE_URL=jdbc:postgresql://postgres:5432/l4j`.

## Provider modes in the stack

| Mode | Start command adds | Required variables |
|------|--------------------|--------------------|
| Local | `--profile local`, then pull a model into `ollama` once | none beyond defaults; `L4J_MODEL_ID` must name a pulled model |
| Cloud | nothing | `L4J_PROVIDER=cloud`, `L4J_MODEL_BASE_URL=https://ollama.com`, `L4J_MODEL_ID`, `OLLAMA_API_KEY` |

## Images

| Service | Build context | Dockerfile | Runtime user |
|---------|---------------|------------|--------------|
| `backend` | repository root | `backend/Dockerfile` | non-root |
| `frontend` | `frontend/` | `frontend/Dockerfile` | `nginx` (non-root) |
| `load-balancer` | none, stock image | — | `nginx` (non-root), listens on 8080 |
