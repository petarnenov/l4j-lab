# One place to start everything (feature 006).
#
# This file is a launcher only. Every target runs a command the README documents; ./gradlew stays the build,
# and CI keeps calling the underlying commands (specs/006-makefile-entrypoint/research.md, R-001). What each
# target runs is listed in specs/006-makefile-entrypoint/contracts/make-targets.md.
#
# Settings (provider, model, credential, published port, replica counts) are never defined here: they come
# from the shell or .env, exactly as the application and compose already read them.

SHELL := /bin/bash
.DEFAULT_GOAL := help

# The Gradle wrapper. Only scripts/make/selftest.sh overrides it, to point at a stub.
GRADLEW ?= ./gradlew
STACK := docker compose -f compose.stack.yaml
SCRIPTS := scripts/make

##@ Help

.PHONY: help

# Help is read from this file: "##@ Group" lines start a group, and a "## description" after a target names
# it. A new target with a description appears here without editing any list (research R-002).
help: ## Show this help
	@printf 'Usage: make <target> [VAR=value]. Settings come from the environment or .env; see README.md.\n'
	@awk 'BEGIN { FS = ":.*## " } /^##@ / { printf "\n%s\n", substr($$0, 5) } /^[a-zA-Z0-9_-]+:.*## / { printf "  %-14s %s\n", $$1, $$2 }' $(MAKEFILE_LIST)

##@ Development

.PHONY: dev dev-backend dev-frontend deps-up deps-down

# The two development ports: 8080 is Micronaut's default (backend/src/main/resources/application.yml) and
# 5173 is Vite's. They are checked, not configured, here.
dev: ## Start everything for development; Ctrl+C stops both halves
	@$(SCRIPTS)/require.sh docker java node npm
	@$(SCRIPTS)/port-free.sh 8080 "The backend needs it. Stop what is listening on it and run again."
	@$(SCRIPTS)/port-free.sh 5173 "The frontend dev server needs it. Stop what is listening on it and run again."
	docker compose up -d --wait
	GRADLEW=$(GRADLEW) $(SCRIPTS)/dev.sh

dev-backend: ## Run the backend from source on 8080, with its containers
	@$(SCRIPTS)/require.sh docker java
	@$(SCRIPTS)/port-free.sh 8080 "The backend needs it. Stop what is listening on it and run again."
	docker compose up -d --wait
	$(GRADLEW) :backend:run

dev-frontend: ## Run the frontend dev server on 5173
	@$(SCRIPTS)/require.sh node npm
	@$(SCRIPTS)/port-free.sh 5173 "The frontend dev server needs it. Stop what is listening on it and run again."
	cd frontend && npm install && npm run dev

deps-up: ## Start PostgreSQL and the model runtime for development
	@$(SCRIPTS)/require.sh docker
	docker compose up -d --wait

deps-down: ## Stop the development containers (data kept)
	@$(SCRIPTS)/require.sh docker
	docker compose down

##@ MCP billing server

.PHONY: mcp-up mcp-up-topology mcp-down mcp-reset mcp-logs mcp-verify

MCP := docker compose -f compose.mcp.yaml
# The acceptance scenarios address individual replicas, which the base stack does not publish.
MCP_TOPOLOGY := docker compose -f compose.mcp.yaml -f compose.mcp.topology.yaml
MCP_PORT ?= $(or $(MCP_HTTP_PORT),8877)

mcp-up: ## Start the MCP stack (3 replicas + proxy) on http://localhost:8877
	@$(SCRIPTS)/require.sh docker
	@$(SCRIPTS)/port-free.sh $(MCP_PORT) "The MCP proxy needs it. Stop what is listening, or choose another with MCP_HTTP_PORT=<port> make mcp-up."
	$(MCP) up -d --build --wait
	@printf 'MCP endpoint: http://localhost:%s/mcp\n' "$(MCP_PORT)"

