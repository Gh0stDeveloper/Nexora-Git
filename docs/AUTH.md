# Authentication Architecture

> **Current reference:** This document describes the implemented Nexora Git system. See the [Documentation Hub](README.md) for navigation.

## Decision

Nexora Git uses:

**GitHub App + OAuth web authorization + PKCE**

This is the required authentication architecture for the application.

## Security boundary

Nexora Git must never collect:

- GitHub passwords.
- GitHub passkeys.
- 2FA secrets.
- Recovery codes.

The application launches the official GitHub authorization/login experience. Passwords, passkeys, 2FA and SSO remain entirely on GitHub-controlled authentication surfaces.

## High-level flow

```text
Nexora Git
   │
   ├── generate state
   ├── generate code_verifier
   └── derive code_challenge
   │
   ▼
GitHub authorization page
   │
   ▼
User authenticates on GitHub
   │
   ▼
Redirect callback
   │
   ├── verify state
   └── obtain authorization code
   │
   ▼
Token exchange
   │
   ▼
User access token
   │
   ▼
Encrypted local session
```

## PKCE

For every authorization attempt:

1. Generate a cryptographically random `code_verifier`.
2. Derive a SHA-256 `code_challenge`.
3. Generate a cryptographically random `state`.
4. Bind both values to the pending login session.
5. Send the challenge with the authorization request.
6. Validate returned `state`.
7. Use the original verifier during token exchange.
8. Destroy transient authorization material after completion/failure.

Never reuse PKCE verifier/state pairs.

## GitHub App

The GitHub App should request the smallest possible set of account/repository permissions.

Permissions should be introduced feature-by-feature rather than requesting broad write access at project start.

Examples of eventual repository permission areas include:

- Contents.
- Metadata.
- Issues.
- Pull requests.
- Actions.
- Workflows.
- Checks / commit statuses.
- Discussions.

Administrative permissions should only be requested if a concrete feature requires them.

## Client secret handling

The Android application is public software and cannot safely keep a permanent GitHub App client secret inside the APK.

Therefore:

- Never hardcode the client secret in Kotlin, resources, native libraries or BuildConfig.
- Never place it in the repository.
- Never assume obfuscation makes an embedded secret secure.

If the GitHub authorization/token flow requires confidential application credentials, use a minimal authentication broker.

```text
Android app
    ↓
GitHub authorization
    ↓
Redirect / authorization code
    ↓
Minimal Auth Broker
    ↓
GitHub token endpoint
```

The broker's responsibility must remain narrow:

- protect confidential application credentials
- perform/assist token exchange
- perform token refresh when required
- validate expected redirect/login context

It should not proxy normal GitHub REST/GraphQL traffic unless there is a specific security or platform reason.

## Token storage

Tokens must never be:

- logged
- stored in plain SharedPreferences
- committed to Git
- included in crash reports
- exposed to analytics
- copied into persistent Git remote URLs

Use Android Keystore-backed encryption for local session secrets.

## Multiple accounts

Authentication data must be isolated per account:

```text
AccountSession
├── accountId
├── login
├── encryptedAccessToken
├── encryptedRefreshToken
├── tokenExpiry
└── installation/context metadata
```

Switching accounts must also switch:

- GitHub API identity
- repository permissions
- Git identity preferences
- account-specific cache namespace

## Logout

Logout must:

1. remove local session tokens
2. clear sensitive in-memory state
3. clear account-specific pending auth state
4. optionally offer revocation/disconnect through the appropriate GitHub flow
5. leave local repositories intact unless the user explicitly asks to delete them

## Browser integration

Use a secure system browser/custom-tab style authorization experience and verified callback handling.

The callback handler must:

- validate scheme/host/path
- validate state
- reject unexpected or replayed authorization responses
- never trust arbitrary deep-link parameters

## Prohibited implementation

The following UI must not exist:

```text
GitHub email:
GitHub password:
GitHub passkey:
[ Sign in ]
```

If a user signs in with email/password or passkey, that interaction happens on GitHub's official page, not inside a Nexora Git credential form.


## Current implementation

The repository implements the authentication boundary with these concrete components:

```text
Android
├── AuthConfig
├── PkceGenerator
├── GitHubAuthorizationUrlFactory
├── OAuthCallbackParser
├── KeystoreCipher
├── SecureAuthStorage
├── AuthBrokerClient
├── GitHubIdentityClient
├── AuthSessionRepository
├── AuthCallbackBus
└── AuthViewModel

Auth Broker
├── /health
├── /oauth/callback
├── /v1/oauth/exchange
├── /v1/oauth/refresh
└── /v1/oauth/revoke
```

### Redirect topology

The production GitHub callback points to the broker, not directly to an arbitrary Android URL:

```text
GitHub
  → https://AUTH_HOST/oauth/callback
  → nexoragit://oauth/callback
  → Android validates state
  → Android sends code + code_verifier to broker
  → broker adds the client_secret
  → GitHub token endpoint
```

The native deep link can safely carry the short-lived authorization code because PKCE prevents the code from being exchanged without the original verifier retained by Nexora Git.

### Durable identity

Nexora Git stores GitHub's numeric user `id` as the durable account identifier. Login/handle is display metadata and is not used as the primary identity key.

### Local secret model

```text
Android Keystore
      ↓ protects AES key
AES-GCM
      ↓ encrypts
SharedPreferences ciphertext only
      ├── access token
      ├── refresh token
      ├── pending OAuth state
      └── pending PKCE verifier

Room
      └── non-secret account metadata

DataStore
      └── active account ID
```

### Expiration and rotation

When an access token is close to expiry, `AuthSessionRepository` serializes refresh with a mutex, sends the current refresh token to the broker and replaces the complete returned token bundle.

A refresh token that has already expired requires a new user authorization flow.

### Multiple accounts

Each GitHub numeric account ID owns a separate encrypted access/refresh token pair. The Profile surface supports switching accounts, adding another GitHub account and signing out the active account.

### Revocation

Logout performs best-effort remote token revocation through the broker and always clears local encrypted credentials. Remote revocation is intentionally performed by the broker because GitHub's application-token revocation API requires the GitHub App client secret.

### Build configuration

Android public configuration:

```text
NEXORA_GITHUB_CLIENT_ID
NEXORA_AUTH_BROKER_BASE_URL
NEXORA_GITHUB_CALLBACK_URL
```

These public values are compiled into the native `libnexoragit.so`, not `BuildConfig`. CMake generates per-build split-XOR encoded byte arrays; Kotlin obtains the decoded values only through `SecureRuntimeConfigNative` at runtime. This raises the cost of static string extraction but does not make public OAuth configuration secret.

Release signing credentials are never compiled into this library. They remain build-time-only inputs supplied by the VPS Signing Vault or GitHub Actions secret store.

Server-only confidential configuration:

```text
GITHUB_APP_CLIENT_ID
GITHUB_APP_CLIENT_SECRET
GITHUB_CALLBACK_URL
APP_CALLBACK_URI
```

See `AUTH_DEPLOYMENT.md` and `../auth-broker/README.md`.

---

[← Documentation hub](README.md)
