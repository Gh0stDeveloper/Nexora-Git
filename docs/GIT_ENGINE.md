# Real Git Engine

> **Current reference:** This document describes the implemented Nexora Git system. See the [Documentation Hub](README.md) for navigation.

## Implementation

Nexora Git uses a real local Git engine based on **libgit2**. GitHub REST/GraphQL calls are not used as a substitute for Git operations.

```text
Feature / use case
       ↓
GitEngine
       ↓
Libgit2GitEngine
       ↓
NativeGitBridge
       ↓
JNI
       ↓
nexoragit_core (C++)
       ↓
libgit2
       ↓
Git object database / index / refs / working tree / remotes
```

All Kotlin-facing operations are suspend functions and execute native work on `Dispatchers.IO`.

## Native dependency baseline

The native Git stack pins security-sensitive dependencies by immutable commit SHA.

### libgit2

```text
Release: v1.9.7
Commit: 49e408b3208bc3093757a1c2db938d3590f3f412
```

### Mbed TLS

```text
Release: 3.6.7 LTS
Commit: 068ff080b369adfac81509f9b57b2afabaf82dc5
```

The dependency graph is resolved through CMake FetchContent. No prebuilt or opaque Git engine binary is committed to the repository.

## Android toolchain

```text
Android NDK: 27.2.12479018
CMake:       3.22.1
C:           C99
C++:         C++17
```

Supported APK ABIs:

```text
arm64-v8a
armeabi-v7a
x86_64
```

## Native build

The native entrypoint is:

```text
native/git/CMakeLists.txt
```

Primary targets:

```text
nexoragit_core
    static C++ library containing the Git engine

nexoragit_native
    Android shared JNI library
```

For host CI:

```text
nexoragit_native_tests
```

libgit2 is configured with:

- HTTPS enabled through Mbed TLS;
- SSH disabled in the native Git transport;
- GSSAPI disabled;
- NTLM disabled;
- bundled zlib;
- builtin regex backend;
- libgit2 CLI/tests/examples/fuzzers disabled in the Android dependency build.

Nexora Git itself owns the focused native tests used by this project.

## Runtime initialization

`NativeGitBridge.ensureInitialized()` initializes libgit2 once per process.

Android runtime configuration:

```text
HOME
→ app internal files directory

TLS trust directory
→ /system/etc/security/cacerts
```

This avoids relying on a shell environment or Termux.

## Kotlin contract

The public application contract is `GitEngine`.

Implemented operations:

```kotlin
interface GitEngine {
    suspend fun version(): String
    suspend fun init(workspacePath: String): GitRepository
    suspend fun clone(request: GitCloneRequest): GitRepository
    suspend fun status(repositoryPath: String): GitStatus
    suspend fun stage(repositoryPath: String, paths: List<String>)
    suspend fun unstage(repositoryPath: String, paths: List<String>)
    suspend fun commit(
        repositoryPath: String,
        message: String,
        author: GitAuthor,
    ): GitCommit
    suspend fun branches(repositoryPath: String): List<GitBranch>
    suspend fun createBranch(
        repositoryPath: String,
        name: String,
        startPoint: String? = null,
    )
    suspend fun checkout(repositoryPath: String, ref: String)
    suspend fun fetch(repositoryPath: String, remote: String = "origin")
    suspend fun pull(request: GitPullRequest): GitMergeResult
    suspend fun push(request: GitPushRequest): GitPushResult
    suspend fun diff(
        repositoryPath: String,
        mode: GitDiffMode = GitDiffMode.ALL,
    ): GitDiff
    suspend fun merge(
        repositoryPath: String,
        ref: String,
        author: GitAuthor,
    ): GitMergeResult
    suspend fun conflicts(repositoryPath: String): List<GitConflict>
}
```

Feature code must depend on `GitEngine`, not directly on JNI or libgit2.

## Repository initialization

`init()` creates a real non-bare repository and sets symbolic HEAD to:

```text
refs/heads/main
```

The engine handles unborn repositories correctly before the first commit.

## Status

Status includes:

- current branch;
- index changes;
- working-tree changes;
- untracked paths;
- renamed/type-changed entries where reported by libgit2;
- conflict state.

Native status flags are preserved for advanced UI interpretation.

## Staging

### Stage

Paths are added to the real Git index.

Deleted tracked paths are removed from the index where appropriate.

### Unstage

For normal repositories, selected paths are reset to HEAD while preserving working-tree content.

For an unborn repository, paths are removed directly from the index.

An empty unstage request is a no-op.

## Commits

Commits are created directly in the local object database from the current index tree.

Nexora Git supplies an explicit author:

```text
name
email
```

The engine supports both initial and parented commits.

## Branches and checkout

The engine supports:

- local branch enumeration;
- remote branch enumeration;
- current HEAD identification;
- upstream metadata;
- local branch creation;
- safe branch checkout;
- detached revision checkout.

Branch checkout uses libgit2's safe checkout strategy and does not intentionally overwrite conflicting local changes.

## Clone

