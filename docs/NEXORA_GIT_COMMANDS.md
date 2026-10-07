# `nexora-git` Command Reference

> [Documentation hub](README.md) · [VPS installer](VPS_INSTALLER.md) · [Android Builder](VPS_ANDROID_BUILDER.md) · [Release process](RELEASE_PROCESS.md)

This document is the operator reference for the `nexora-git` management CLI installed by the Nexora Git VPS installer.

It documents every supported public command in the current implementation, plus the one internal autobuild dispatcher command used by systemd.

## Conventions

All `nexora-git` commands require root privileges because the manager operates on systemd units, Docker, Nginx, protected deployment state, the Android Signing Vault and other root-owned paths.

Use:

```bash
sudo nexora-git <command>
```

Placeholders used below:

| Placeholder | Meaning |
| --- | --- |
| `JOB_ID` | Android build identifier such as `20261007T010000Z-a1b2c3d4` |
| `CHECKPOINT_ID` | Operational recovery checkpoint identifier |
| `REF` | Git branch, tag or commit that resolves to a commit in the managed repository |
| `PATH` | Absolute or relative filesystem destination/source |
| `LIMIT` | Maximum number of rows to show |
| `LINES` | Number of log lines to show |

Commands that display configuration intentionally hide confidential values unless the command's explicit purpose is to create a protected secret export.

---

## Quick decision guide

| Goal | Command |
| --- | --- |
| Check whether the VPS deployment is running | `sudo nexora-git status` |
| Diagnose the whole deployment | `sudo nexora-git doctor` |
| Update Nexora Git safely | `sudo nexora-git update` |
| Follow Auth Broker logs | `sudo nexora-git logs` |
| Start an Android release build | `sudo nexora-git android build release` |
| See Android build jobs | `sudo nexora-git android builds` |
| Follow one build's persistent log | `sudo nexora-git android log JOB_ID` |
| Sign a finished release | `sudo nexora-git android sign JOB_ID` |
| Verify a signed release | `sudo nexora-git android signed JOB_ID` |
| Configure GitHub App credentials | `sudo nexora-git github configure` |
| Push VPS signing/config values to GitHub Secrets | `sudo nexora-git github secrets apply` |
| Enable build-after-update | `sudo nexora-git android autobuild enable release 30` |
| Create a recovery checkpoint | `sudo nexora-git recovery checkpoint "before-maintenance"` |
| Restore an operational checkpoint | `sudo nexora-git recovery restore CHECKPOINT_ID` |

---

# Core VPS commands

## `nexora-git status`

```bash
sudo nexora-git status
```

Shows a concise runtime summary for the VPS deployment.

It reports:

- public Auth Broker domain;
- loopback broker port;
- configured Git branch;
- installed Git revision;
- Nginx service state;
- Docker service state;
- TLS certificate expiry;
- Docker Compose service status.

Use this first when you want a quick health snapshot without running deeper diagnostics.

This command does not change the deployment.

---

## `nexora-git update`

```bash
sudo nexora-git update
```

Updates the managed Nexora Git installation to the configured remote branch.

The updater:

1. takes the dedicated update lock so two updates cannot run concurrently;
2. checks required services and repository cleanliness;
3. fetches the configured branch;
4. skips expensive work if the installed commit is already current;
5. creates a protected operational checkpoint before a real fast-forward;
6. fast-forwards the repository only;
7. rebuilds and restarts the Auth Broker;
8. verifies local health;
9. refreshes managed systemd/runtime assets;
10. schedules Android autobuild when enabled;
11. automatically rolls back critical failures to the known-good previous revision.

A public HTTPS health failure after local health succeeds is treated as an infrastructure warning rather than immediate proof that the application revision is bad.

### Important

The update refuses to overwrite tracked local Git modifications. Commit, stash or remove tracked changes before updating.

Autobuild does not automatically sign the release. Signing remains a separate privileged operation.

---

