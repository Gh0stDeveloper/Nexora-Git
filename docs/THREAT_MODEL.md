# Nexora Git Threat Model

> **Current reference:** This document describes the implemented Nexora Git system. See the [Documentation Hub](README.md) for navigation.

## Scope

Covers the Android client, authentication flow, local Git workspace, GitHub API communication and minimal Auth Broker.

## Protected assets

- GitHub access/refresh tokens
- private repository source code
- local uncommitted changes
- Git identities
- GitHub App confidential credentials
- repository write permissions
- release-signing material

## Trust boundaries

```text
User
 ↓
Android UI/process
 ↓
Keystore / local storage
 ↓
Git engine (JNI/native)
 ↓
Network boundary
 ├─ GitHub
 └─ optional auth broker
```

SAF document providers are an additional storage boundary.

## Threats and controls

### Credential phishing

Control: no custom GitHub credential form; use official GitHub web authorization and validate the expected GitHub host.

### OAuth callback injection / CSRF

Controls: random `state`, PKCE, single-use pending login sessions, strict callback validation and expiry.

### Embedded client secret extraction

Control: no confidential GitHub secret inside the APK; use a minimal auth broker where confidential exchange is required.

### Local token theft

Controls: Keystore-backed encryption, no plain SharedPreferences, no token logs/analytics and secure logout cleanup.

### Token leak through Git remote URL

Controls: persist clean HTTPS remotes and supply credentials through libgit2 callbacks.

### Malicious repository content

Controls: canonicalize paths, never write outside workspace roots, bound/stream large input and never auto-execute repository scripts.

### Accidental secret publication

Controls: pre-push/import risk scanner, .gitignore awareness and explicit warnings for credential-like files.

### Destructive Git actions

Controls: clearly separate local/remote effects, confirm discard/reset/force push/delete and never silently overwrite conflicts.

### Compromised native dependency

Controls: pin/review libgit2 versions, monitor security fixes and keep the JNI surface minimal.

### CI supply-chain compromise

Controls: minimal workflow permissions, no secrets for untrusted PRs, dependency review and pin critical third-party actions before stable release.

## Security invariants

1. GitHub credentials are never collected by Nexora Git.
2. Confidential app secrets never ship in the APK.
3. Tokens are never stored in Git URLs.
4. Repository content is treated as untrusted input.
5. Destructive operations are explicit.
6. Native errors cannot leak credentials.
7. Production TLS verification is never bypassed.

## Revisit this model when adding

- auth broker
- Git LFS
- archive extraction
- terminal/script execution
- Codespaces
- plugins
- AI integrations
- release signing

---

[← Documentation hub](README.md)
