# Public distribution signing identity — S.17.2

**State: implemented as a pipeline, awaiting the first real signed RC and independently verifiable certificate.**

## Which fingerprints are authoritative?

1. **GitHub Releases direct APK:** SHA-256 of the certificate embedded in the signed APK, produced by Android `apksigner`.
2. **GitHub Releases AAB:** JAR signer certificate on the upload AAB, checked against the direct APK and the VPS Signing Vault.
3. **Google Play APK:** after Play App Signing, Google can use a *distinct Play app-signing key*. The Android certificate on Play-delivered APKs may differ from the project's upload key. **Do not tell users that these certificates always match.**

Until a real signed RC exists, no fingerprint is published and no arbitrary SHA is committed.

## Automated evidence

The tagged release workflow verifies APK/AAB signing, then produces:

- `SIGNING-CERTIFICATE-SHA256.txt` — the **real** full 64-digit signer certificate fingerprint.
- `SIGNING-IDENTITY.json` — version, source commit, signer identity, APK/AAB filenames and hashes.
- `SHA256SUMS.txt` — checksums covering APK, AAB, SBOM and signing identity evidence.

The website's `latest.json` must include `signingCertificateSha256` from the pre-verified Signing Vault manifest and show it beside the APK SHA-256. The website refuses to advertise a release that lacks a valid certificate fingerprint.

The Signing Vault private key and passwords must never be placed in `latest.json`, GitHub assets, documentation or client code.

## User verification — direct APK

After downloading a signed APK and the official files:

```bash
sha256sum -c SHA256SUMS.txt
apksigner verify --verbose --print-certs NexoraGit-VERSION.apk
```

Read `Signer #1 certificate SHA-256 digest` and compare it with `SIGNING-CERTIFICATE-SHA256.txt`. The checksum file must be obtained from the same official signed release. A checksum stored on the same compromised host alone is not independent assurance; use the repository source-commit provenance and GitHub attestations where available.

On the VPS, an operator can see the vault's reference certificate without exposing secrets:

```bash
sudo nexora-git android signing fingerprint
```

For Play Store installation, compare the delivered APK certificate with the **Play App Signing** fingerprint from the owner's Play Console listing when available, not necessarily the AAB upload key.

## Release and publication gates

- [ ] First real signed RC has published GitHub Release evidence.
- [ ] APK/AAB fingerprints match the intended official Signing Vault.
- [ ] VPS site is deployed and displays both fingerprints (APK checksum and signing certificate).
- [ ] Owner confirms production signing identity and any Play App Signing vs upload-key difference.
- [ ] Revoke/quarantine compromised releases and document rotation process if the signing identity changes.

Related: [Release process](../RELEASE_PROCESS.md), [Store assets](ASSET_DELIVERY.md), [Website follow-up](https://github.com/Gh0stDeveloper/Nexora-Git/issues/46).
