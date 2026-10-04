# Security Architecture

## Security goals

Nexora Git handles source code, private repositories and authorization credentials. Security is therefore a core product requirement, not a later hardening phase.

## Threat categories

The project must explicitly defend against:

- leaked OAuth tokens
- malicious deep links/callback injection
- authorization-code interception
- embedded client secrets
- token exposure in logs
- malicious repository content
- path traversal
- unsafe archive extraction
- accidental publication of secrets
- destructive Git operations
- compromised third-party dependencies
- insecure network transport
- stale/replayed authentication attempts

## Authentication

Required controls:

- GitHub App.
- OAuth web authorization.
- PKCE.
- cryptographically random state.
- strict redirect validation.
- single-use pending auth sessions.
- no GitHub password collection.

See `AUTH.md`.

## Secret storage

Use Android Keystore-backed protection for local sensitive material.

Do not place secrets in:

- source code
- XML resources
- assets
- BuildConfig constants committed to Git
- logs
- analytics
- crash reports
- plain SharedPreferences

## Network

- HTTPS only for GitHub/API/auth production communication.
- Standard Android certificate validation.
- Do not implement trust-all certificate managers.
- Do not disable hostname verification.
- Centralize authentication headers.
- Automatically redact secrets from network debugging logs.

## Logging redaction

At minimum redact values matching:

```text
Authorization
access_token
refresh_token
client_secret
password
private keys
session cookies
```

Release builds must not emit sensitive HTTP bodies by default.

## Git credentials

Git remotes stored on disk must not contain access tokens.

Use a temporary credential callback/provider during network Git operations.

## Project scanning

Before first publication, the application should warn users when common credentials or private-key file patterns are present.

This is a warning system, not a guarantee that every secret will be detected.

## Destructive operations

Require clear confirmation for actions such as:

- deleting a repository
- deleting a branch with unmerged work
- discard/reset of local modifications
- force push
- removing a workspace
- deleting downloaded/local project data

Destructive actions must explain whether the change is local, remote or both.

## Dependency security

CI should include:

- dependency update visibility
- secret scanning
- static analysis
- native dependency review
- reproducible/version-pinned critical native dependencies where practical

libgit2 and native dependencies must be kept current with security patches.

## Privacy

Telemetry should be optional.

Never send application analytics containing:

- source code
- private file contents
- tokens
- private repository names unless strictly required and disclosed
- commit content from private repositories

## Security reporting

The repository should eventually provide a root `SECURITY.md` containing the supported-version and vulnerability-reporting policy before the first public stable release.
