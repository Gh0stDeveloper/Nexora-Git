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
| A | persistent Android toolchain, isolated builder user, storage layout, setup/status/doctor | complete |
| B | persistent Signing Vault, one-time keystore generation, fingerprint, encrypted backup/restore guards | complete |
| C | systemd background build worker, persistent queue, immutable snapshots, cancellation and detached execution | complete |
| D | APK/AAB staging, persistent logs, metadata, checksums, retention and diagnostics | complete |
| E | secure GitHub Actions secret export, isolated release signing and VPS/GitHub parity verification | complete |
| F | update/autobuild integration, coalescing, operational recovery and final hardening | complete |

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

## Phase E — GitHub production credentials, signing and parity

Phase E deliberately separates three trust boundaries.

### E1 — GitHub App configuration on the VPS

The VPS can now manage the GitHub App credentials without re-entering the domain-derived URLs:

```bash
sudo nexora-git github status
sudo nexora-git github configure
```

`github configure` asks for the GitHub App Client ID and hidden Client Secret. A blank secret keeps the current secret. The command writes the broker environment atomically with mode `0600`, derives:

```text
NEXORA_AUTH_BROKER_BASE_URL=https://DOMAIN
NEXORA_GITHUB_CALLBACK_URL=https://DOMAIN/oauth/callback
APP_CALLBACK_URI=https://auth.example.com/oauth/android/callback
```

and recreates/health-checks the Auth Broker.

The **GitHub App Client Secret remains server-only**. It is never exported to Android or GitHub Actions.

### E2 — GitHub Actions production Secrets

The stable release workflow requires exactly seven values:

```text
NEXORA_GITHUB_CLIENT_ID
NEXORA_AUTH_BROKER_BASE_URL
NEXORA_GITHUB_CALLBACK_URL
NEXORA_SIGNING_KEYSTORE_BASE64
NEXORA_SIGNING_STORE_PASSWORD
NEXORA_SIGNING_KEY_ALIAS
NEXORA_SIGNING_KEY_PASSWORD
```

Create a root-only export bundle when manual transfer is desired:

```bash
sudo nexora-git github secrets export
```

The export directory and files are mode `0700/0600` and include a non-secret hash manifest. Delete the bundle after use.

If GitHub CLI is installed and authenticated as root, the VPS can synchronize the same values directly into the repository's `production` environment:

```bash
sudo gh auth login
sudo nexora-git github secrets apply
sudo nexora-git github secrets status
```

Values are piped through stdin to `gh secret set`; signing passwords/base64 are not placed in command-line arguments. The public signing-certificate fingerprint is also written to the protected environment's **non-secret** `NEXORA_SIGNING_CERT_SHA256` variable using `gh variable set`. On success the VPS records hashes of the seven secrets and the public certificate fingerprint in its local parity record. Official GitHub tag releases fail if that public fingerprint does not match their actual signing keystore. GitHub itself does not expose secret values for later reading, so parity checks compare current local material to this successful synchronization record while GitHub exposes only secret names.

### E3 — isolated production signing

The background Gradle worker still has no Signing Vault access. Signing is an explicit privileged operation after a release build:

```bash
sudo nexora-git android sign BUILD_ID
sudo nexora-git android signed BUILD_ID
```

The signer:

- verifies the Phase D unsigned artifacts/checksums first;
- signs APK with Android Build Tools `apksigner` using v1/v2/v3;
- signs AAB with JDK `jarsigner`;
- passes keystore passwords through process environment references rather than command arguments;
- verifies both signatures;
- extracts the SHA-256 certificate fingerprint from APK and AAB;
- requires both fingerprints to equal the persistent Signing Vault certificate;
- writes signed outputs atomically below `artifacts/BUILD_ID/signed/`;
- generates a signed-artifact checksum manifest and public signing metadata.

Repeated signing is idempotent only when the existing signed output verifies against the current Signing Vault.

### VPS ↔ GitHub parity

After `github secrets apply`:

```bash
sudo nexora-git android parity
sudo nexora-git android parity BUILD_ID
```

Parity requires:

- broker Client ID and domain-derived URLs to be internally consistent;
- current Signing Vault material to match the hashes recorded at the last successful GitHub synchronization;
- the Stable Release workflow to reference all seven production secrets and the `production` environment;
- when a build ID is supplied, its signed APK/AAB fingerprint to match the same Signing Vault certificate.

