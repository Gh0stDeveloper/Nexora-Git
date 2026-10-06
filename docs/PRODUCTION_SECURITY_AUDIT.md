# Production Security Audit

> **Current reference:** This document describes the implemented Nexora Git system. See the [Documentation Hub](README.md) for navigation.

## Result

Repository-level production security controls are implemented. External credential provisioning and deployment are operational launch requirements rather than application implementation gaps.

## Findings remediated

### Application backup

Previous state: application backup was enabled even though Nexora Git can hold encrypted tokens, Room metadata and private source workspaces.

Remediation:

- `android:allowBackup="false"`;
- cloud/device-transfer exclusion rules retained as defense in depth.

### Network policy

- cleartext traffic is disabled;
- production trusts system certificate authorities only;
- user-installed CAs are accepted only through Android `debug-overrides`;
- release OkHttp logging remains `NONE`;
- Authorization/Cookie headers are redacted even in debug logging.

### Credential storage

- OAuth tokens remain encrypted by Android Keystore AES-GCM;
- Git credentials are injected into libgit2 callbacks instead of persisted in remote URLs;
- release signing material is loaded only from environment/Gradle properties.

### Supply chain

- critical native dependencies are pinned to immutable commits;
- CI actions used by production workflows are pinned to immutable Git commits;
- CodeQL analyzes Java/Kotlin;
- CI scans for common committed secret forms;
- release publication requires a tag contained in `main`.

### Release integrity

- APK signature is verified before publication;
- AAB/APK are built from the release variant with R8/resource shrinking;
- SHA-256 checksums are published beside artifacts;
- native symbols are distributed separately.

## Residual risks

- compromised Android OS/root can bypass application-level secrecy;
- GitHub, the configured Auth Broker and Android document providers remain external trust boundaries;
- Tree-sitter/libgit2 parsers process untrusted repository content and must continue receiving security updates;
- production key custody is operational and cannot be solved in source control.

No credential, source-code telemetry or trust-all TLS mechanism was introduced.

---

[← Documentation hub](README.md)
