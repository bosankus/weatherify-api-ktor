# APE-10: Bot Atlassian OAuth (middleware-owned)

## Purpose

Wire OAuth 2.0 (3LO) for a **shared Atlassian service account** so bots can act against
[androidplay.atlassian.net](https://androidplay.atlassian.net) / project **APE** without ever
holding Atlassian access tokens.

## Architecture locks

1. **Middleware owns ALL Atlassian HTTP.** Bots call backend `/bot/atlassian/...` only.
   Access tokens never leave the API process.
2. **Atomic refresh_token rotation** with a single-flight mutex so concurrent refreshes cannot
   invalidate tokens.
3. **Redirect URI fixed allowlist** (configured HTTPS; localhost only if `ATLASSIAN_OAUTH_LOCALHOST_REDIRECT` is explicitly set).
4. **Redact** tokens, `Authorization` headers, and refresh bodies from logs/metrics/errors.
5. **One config object** (`AtlassianOAuthConfig`) for site URL + cloudId + project + OAuth endpoints.
6. **Backoff** on 429/5xx; refresh **only** on classified auth failures (401 + auth-shaped body).

## Scopes

`read:jira-work` `write:jira-work` `offline_access`

## Secrets / env

| Secret key | Env var | Purpose |
|---|---|---|
| `atlassian-oauth-client-id` | `ATLASSIAN_OAUTH_CLIENT_ID` | OAuth client ID |
| `atlassian-oauth-client-secret` | `ATLASSIAN_OAUTH_CLIENT_SECRET` | OAuth client secret |
| `atlassian-oauth-redirect-uri` | `ATLASSIAN_OAUTH_REDIRECT_URI` | Allowlisted HTTPS callback |
| `atlassian-oauth-refresh-token` | `ATLASSIAN_OAUTH_REFRESH_TOKEN` | Shared refresh token |
| `bot-atlassian-shared-secret` | `BOT_ATLASSIAN_SHARED_SECRET` | Caller auth for `/bot/atlassian/*` |

Also: `ATLASSIAN_BASE_URL`, `ATLASSIAN_CLOUD_ID`, `ATLASSIAN_DEFAULT_PROJECT`
(defaults: `https://androidplay.atlassian.net`, `5dc5cb59-3451-414c-9239-406e9ae97a96`, `APE`).

## Bot routes

Require `Authorization: Bearer <BOT_ATLASSIAN_SHARED_SECRET>` or `X-Bot-Token: <secret>` (401 otherwise).
Blank and the public example value `dummy_bot_atlassian_shared_secret` are rejected;
the gate fails closed when Secret Manager or environment configuration is missing.

| Method | Path | Behavior |
|---|---|---|
| GET | `/bot/atlassian/health` | Config + token freshness + `refreshTokenPersistOk` (no secrets) |
| GET | `/bot/atlassian/myself` | Proxy `GET /rest/api/3/myself` |
| GET | `/bot/atlassian/project` | Proxy default project (APE) |
| GET | `/bot/atlassian/project/{key}` | Proxy project by key (**case-insensitive** match to default; else 403) |

## Admin bootstrap (one-time)

Require admin JWT (`getAuthenticatedAdminOrRespond`) on `/start` only. For the browser
bootstrap runbook, use an authenticated browser session with the `jwt_token` cookie on the
API origin when opening `/admin/atlassian/oauth/start`. The Atlassian cross-site redirect
will not carry a custom `Authorization: Bearer` header, and a `SameSite=Strict` cookie is
not expected on that redirect, so `/callback` deliberately does not require the admin JWT.
Instead, its single-use, 10-minute in-process `state` is the callback CSRF check.

1. Sign in as an admin so the browser has the `jwt_token` cookie.
2. In that same browser session, open `/admin/atlassian/oauth/start`.
3. Approve access at Atlassian; the callback validates and consumes the one-time `state`.
4. Confirm `/bot/atlassian/health` with the bot shared secret and check token health.

| Method | Path | Behavior |
|---|---|---|
| GET | `/admin/atlassian/oauth/start` | Issues single-use `state`; redirect to Atlassian authorize (allowlisted URI only) |
| GET | `/admin/atlassian/oauth/callback` | No JWT gate; verifies+consumes single-use `state`; code exchange; stores tokens in memory; persists rotated refresh to SM |

Rotated refresh tokens are written to Secret Manager key `atlassian-oauth-refresh-token`
(and master `app-secrets` when available). SM write failure does not fail the request but sets
`refreshTokenPersistOk=false` on health; if the write is unavailable, the rotated token is
only in memory and will be lost on process restart.

OAuth `state` is held in a 10-minute in-process store. This supports a single running
instance only; multiple instances require session affinity or a durable shared state store.
The bot shared secret is resolved when the bot routes are registered, so rotating it requires
a process restart before the new value is accepted.

## Pending Ankush decisions

| Topic | Current provisional default | Notes |
|---|---|---|
| Bot caller auth scheme | Bearer or `X-Bot-Token` shared secret | Final scheme TBD (API key header name, rotation) |
| OAuth bootstrap `state` TTL | 10 minutes in-process | Multi-instance / durable state store TBD |
| SM write scope | `atlassian-oauth-refresh-token` + best-effort master `app-secrets` | Confirm whether master JSON patch is desired |
| SM Writer IAM | Not granted yet | Need Secret Version Adder (ideally Manager) on runtime SA |

## Secret Manager write IAM (Ankush pending)

Rotated refresh tokens are persisted via `secrets:addVersion` on
`atlassian-oauth-refresh-token` (and optionally master `app-secrets`). The Cloud Run
runtime service account needs Secret Manager **Secret Version Adder** (and ideally
**Secret Version Manager** / updater) on those secrets. Until IAM is granted, writes
fail soft: in-memory tokens still work, health reports `refreshTokenPersistOk=false`,
and logs stay redacted (no token values).

Provisional bot caller auth uses a shared secret (`Authorization: Bearer` or
`X-Bot-Token`). Final scheme is an Ankush decision; documented here as provisional.

## Key types

- `config.AtlassianOAuthConfig`
- `data.atlassian.AtlassianTokenStore`
- `data.atlassian.AtlassianOAuthClient`
- `data.atlassian.AtlassianApiClient`
- `data.atlassian.AtlassianLogRedactor`
- `data.atlassian.AtlassianOAuthStateStore`
- `data.atlassian.AtlassianRefreshTokenPersister`
- `bose.ankush.route.botAtlassianRoute`

## Tests

```bash
./gradlew test --tests '*Atlassian*'
```