# Feature 008, FR-017: the console can address an individual replica only while they are published,
# and the overlay was until now started solely inside mcp-verify, which stops it again. This leaves it
# running, so the cross-replica scenarios can be demonstrated by hand.
mcp-up-topology: ## Start the MCP stack with each replica published too (8881-8883); needed for the cross-replica demos
	@$(SCRIPTS)/require.sh docker
	@$(SCRIPTS)/port-free.sh $(MCP_PORT) "The MCP proxy needs it. Stop what is listening, or choose another with MCP_HTTP_PORT=<port> make mcp-up-topology."
	$(MCP_TOPOLOGY) up -d --build --wait
	@printf 'MCP endpoint: http://localhost:%s/mcp\nReplicas: %s, %s, %s\n' "$(MCP_PORT)" \
		"http://localhost:$(or $(MCP_REPLICA_A_PORT),8881)" \
		"http://localhost:$(or $(MCP_REPLICA_B_PORT),8882)" \
		"http://localhost:$(or $(MCP_REPLICA_C_PORT),8883)"

mcp-down: ## Stop the MCP stack (data kept)
	@$(SCRIPTS)/require.sh docker
	$(MCP) down

# Feature 008, FR-012b: the console offers no undo for an applied fee adjustment, because an undo
# would have to be a second, opposite change and the audit log would then describe two events where
# one happened. This is what it names instead, and it deletes data, so it asks first.
mcp-reset: ## Stop the MCP stack and DELETE its stored data, reloading the seeded fixtures on next start (asks first)
	@$(SCRIPTS)/require.sh docker
	@$(SCRIPTS)/confirm.sh "the MCP stack's stored billing data"
	$(MCP_TOPOLOGY) down -v

mcp-logs: ## Tail all six MCP services
	@$(SCRIPTS)/require.sh docker
	$(MCP) logs -f

# LEGACY_RUN_DURATION_MS compresses the simulated billing run. Without it a run takes its real
# 30-90s (FR-024) and the two run-completion scenarios would each wait one out.
mcp-verify: ## Start the MCP stack, run the acceptance scenarios, stop
	@$(SCRIPTS)/require.sh docker java
	@$(SCRIPTS)/port-free.sh $(MCP_PORT) "The MCP proxy needs it."
	LEGACY_RUN_DURATION_MS=3000 $(MCP_TOPOLOGY) up -d --build --wait
	MCP_PROXY_URL=http://localhost:$(MCP_PORT) $(GRADLEW) :mcp-server:topologyTest; \
		status=$$?; $(MCP_TOPOLOGY) down; exit $$status

##@ Packaged system

.PHONY: up up-local pull-model status logs logs-lb scale down reset

# Before starting, check the published port the stack will use. It is read back from compose, which applies
# L4J_HTTP_PORT from the shell or .env, so the default stays declared once, in compose.stack.yaml. When this
# stack's balancer is already running it holds the port itself, and `up` is a legitimate re-apply (R-006).
stack_port_check = set -o pipefail; \
	port=$$($(STACK) config | awk '/published:/ { gsub(/"/, "", $$2); print $$2; exit }') || exit $$?; \
	if [ -z "$$($(STACK) ps -q load-balancer 2>/dev/null)" ]; then \
		$(SCRIPTS)/port-free.sh "$$port" "Stop what is listening on it, or choose another port with L4J_HTTP_PORT=<port> make up."; \
	fi

up: ## Build and start the stack (the packaged system) on http://localhost:8866; provider from env or .env
	@$(SCRIPTS)/require.sh docker
	@$(stack_port_check)
	$(STACK) up -d --build --wait

up-local: ## Start the stack with the local model runtime; unset cloud settings (L4J_PROVIDER, L4J_MODEL_ID, ...) for local inference
	@$(SCRIPTS)/require.sh docker
	@$(stack_port_check)
	$(STACK) --profile local up -d --build --wait

# The model comes from compose (L4J_MODEL_ID, default llama3.2) and is named before a possibly large download.
pull-model: ## Pull the model named by L4J_MODEL_ID (default llama3.2) into the local runtime
	@$(SCRIPTS)/require.sh docker
	@set -o pipefail; \
	model=$$($(STACK) config | awk '/L4J_MODEL_ID:/ { print $$2; exit }') || exit $$?; \
	echo "Pulling $$model into the local model runtime."; \
	$(STACK) --profile local exec ollama ollama pull "$$model"

