# Phase D — Real Git Engine Report

Date: 2026-10-04

## Status

**Implementation complete and validated with real Git repositories.**

Phase D establishes Nexora Git's local Git execution layer. Git operations no longer exist only as architecture documentation: the repository contains a compiled libgit2 engine, JNI bridge and Kotlin `GitEngine` implementation.

## Native dependency baseline

### libgit2

```text
Version: v1.9.7
Commit: 49e408b3208bc3093757a1c2db938d3590f3f412
```

### Mbed TLS

```text
Version: 3.6.7 LTS
Commit: 068ff080b369adfac81509f9b57b2afabaf82dc5
```

Both dependencies are resolved from upstream source with immutable Git commit pins.

## Android native toolchain

```text
NDK:   27.2.12479018
CMake: 3.22.1
C:     C99
C++:   C++17
```

Supported ABIs:

```text
arm64-v8a
armeabi-v7a
x86_64
```

## Native architecture

```text
GitEngine
   ↓
Libgit2GitEngine
   ↓
NativeGitBridge
   ↓ JNI
nexoragit_native
   ↓
nexoragit_core
   ↓
libgit2
   ↓
Git repositories on device
```

The application does not invoke a shell Git executable and does not depend on Termux.

## Implemented Git operations

- init;
- status;
- stage;
- unstage;
- commit;
- local and remote branches;
- create branch;
- checkout;
- clone;
- remote URL lookup;
- fetch;
- pull;
- push;
- staged diff;
- unstaged diff;
- full working-tree diff;
- merge analysis;
- fast-forward merge;
- normal merge commit;
- conflict detection;
- conflict enumeration.

## Pull behavior

Pull is implemented as an explicit workflow:

```text
fetch
→ resolve local branch/upstream
→ merge analysis
→ up-to-date / fast-forward / normal merge
→ return conflict state when necessary
```

A conflicted pull leaves the Git index in a real conflict state for later resolution. Local changes are not silently force-overwritten.

## Push behavior

Push uses an explicit refspec or the current branch.

Phase D intentionally contains no automatic force push.

## GitHub credentials

The Phase B GitHub OAuth access token is supplied to libgit2 only in memory.

Credential protections:

1. Kotlin validates the remote with `GitRemoteSecurityPolicy`.
2. The native credential callback independently validates the request URL.
3. Only normal `https://github.com` traffic receives the GitHub token.
4. HTTP, SSH, foreign hosts, embedded credentials and custom ports do not receive it.
5. Tokens are never added to `.git/config` or remote URLs.

SSH is intentionally disabled during Phase D.

## TLS

HTTPS uses Mbed TLS.

On Android, libgit2 is initialized with the system certificate directory:

```text
/system/etc/security/cacerts
```

The libgit2 HOME directory points to Nexora Git's private internal files directory rather than relying on a shell environment.

## Native workflow validation

Native Git CI validated the real C++ engine against disposable Git repositories.

Successful run:

```text
Run: 37221925065
Conclusion: success
```

The test performs real operations:

- initialize repository;
- detect untracked file;
- stage;
- staged diff;
- commit;
- branch create/switch;
- feature commit;
- local fast-forward merge;
- unstaged diff;
- unstage while preserving working-tree content;
- create bare remote;
- configure origin;
- push;
- clone twice;
- fetch;
- inspect remote branch;
- pull;
- create divergent histories;
- generate a real merge conflict;
- enumerate the conflict.

No mocked repository is used.

## Android validation

Android CI validated the JNI/NDK engine inside the Android application.

```text
Run: 37221928159
Head SHA: d70deea4d91aaec4b44e9043098410b997979730
Conclusion: success
```

Validated:

- native dependency configuration;
- libgit2/Mbed TLS Android compilation;
- JNI library compilation;
- arm64-v8a;
- armeabi-v7a;
- x86_64;
- debug APK;
- instrumentation-test APK compilation;
- JVM unit tests;
- Android lint;
- artifact packaging.

APK artifact:

```text
Name: NexoraGit-debug
Artifact ID: 11310631758
Size: 24,942,848 bytes
SHA-256: c199e2c683bdbda2109a247baddda42a108c9e6f7c101ed2ec92cca44e702395
```

## Security/reproducibility controls

- upstream native dependencies pinned by commit SHA;
- no precompiled third-party Git binary committed;
- HTTPS token supplied through credential callback;
- GitHub hostname checked in Kotlin and native code;
- release R8 rules preserve JNI entrypoints;
- credentials are not included in repository URLs;
- no implicit force push;
- safe checkout strategy;
- native exceptions cross JNI through a typed exception.

## Deliberately deferred work

Phase D supplies the foundational engine, not every advanced Git workflow.

Later phases retain:

- rebase;
- cherry-pick;
- stash;
- reset UX;
- revert;
- tags;
- submodules;
- Git LFS;
- SSH key management;
- progress/cancellation UI;
- interactive conflict-resolution UI.

## Exit criteria

The Real Git Engine is implemented and validated.

The next development phase is **Phase E — Android Project Storage**, where Nexora Git will connect this engine to user-selected Android project folders through SAF and managed workspaces.
