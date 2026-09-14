# Contract: an empty result, and a refusal

Two answers a caller must be able to tell apart, and today cannot: one of them is a crash.

## An empty result

A search that matches nothing is a successful search.

```json
{ "runs": [], "total_match_count": 0, "truncated": false }
```

**At every level**:

| Layer | Rule |
|---|---|
| System of record | Serialises the empty collection. `{"totalCount":0}` with `items` omitted makes "nothing matched" and "malformed response" the same bytes. |
| MCP server | Treats a missing collection as an empty one anyway. Every one of these tools declares `openWorldHint: true`; a client of a system it does not control does not dereference what it did not put there. |
| Caller | Receives `resultType: "complete"`, `isError` absent, an empty array. |

**What must never happen**: an empty result reaching a caller as anything other than an empty result.
Today a date range with nothing in it returns HTTP 500 and a JSON-RPC code that appears nowhere in
feature 007's error table.

## A refusal

An entitlement decision is a **tool** failure carried by a successful response, as feature 007's error
table already says:

```json
{ "resultType": "complete", "isError": true,
  "content": [{ "type": "text", "text": "No access to that record." }] }
```

**And it says nothing about what exists.** A record the caller may not see and a record that does not
exist are answered identically. The pair would otherwise be a probe: ask for both, compare the
answers, learn what is there.

That rule is already implemented — `LegacyErrorTranslator` has one code path and a fixed vocabulary,
and its comment explains exactly this. Nothing here changes it. It is written down because the
finding that opened this feature was *reported* as a broken refusal, and it was not one.

## Telling them apart

| Situation | HTTP | `resultType` | `isError` | Content |
|---|---|---|---|---|
| Nothing matched | 200 | `complete` | absent | an empty collection |
| Not permitted | 200 | `complete` | `true` | one short sentence |
| Does not exist | 200 | `complete` | `true` | one short sentence, identical to the above |
| System of record unreachable | 200 | `complete` | `true` | "do not retry" |
| The request was malformed | 400 | — | — | a JSON-RPC error with a code from the table |

**Nothing produces a 500.** That is the whole content of this contract.

## An unexpected exception

Something will eventually throw where nobody expected it. When it does:

- The caller receives a refusal they can read, not an empty message and not a stack trace.
- The code is one the error table lists.
- What actually happened is logged, structured, server-side, where feature 007's SC-006 already
  requires it to stay.

Today an unexpected exception becomes a JSON-RPC error with an **empty message**, which the SDK then
rejects — so the caller learns the server's validator complained, and nothing else. A boundary that
loses the message is one bad exception away from leaking one instead.
