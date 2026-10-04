# Security Policy

## Supported versions

Nexora Git is currently pre-release. Security fixes apply to the active development line and latest published release once releases begin.

## Reporting a vulnerability

Do not publish working exploits, access tokens, private repository data or other sensitive information in a public issue.

Use GitHub private vulnerability reporting when enabled. If it is not enabled yet, open only a minimal public issue indicating that a private security report is available, without sensitive details.

## High-priority scope

- OAuth/PKCE bypasses
- token disclosure
- callback/deep-link injection
- credential leakage through Git
- path traversal
- unauthorized repository writes
- security-relevant native-memory issues
- release/signing compromise
- secrets exposed in logs or artifacts

## Architecture references

- `docs/SECURITY.md`
- `docs/THREAT_MODEL.md`
- `docs/AUTH.md`
- `docs/GIT_ENGINE.md`
