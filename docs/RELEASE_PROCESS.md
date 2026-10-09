# Release Process

> **Current reference:** This document describes the implemented Nexora Git system. See the [Documentation Hub](README.md) for navigation.

## 1. External prerequisites

Create/configure:

- production GitHub App;
- HTTPS Auth Broker with the GitHub App client secret;
- Android release/upload keystore (the VPS installer can create and preserve the authoritative Nexora Git release identity);
- GitHub `production` environment.

Required production secrets:

- `NEXORA_GITHUB_CLIENT_ID`
- `NEXORA_AUTH_BROKER_BASE_URL`
- `NEXORA_GITHUB_CALLBACK_URL`
- `NEXORA_SIGNING_KEYSTORE_BASE64`
- `NEXORA_SIGNING_STORE_PASSWORD`
- `NEXORA_SIGNING_KEY_ALIAS`
- `NEXORA_SIGNING_KEY_PASSWORD`

Required **non-secret** `production` environment variable: `NEXORA_SIGNING_CERT_SHA256`. The VPS command `sudo nexora-git github secrets apply` also writes this public signing-certificate fingerprint into GitHub Environment Variables. Official tags must match that pinned identity before release publication.

Never commit these values. On a managed VPS, use `sudo nexora-git github secrets export` for a protected manual bundle or authenticate GitHub CLI and run `sudo nexora-git github secrets apply` to synchronize the exact Signing Vault identity and domain-derived public URLs into the `production` environment. The GitHub App Client Secret is intentionally excluded because it belongs only to the Auth Broker.

## 1.1 Signing before VPS deployment

GitHub Actions can generate a **new throwaway PKCS#12 test keystore in each ephemeral signing CI run**. This produces separate `com.nexora.git.ci` APK/AAB diagnostic artifacts and **does not** satisfy the public release gate. The permanent `com.nexora.git` signer remains unavailable until the VPS Signing Vault is initialized and synchronized to protected GitHub `production` secrets. See [CI ephemeral signing](CI_EPHEMERAL_SIGNING.md).

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

It validates configuration, decodes the PKCS#12 keystore into the runner's temporary directory, builds the signed APK/AAB, runs tests/lint, verifies APK/AAB signatures and signing-certificate parity, requires the callback to equal the broker base URL plus `/oauth/callback`, enforces size budgets, generates SHA-256 checksums and publishes/updates the GitHub Release.

## 4.1 Public signing transparency

The signed APK and AAB in the same GitHub Release must be signed with the **same verified upload/release certificate**. After signing verification, the pipeline exports `SIGNING-CERTIFICATE-SHA256.txt` and `SIGNING-IDENTITY.json` (certificate SHA-256, version, full commit SHA, APK/AAB hashes). `SHA256SUMS.txt` covers these public evidence files as well as the release artifacts.

The VPS website's `latest.json` includes the verified `signingCertificateSha256` alongside the APK digest. The website displays both and rejects publication without valid signer metadata.

**Play App Signing distinction:** Google Play may re-sign Play-delivered APKs using Google's app-signing certificate, which differs from the APK/AAB upload certificate. Verify the Play fingerprint separately in Play Console. See [Signer identity guide](store/SIGNING_TRANSPARENCY.md).

## 5. Play Store

Upload the generated AAB as the Play upload artifact. Keep the Play App Signing key and project upload key lifecycle separate according to Play Console guidance.

## 6. Key rotation/recovery

The VPS Signing Vault is the authoritative self-hosted copy when that deployment model is used. Create an encrypted `nexora-git android signing backup`, copy it off-host, and store its passphrase separately. The later GitHub-export phase mirrors this same identity into GitHub Secrets; it must never generate a second release key. A lost release key cannot be reconstructed from this repository.

---

[← Documentation hub](README.md)
