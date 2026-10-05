# Architecture

## Overview

Nexora Git uses a native Android, feature-modular architecture.

```text
Jetpack Compose UI
        ↓
Presentation / ViewModels
        ↓
Domain use cases
        ↓
Repository interfaces
   ┌────┼───────────────┐
   ↓    ↓               ↓
GitHub  Local Git       Local persistence
API     Engine          Room/DataStore
   ↓    ↓               ↓
HTTPS   libgit2/JNI     Android storage
```

## Architectural style

- Clean Architecture boundaries.
- Feature-oriented modules.
- MVVM/MVI-style unidirectional state.
- Kotlin Coroutines and Flow.
- Dependency inversion between UI/domain and external implementations.

## Proposed modules

```text
app/

core/
├── api/
├── auth/
├── common/
├── database/
├── design-system/
├── git/
├── model/
├── network/
├── security/
├── storage/
└── testing/

feature/
├── auth/
├── home/
├── explore/
├── repositories/
├── repository/
├── files/
├── editor/
├── git/
├── branches/
├── commits/
├── issues/
├── pulls/
├── actions/
├── releases/
├── notifications/
├── search/
├── organizations/
├── profile/
└── settings/

native/
└── git/
    ├── libgit2/
    ├── jni/
    └── CMakeLists.txt
```

## Dependency rule

Feature modules must not directly depend on Retrofit, libgit2 or Room implementations.

Example:

```text
RepositoryScreen
      ↓
RepositoryViewModel
      ↓
GetRepositoryUseCase
      ↓
RepositoryGateway
      ↓
GitHubRepositoryDataSource
```

For Git:

```text
ChangesScreen
      ↓
ChangesViewModel
      ↓
GetGitStatusUseCase
      ↓
GitEngine
      ↓
Libgit2GitEngine
      ↓
JNI
      ↓
libgit2
```

## Remote versus local responsibility

### GitHub API layer

Responsible for:

- profile
- organizations
- repository administration
- issues
- pull requests
- reviews
- notifications
- Actions
- releases
- GitHub Projects
- Discussions
- security information where supported

### Local Git engine

Responsible for:

- repository database
- object graph
- working tree
- index
- commits
- refs/branches
- diffs
- remotes
- clone/fetch/pull/push
- merges
- conflicts

## Background work

Long operations must not block the main thread.

Use:

- Coroutines for cancellable application work.
- WorkManager for durable tasks that need to survive process death where appropriate.
- Foreground execution/notification when Android requires it for user-visible long transfers.

Examples:

- clone
- fetch
- pull
- push
- large project import
- artifact download

## State and caching

The transport layer and the feature-data layer have different cache responsibilities.

### Phase C transport cache

The shared GitHub platform layer uses a bounded, account-scoped **in-memory** cache.

REST GET requests support ETag revalidation with `If-None-Match` and reuse cached bodies on `304 Not Modified`. Network-first requests may fall back to memory cache on connectivity failure.

This cache is intentionally not written to disk because generic GitHub responses may contain private repository metadata.

### Feature data caches

Feature repositories may introduce Room-backed normalized caches where offline behavior requires them:

```text
GitHub Platform Client
  ↓
Feature remote data source
  ↓
Feature repository
  ↓
Optional normalized Room cache
  ↓
Flow
  ↓
UI
```

Lists should use Paging 3 where appropriate.

## Error model

A shared domain error hierarchy should distinguish:

- Network
- Authentication
- Permission
- Rate limit
- Not found
- Conflict
- Git conflict
- Storage permission
- Disk full
- Large file
- Server
- Unknown

The UI should convert low-level errors into actionable user messages instead of exposing raw HTTP codes or native error numbers.


## GitHub platform layer

Phase C introduces a reusable authenticated transport layer:

```text
Feature / repository
       ↓
GitHubPlatformClient
   ┌───┴──────────┐
   ↓              ↓
REST client   GraphQL client
   ↓              ↓
AuthSessionRepository
   ↓
GitHub user access token
```

Shared responsibilities:

- attach the active GitHub account token;
- proactively refresh expiring tokens;
- perform one controlled retry after an HTTP 401;
- centralize the REST API version;
- validate official GitHub API URLs;
- track rate limits by resource;
- parse REST pagination;
- expose GraphQL page information;
- parse GitHub App permission requirements;
- normalize HTTP/GraphQL errors;
- isolate caches by GitHub account.

Feature code should consume `GitHubPlatformClient` rather than building ad-hoc OkHttp calls.


## Native Git engine

Phase D provides the real local Git implementation:

```text
Feature domain
      ↓
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
libgit2 + Mbed TLS
```

Native source lives under `native/git/`.

