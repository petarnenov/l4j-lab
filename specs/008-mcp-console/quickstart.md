# Quickstart: MCP console

**Feature**: `008-mcp-console`

A run-and-verify guide. Contracts are in `contracts/`, entities in `data-model.md`, decisions and
their evidence in `research.md`. Nothing is duplicated here.

## Prerequisites

- Docker with Compose v2, and a JDK for the Gradle toolchain — the same ones feature 007 needs
- Node 24 (`frontend/.nvmrc`)
- No credentials and no network beyond the container registry: the MCP stack runs offline, and this
  console adds no provider of any kind

## Start it

Two commands, in two terminals.

```bash
make mcp-up          # feature 007's stack: 3 replicas behind a proxy on :8877
make dev-frontend    # the application's dev server on :5173
```

Then open <http://localhost:5173> and choose **MCP console** in the header. The item is marked as a
development tool and appears only in the development build (FR-001a, FR-002).

To demonstrate the cross-replica scenarios, start the stack with the topology overlay instead, which
publishes `mcp-a`, `mcp-b`, and `mcp-c` individually:

```bash
make mcp-up-topology
```

With a plain `make mcp-up` the console offers the proxy alone and says why the others are absent.
That is the expected state, not a fault (FR-017).

If the MCP stack uses a non-default port, export the same variable for the dev server — the names
and defaults are shared with Compose, and the full table is in
[contracts/dev-proxy.md](./contracts/dev-proxy.md):

```bash
MCP_HTTP_PORT=9001 make mcp-up
MCP_HTTP_PORT=9001 make dev-frontend
```

## Verify it, scenario by scenario

Each row is one thing to do and what must happen. Scenario numbering follows `spec.md`.

### The console itself

| Do this | Expect |
|---|---|
| Open the console with the stack **stopped** | It says the stack is not reachable and names `make mcp-up`. Never an empty page (US1-4, SC-006) |
| Open it with the stack running | Five tools, in the server's order, each showing read-only, destructive, idempotent and open-world (US1-1) |
| Select a tool | A field per declared argument, required ones marked; the call disabled while one is empty (US1-2) |
| Call `search_billing_runs` with `firm_id: firm-alpha` | The structured result **and** the exact request and response beside it (US1-3, SC-003) |

SC-001 is the one to judge honestly: hand the page to someone who has not seen it and see whether
they reach a result in two minutes without asking.

### Identity decides what is visible (US2)

| Do this | Expect |
|---|---|
| Choose `advisor-alpha-101` | Its user, firm, role, and permitted advisors shown, none of it typed in (US2-1) |
| Search `firm-alpha`, then switch to `admin-alpha` and search again | The advisor's result is smaller and contains only `adv-101`'s runs (US2-2) |
| As either, search `firm_id: firm-beta` | The server's refusal, shown as a **tool** failure carried by a successful response — not a protocol failure (US2-3) |

That last row is SC-004, and it is the fastest demonstration in the console: two searches, no writes.

### A change that asks before it acts (US3)

Call `post_fee_adjustment` three times with the **same** `operation_id` (any stable string of 8+
characters), `account_id: acc-0101`, `delta_bps: 15`, and an `effective_date`. On a freshly
seeded stack `acc-0101` starts at 100 bps and belongs to `adv-101`, so both `advisor-alpha-101`
and `admin-alpha` may adjust it.

| Call | Expect |
|---|---|
| First | The server's question verbatim, a statement that nothing has been applied, and no result panel (US3-1) |
| Confirm | Applied, with the identifier the billing system assigned, and the fee before and after (US3-2, US3-5) |
| Third, same `operation_id` | The original result, marked as replayed — nothing happened a second time (US3-3) |
| A fourth, new `operation_id`, declined | Nothing applied, shown as a result and not an error (US3-4) |

The fee has now moved and stays moved. The panel names `make mcp-reset`, which discards the stack's
stored data and reloads the fixtures; there is no undo, and [spec FR-012b](./spec.md) says why
(US3-6).

### A long operation, and the replicas behind it (US4)

| Do this | Expect |
|---|---|
| Call `start_billing_run` | A handle at once, with progress updating on its own (US4-1) |
| Cancel it | It reaches a cancelled state (US4-2) |
| With the overlay running, aim a call at `mcp-b` | The console says which replica answered (US4-3) |
| Search with `page_size: 1` on `mcp-a`, then take the next page on `mcp-b` | The continuation succeeds; the two pages share no run (US4-4, SC-005) |
| Without the overlay | The proxy alone, with the absence explained (US4-5) |

**On timing**: a billing run takes 30–90 seconds unless the stack was started with
`LEGACY_RUN_DURATION_MS` set (007 FR-024). For a quick demonstration:
`LEGACY_RUN_DURATION_MS=3000 make mcp-up`. A run that seems to hang for a minute is the feature
behaving correctly.

### Deliberate refusals (SC-003a)

Send each of the three prepared malformed requests, one action apiece. Full catalogue in
[contracts/malformed-requests.md](./contracts/malformed-requests.md).

| Send | Expect |
|---|---|
| Header disagreeing with the body | HTTP 400, `-32020`, shown as a **protocol** failure with its code |
| An unimplemented version | HTTP 400, `-32022`, showing the versions the server does support |
| A required header omitted | HTTP 400, `-32020` |

Each is marked deliberate in the log, so its refusal is not read as a fault.

## Run the tests

Two suites, split by task and never by a flag — the same shape feature 007 uses.

```bash
make test-frontend        # deterministic: jsdom + MSW, no network, no credential
make test-console         # against the running stack; needs `make mcp-up`
./gradlew check           # includes the deterministic suite and the dev-only build check
```

`make test-console` deliberately does not start containers. With the stack down it fails with a
message naming `make mcp-up`, not an unexplained connection error (SC-008).

The dev-only check is the one that proves FR-001a rather than asserting it:

```bash
cd frontend && npm run check:dev-only    # builds, and fails if the console is in the output
```

## Reset

```bash
make mcp-reset            # discards the stack's stored data (asks first); the fixtures reload on the next start
make mcp-down             # stop, keeping data
```

## Troubleshooting

| Symptom | Cause |
|---|---|
| The console says the stack is not reachable, but `make mcp-up` ran | The stack is on a non-default port. Export the same `MCP_HTTP_PORT` for the dev server |
| All three replicas show as unreachable | Normal for `make mcp-up`: it publishes one port. Use `make mcp-up-topology` |
| Every call returns 401 | The issuer is unreachable through the proxy, or the stack is still starting. `docker compose -f compose.mcp.yaml ps` |
| `post_fee_adjustment` returns `-32021` | Only possible if the console stopped declaring `elicitation`. That is a bug here, not in the server |
| The fee is not the value it was last time | Expected: the writes are real and they accumulate. `make mcp-reset` |
| The MCP console item is missing from the header | You are looking at a packaged build. It is development-only by design (FR-001a) |
