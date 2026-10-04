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
