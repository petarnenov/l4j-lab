# Contract: local token issuer (development only)

Mints signed JWTs for both audiences from a **static, committed** RSA key pair so the whole
system runs offline (FR-025). The key is committed on purpose and is worthless: it exists so
that a fresh clone works with no setup. The README must say so where someone will read it
before wondering.

This service is not part of the deployed topology in any sense other than local
development, and the OAuth discovery flow is explicitly out of scope for this iteration.

**"Development only" is enforced, not merely stated** (`research.md` R-013). Every controller
below is annotated `@Requires(env = …)` for the development and `test-capture` environments.
In any other environment the beans do not exist and every path here returns `404` — including
the test levers, so no profile can mint a deliberately broken token by accident. A committed
key pair deserves a constraint, not a comment.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/dev/token` | Mint a token for a named fixture principal |
| `POST` | `/dev/exchange` | Exchange an `mcp-billing-server` token for a `legacy-billing-api` token |
| `GET` | `/.well-known/jwks.json` | Public key, so both services verify rather than share a secret |

### `POST /dev/token`

Request `{ "principal": "advisor-alpha-101", "audience": "mcp-billing-server" }`.

Response `{ "token": "<jwt>", "expiresInSeconds": 3600 }`.

Fixture principals, matching the seeded data and the acceptance scenarios:

| Name | user | firm | role | advisor_ids |
|---|---|---|---|---|
| `advisor-alpha-101` | `usr-101` | `firm-alpha` | `ADVISOR` | `["adv-101"]` |
| `advisor-alpha-102` | `usr-102` | `firm-alpha` | `ADVISOR` | `["adv-102"]` |
| `admin-alpha` | `usr-900` | `firm-alpha` | `FIRM_ADMIN` | `["adv-101","adv-102"]` |
| `ops-alpha` | `usr-901` | `firm-alpha` | `OPS` | `["adv-101","adv-102"]` |
| `readonly-alpha` | `usr-902` | `firm-alpha` | `READ_ONLY` | `["adv-101","adv-102"]` |
| `admin-beta` | `usr-800` | `firm-beta` | `FIRM_ADMIN` | `["adv-201"]` |

### `POST /dev/exchange`

Request `{ "subjectToken": "<mcp-audience jwt>", "targetAudience": "legacy-billing-api" }`.

The issuer validates `subjectToken` against audience `mcp-billing-server`, then mints a new
token for `legacy-billing-api` carrying the same `sub`, `firm_id`, `role`, and
`advisor_ids`. The subject token is **not** returned and **not** embedded in the new one.

Shaped after RFC 8693 so the move to a real authorization server is mechanical, without
implementing the discovery flow this iteration excludes.

## Test levers

| Ability | Why it exists |
|---|---|
| Mint with a wrong `aud` | FR-015 audience-mismatch tests |
| Mint already expired | FR-015 expiry tests |
| Mint signed by a different key | FR-015 bad-signature tests |
