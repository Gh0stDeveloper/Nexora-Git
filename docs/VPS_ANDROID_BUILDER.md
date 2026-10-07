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
| C | systemd background build worker, persistent queue, immutable snapshots, cancellation and detached execution | implemented on the feature branch |
| D | APK/AAB staging, persistent logs, metadata, checksums, retention and diagnostics | implemented on the feature branch |
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

## Phase C — detached background build worker

Phase C is split into six internal delivery gates:

| Subphase | Scope |
| --- | --- |
| C1 | persistent FIFO queue and job state model |
| C2 | immutable Git snapshot per job |
| C3 | hardened systemd worker under the non-login build account |
| C4 | cancellation, crash/reboot recovery and single-build locking |
| C5 | build/job/worker CLI and journald diagnostics |
| C6 | security isolation and CI tests |

### Queue and immutable source snapshot

A build request receives a sortable unique ID such as:

```text
20261007T010000Z-a1b2c3d4
```

The root-side manager resolves the requested Git ref to an exact 40-character commit SHA and exports that commit with `git archive` into:

```text
/var/lib/nexora-git/android/builds/BUILD_ID/source/
```

The background worker compiles that immutable snapshot. Updating or changing the managed checkout while a build is running cannot mutate the job's source tree.

Queue state is persisted outside Git:

```text
/var/lib/nexora-git/android/
├── queue/
│   ├── pending/
│   ├── running/
│   ├── completed/
│   ├── failed/
│   └── cancelled/
├── cancel/
├── locks/
├── current-job
└── builds/
    └── BUILD_ID/
        ├── source/
        └── job.conf
```

Only one Gradle build is allowed at a time. The service model already provides a single worker, and an additional `flock` lock prevents accidental duplicate worker processes.

### Detached systemd execution

The installer enables:

```text
nexora-git-android-worker.service
```

The service runs continuously as the isolated `nexora-build` account and starts automatically after a reboot. SSH is not part of the worker lifecycle: once a request is queued, the terminal can disconnect without affecting the build.

Hardening includes:

- `NoNewPrivileges=yes`;
- empty capability bounding and ambient capability sets;
- private `/tmp` and device namespace;
- read-only system filesystem except the Android state and Gradle-cache paths;
- protected home, hostname, clock, kernel tunables/modules/logs and control groups;
- control-group kill semantics so cancelling a build also terminates Gradle/native child processes;
- reduced CPU/I/O priority so application builds do not unnecessarily compete with hosted services.

### Signing isolation

The Phase C worker intentionally receives **no Signing Vault credential and no keystore**.

Release mode runs the existing release/lint/test Gradle tasks without signing variables. This produces the release build outputs while keeping the authoritative release identity outside the execution boundary of repository-controlled Gradle scripts.

Controlled artifact staging/signing and final identity parity are handled by later phases. This separation prevents a modified build script from simply reading or exfiltrating the release signing key.

### Build commands

```bash
sudo nexora-git android build release
sudo nexora-git android build debug
sudo nexora-git android build release main
sudo nexora-git android builds
sudo nexora-git android builds 50
sudo nexora-git android job BUILD_ID
sudo nexora-git android cancel BUILD_ID
sudo nexora-git android worker status
sudo nexora-git android worker logs
```

The optional ref may be a branch, tag or commit that resolves in the managed repository. The resolved commit SHA is persisted in the job and cannot change afterwards.

### Cancellation and recovery

Queued jobs can be cancelled without touching the worker. For a running job, the manager writes a cancellation marker and asks systemd to terminate the complete worker control group. Because the service uses `Restart=always`, systemd starts a clean worker process again and the remaining queue continues.

If a process or host stops without an explicit cancellation marker, a request left in `running/` is considered orphaned on startup and is returned to `pending/` with status `QUEUED_RECOVERED`. An explicit cancellation marker instead moves it to `cancelled/`.

This distinction prevents host reboots or service crashes from silently losing jobs.

