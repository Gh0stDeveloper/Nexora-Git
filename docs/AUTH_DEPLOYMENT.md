# GitHub Authentication Deployment Runbook

> **Current reference:** This document describes the implemented Nexora Git system. See the [Documentation Hub](README.md) for navigation.

## 1. Register the GitHub App

Create a GitHub App named **Nexora Git** under the intended owner account.

Use the least-privilege permission matrix in `GITHUB_APP.md`.

Enable user authorization and expiring user access tokens.

## 2. Configure callback

Production example:

```text
https://auth.example.com/oauth/callback
```

This exact URL must be configured in the GitHub App and supplied to both the broker and Android build as `GITHUB_CALLBACK_URL`.

The callback must not contain dynamic redirect parameters.

## 3. Deploy the Auth Broker

Server environment:

```env
GITHUB_APP_CLIENT_ID=Iv1....
GITHUB_APP_CLIENT_SECRET=...
GITHUB_CALLBACK_URL=https://auth.example.com/oauth/callback
APP_CALLBACK_URI=https://auth.example.com/oauth/android/callback
PORT=8080
```

Never expose `GITHUB_APP_CLIENT_SECRET` to Android, Gradle source, GitHub artifacts, logs or the public repository.

## 4. Configure the Android build

The Android build consumes only public/non-confidential values:

```text
NEXORA_GITHUB_CLIENT_ID
NEXORA_AUTH_BROKER_BASE_URL
NEXORA_GITHUB_CALLBACK_URL
```

These values are no longer emitted as `BuildConfig` string constants. During the native CMake configuration step they are encoded into a native `libnexoragit.so` using per-build split XOR masks and are decoded only at runtime through a registered JNI method.

For CI/VPS builds, keep using environment variables:

```bash
export NEXORA_GITHUB_CLIENT_ID="Iv1...."
export NEXORA_AUTH_BROKER_BASE_URL="https://auth.example.com"
export NEXORA_GITHUB_CALLBACK_URL="https://auth.example.com/oauth/callback"

./gradlew :app:assembleDebug
```

For local development, copy the repository example file:

```bash
cp nexora.local.properties.example nexora.local.properties
```

and edit only the three public Android values there. `nexora.local.properties` is gitignored.

The release keystore, store password, key alias and key password are **not accepted from this local runtime-config file**. Release signing remains environment/secret-store only.

> Native XOR encoding is obfuscation, not a cryptographic secret store. A determined reverse engineer can still recover public runtime values from a running application. The confidential GitHub App Client Secret must remain on the Auth Broker and must never be embedded in the APK, DEX, resources or native libraries.

The production site must publish `/.well-known/assetlinks.json` with package `com.nexora.git` and the SHA-256 fingerprint of the active release signing certificate. The VPS installer derives this public fingerprint from the Signing Vault and injects it into the website container.\n\n## 5. Expected authorization path

```text
Nexora Git
  → GitHub official authorization page
  → broker HTTPS callback
  → fixed nexoragit:// native callback
  → state validation
  → broker code exchange with PKCE verifier
  → /user identity lookup
  → Keystore-protected session
```

## 6. Multiple accounts

Adding another GitHub account repeats the authorization flow with `prompt=select_account`.

Account metadata is stored in Room. Access and refresh tokens are stored separately using Android Keystore-backed AES-GCM encryption.

## 7. Logout

Logout attempts remote token revocation through the broker and always removes the local encrypted session.

The broker uses GitHub's application-token revocation endpoint, which requires the confidential GitHub App credentials.

## 8. Production checklist

- [ ] GitHub App created.
- [ ] Exact HTTPS callback configured.
- [ ] Expiring user access tokens enabled.
- [ ] Least-privilege permissions reviewed.
- [ ] Broker DNS configured.
- [ ] TLS certificate active.
- [ ] Client secret stored only in server secret management/environment.
- [ ] Broker `/health` returns 200.
- [ ] Android build configured with client ID and broker URLs.
- [ ] Login tested with password + 2FA account.
- [ ] Login tested with passkey-capable account.
- [ ] Account switching tested.
- [ ] Refresh tested after forced/shortened expiry in a test environment.
- [ ] Logout/revocation tested.

---

[← Documentation hub](README.md)
