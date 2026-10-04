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

Remote data follows a local-first presentation strategy:

```text
GitHub
  ↓
Remote data source
  ↓
Repository
  ↓
Room cache
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
