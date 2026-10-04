# Real Git Engine

## Decision

Nexora Git uses a real local Git engine based on **libgit2**.

Integration path:

```text
Kotlin domain
     ↓
GitEngine interface
     ↓
Libgit2GitEngine
     ↓
JNI
     ↓
C/C++
     ↓
libgit2
```

GitHub REST/GraphQL calls are not a substitute for this layer.

## Why libgit2

The engine must operate on actual Git repositories and object databases on-device, including offline operation.

Required capabilities include:

- init
- clone
- status
- add/stage
- unstage
- commit
- log
- diff
- branch
- checkout/switch
- remotes
- fetch
- pull
- push
- merge
- conflict detection/resolution

Advanced phases:

- rebase
- cherry-pick
- stash
- reset
- revert
- submodules
- Git LFS integration

## Kotlin API boundary

Native details must be hidden behind a stable Kotlin contract.

Conceptual interface:

```kotlin
interface GitEngine {
    suspend fun init(workspace: GitWorkspace): GitRepository
    suspend fun clone(request: CloneRequest): GitRepository
    suspend fun status(repository: GitRepository): GitStatus
    suspend fun stage(repository: GitRepository, paths: List<String>)
    suspend fun unstage(repository: GitRepository, paths: List<String>)
    suspend fun commit(repository: GitRepository, request: CommitRequest): GitCommit
    suspend fun branches(repository: GitRepository): List<GitBranch>
    suspend fun createBranch(repository: GitRepository, name: String, startPoint: String?)
    suspend fun checkout(repository: GitRepository, ref: String)
    suspend fun fetch(repository: GitRepository, remote: String)
    suspend fun pull(repository: GitRepository, request: PullRequest): PullResult
    suspend fun push(repository: GitRepository, request: PushRequest): PushResult
    suspend fun diff(repository: GitRepository, request: DiffRequest): GitDiff
}
```

Exact public API will be refined during implementation.

## Native safety

JNI code must:

- use explicit ownership rules
- release libgit2 resources deterministically
- map native errors to typed Kotlin errors
- never leak credentials into exception text
- avoid blocking Android main thread
- support cancellation where practical
- keep global native state minimal

## Authentication for Git transport

Git HTTPS authentication should use a runtime credential callback/provider.

Never persist tokens in:

```text
https://TOKEN@github.com/...
```

inside `.git/config`.

Persist clean remotes such as:

```text
https://github.com/owner/repository.git
```

and provide temporary credentials to the transport layer only when needed.

## Pull behavior

Pull is not a single low-level operation.

The engine should model:

```text
fetch
  ↓
analyze local/remote state
  ↓
fast-forward / merge / rebase policy
  ↓
conflict handling if required
```

Nexora Git must never silently destroy local changes.

## Push behavior

Before push:

- detect local commits
- detect remote tracking state
- surface non-fast-forward failures
- surface protected-branch/permission errors
- never silently force push

Any force operation must require explicit confirmation. Prefer force-with-lease semantics when the underlying implementation supports the intended safety model.

## Conflict model

Conflicts must be represented as domain objects rather than raw native output.

The UI should support:

- local version
- remote/incoming version
- result
- use local
- use remote
- use both
- manual editing
- mark resolved

## Progress

Long operations should expose structured progress events:

```text
Connecting
Negotiating
ReceivingObjects
ResolvingDeltas
Checkout
UpdatingReferences
Completed
```

This allows Compose screens and Android notifications to show meaningful progress.

## Testing

Git engine tests must use disposable local repositories and cover at least:

- initialization
- commits
- branch creation/switching
- clone
- fetch
- fast-forward pull
- divergent history
- push
- authentication failures
- conflicts
- binary files
- cancellation
- corrupted repository/error paths
