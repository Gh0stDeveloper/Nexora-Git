# Authentication Architecture

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