## `nexora-git doctor`

```bash
sudo nexora-git doctor
```

Runs the broadest deployment diagnostic.

It validates areas such as:

- required binaries;
- Docker daemon;
- Nginx syntax;
- local Auth Broker health;
- public HTTPS health;
- TLS certificate lifetime;
- Compose service state;
- Android SDK/JDK/NDK/CMake foundation;
- Signing Vault consistency;
- Android worker state;
- release/signing tooling and workflow wiring.

Use `doctor` after installation, after a failed update, after moving the VPS, or whenever several components appear unhealthy.

A non-zero exit status means at least one required diagnostic failed.

---

## `nexora-git logs`

```bash
sudo nexora-git logs
```

Follows the Auth Broker Docker Compose logs.

Equivalent operational intent:

- show the latest broker output;
- continue streaming new lines;
- help diagnose OAuth callback, HTTP, startup or container problems.

This is for the **Auth Broker**, not Android Gradle builds.

For Android build logs use:

```bash
sudo nexora-git android log JOB_ID
```

or:

```bash
sudo nexora-git android worker logs
```

---

## `nexora-git start`

```bash
sudo nexora-git start
```

Starts the Auth Broker container through Docker Compose.

It does not start or queue an Android build.

---

## `nexora-git stop`

```bash
sudo nexora-git stop
```

Stops the Auth Broker container.

It does **not** disable Nginx, remove TLS certificates, delete data, disable the Android worker or delete build jobs.

Use it when intentionally taking the OAuth broker offline.

---

## `nexora-git restart`

```bash
sudo nexora-git restart
```

Restarts the Auth Broker container and then requires the local health endpoint to recover.

Use this after broker-only operational changes when a full repository update is unnecessary.

---

## `nexora-git renew-cert`

```bash
sudo nexora-git renew-cert
```

Runs Certbot renewal for the configured Nexora Git domain, validates Nginx configuration and reloads Nginx.

Use it for manual TLS renewal/testing. Normal Certbot timer-based renewal remains the preferred unattended path.

---

## `nexora-git reconfigure`

```bash
sudo nexora-git reconfigure
```

Re-enters the VPS installer's reconfiguration path.

Use it when changing deployment-level settings such as the domain or other installer-managed configuration.

Reconfiguration retains the installer's safety rules: unrelated Nginx sites are not overwritten and confidential broker configuration remains outside Git.

For only changing GitHub App Client ID/Client Secret, prefer:

```bash
sudo nexora-git github configure
```

---

## `nexora-git config`

```bash
sudo nexora-git config
```

Prints non-secret deployment configuration, including:

- installation directory;
- domain;
- Let's Encrypt email;
- local broker port;
- repository URL;
- managed branch.

Confidential broker values are deliberately hidden.

---

## `nexora-git version`

```bash
sudo nexora-git version
```

Prints the full Git commit SHA currently installed in the managed repository.

Use it to identify exactly which source revision the VPS is running.

---

# Android build-host commands

## `nexora-git android status`

```bash
sudo nexora-git android status
```

Shows the Android build-host summary.

It combines:

- pinned Android toolchain/component status;
- Signing Vault public status;
- background worker/queue status.

Use it for a quick Android-side overview.

---

## `nexora-git android doctor`

```bash
sudo nexora-git android doctor
```

Runs Android-specific diagnostics.

It checks:

- JDK;
- Android SDK;
- platform/build tools;
- NDK;
- CMake;
- Gradle wrapper;
- persistent build paths;
- Signing Vault integrity;
- worker/service state.

Use the top-level `nexora-git doctor` when you also need broker, Nginx, TLS and release-layer checks.

---

## `nexora-git android setup`

```bash
sudo nexora-git android setup
```

Idempotently provisions or repairs the Android build foundation.

It:

- installs/reuses the pinned Android toolchain;
- creates or verifies the persistent Signing Vault;
- installs/enables the Android worker service.

