# Nexora Git Auth Broker

The Auth Broker is a deliberately small confidential component used by Nexora Git's GitHub App OAuth flow.

It is **not** a GitHub API proxy.

## Why it exists

A public Android APK cannot protect a GitHub App `client_secret`.

GitHub's web application flow supports PKCE, but exchanging the authorization code for a GitHub App user access token still requires the GitHub App client secret. The broker keeps that secret server-side while the Android app owns the PKCE verifier and OAuth state.

## Flow

```text
Android
  │ generates state + verifier/challenge
  ▼
github.com/login/oauth/authorize
  │
  ▼
GET https://auth.example.com/oauth/callback?code=...&state=...
  │ fixed redirect; no dynamic return URL
  ▼
nexoragit://oauth/callback?code=...&state=...
  │
  │ Android validates state
  │ sends code + original verifier
  ▼
POST /v1/oauth/exchange
  │ broker adds client_secret
  ▼
github.com/login/oauth/access_token
  │
  ▼
Android Keystore-backed encrypted token storage
```

## Endpoints

### `GET /health`

Returns broker health.

### `GET /oauth/callback`

GitHub App callback URL. It forwards only the expected OAuth response parameters to the fixed native callback URI configured on the server.

### `POST /v1/oauth/exchange`

Input:

```json
{
  "code": "authorization-code",
  "code_verifier": "pkce-verifier"
}
```

The broker adds the confidential GitHub App credentials and exchanges the code with GitHub.

### `POST /v1/oauth/refresh`

Input:

```json
{
  "refresh_token": "ghr_..."
}
```

Refresh tokens are rotated by GitHub; clients must replace both tokens with the returned bundle.

### `POST /v1/oauth/revoke`

Input:

```json
{
  "access_token": "ghu_..."
}
```

Revokes the individual user access token through GitHub's app authorization API.

## Environment

Copy `.env.example` to a non-committed `.env`:

```bash
cp .env.example .env
```

Required values:

```env
GITHUB_APP_CLIENT_ID=Iv1....
GITHUB_APP_CLIENT_SECRET=...
GITHUB_CALLBACK_URL=https://auth.example.com/oauth/callback
APP_CALLBACK_URI=nexoragit://oauth/callback
PORT=8080
```

The GitHub App callback setting must contain exactly the same `GITHUB_CALLBACK_URL`.

## Docker Compose

```bash
docker compose up -d --build
```

The service binds only to:

```text
127.0.0.1:8080
```

Place Nginx/Caddy/another TLS reverse proxy in front of it.

## Nginx example

```nginx
server {
    listen 443 ssl http2;
    server_name auth.example.com;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

Use a valid TLS certificate. Do not expose the broker over plain HTTP.

## Security properties

- client secret exists only in the broker environment;
- request bodies are never logged by the application;
- OAuth callback destination is fixed server-side;
- JSON bodies are size-limited;
- PKCE verifier format is validated;
- token prefixes are validated for refresh/revoke operations;
- responses use `Cache-Control: no-store`;
- per-IP rate limiting is enabled;
- container runs non-root with a read-only filesystem;
- no general-purpose GitHub proxy endpoint exists.

## CI

`Auth Broker CI` runs:

```text
gofmt check
go vet ./...
go test ./...
go build ./...
```
