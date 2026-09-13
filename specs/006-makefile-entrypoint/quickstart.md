# Quickstart: Validating the Makefile Entry Point

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Targets**: [contracts/make-targets.md](./contracts/make-targets.md)

Run everything from the repository root. Prerequisites are the README's: Docker, Node.js at the version in
`frontend/.nvmrc`, and a Java on the `PATH` for the Gradle wrapper to launch with. Scenarios 1 to 3 need
nothing else. Scenario 5 in cloud mode needs the provider variables in the shell or `.env`.

## Scenario 1: Self-test (SC-002 to SC-005, FR-016)

```bash
make --version | head -1         # GNU Make 3.81 on macOS
scripts/make/selftest.sh
```

**Expect**: every check in research R-010 prints `ok`, and the script exits 0. No container, JDK build, or
network call happens (the tools are stubs).

## Scenario 2: Help (US2)

```bash
make
make help | grep -c '^  '        # number of described targets
```

**Expect**: groups Development, Packaged system, Verification, Maintenance, each with its targets and one
line each. Nothing starts (`docker ps` is unchanged). The count equals the number of targets in the
contract.

## Scenario 3: Safety without a terminal (US3 scenario 5, US4 scenario 4, SC-004)

```bash
make reset  </dev/null; echo "exit=$?"
make golden </dev/null; echo "exit=$?"
git status --porcelain backend/src/test/resources/golden
```

Then interactively in a terminal: `make golden`, answer `n`. **Expect** `Aborted.` and no change.

**Expect** (non-interactive part): both refuse naming `CONFIRM=yes`, both print `exit=2` (Make's status for a failed recipe), and
no golden file changed. `docker volume ls` still lists the stack's volumes if they existed.

## Scenario 4: Development (US1, SC-001)

```bash
make dev
```

**Expect**: labeled `[backend]` and `[frontend]` output. The backend answers on
`http://localhost:8080/api/catalog`, and the application opens on `http://localhost:5173`. Complete a run.
Press Ctrl+C once, then:

```bash
nc -z 127.0.0.1 8080 && echo open || echo closed    # closed
nc -z 127.0.0.1 5173 && echo open || echo closed    # closed
make deps-down
```

Port check: start `make dev-backend` in one terminal, then `make dev` in another. **Expect**: `make dev`
fails naming port 8080 and starts nothing.

## Scenario 5: Packaged system on 8866 (US3, FR-011a, SC-007)

```bash
make up                                               # cloud variables in the shell or .env
curl -fsS http://localhost:8866/ | grep -c '<div id="root"'
curl -fsS http://localhost:8866/api/catalog | grep -q '"companies"' && echo api ok
nc -z 127.0.0.1 8000 && echo open || echo closed      # closed
make status
make scale BACKEND_REPLICAS=3 && make status          # three backend instances
make logs-lb | tail -3                                # JSON lines with "upstream"
make down
L4J_HTTP_PORT=9000 make up && curl -fsS -o /dev/null http://localhost:9000/ && echo override ok && make down
# local part: drop the cloud settings, or pull-model would fetch the cloud model id locally
env -u L4J_PROVIDER -u L4J_MODEL_BASE_URL -u L4J_MODEL_ID -u OLLAMA_API_KEY make up-local
env -u L4J_PROVIDER -u L4J_MODEL_BASE_URL -u L4J_MODEL_ID -u OLLAMA_API_KEY make pull-model   # "Pulling llama3.2"
make down
```

**Expect**: each line as commented. `make up` returns only once every service is healthy.

Remaining references to the old port:

```bash
grep -rn '8000' --exclude-dir=node_modules --exclude-dir=build --exclude-dir=.git --exclude-dir=.gradle . \
  | grep -v 'specs/004-containerized-deployment/tasks.md' | grep -v 'specs/006-makefile-entrypoint/'
```

**Expect**: no line refers to the load balancer's port (research R-011 explains the two exclusions).

## Scenario 6: Verification parity (US4, SC-003, SC-006)

```bash
make check;        echo "make=$?"
./gradlew check;   echo "gradlew=$?"
make test-live                                        # with no provider configured
make lint
```

**Expect**: `make check` and `./gradlew check` both pass with the README's test counts (backend 145,
frontend 126). `make test-live` reports skipped with the named reason. `lint` exits 0.

## Scenario 7: Missing tool (FR-015)

```bash
env PATH=/usr/bin:/bin make up; echo "exit=$?"        # docker not on this PATH on macOS
```

**Expect**: `Missing tool: docker. ...` and nothing started.