Existing healthy components are reused rather than downloaded or regenerated unnecessarily.

### Signing-key safety

If a valid release signing identity already exists, it is reused. The setup path does not silently replace it with a new identity.

---

## `nexora-git android config`

```bash
sudo nexora-git android config
```

Displays non-secret Android build-host configuration, such as SDK paths and pinned toolchain versions.

Signing passwords and other confidential values are not printed.

---

# Android Signing Vault commands

## `nexora-git android signing status`

```bash
sudo nexora-git android signing status
```

Shows public metadata for the persistent Android signing identity.

Typical information includes:

- whether the vault is configured;
- identity ID;
- creation timestamp;
- store type;
- key alias;
- SHA-256 certificate fingerprint;
- vault location.

Passwords are never displayed.

Running `nexora-git android signing` with no subcommand also defaults to `status`.

---

## `nexora-git android signing setup`

```bash
sudo nexora-git android signing setup
```

Ensures the Android toolchain exists and then creates or verifies the Signing Vault.

Use this when specifically repairing/provisioning release signing without reinstalling the entire VPS stack.

If valid signing state already exists, the command verifies and reuses it.

If partial signing state exists, the implementation fails safely instead of overwriting it.

---

## `nexora-git android signing verify`

```bash
sudo nexora-git android signing verify
```

Performs strict Signing Vault validation.

It checks items such as:

- vault/file permissions;
- secret-file format;
- alias consistency;
- PKCS#12 credentials;
- certificate SHA-256/SHA-1 fingerprints;
- keystore SHA-256;
- certificate validity horizon.

Run this before signing important releases or after restoring signing material.

---

## `nexora-git android signing fingerprint`

```bash
sudo nexora-git android signing fingerprint
```

Prints the public SHA-256 and SHA-1 certificate fingerprints for the release identity.

These fingerprints are safe to compare against signed APK/AAB outputs and CI configuration.

No private key or password is exposed.

---

## `nexora-git android signing certificate`

```bash
sudo nexora-git android signing certificate
```

Prints the public release certificate in PEM form.

Use it for public certificate inspection, archival or fingerprint verification.

This does not expose the private key.

---

## `nexora-git android signing backup [PATH]`

```bash
sudo nexora-git android signing backup
sudo nexora-git android signing backup /secure/offhost/nexora-signing.nxbk
```

Creates an encrypted backup of the Android signing identity.

If `PATH` is omitted, the manager chooses a timestamped backup path inside the protected Signing Vault backup directory.

The command asks interactively for a separate backup passphrase and creates integrity metadata.

### Important

Store at least one encrypted signing backup **off the VPS**, and store its passphrase separately.

Losing the release signing identity can prevent future APK updates from being accepted as the same Android application.

---

## `nexora-git android signing restore <PATH>`

```bash
sudo nexora-git android signing restore /secure/offhost/nexora-signing.nxbk
```

Restores an encrypted Signing Vault backup.

Before installing anything, restore logic validates:

- decryption;
- archive allow-list;
- internal checksums;
- keystore credentials;
- certificate fingerprint;
- compatibility with any currently installed identity.

A backup containing a different release identity is not silently written over an existing valid vault.

---

## Signing help

```bash
sudo nexora-git android signing help
sudo nexora-git android signing -h
sudo nexora-git android signing --help
```

Prints the Signing Vault command usage.

---

# Android build queue and worker commands

## `nexora-git android build [release|debug] [REF]`

```bash
sudo nexora-git android build
sudo nexora-git android build release
sudo nexora-git android build debug
sudo nexora-git android build release main
sudo nexora-git android build release v1.2.0
sudo nexora-git android build debug 0123456789abcdef...
```

Queues an Android build in the detached background worker.

Defaults:

- mode: `release`;
- ref: `HEAD`.

The supplied `REF` is resolved immediately to an exact commit SHA. The worker builds an immutable source snapshot of that commit, so later repository updates do not mutate the queued/running build.