The application does not invoke a shell executable and does not depend on Termux or a separately installed Git binary.

### Credential boundary

GitHub OAuth credentials originate in `AuthSessionRepository` and are supplied to libgit2 only for approved `https://github.com` transports.

The remote URL stored by Git remains credential-free.

### Threading

`Libgit2GitEngine` sends native operations to `Dispatchers.IO`; native Git work must never run on the Compose/main thread.

### Native dependency reproducibility

libgit2 and Mbed TLS are pinned to immutable upstream commit SHAs through CMake FetchContent.

The Android build produces native libraries for:

- arm64-v8a;
- armeabi-v7a;
- x86_64.


## Android project storage

Phase E bridges Android document storage with the POSIX filesystem expected by libgit2.

```text
OpenDocumentTree
      ↓
persisted SAF grant
      ↓
Project scanner
      ↓
WorkspaceRegistry / Room
      ↓
┌───────────────┬────────────────────────┐
↓               ↓
DIRECT          MANAGED
real path       SAF → app-private mirror
↓               ↓
└────────── GitEngine / libgit2 ─────────┘
```

### Storage safety boundaries

- arbitrary `content://` URIs are never converted through undocumented `_data` path hacks;
- managed paths are canonicalized and constrained below the workspace root;
- source filenames containing path separators or traversal names are rejected;
- removing a direct workspace never deletes the user's directory;
- managed `.git` metadata is protected from later source syncs;
- source/managed concurrent changes become explicit sync conflicts;
- sync metadata lives outside the repository so it cannot appear as an untracked project file.

### Risk scan boundary

The project scanner records path/size/ignore metadata and warnings. It never stores detected secret values in Room or the sync manifest.


## Repository experience

Phase F composes GitHub platform data, persistent normalized metadata and local Git workspaces behind a feature-level gateway.

```text
Repositories / Repository Detail
              ↓
       RepositoryGateway
       ↙             ↘
GitHubPlatformClient  Room v4 metadata cache
       ↓
 REST + GraphQL

Clone / Import local project
              ↓
RepositoryWorkspaceCoordinator
        ↙             ↘
   GitEngine       WorkspaceRegistry
      ↓                 ↓
   libgit2      DIRECT / MANAGED / REMOTE_CLONE
```

### Remote repository cache

The generic transport cache from Phase C remains memory-only. Phase F adds a separate normalized Room cache specifically for repository-list metadata, keyed by GitHub account ID.

It stores repository metadata and permission flags, never access tokens or refresh tokens.

On network/server/rate-limit failures, list and detail flows may surface cached metadata. Mutation actions remain online-only.

### Repository workspaces

A remote clone is registered as `REMOTE_CLONE` and lives only under app-private workspace storage.

Imported projects retain their existing `DIRECT` or `MANAGED` strategy. `RepositoryWorkspaceCoordinator` runs `git init` only when the workspace does not already contain a `.git` directory.

Deleting a `REMOTE_CLONE` may delete its app-owned directory. Deleting a `DIRECT` workspace never deletes the user's source directory.

### Permission boundaries

Repository UI uses the permissions returned by GitHub to gate administration controls.

- repository creation/settings are remote API operations;
- star/unstar is a GitHub user action;
- watch/unwatch uses GraphQL capability checks and subscription state;
- clone credentials continue to cross into libgit2 only through the Phase D credential callback boundary.

The feature layer does not embed tokens in clone URLs or persist them in Room.


## Code browser

Phase G adds a read-only local browsing layer over registered workspaces.

```text
Workspace card
     ↓
CodeBrowserScreen / ViewModel
     ↓
CodeBrowserFileSystem
     ↓
WorkspacePathPolicy
     ↓
DIRECT / MANAGED / REMOTE_CLONE filesystem

History / Blame
     ↓
GitEngine
     ↓
JNI
     ↓
libgit2
```

### Filesystem trust boundary

Navigation inputs are always workspace-relative. Canonical-path checks prevent `..` traversal and symlink escapes, while `.git` is explicitly hidden and blocked from browsing/share.

The browser does not mutate project files.

### Rendering boundary

Text rendering is bounded to 2 MiB per selected file. Binary content is not coerced into text. Image previews use sampled bitmap decoding. Markdown rendering does not execute embedded HTML or scripts.

Syntax highlighting in Phase G is intentionally lexical and lightweight; Tree-sitter remains a Phase Q concern.

### Git metadata

History and blame are computed locally through libgit2, not through GitHub APIs. This keeps these views available offline and aligned with the exact checked-out repository state.

### Android file actions

Sharing copies one selected file into a narrowly scoped cache directory exposed by FileProvider. Saving a copy uses Android's document destination flow. Neither operation requires broad filesystem permission.
