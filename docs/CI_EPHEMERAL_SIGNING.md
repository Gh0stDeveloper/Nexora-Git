# Preproduction Android signing — ephemeral CI vs. permanent production identity

**Current decision (2026-10-08):** continue development without a purchased/qualified VPS. Real HTTPS domain checks, owner-captured Android screenshots, real-device QA, Play Console and F-Droid publication remain explicit OPEN release gates. Development builds can be validated without fabricating those records.

## 1. Temporary signing managed by GitHub Actions

[Ephemeral Signed Android CI](../.github/workflows/ci-ephemeral-signing.yml) runs on relevant pull requests to `main` or manually after the workflow exists on the repository default branch.

- Creates a random 256-bit password and a new PKCS#12 RSA-3072 self-signed key **inside that run's private runner directory**. It is never checked into Git or stored in repository/environment secrets, and is discarded with the runner.
- Builds **signed** APK/AAB, runs the Android unit tests and release lint, cryptographically verifies signature and APK/AAB certificate parity, and checks the APK application ID.
- Builds with `NEXORA_CI_EPHEMERAL_SIGNING=true`, which changes the installation package from production `com.nexora.git` to **`com.nexora.git.ci`** and adds the version suffix `-ci`. Thus it cannot overwrite or be mistaken for an in-place upgrade of the production package.
- Uses a nonfunctional `.invalid` OAuth hostname and CI-only GitHub App ID. **GitHub login and production services are intentionally unavailable in these artifacts.**
- Uploads a diagnostic artifact named `NexoraGit-EPHEMERAL-NOT-FOR-PRODUCTION-<run id>` with APK, AAB, SHA256SUMS and a warning/fingerprint manifest. It expires after 3 days. **Never upload these as a GitHub Release, distribute them through the official website or Play, or use them as the public first install.**
- Does **not** access the `production` environment or any persistent signing secret. Fork PRs receive no release credentials. The temporary key is not a signing identity to preserve between runs.

The official [Signed Release](../.github/workflows/release.yml) still requires its protected `production` GitHub environment and **all seven real secrets**; it rejects the ephemeral flag and ensures the published APK is `com.nexora.git`. It is not bypassed while the VPS is unavailable.

A green signing smoke CI proves the build and signing pipeline can work with a throwaway certificate. It does **not** prove a permanent signer, Android install/update parity between machines, real login, website or production readiness.

## 2. Permanent signing when the production VPS exists

**Single authoritative identity:** let the VPS installer/Signing Vault create the permanent release/upload keystore **once**. Do not generate a second production key in GitHub; it would result in a different fingerprint and break direct APK updates.

1. Provision VPS, official HTTPS origin, Nginx and Auth Broker; configure the GitHub App. Verify the real HTTPS health, callback, Android App Links and /privacy endpoints.
2. Initialize and verify the persistent Signing Vault using the documented `nexora-git android signing` command family. Save an **encrypted, off-server recovery backup** with its passphrase separated from the archive. Keep the keystore out of Git.
3. On the VPS, authenticate the GitHub CLI with limited administration rights and execute `sudo nexora-git github secrets apply` to populate the **`production` environment**, or export a protected bundle with `sudo nexora-git github secrets export` for manually adding values. The scripts mirror the same keystore bytes/alias/password, GitHub client ID and HTTPS callback URLs.
4. Verify the seven `production` secret names are present (not the plaintext values): `NEXORA_GITHUB_CLIENT_ID`, `NEXORA_AUTH_BROKER_BASE_URL`, `NEXORA_GITHUB_CALLBACK_URL`, `NEXORA_SIGNING_KEYSTORE_BASE64`, `NEXORA_SIGNING_STORE_PASSWORD`, `NEXORA_SIGNING_KEY_ALIAS`, `NEXORA_SIGNING_KEY_PASSWORD`. The confidential **GitHub App Client Secret belongs only to the VPS Auth Broker**.
5. Require release build output from VPS and GitHub Actions to have the **same package ID** `com.nexora.git`, the **same SHA-256 signing certificate** and compatible versionCode. The APK byte SHA-256 need not be identical across nonreproducible builds; if it is expected to be identical, test that separately.
6. Only then qualify a signed RC, Play upload key/App Signing separation, public `/verify` and production downloads. **After users install the first official APK, do not regenerate or silently rotate the signer.**

Secrets and the permanent keystore never belong in source files or Android BuildConfig. Use protected GitHub environments and keep publication tied to protected `main` tags. A GitHub Actions run is not a substitute for the Signing Vault disaster-recovery backup.

## 3. Still intentionally pending

- VPS real network, health/TLS/header/SEO and callback verification (issue #46).
- Physical Android device/emulator instrumentation and eight manually captured product screenshots (issue #47).
- Google Play graphics, Data safety approval and Play App Signing identity (issue #49).
- F-Droid full Gradle/Maven + native offline reproducibility (issue #50).
- S.18 signed release-candidate matrix, accessibility and stable-promotion gates.

The relevant PR can merge as a **development/CI implementation** after passing required checks, with public release remaining **NO-GO**. Never mark these external acceptance gates complete from workflow YAML or fake screenshots alone.