The command returns a `JOB_ID`.

You may disconnect SSH after the job is queued.

### Release behavior

A release build creates an **unsigned** release candidate. The unprivileged worker never receives the persistent release keystore.

After the build completes, sign it explicitly with:

```bash
sudo nexora-git android sign JOB_ID
```

---

## `nexora-git android builds [LIMIT]`

```bash
sudo nexora-git android builds
sudo nexora-git android builds 50
```

Lists recent Android build jobs with information such as:

- job ID;
- mode;
- state;
- exit code;
- commit.

Default limit is 20. The accepted range is 1–200.

Historical `job.conf` records remain even after retention removes old heavy artifacts.

---

## `nexora-git android job <JOB_ID>`

```bash
sudo nexora-git android job 20261007T010000Z-a1b2c3d4
```

Shows detailed state for one build:

- job ID;
- mode;
- exact commit;
- creation/update times;
- status;
- exit code.

Useful states include `QUEUED`, `QUEUED_RECOVERED`, `RUNNING`, `COMPLETED`, `FAILED` and `CANCELLED`.

---

## `nexora-git android cancel <JOB_ID>`

```bash
sudo nexora-git android cancel JOB_ID
```

Cancels a queued or running Android build.

For a queued build, the request is moved directly to the cancelled state.

For a running build, the manager records a cancellation marker and terminates the worker control group so Gradle/native child processes are also stopped. systemd restarts a clean worker for later jobs.

A job already in a terminal state cannot be cancelled again.

---

## `nexora-git android worker status`

```bash
sudo nexora-git android worker status
```

Shows worker and queue state, including counts for:

- pending;
- running;
- completed;
- failed;
- cancelled;
- current job, when any.

Use it when checking whether the detached worker is alive and consuming jobs.

---

## `nexora-git android worker logs`

```bash
sudo nexora-git android worker logs
```

Follows the systemd journal for the Android build worker.

This is the service-level log stream.

For a durable log tied to one specific build use:

```bash
sudo nexora-git android log JOB_ID
```

---

# Android artifact and log commands

## `nexora-git android artifacts <JOB_ID>`

```bash
sudo nexora-git android artifacts JOB_ID
```

Lists the staged artifact directory for a successful build and prints its manifest.

Depending on build mode/output it can include:

- APK;
- AAB;
- native debug symbols;
- `manifest.json`;
- `SHA256SUMS.txt`.

Release files are staged as unsigned until the privileged signing command is run.

---

## `nexora-git android verify <JOB_ID>`

```bash
sudo nexora-git android verify JOB_ID
```

Verifies the integrity of staged build artifacts.

It checks:

- the artifact SHA-256 manifest;
- safe manifest paths;
- the persistent build-log checksum recorded in the artifact manifest.

Use this before signing or copying artifacts elsewhere.

---

## `nexora-git android log <JOB_ID> [LINES]`

```bash
sudo nexora-git android log JOB_ID
sudo nexora-git android log JOB_ID 1000
```

Prints the persistent Gradle log for one build.

Default: 200 lines.

Accepted range: 1–5000 lines.

Unlike the live systemd journal, this log remains associated with the build job until retention removes heavy historical data.

---

## `nexora-git android retention status`

```bash
sudo nexora-git android retention status
```

Shows the configured artifact/log retention policy and how many terminal jobs currently qualify for cleanup.

Default policy:

- keep at least the newest 20 terminal builds;
- only consider jobs older than 30 days.

Queued/running jobs are never retention candidates.

---

## `nexora-git android cleanup [--dry-run]`

Preview:

```bash
sudo nexora-git android cleanup --dry-run
```

Apply:

```bash
sudo nexora-git android cleanup
```

Removes heavy data for jobs that qualify under the retention policy.

For eligible terminal jobs it removes:

- immutable source snapshot;
- staged artifacts;
- persistent build log.

