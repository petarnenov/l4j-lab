# Contract: the console's user-facing surface

What a person must be able to see and do. Every row traces to a requirement; nothing here prescribes
a component beyond what the requirement needs.

## Reachability

| Rule | Requirement |
|---|---|
| Reached from the existing header navigation, alongside "New run" and "Previous runs" | FR-001 |
| Present only in the development build; absent from `vite build` output | FR-001a |
| Carries a visible development marker and an opening sentence saying what it is for | FR-002 |
| Existing pages unchanged in behaviour and appearance | FR-001, SC-007 |

## Regions

Each is a named landmark or region, so the page is navigable by keyboard and by screen reader in the
same way the existing pages are.

### 1. Stack state

| Must show | Requirement |
|---|---|
| When the proxy does not answer: that it does not, and `make mcp-up` as the command that starts it — never an empty page or a generic failure | FR-003, SC-006 |
| When it does answer: the server's `serverInfo`, its `supportedVersions`, and its declared capabilities | US1-1 |

### 2. Principal

| Must show | Requirement |
|---|---|
| A chooser over the six seeded principals | FR-004 |
| For the chosen one: user, firm, role, and permitted advisors, read from the minted token's claims | FR-004 |
| That the credential was obtained by the console — nothing to mint or paste | FR-005 |
| The bearer header rendered as a labelled redaction, not the token | R-009 |
| Which principal any displayed result was obtained as | FR-006 |

Switching principal must not re-run anything already on screen (FR-006).

### 3. Target

| Must show | Requirement |
|---|---|
| The shared entry point, always | FR-016 |
| The three replicas, selectable when reachable | FR-016 |
| When a replica is unreachable: that it is, why, and `make mcp-up-topology` | FR-017, SC-005 |
| Which target each result was obtained from | FR-016 |
| For the proxy: that it chose a replica and does not report which | R-011 |

### 4. Tools

| Must show | Requirement |
|---|---|
| The five tools in the server's order | FR-007 |
| Each tool's read-only, destructive, idempotent and open-world hints | FR-007, US1-1 |
| Each tool's `title` and `description` verbatim | US1-1 |
| A field per declared argument, built from `inputSchema` | FR-008, US1-2 |
| Which arguments are required | FR-009, US1-2 |
| Required arguments **first**, keeping the server's order within each group | SC-001, and F-001: the server does not preserve declared order, so the one field you must fill can arrive fifth of seven |
| The call disabled while a required argument is empty | FR-009 |
| An unrenderable schema keyword as a marked raw-JSON field, never dropped | R-003 |

### 5. Exchange

| Must show | Requirement |
|---|---|
| The structured result, readable | FR-010, US1-3 |
| Alongside it, the request and response exactly as they travelled | FR-010, SC-003 |
| A tool failure distinguished from a protocol failure, the latter with its code | FR-011, US2-3 |
| That a tool failure arrived as a successful response carrying an error flag | US2-3 |
| Elapsed time per call | Entity: Exchange |
| Without leaving the console or opening a developer tool | SC-003 |

### 6. Deliberate refusals

| Must show | Requirement |
|---|---|
| Three prepared malformed requests, each sendable in one action | FR-011a, SC-003a |
| Each marked deliberate, so its refusal is not read as a fault | spec edge case |
| The refusal, its code, and for the version case the versions the server supports | FR-011a, spec edge case |
| No free-form editing of headers or body | FR-011a |

### 7. Confirmation flow

| Must show | Requirement |
|---|---|
| The server's question verbatim | FR-012, US3-1 |
| That nothing has been applied, with no result panel presented | FR-012, US3-1 |
| Confirm and decline, both sending the state the server issued | FR-012, US3-4 |
| On applied: the identifier the billing system assigned | US3-2 |
| On applied: the fee before and after | FR-012a, US3-5 |
| On repeat: the original result, and that nothing happened a second time | US3-3 |
| On declined: that nothing was applied | US3-4 |
| No undo; instead `make mcp-reset`, with the one-sentence reason | FR-012b, US3-6 |

### 8. Long operations

| Must show | Requirement |
|---|---|
| The handle immediately, before any poll | FR-013, US4-1 |
| Progress as it changes, without a person polling by hand | FR-013 |
| Cancellation, while non-terminal | FR-014, US4-2 |
| A cancelled operation reaching a cancelled state | US4-2 |
| Each poll as its own exchange, so a poll on another replica is visible | US4-3 |

### 9. Paging

| Must show | Requirement |
|---|---|
| A next page fetched without anyone copying a cursor | FR-015 |
| That more results remain, using the server's own count and hint | FR-015 |
| Pages listed one under another, so absence of overlap is visible | SC-005, US4-4 |
| A refused or expired cursor as the server's tool error, not an empty page | spec edge case |

## Language and accessibility

- English, as the existing pages already use (spec Out of Scope).
- Both themes, keyboard-reachable controls, and named regions — the standard the existing pages are
  already held to, and the reason the console reuses antd rather than introducing its own controls.

## Out of this surface

Editing, saving, or replaying past exchanges; a history that survives a reload; free-form request
composition; any control that would administer a billing system rather than look at one.