Clone uses `git_clone` with:

- real object transfer;
- checkout;
- runtime credentials;
- no token embedded in the repository URL.

## Fetch

Fetch resolves a named remote, defaults to `origin`, updates FETCH_HEAD and remote refs, and supplies credentials only through libgit2 callbacks.

## Pull

Pull is implemented explicitly as:

```text
fetch
  ↓
resolve checked-out local branch
  ↓
resolve upstream / origin/<branch>
  ↓
merge analysis
  ├── up to date
  ├── fast-forward
  └── normal merge
          ↓
       conflicts?
```

Results are represented by:

```text
UP_TO_DATE
FAST_FORWARD
MERGED
CONFLICTS
```

A conflicted pull leaves the repository in a conflict state for the integrated conflict-resolution workflow. It does not silently discard either side.

Merge-based pull remains the foundational engine primitive; rebase-based pull and explicit rebase workflows are available through the higher-level Git workflow.

## Push

Push sends an explicit refspec or, when omitted, the current local branch to the same branch name on the selected remote.

No automatic force push is implemented.

A non-fast-forward or protected-branch failure is surfaced through the native Git error path.

## Diff

Supported modes:

```text
ALL
STAGED
UNSTAGED
```

The engine returns:

- unified patch;
- changed file count;
- insertions;
- deletions.

## Merge

Merge supports:

- up-to-date detection;
- fast-forward;
- normal two-parent merge commit;
- conflict detection.

Normal merge commits use the supplied Git author.

## Conflicts

Conflicts are read from the real index conflict iterator and returned as domain objects containing:

- path;
- ancestor path;
- ours path;
- theirs path.

Content-level conflict resolution is handled by the Git workbench and integrated editor.

## GitHub HTTPS credentials

Nexora Git uses the GitHub user access token produced by the authentication layer.

Credentials are injected at runtime:

```text
GitHub App OAuth session
       ↓
GitCredentialProvider
       ↓
host security policy
       ↓
libgit2 credential callback
       ↓
HTTPS request only
```

Tokens are never written into:

```text
.git/config
remote URLs
cache keys
native error messages by Nexora Git
```

Clean remote example:

```text
https://github.com/owner/repository.git
```

### Host isolation

OAuth credentials are released only for standard HTTPS remotes on:

```text
github.com
```

The policy is checked twice:

1. Kotlin `GitRemoteSecurityPolicy`;
2. native libgit2 credential callback.

Non-GitHub hosts, HTTP, SSH, embedded-userinfo URLs and custom ports do not receive the GitHub OAuth token.

## SSH

SSH transport is deliberately disabled in the current native Git transport.

This does not affect the primary Nexora Git authentication architecture because GitHub access uses GitHub App OAuth over HTTPS.

A future SSH-key transport would require its own secure key-management model and explicit credential policy.

## Native errors

Native failures are converted into:

```text
GitNativeException
├── nativeCode
├── nativeClass
└── sanitized operation context / libgit2 message
```

JNI owns conversion only; feature UI must map these errors into user-facing domain states rather than exposing native numeric codes directly.

## JNI safety

The bridge:

- converts Java strings and arrays explicitly;
- catches C++ exceptions at the JNI boundary;
- releases local JNI references;
- releases libgit2 resources deterministically;
- keeps native initialization process-wide and minimal;
- keeps credentials ephemeral.

Release builds preserve JNI entrypoints through explicit R8/ProGuard rules.

## Native testing

`Native Git CI` compiles the same `nexoragit_core` code on a disposable Linux host and uses real temporary repositories.

The workflow covers:

- libgit2 initialization/version;
- repository init on `main`;
- untracked status;
- stage;
- staged diff;
- commit;
- branch creation;
- branch checkout;
- fast-forward merge;
- unstaged diff;
- unstage preserving working-tree content;
- branch enumeration;
- bare remote creation;
- remote URL inspection;
- push;
- clone;
- fetch;
- remote branch update;
- fast-forward pull;
- divergent histories;
- merge conflict generation;
- conflict enumeration.

No mocked Git repository is used for these native workflow tests.

## Android validation

Android CI installs the pinned NDK and CMake versions and builds libgit2/Mbed TLS for every supported ABI as part of the APK build.

It also validates:

- Kotlin/JNI contract compilation;
- JVM tests;
- instrumentation-test APK compilation;
- Android lint;
- debug APK packaging.

## Extended Git capabilities

The foundational libgit2 engine is extended by the higher-level Git workflow with:

- rebase and rebase-based pull;
- cherry-pick;
- stash;
- reset and revert;
- local tags;
- submodules;
- Git LFS upload/download and local object hydration;
- conflict resolution and recovery;
- guarded force-with-lease;
- multi-remote and upstream controls.

See [DAILY_GIT_WORKFLOW.md](DAILY_GIT_WORKFLOW.md) and [ADVANCED_GIT.md](ADVANCED_GIT.md).

SSH transport remains intentionally disabled until a dedicated secure key-management design is implemented.

---

[← Documentation hub](README.md)