It preserves `job.conf`, so lightweight build history remains visible.

Always use `--dry-run` first when manually cleaning production storage.

---

# Android release signing and parity commands

## `nexora-git android sign <JOB_ID>`

```bash
sudo nexora-git android sign JOB_ID
```

Signs an already-completed unsigned release build using the persistent Signing Vault.

The privileged signing stage is intentionally separate from the unprivileged Gradle worker.

It:

1. verifies the unsigned Phase D artifacts;
2. verifies the Signing Vault;
3. signs the APK with `apksigner`;
4. signs the AAB with `jarsigner`;
5. verifies both cryptographic signatures;
6. compares APK/AAB certificate SHA-256 fingerprints to the Signing Vault certificate;
7. writes signed outputs atomically under the build's `signed/` directory;
8. creates signed-artifact checksums and signing metadata.

If valid signed output already exists, the command verifies and reuses it instead of blindly overwriting it.

---

## `nexora-git android signed <JOB_ID>`

```bash
sudo nexora-git android signed JOB_ID
```

Verifies and lists the signed APK/AAB for a build.

It validates:

- signed-output checksums;
- signing metadata;
- APK signature;
- AAB signature;
- certificate fingerprint equality with the current Signing Vault.

Use this after signing and before distributing the build.

---

## `nexora-git android parity [JOB_ID]`

Without a build:

```bash
sudo nexora-git android parity
```

With a signed build:

```bash
sudo nexora-git android parity JOB_ID
```

Checks production identity/configuration parity between the VPS and the last successful GitHub Secrets synchronization.

It requires:

- valid GitHub App/broker configuration;
- healthy Signing Vault;
- Stable Release workflow wired to the expected production Secrets;
- current local secret/config digests matching the recorded successful GitHub synchronization.

When `JOB_ID` is supplied it also verifies that build's signed APK/AAB against the same Signing Vault certificate.

### Limitation

GitHub does not allow secret values to be read back after storage. Therefore parity uses the successful local synchronization record containing only SHA-256 digests, while GitHub can expose secret names but not their values.

---

# Android autobuild commands

## `nexora-git android autobuild status`

```bash
sudo nexora-git android autobuild status
```

Shows:

- whether autobuild is enabled;
- build mode;
- debounce duration;
- pending desired commit, if any;
- systemd timer state.

Running `nexora-git android autobuild` with no additional subcommand also defaults to `status`.

---

## `nexora-git android autobuild enable [release|debug] [DEBOUNCE_SECONDS]`

```bash
sudo nexora-git android autobuild enable
sudo nexora-git android autobuild enable release 30
sudo nexora-git android autobuild enable debug 60
```

Enables automatic Android builds after successful `nexora-git update` fast-forwards.

Defaults:

- mode: `release`;
- debounce: 30 seconds.

Allowed debounce range: 5–3600 seconds.

The debounce window coalesces rapid updates so only the newest desired commit is dispatched.

If an older autobuild is still pending, a newer update can cancel that superseded pending autobuild. A currently running build is allowed to finish.

Release autobuild remains unsigned by design; run `android sign JOB_ID` afterwards when a candidate should become a distributable release.

---

## `nexora-git android autobuild disable`

```bash
sudo nexora-git android autobuild disable
```

Disables update-triggered Android builds.

It clears the pending desired-build marker and disables/stops the autobuild timer.

Manual `nexora-git android build ...` commands continue to work.

---

## Internal: `nexora-git android autobuild run-pending`

```bash
sudo nexora-git android autobuild run-pending
```

This command exists for the managed systemd autobuild service.

It evaluates the pending desired commit after the configured debounce window, suppresses duplicates/coalesces superseded requests and queues the required Android job.

**Do not use this as the normal manual build command.** Use `nexora-git android build` for operator-triggered builds.

---

# GitHub production configuration commands

## `nexora-git github status`

```bash
sudo nexora-git github status
```

