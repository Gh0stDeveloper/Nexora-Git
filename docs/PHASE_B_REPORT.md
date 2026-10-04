# Phase B — GitHub Authentication Report

Date: 2026-10-04

## Status

**Repository implementation complete and CI validated.**

Production authentication still requires two external deployment operations:

1. registering/configuring the real **Nexora Git GitHub App**;
2. deploying the confidential **Auth Broker** with the GitHub App client secret.

Those secrets are intentionally absent from the public repository and Android APK.

## Delivered

### Official GitHub authorization

Nexora Git now uses:

```text
GitHub App
+
OAuth Authorization Code flow
+
PKCE S256
+
cryptographically random state
```

The Android app never collects GitHub passwords, passkeys, 2FA secrets or recovery codes.

Authorization opens the official GitHub page through Android Custom Tabs.

### Redirect flow

```text
Android
  │ creates state + verifier + S256 challenge
  ▼
github.com/login/oauth/authorize
  ▼
HTTPS Auth Broker callback
  ▼
fixed nexoragit://oauth/callback
  ▼
Android validates state + age
  ▼
Android sends code + original verifier to broker
  ▼
broker adds GitHub App client secret
  ▼
GitHub token endpoint
```

The broker never accepts a caller-controlled return destination.

### Secure local storage

Sensitive session material is protected as:

```text
Android Keystore
       ↓
non-exportable AES key
       ↓
AES-GCM
       ↓
encrypted local ciphertext
```

Encrypted values include:

- access tokens;
- refresh tokens;
- pending OAuth state;
- pending PKCE verifier.

Room stores only non-secret account metadata.

DataStore stores only the active GitHub account ID.

### Account identity

The durable local account key is GitHub's numeric user ID.

The username/login remains display metadata so account identity does not depend on a renameable handle.

### Token lifecycle

The implementation supports:

- access-token expiry timestamps;
- five-minute proactive refresh window;
- serialized refresh operations with a mutex;
- complete access + refresh bundle replacement after rotation;
- refresh-token expiry handling;
- reauthentication when refresh is no longer possible.

### Multiple accounts

The app supports the session foundation for:

- adding another GitHub account;
- storing separate encrypted credentials per account;
- selecting an active account;
- switching accounts;
- signing out one account while preserving others.

### Logout and revocation

Logout:

1. attempts server-side revocation through the Auth Broker;
2. deletes the account's encrypted local credentials;
3. removes account metadata;
4. selects another saved account when available.

Local cleanup does not depend on remote revocation succeeding.

## Auth Broker

The repository now contains a minimal Go service under `auth-broker/`.

Endpoints:

```text
GET  /health
GET  /oauth/callback
POST /v1/oauth/exchange
POST /v1/oauth/refresh
POST /v1/oauth/revoke
```

The broker deliberately does **not** proxy arbitrary GitHub API requests.

Security controls include:

- HTTPS-only production callback configuration;
- fixed native callback target;
- request-size limits;
- PKCE verifier validation;
- token-prefix validation for refresh/revoke operations;
- no-store response headers;
- server timeouts;
- per-IP request limiting;
- non-root distroless container;
- read-only filesystem in Docker Compose;
- no Linux capabilities in the Compose service.

## Tests

Android authentication coverage includes:

- RFC 7636 PKCE reference challenge;
- generated verifier/challenge format;
- random OAuth state;
- valid state acceptance;
- state mismatch rejection;
- authorization expiry rejection;
- invalid future timestamp rejection;
- unconfigured-build sign-in UI state.

Auth Broker coverage includes:

- fixed callback forwarding;
- PKCE verifier forwarding;
- confidential client-secret use without returning the secret to Android;
- invalid verifier rejection;
- no-cache security headers.

## CI validation

### Android CI

```text
Run: 37193401684
Head SHA: 82f25583d5f4ea82fdc1dbad1f1e5f5969ed2d5b
Conclusion: success
```

Validated:

- debug APK build;
- Android instrumentation-test APK compilation;
- JVM unit tests;
- Android lint;
- Gradle Wrapper validation;
- debug APK artifact upload.

Artifact:

```text
Name: NexoraGit-debug
Artifact ID: 11299842604
Size: 20,434,354 bytes
SHA-256: bc95c7dd49d13552046ea1410c47c6cb5dbb53ba2aceffeca646ae69ca479c09
```

### Auth Broker CI

```text
Run: 37193021373
Conclusion: success
```

Validated:

- gofmt;
- go vet;
- Go tests;
- Go build.

### Foundation CI

The final Android-fix SHA also passed Foundation CI.

## External production prerequisites

Before real users can authenticate:

- create the GitHub App according to `GITHUB_APP.md`;
- enable expiring user access tokens;
- configure the exact HTTPS callback;
- deploy `auth-broker/` behind valid TLS;
- provide the broker with the client secret through server-side secret management;
- configure Android with only the public client ID and broker/callback URLs;
- execute live login, refresh and revocation tests against the production GitHub App.

## Exit criteria

All repository-side Phase B requirements are implemented.

The next development phase is **Phase C — GitHub Platform Layer**.
