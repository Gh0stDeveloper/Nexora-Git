# Release Process

> **Current reference:** This document describes the implemented Nexora Git system. See the [Documentation Hub](README.md) for navigation.

## 1. External prerequisites

Create/configure:

- production GitHub App;
- HTTPS Auth Broker with the GitHub App client secret;
- Android release/upload keystore;
- GitHub `production` environment.

Required production secrets:

- `NEXORA_GITHUB_CLIENT_ID`
- `NEXORA_AUTH_BROKER_BASE_URL`
- `NEXORA_GITHUB_CALLBACK_URL`
- `NEXORA_SIGNING_KEYSTORE_BASE64`
- `NEXORA_SIGNING_STORE_PASSWORD`
- `NEXORA_SIGNING_KEY_ALIAS`
- `NEXORA_SIGNING_KEY_PASSWORD`

Never commit these values.

## 2. Merge gate

Before tagging, `main` must have green Android, Native Git, Auth Broker, Foundation, Production and CodeQL checks.

## 3. Tag

The stable workflow accepts strict semantic tags:

```text
vMAJOR.MINOR.PATCH
```

For the first stable build:

```text
v1.0.0
```

The tag must match `app/build.gradle.kts` `versionName` and its commit must be contained in `main`.

## 4. Automatic release

Pushing the tag triggers `Stable Release`.

It validates configuration, decodes the keystore into the runner's temporary directory, builds the signed APK/AAB, runs tests/lint, verifies the APK signature, enforces size budgets, generates SHA-256 checksums and publishes/updates the GitHub Release.

## 5. Play Store

Upload the generated AAB as the Play upload artifact. Keep the Play App Signing key and project upload key lifecycle separate according to Play Console guidance.

## 6. Key rotation/recovery

Store the keystore and passwords in a dedicated password/secret manager outside GitHub source history. Document operational ownership privately. A lost upload/release key cannot be reconstructed from this repository.

---

[← Documentation hub](README.md)