### Public application configuration

Each queue request captures only the public Android configuration required by the app:

- GitHub App Client ID;
- HTTPS Auth Broker base URL;
- HTTPS callback URL.

The GitHub App Client Secret is never copied into a build request or worker environment.

## Phase D — artifact lifecycle and persistent diagnostics

Phase D is implemented as six subphases:

| Subphase | Scope |
| --- | --- |
| D1 | per-build persistent log independent of journald |
| D2 | atomic APK/AAB/native-symbol staging |
| D3 | `manifest.json`, file sizes and SHA-256 manifests |
| D4 | artifact verification and CLI inspection |
| D5 | conservative terminal-build retention policy |
| D6 | corruption, lifecycle and retention regression tests |

### Persistent output layout

A successful build now leaves a durable record under:

```text
/var/lib/nexora-git/android/
├── builds/BUILD_ID/
│   ├── source/
│   └── job.conf
├── logs/
│   └── BUILD_ID.log
└── artifacts/
    └── BUILD_ID/
        ├── NexoraGit-COMMIT-release-unsigned.apk
        ├── NexoraGit-COMMIT-release-unsigned.aab
        ├── NexoraGit-COMMIT-release-native-symbols.zip   # when available
        ├── manifest.json
        └── SHA256SUMS.txt
```

Debug jobs stage a debug APK instead. Artifact directories are built in a hidden staging directory and renamed into place only after every mandatory file and manifest is complete, so consumers do not observe half-written releases.

### Build-specific logs

Every running job gets its own persistent log:

```text
/var/lib/nexora-git/android/logs/BUILD_ID.log
```

Gradle stdout/stderr is written both to the service journal and this job-specific file using `tee`. The log remains available after SSH disconnects and after the worker moves on to later jobs.

### Metadata and integrity

Each successful artifact directory contains `manifest.json` with:

- schema version;
- job ID;
- build mode;
- exact 40-character source commit;
- queue and staging timestamps;
- signing state;
- SHA-256 of the build log;
- artifact name, kind, size and SHA-256.

`SHA256SUMS.txt` covers the staged artifacts and manifest. Verification refuses checksum entries with absolute/traversal paths before invoking `sha256sum --check --strict`.

Release artifacts are deliberately labelled `UNSIGNED_RELEASE` and named `*-release-unsigned.*` until Phase E performs controlled signing/parity work. Debug builds are marked `DEBUG_DEFAULT`.

### Artifact size gates

The VPS enforces the same production ceilings used by CI:

- APK: 250 MiB;
- AAB: 200 MiB.

An output that exceeds its budget fails artifact staging rather than being promoted into the durable artifact directory.

### Commands

```bash
sudo nexora-git android artifacts BUILD_ID
sudo nexora-git android verify BUILD_ID
sudo nexora-git android log BUILD_ID
sudo nexora-git android log BUILD_ID 1000
sudo nexora-git android retention status
sudo nexora-git android cleanup --dry-run
sudo nexora-git android cleanup
```

### Retention

The default policy keeps at least the newest **20 terminal jobs** and requires a job to be older than **30 days** before its heavy data is eligible for pruning.

Retention never touches `QUEUED`, `QUEUED_RECOVERED` or `RUNNING` jobs. For an eligible terminal job it removes only:

- immutable source snapshot;
- staged artifact directory;
- per-build log.

The small `job.conf` history remains, so `nexora-git android builds` can still show the historical result and commit after large files are pruned.

The worker applies the same conservative policy after terminal jobs. Operators can preview eligible jobs with `cleanup --dry-run` and inspect the active defaults with `retention status`.

## Why the VPS and GitHub builds can match

The Gradle project already reads production configuration and signing paths from environment variables. The final service will feed the VPS build the same public application configuration and the same signing identity that the release workflow receives through GitHub Secrets.

That produces two independent build locations with a single Android application identity.

---

[← VPS installer](VPS_INSTALLER.md)
