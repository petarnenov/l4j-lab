# Development token issuer

Mints signed JWTs for the two audiences this system uses, so the whole stack runs offline with no
setup (FR-025). It exists because the MCP server must never forward the token it received: it
exchanges that token for one issued for the legacy API's audience, and something has to do the
minting.

## The key is generated, never stored

There is no key file in this repository. The issuer generates an RSA pair when it starts and keeps it
in memory for as long as it runs.

That works because **only this service ever holds the private key**. The MCP server and the legacy
API verify tokens against the public half, which they fetch from `/.well-known/jwks.json` — neither
has ever seen the other one. So nothing needs the key to survive a restart, and a fresh clone still
starts with one command, because there is no key to create first.

An earlier version committed the pair, reasoning that it protected nothing. True, and beside the
point: a real RSA private key in a public repository trips secret scanning, cannot be unpublished,
and invites reuse somewhere it would matter. Generating costs about a tenth of a second
(`research.md` R-020).

**The cost:** restarting the issuer invalidates every token minted before it. For a development stack
that is fine, and arguably better than a key that outlives its process.

**Development-only is enforced, not stated.** Every controller here carries `@Requires(env = ...)`
for the development and `test-capture` environments. Elsewhere the beans do not exist and every path
returns 404 — including the levers that mint deliberately broken tokens, so no production profile can
reach them by accident. `EnvironmentGatingTest` holds that property.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/dev/token` | Mint a token for a named fixture principal |
| `POST` | `/dev/exchange` | Exchange an `mcp-billing-server` token for a `legacy-billing-api` one |
| `GET` | `/.well-known/jwks.json` | The public key, so both services verify rather than share a secret |

Reachable through the MCP stack's single published port: the proxy routes `/dev/` and
`/.well-known/` here.

```bash
curl -s -X POST localhost:8877/dev/token \
  -H 'Content-Type: application/json' \
  -d '{"principal":"admin-alpha","audience":"mcp-billing-server"}'
```

Six fixture principals, matching the seeded data: `advisor-alpha-101`, `advisor-alpha-102`,
`admin-alpha`, `ops-alpha`, `readonly-alpha`, `admin-beta`. Full shape in
[`contracts/token-issuer.md`](../specs/007-mcp-billing-server/contracts/token-issuer.md).

## What this is not

Not an authorization server. The OAuth discovery flow — protected resource metadata, authorization
server metadata, Client ID Metadata Documents — is out of scope for this iteration. `/dev/exchange`
is shaped after RFC 8693 so replacing it with a real one is mechanical, but it implements none of the
discovery that a real one would need.
