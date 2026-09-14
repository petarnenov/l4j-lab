# Contract: the deliberately malformed requests

Three, fixed in code, each sendable in one action (FR-011a). Not a request editor — that is `curl`,
and the spec says so.

Every exchange produced here is flagged `deliberate: true` and labelled in the log, so a refusal is
never read as a fault in the system (spec edge case). Each is sent as the currently chosen principal,
to the currently chosen target, with a valid token: the point is the *protocol* refusal, and an
invalid token would produce a 401 before the server reached the check being demonstrated.

Codes and statuses below come from the error mapping table in
`specs/007-mcp-billing-server/contracts/mcp-protocol.md`, which is their authority.

---

## 1. Header disagreeing with the body

**What is wrong**: `Mcp-Name` names a different tool than `params.name` does.

**Sent**: a well-formed `tools/call` for `search_billing_runs`, with every required header present
and correct except `Mcp-Name: get_run_failures`.

**Expected**: HTTP `400`, JSON-RPC `-32020`.

**What it demonstrates**: the revision's rule that routing headers must mirror the body, and that the
server checks rather than trusts. This is a **protocol** failure — the tool was never reached, so
there is no `isError` anywhere in the answer. Pairing it with an ordinary tool failure on the same
screen is the clearest way to see the difference FR-011 is about.

---

## 2. A version the server does not implement

**What is wrong**: both the header and `_meta` announce `2025-06-18`.

**Sent**: a `tools/list` with `MCP-Protocol-Version: 2025-06-18` and
`params._meta["io.modelcontextprotocol/protocolVersion"]: "2025-06-18"` — consistent with each other,
so the refusal is unambiguously about the version and not about mirroring.

**Expected**: HTTP `400`, JSON-RPC `-32022`, with `data.supported` and `data.requested`.

**What it demonstrates**: that a request carries its own protocol version, and that a server refusing
one says which it does support. The console shows `data.supported` prominently, which is the spec's
edge case "an unsupported protocol version must show the versions the server does support".

**Why the header and the body agree here**: making them disagree would produce `-32020` first, and
the case would silently demonstrate the previous one instead.

---

## 3. A required header omitted

**What is wrong**: `Mcp-Method` is absent.

**Sent**: a well-formed `tools/list` body with `MCP-Protocol-Version`, `Content-Type`, `Accept`, and
`Authorization` all present, and no `Mcp-Method`.

**Expected**: HTTP `400`, JSON-RPC `-32020`.

**What it demonstrates**: that the header set is required, not advisory — and, next to case 1, that
"missing" and "disagreeing" are the same refusal, which is worth seeing rather than assuming.

---

## What the console does with each

1. Shows the label and a sentence naming what is wrong, **before** sending.
2. Sends, in one action.
3. Renders the exchange through the protocol-failure path: message, code, and `data` when present.
4. Keeps the flag visible in the log, so scrolling back does not lose the context.

## What is deliberately not in the catalogue

- **`-32021`** (client capability missing for what the server must send). Reachable by calling
  `post_fee_adjustment` without declaring `elicitation`, but the console declares it and honours it
  — withholding it would mean a second client-capability code path for a console asked to stay
  light. Recorded as considered, in [research.md R-008](../research.md).
- **`-32700`** (malformed JSON) and **`-32601`** (unknown method). Both refusals of a request no real
  client would build; FR-011a names three, and these add no distinction the three do not already show.
- Anything requiring a bad token. That is a transport failure, not a protocol one, and 007's own
  suite covers it.