Displays the public/server-side GitHub App deployment state:

- whether GitHub App configuration is valid;
- Client ID;
- whether a Client Secret is configured, without revealing it;
- broker base URL;
- callback URL;
- Android callback URI;
- GitHub production environment name.

Running `nexora-git github` with no subcommand also defaults to `status`.

---

## `nexora-git github configure`

```bash
sudo nexora-git github configure
```

Interactively changes the GitHub App Client ID and Client Secret used by the VPS Auth Broker.

Behavior:

- current Client ID can be kept by submitting a blank value;
- Client Secret input is hidden;
- blank secret keeps the current secret;
- broker and callback URLs are derived automatically from the configured VPS domain;
- broker environment is written atomically with restrictive permissions;
- the Auth Broker is recreated;
- local and public health checks must succeed.

The GitHub App Client Secret stays server-only.

---

## `nexora-git github secrets status`

```bash
sudo nexora-git github secrets status
```

Shows whether the current VPS signing/config values still match the local digest record created by the last successful GitHub Secrets synchronization.

If GitHub CLI is available and authenticated, it also lists the GitHub production secret **names**.

It cannot display secret values because GitHub does not expose stored Actions secret values.

Running `nexora-git github secrets` with no further subcommand also defaults to `status`.

---

## `nexora-git github secrets export [PATH]`

```bash
sudo nexora-git github secrets export
sudo nexora-git github secrets export /root/nexora-git-secrets-export
```

Creates a protected manual bundle containing the seven production values required by the Stable Release GitHub Actions workflow:

- `NEXORA_GITHUB_CLIENT_ID`;
- `NEXORA_AUTH_BROKER_BASE_URL`;
- `NEXORA_GITHUB_CALLBACK_URL`;
- `NEXORA_SIGNING_KEYSTORE_BASE64`;
- `NEXORA_SIGNING_STORE_PASSWORD`;
- `NEXORA_SIGNING_KEY_ALIAS`;
- `NEXORA_SIGNING_KEY_PASSWORD`.

The GitHub App **Client Secret is deliberately excluded**.

The export directory/files use restrictive permissions and contain live production secrets.

### Important

Delete the export bundle after transferring its contents into the intended secret store.

---

## `nexora-git github secrets apply`

```bash
sudo nexora-git github secrets apply
```

Synchronizes the seven required production values directly into the repository's GitHub Actions `production` environment.

Requirements:

- GitHub CLI (`gh`) installed;
- root's `gh` session authenticated;
- Signing Vault healthy;
- GitHub App/broker configuration valid.

The command creates/ensures the `production` environment and pipes each secret value through standard input to `gh secret set`.

After a successful sync it stores only SHA-256 digests locally for later parity checking.

It does not upload the GitHub App Client Secret.

---

## GitHub command help

```bash
sudo nexora-git github help
sudo nexora-git github -h
sudo nexora-git github --help
```

Prints GitHub/VPS production configuration usage.

---

# Recovery commands

## `nexora-git recovery status`

```bash
sudo nexora-git recovery status
```

Shows operational recovery state, including:

- number of operational checkpoints;
- newest checkpoint;
- number of encrypted Signing Vault backups;
- checkpoint retention count.

Running `nexora-git recovery` with no subcommand also defaults to `status`.

---

## `nexora-git recovery checkpoint [REASON]`

```bash
sudo nexora-git recovery checkpoint
sudo nexora-git recovery checkpoint "before-nginx-maintenance"
```

Creates a root-only operational checkpoint.

Default reason: `manual`.

A checkpoint records the current Git commit plus allow-listed deployment/runtime files such as:

- deployment state;
- Android builder/autobuild configuration;
- Auth Broker environment;
- Nexora Nginx configuration;
- worker/autobuild systemd units;
- GitHub sync metadata.

Every captured file is checksummed.

### Not included

