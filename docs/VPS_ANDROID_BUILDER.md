# VPS Android Build Service

> [Documentation hub](README.md) · [VPS installer](VPS_INSTALLER.md) · [Release process](RELEASE_PROCESS.md)

Nexora Git is being extended so the same Android application can be built both by GitHub Actions and by the self-hosted VPS. The design treats the VPS as a persistent build host rather than a disposable CI runner.

## Design invariants

- the Android signing identity is generated once and reused;
- GitHub Actions and the VPS must build the same application ID with the same public signing certificate;
- the Android SDK, NDK, CMake and Gradle caches live outside the Git checkout and survive repository updates;
- build work must run under a dedicated non-login service account rather than as root;
- signing secrets must never be committed to Git or printed by status/doctor commands;
- build logs and artifacts must remain inspectable after the SSH session ends;
- updates must be idempotent and must not redownload already-installed SDK components.

## Rollout

| Phase | Scope | State |
| --- | --- | --- |
| A | persistent Android toolchain, isolated builder user, storage layout, setup/status/doctor | implemented on the feature branch |
| B | persistent Signing Vault, one-time keystore generation, fingerprint, encrypted backup/restore guards | implemented on the feature branch |
| C | systemd background build worker, queue, cancellation and detached execution | planned |
| D | APK/AAB staging, logs, metadata, checksums, retention and diagnostics | planned |
| E | secure GitHub Actions secret export and signing/config parity verification | planned |
| F | update/autobuild integration, coalescing, disaster recovery and final hardening | planned |

The phases are intentionally layered. No phase is allowed to regenerate a signing identity merely because a source update occurs.

## Phase A — build-host foundation

The managed installer provisions or reuses:

| Component | Pin / location |
| --- | --- |
| JDK | OpenJDK 17 |
| Android Command Line Tools | revision 15859902, SHA-256 verified before extraction |
| Android platform | android-37.0 |
| Android Build Tools | 37.0.0 |
| Android NDK | 27.2.12479018 |
| CMake | 3.22.1 |
| SDK root | /opt/nexora-android-sdk |
| Gradle cache | /var/cache/nexora-git/gradle |
| persistent builder state | /var/lib/nexora-git/android |
| builder account | nexora-build |
| non-secret config | /etc/nexora-git/android-builder.conf |

The Command Line Tools archive is pinned to an immutable revision and expected SHA-256. Installation aborts if the downloaded bytes do not match the expected digest.

### Persistent layout

```text
/opt/nexora-android-sdk/
├── cmdline-tools/
├── platform-tools/
├── platforms/
├── build-tools/
├── ndk/
└── cmake/

/var/cache/nexora-git/
└── gradle/

/var/lib/nexora-git/android/
├── worker/
├── builds/
├── artifacts/
├── logs/
└── tmp/
```

The repository remains under `/opt/nexora-git`. Future build workers will create isolated workspaces under the persistent builder state instead of compiling directly in the managed deployment checkout.

## Idempotency

Running:

```bash
sudo nexora-git android setup
```

performs detection before installation. Existing JDK/SDK packages are reused by `sdkmanager`; the command-line tools archive is not downloaded when a working pinned installation already exists.

This is intentionally separate from repository source updates. Repeated application releases therefore do not reinstall Nginx, Docker, the Android SDK or the NDK.

## Diagnostics

```bash
sudo nexora-git android status
sudo nexora-git android doctor
sudo nexora-git android config
```

`status` is informational. `doctor` is strict and returns a failing exit status when the configured JDK, SDK, Build Tools, NDK, CMake, Gradle wrapper or persistent directories are missing.

`config` exposes only non-secret toolchain configuration. Signing credentials live behind the separate root-only Signing Vault described below.

## Security boundary

The installer creates the system account `nexora-build` with `/usr/sbin/nologin`. Build-owned mutable directories are writable by that account. The Android SDK itself is provisioned by root and reused read-only by future workers.

The Signing Vault does not live inside the Git repository, Gradle project or ordinary artifact directories. It uses a root-only directory and protected files so ordinary repository updates cannot replace the application identity.

## Phase B — Signing Vault

The installer now creates the Android release identity automatically after the build-host foundation is healthy.

The identity is stored outside Git:

```text
/var/lib/nexora-git/signing/
├── release.p12
├── secrets.env
├── identity.conf
├── certificate.pem
└── backups/
```

Security properties:

- the vault directory is mode `0700`;
- the keystore, secret file and identity metadata are mode `0600`;
- the public certificate is mode `0644` inside the root-only vault;
- passwords are generated from 32 random bytes and are never printed by status or doctor commands;
- the keystore uses PKCS#12 with RSA-4096 and SHA-256;
- the same release identity is reused on every installer/update run;
- an incomplete vault causes a hard failure instead of silent key regeneration;
- verification checks the keystore credentials, alias, certificate fingerprints, keystore SHA-256 and certificate lifetime.

The PKCS#12 store uses the same randomly generated store/key password because modern PKCS#12 providers do not reliably support separate key passwords. GitHub Actions can still receive the two required secret names with the same value during the later export phase.

### Signing commands

```bash
sudo nexora-git android signing status
sudo nexora-git android signing verify
sudo nexora-git android signing fingerprint
sudo nexora-git android signing certificate
sudo nexora-git android signing backup
sudo nexora-git android signing restore /secure/path/backup.nxbk
```

`fingerprint` exposes only public certificate fingerprints. `certificate` prints the public PEM certificate.

### Encrypted backup

`signing backup` asks for a backup passphrase through the controlling TTY, requires at least 16 characters, creates an internal checksum manifest, and encrypts the archive using AES-256-CBC with PBKDF2-SHA256 and 600,000 iterations. A SHA-256 file checksum is also created beside the encrypted backup.

Backups should be copied off the VPS and the backup passphrase stored separately.

Restore is deliberately conservative:

- archive paths and file types are allow-listed;
- internal SHA-256 checksums are verified;
- the embedded keystore must open with the embedded protected credentials;
- the certificate must match the recorded identity fingerprint;
- restoring the same identity is an idempotent no-op;
- restoring a different identity over an existing valid vault is refused;
- restoring over partial/corrupt local signing state is refused.

This prevents an update or operator mistake from silently changing the application signing identity.

## Why the VPS and GitHub builds can match

The Gradle project already reads production configuration and signing paths from environment variables. The final service will feed the VPS build the same public application configuration and the same signing identity that the release workflow receives through GitHub Secrets.

That produces two independent build locations with a single Android application identity.

---

[← VPS installer](VPS_INSTALLER.md)
