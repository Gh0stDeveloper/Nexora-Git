# Production Readiness

> **Current reference:** This document describes the implemented Nexora Git system. See the [Documentation Hub](README.md) for navigation.

Nexora Git includes the repository hardening and distribution controls required for a safe production release.

## Production gates

A release candidate must pass:

- Android CI;
- Native Git CI;
- Auth Broker CI;
- Foundation CI;
- Production CI;
- CodeQL;
- `lintRelease`;
- unit tests;
- release APK/AAB build;
- artifact-size budgets.

The release workflow then adds stronger gates: semantic tag validation, main-branch ancestry, production OAuth configuration, release signing, APK signature verification and SHA-256 checksums.

## External launch inputs

The repository intentionally does not contain or invent these values:

- production GitHub App client ID;
- deployed HTTPS Auth Broker URL and callback URL;
- GitHub App client secret on the broker;
- Android release/upload keystore;
- signing passwords;
- Play Console account and store graphics.

These are deployment credentials, not source artifacts.

## Version

First stable repository target:

- application ID: `com.nexora.git`
- versionName: `1.0.0`
- versionCode: `10000`
- min SDK: 26
- target SDK: 36

---

[← Documentation hub](README.md)
