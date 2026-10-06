# Security Policy

## Supported versions

Nexora Git is preparing the 1.0.x stable line. Once `v1.0.0` is published, security fixes target the latest 1.0.x release and the active `main` development line.

Pre-release and locally modified builds are not covered by a long-term support commitment.

## Reporting a vulnerability

Do **not** publish working exploits, access tokens, private repository data, signing material or other sensitive information in a public issue.

Use GitHub private vulnerability reporting when it is enabled for this repository. If private reporting is temporarily unavailable, open only a minimal public issue stating that a private security report is available; do not include exploit details or secrets.

## High-priority scope

- OAuth/PKCE bypasses or callback injection
- access/refresh token disclosure
- credential leakage through Git remotes, logs or artifacts
- path traversal or workspace confinement bypass
- unauthorized repository writes
- native memory-safety issues
- release-signing or CI supply-chain compromise
- secrets exposed through backups
- insecure network transport

## Release security invariants

Production releases must satisfy all of the following:

1. No release keystore or password is committed to Git.
2. Android backup is disabled for application data.
3. Cleartext network traffic is disabled.
4. Production HTTP logging is disabled.
5. Third-party GitHub Actions are pinned to immutable commit SHAs.
6. Release APK signatures are verified before publication.
7. Published artifacts include SHA-256 checksums.
8. The release tag matches the Android `versionName` and belongs to `main`.

## Architecture references

- `docs/SECURITY.md`
- `docs/THREAT_MODEL.md`
- `docs/AUTH.md`
- `docs/GIT_ENGINE.md`
- `docs/PRODUCTION_SECURITY_AUDIT.md`
- `docs/RELEASE_PROCESS.md`