The private Android signing keystore is deliberately not copied into routine operational checkpoints. Use the encrypted Signing Vault backup command for signing-key disaster recovery.

---

## `nexora-git recovery list [LIMIT]`

```bash
sudo nexora-git recovery list
sudo nexora-git recovery list 50
```

Lists operational checkpoints with:

- checkpoint ID;
- recorded Git commit;
- reason.

Default limit is 20. Accepted range is 1–100.

---

## `nexora-git recovery verify <CHECKPOINT_ID>`

```bash
sudo nexora-git recovery verify CHECKPOINT_ID
```

Verifies an operational checkpoint without restoring it.

Validation includes:

- checkpoint ID format;
- required manifest/checksum files;
- strict filename allow-list;
- refusal of symlinks;
- path traversal protection;
- SHA-256 verification.

Use this before a manual restore.

---

## `nexora-git recovery restore <CHECKPOINT_ID>`

```bash
sudo nexora-git recovery restore CHECKPOINT_ID
```

Restores a verified operational checkpoint.

In a real deployment the restore flow:

1. verifies the checkpoint;
2. refuses to overwrite tracked local Git changes;
3. confirms the recorded commit still exists;
4. stops autobuild/worker runtime as required;
5. restores allow-listed configuration files;
6. resets the managed repository to the recorded commit;
7. reloads deployment state;
8. refreshes systemd/runtime assets;
9. validates/reloads Nginx;
10. rebuilds and starts the Auth Broker;
11. requires the local health gate to pass.

This is a recovery command, not a normal update mechanism.

### Important

A checkpoint restore can intentionally move the managed repository backwards to the recorded commit. Verify the checkpoint and understand its date/reason before restoring.

---

# Help commands

## `nexora-git help`

```bash
sudo nexora-git help
sudo nexora-git -h
sudo nexora-git --help
```

Prints the complete top-level command list.

---

## `nexora-git android help`

```bash
sudo nexora-git android help
sudo nexora-git android -h
sudo nexora-git android --help
```

Prints the public Android builder command list.

---

# Recommended operational workflows

## Safe VPS update

```bash
sudo nexora-git doctor
sudo nexora-git update
sudo nexora-git status
sudo nexora-git android autobuild status
```

If autobuild is enabled, the Android candidate is dispatched after the debounce window.

---

## Manual release build on the VPS

```bash
JOB_ID="$(sudo nexora-git android build release main)"
sudo nexora-git android job "$JOB_ID"
sudo nexora-git android log "$JOB_ID"
sudo nexora-git android verify "$JOB_ID"
sudo nexora-git android sign "$JOB_ID"
sudo nexora-git android signed "$JOB_ID"
```

The build itself continues independently of SSH after queueing.

---

## Configure GitHub production parity

```bash
sudo nexora-git github configure
sudo gh auth login
sudo nexora-git github secrets apply
sudo nexora-git github secrets status
sudo nexora-git android parity
```

This keeps GitHub Actions and VPS releases tied to the same signing identity and public broker configuration.

---

## Disaster-recovery preparation

```bash
sudo nexora-git android signing verify
sudo nexora-git android signing backup /secure/offhost/nexora-signing.nxbk
sudo nexora-git recovery checkpoint "known-good-production"
sudo nexora-git recovery verify CHECKPOINT_ID
```

The encrypted signing backup and operational checkpoint solve different recovery problems; keep both.

---

# Security notes

- Never paste the GitHub App Client Secret into Android configuration.
- Never commit the Signing Vault, secret exports or backup passphrases.
- Prefer `github secrets apply` over leaving a manual export bundle on disk.
- Use `android cleanup --dry-run` before applying manual retention cleanup.
- Verify a checkpoint before restoring it.
- Keep an encrypted Signing Vault backup off-host.
- Do not bypass signing fingerprint/parity verification.
- Do not use the internal `autobuild run-pending` command as a replacement for normal manual builds.

---

[← Documentation hub](README.md)
