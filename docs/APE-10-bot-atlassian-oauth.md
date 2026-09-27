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
3. **Redirect URI fixed allowlist** (configured HTTPS + optional localhost for bootstrap).
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

Also: `ATLASSIAN_BASE_URL`, `ATLASSIAN_CLOUD_ID`, `ATLASSIAN_DEFAULT_PROJECT`
(defaults: `https://androidplay.atlassian.net`, `5dc5cb59-3451-414c-9239-406e9ae97a96`, `APE`).

## Bot routes

| Method | Path | Behavior |
|---|---|---|
| GET | `/bot/atlassian/health` | Config + token freshness (no secrets) |
| GET | `/bot/atlassian/myself` | Proxy `GET /rest/api/3/myself` |
| GET | `/bot/atlassian/project` | Proxy default project (APE) |
| GET | `/bot/atlassian/project/{key}` | Proxy project by key |

## Admin bootstrap (one-time)

| Method | Path | Behavior |
|---|---|---|
| GET | `/admin/atlassian/oauth/start` | Redirect to Atlassian authorize (allowlisted URI only) |
| GET | `/admin/atlassian/oauth/callback` | Code exchange; stores tokens in memory |

After bootstrap, persist the rotated refresh token into Secret Manager /
`ATLASSIAN_OAUTH_REFRESH_TOKEN` for production restarts.

## Key types

- `config.AtlassianOAuthConfig`
- `data.atlassian.AtlassianTokenStore`
- `data.atlassian.AtlassianOAuthClient`
- `data.atlassian.AtlassianApiClient`
- `data.atlassian.AtlassianLogRedactor`
- `bose.ankush.route.botAtlassianRoute`

## Tests

```bash
./gradlew test --tests '*Atlassian*'
```