status: ## Show the stack's services and health
	@$(SCRIPTS)/require.sh docker
	$(STACK) ps

logs: ## Show all stack logs
	@$(SCRIPTS)/require.sh docker
	$(STACK) logs

logs-lb: ## Show the load balancer's JSON access log ("upstream" names the instance)
	@$(SCRIPTS)/require.sh docker
	$(STACK) logs --no-log-prefix load-balancer

# Compose reads BACKEND_REPLICAS and FRONTEND_REPLICAS itself. A half not named returns to its own setting.
scale: ## Scale with BACKEND_REPLICAS=N and/or FRONTEND_REPLICAS=N (the other half returns to its setting)
	@$(SCRIPTS)/require.sh docker
	@if [ -z "$$BACKEND_REPLICAS$$FRONTEND_REPLICAS" ]; then \
		echo "Set BACKEND_REPLICAS and/or FRONTEND_REPLICAS, e.g. make scale BACKEND_REPLICAS=3." >&2; exit 1; \
	fi
	$(STACK) up -d --wait

# --profile local here and in reset: without it, a model runtime started by up-local keeps running (R-008).
down: ## Stop the stack, the model runtime included (stored runs kept)
	@$(SCRIPTS)/require.sh docker
	$(STACK) --profile local down

reset: ## Stop the stack and DELETE stored runs and pulled models (asks first)
	@$(SCRIPTS)/require.sh docker
	@$(SCRIPTS)/confirm.sh "the stack's stored runs and pulled models"
	$(STACK) --profile local down -v

##@ Verification

.PHONY: check test test-backend test-frontend test-console test-live check-api check-specs

# check, test, and check-api reach :frontend:* tasks, so Node.js is checked up front rather than after the
# backend suite has run for minutes (FR-015).
check: ## Verify everything: both suites and the API contract (same as ./gradlew check)
	@$(SCRIPTS)/require.sh java node npm
	$(GRADLEW) check

test: ## Run the backend and frontend test suites
	@$(SCRIPTS)/require.sh java node npm
	$(GRADLEW) :backend:test :frontend:test

test-backend: ## Run the backend tests (Docker for the database tests, no credential)
	@$(SCRIPTS)/require.sh java
	$(GRADLEW) :backend:test

test-frontend: ## Run the frontend tests
	@$(SCRIPTS)/require.sh node npm
	cd frontend && npm test

test-console: ## Run the MCP console's live tests against the running MCP stack (needs `make mcp-up`)
	@$(SCRIPTS)/require.sh java node npm
	$(GRADLEW) :frontend:mcpConsoleTest

test-live: ## Run the live model tests against the configured provider (skips when none)
	@$(SCRIPTS)/require.sh java
	$(GRADLEW) :backend:liveTest

check-specs: ## Check that the specification documents still describe the code (feature 009)
	@$(SCRIPTS)/require.sh java node
	$(GRADLEW) specDrift

check-api: ## Check that the frontend's API types match the backend
	@$(SCRIPTS)/require.sh java node npm
	$(GRADLEW) :frontend:checkApi

##@ Maintenance

.PHONY: generate-api lint format golden

generate-api: ## Regenerate the frontend's API types from the backend
	@$(SCRIPTS)/require.sh java node npm
	$(GRADLEW) :backend:classes
	cd frontend && npm run generate:api

lint: ## Lint the frontend
	@$(SCRIPTS)/require.sh node npm
	cd frontend && npm run lint

format: ## Format the frontend with prettier (rewrites files)
	@$(SCRIPTS)/require.sh node npm
	cd frontend && npm run format

# --rerun: Gradle does not track GOLDEN_WRITE as a test input, so without it the task can be UP-TO-DATE and
# write nothing (R-008).
golden: ## Rewrite the golden run snapshots (asks first)
	@$(SCRIPTS)/require.sh java
	@$(SCRIPTS)/confirm.sh "the committed golden run snapshots in backend/src/test/resources/golden"
	GOLDEN_WRITE=1 $(GRADLEW) :backend:test --tests '*GoldenRunSnapshotTest' --rerun
