# Security Policy

Nexora Git handles source code, private repositories, GitHub authorization credentials and native Git operations. Security issues are treated as product issues, not only release-hardening concerns.

## Supported versions

Security fixes target the active `main` development line and the most recent supported stable release after publication.

Locally modified or unofficial builds may differ materially from the reviewed source and are not covered by a long-term support commitment.

## Reporting a vulnerability

Do **not** publish working exploits, access tokens, refresh tokens, private repository content, signing material or other sensitive information in a public issue.

Use GitHub private vulnerability reporting when available. If private reporting is temporarily unavailable, create only a minimal public issue stating that a private security report is available; do not include exploit details or secrets.

## High-priority scope

- OAuth/PKCE bypasses, replay or callback injection;
- access/refresh token disclosure;
- GitHub App client-secret exposure;
- credential leakage through Git remotes, logs or artifacts;
- path traversal, symlink or workspace-confinement bypass;
- unauthorized repository writes or destructive Git behavior;
- native memory-safety issues;
- Git LFS credential leakage;
- release-signing or CI supply-chain compromise;
- secrets exposed through Android backup;
- insecure network transport;
- Auth Broker/VPS secret or TLS compromise.

## Security invariants

Production code and releases must preserve these invariants:

1. The GitHub App client secret never ships in the Android APK.
2. Access and refresh tokens are encrypted with an Android Keystore-backed key.
3. GitHub credentials are not persisted in Git remote URLs or `.git/config`.
4. Git credentials are released only to eligible HTTPS GitHub hosts.
5. Android application backup is disabled for sensitive app data.
6. Cleartext production traffic is disabled.
7. Production HTTP logging is disabled.
8. Release signing secrets are supplied externally and never committed.
9. Third-party production CI actions are pinned to immutable revisions.
10. Release APK signatures are verified before publication.
11. Published release artifacts include SHA-256 checksums.
12. The Auth Broker exposes only its narrow OAuth API and keeps the client secret server-side.

## Security architecture

Read:

- [Application security](docs/SECURITY.md)
- [Threat model](docs/THREAT_MODEL.md)
- [Authentication](docs/AUTH.md)
- [Native Git engine](docs/GIT_ENGINE.md)
- [Production security audit](docs/PRODUCTION_SECURITY_AUDIT.md)
- [Release process](docs/RELEASE_PROCESS.md)
- [VPS installer and operations](docs/VPS_INSTALLER.md)

## Disclosure expectations

Please provide enough private detail to reproduce and assess the issue: affected component, build/commit, prerequisites, proof of impact and a minimal reproduction where safe.

Do not test against repositories, accounts or infrastructure you do not own or have explicit permission to assess.