The Stable Release workflow also performs an independent signing-parity gate: callback must equal `AUTH_BROKER_BASE_URL/oauth/callback`, and APK/AAB certificate fingerprints must equal the certificate inside the supplied production keystore.

## Phase F — update automation, recovery and final hardening

Phase F closes the Android Build Service in six subphases:

| Subphase | Scope |
| --- | --- |
| F1 | serialized VPS updates with a dedicated `flock` update lock |
| F2 | root-only pre-update operational checkpoints and bounded retention |
| F3 | configurable Android autobuild after a successful update |
| F4 | debounce and latest-pending-wins coalescing |
| F5 | automatic rollback plus explicit checkpoint recovery |
| F6 | runtime reload hardening, final diagnostics and regression tests |

### Update lock and idempotent no-op updates

Only one `nexora-git update` may run at a time. A second invocation fails immediately instead of racing Git, Docker, systemd or the queue.

If the fetched branch already points to the installed commit, the updater no longer rebuilds the Auth Broker and does not queue another Android build. It refreshes managed runtime links/units and exits.

### Operational checkpoints

Before a real fast-forward, the updater creates a root-only checkpoint below:

```text
/var/lib/nexora-git/operations/checkpoints/CHECKPOINT_ID/
```

A checkpoint captures the exact Git commit plus deployment/runtime configuration such as the VPS state file, Android toolchain/autobuild config, broker environment, Nexora Nginx vhost, systemd units and GitHub synchronization metadata. Every captured file is covered by `SHA256SUMS`; unexpected paths or symlinks are rejected during verification.

The private Android keystore is intentionally **not copied** into routine operational checkpoints. Its disaster-recovery path remains the separately encrypted Signing Vault backup from Phase B.

The newest ten operational checkpoints are retained by default.

Commands:

```bash
sudo nexora-git recovery status
sudo nexora-git recovery checkpoint "before-maintenance"
sudo nexora-git recovery list
sudo nexora-git recovery verify CHECKPOINT_ID
sudo nexora-git recovery restore CHECKPOINT_ID
```

An explicit restore verifies checksums, refuses tracked local Git modifications, restores the captured configuration, resets the managed repository to the recorded commit, refreshes systemd/runtime assets, rebuilds the Auth Broker and requires local health before succeeding.

### Autobuild after update

Autobuild is enabled by default for **release** candidates with a 30-second debounce:

```bash
sudo nexora-git android autobuild status
sudo nexora-git android autobuild enable release 30
sudo nexora-git android autobuild enable debug 30
sudo nexora-git android autobuild disable
```

A successful `nexora-git update` writes the desired commit atomically and returns. The persistent systemd timer dispatches it after the debounce window, so SSH is not part of the build lifecycle.

If several updates arrive during the window, the desired commit file is overwritten atomically and only the newest commit is dispatched. If an older autobuild is still **pending**, it is cancelled when the newer one is queued. A currently running build is allowed to finish, while at most the newest pending autobuild remains behind it.

A commit that is already queued, running or successfully completed in the same mode is not queued again.

Autobuild continues to produce an unsigned release candidate. The Phase E privileged signing command remains explicit by design; repository-controlled Gradle code never receives the release keystore automatically.

### Runtime reload without destroying an active build

When an update changes worker code, the runtime unit is refreshed immediately. If no Android build is active, the worker restarts at once. If a build is active, a protected restart marker is written instead; the worker finishes that job, exits before starting the next one, and systemd restarts it with the new runtime.

This prevents a normal application update from needlessly killing a long-running Gradle/native build.

### Automatic rollback

Once the repository fast-forwards, an EXIT/INT/TERM rollback guard remains armed through:

- Auth Broker image build;
- container restart;
- local health gate;
- systemd/runtime refresh.

If any of those critical stages fails or the update process is interrupted, the updater resets the previous commit, restores the pre-update checkpoint, rebuilds the known-good broker and refreshes the prior runtime.

Public HTTPS failure remains a warning after local health passes because DNS/provider/TLS routing can fail independently of the application binary.

## Why the VPS and GitHub builds can match

The Gradle project already reads production configuration and signing paths from environment variables. The final service will feed the VPS build the same public application configuration and the same signing identity that the release workflow receives through GitHub Secrets.

That produces two independent build locations with a single Android application identity.

---

[← VPS installer](VPS_INSTALLER.md)
