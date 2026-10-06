# Phase F — Repository Experience Report

> **Historical implementation record — Complete.** This report is retained for traceability. For current product behavior and operations, use the [Documentation Hub](README.md).

Date: 2026-10-04

## Status

**Implementation complete and functional code CI validated.**

## Delivered

### GitHub repository list

- authenticated `/user/repos` loading;
- Link-header pagination;
- pagination-cycle safety;
- active-account isolation;
- Room v4 normalized repository cache;
- offline fallback for network/server/rate-limit failures.

### Repository details

- online repository detail loading;
- cached metadata fallback;
- visibility/fork/archive/default-branch/language presentation;
- star/fork/issues/watchers/size information;
- GitHub effective permission presentation;
- offline snapshot indication.

### Create repository

- repository name validation;
- description;
- public/private choice;
- README initialization;
- newly created repository inserted into cache.

### Local project import

- imported Phase E workspace lookup;
- existing `.git` preservation;
- real `GitEngine.init` when repository metadata is absent;
- current branch/status integration;
- active account association.

### Clone

- clone from remote repository card;
- clone from validated GitHub HTTPS URL;
- libgit2 clone through Phase D;
- app-private `REMOTE_CLONE` workspace;
- partial-clone cleanup;
- completed-clone reuse;
- clean remote URLs without embedded OAuth tokens.

### Fork

- repository fork request through GitHub API;
- response normalized back into repository metadata.

### Star

- star/unstar operation;
- viewer state integrated into detail UI.

### Watch

- GraphQL `viewerCanSubscribe`;
- GraphQL `viewerSubscription`;
- `updateSubscription` mutation;
- UI disabled when capability is unavailable.

### Repository settings

Admin-only Phase F settings:

- description;
- homepage;
- Issues;
- Wiki;
- delete branch after merge.

Destructive delete/transfer/visibility operations are intentionally outside this phase.

### UI

Repositories now separates:

- GitHub repositories;
- local development workspaces.

Actions include:

- create repository;
- clone URL;
- open folder;
- refresh remote list;
- open repository details;
- clone repository;
- initialize/open local Git;
- sync/rescan workspace;
- safe local workspace removal.

Repository details include:

- Star;
- Watch;
- Fork;
- Clone;
- permission-aware Settings.

Top-level bottom navigation is hidden on the repository detail route.

## Database

Room schema version:

```text
3 → 4
```

New table:

```text
github_repositories
```

The table stores repository metadata only. No authentication token is persisted there.

## Tests

Phase F adds coverage for:

- canonical GitHub clone URLs;
- malicious/unsupported clone URL forms;
- REST repository JSON parsing;
- repository permission parsing;
- repository detail parsing;
- Room cache entity/domain round trips;
- repository list Compose rendering;
- repository detail actions and admin settings.

## Functional CI validation

Final functional code SHA:

```text
2640f52766adbb47b38a7f4b7b491f3418071d86
```

Android CI:

```text
Run: 37230457435
Conclusion: success
```

Validated:

- debug APK;
- Android instrumentation-test APK compilation;
- JVM unit tests;
- Android lint;
- existing native libgit2/NDK build transitively through Android;
- debug APK artifact upload.

Artifact:

```text
Name: NexoraGit-debug
Artifact ID: 11313562371
Size: 25,204,271 bytes
SHA-256: 8300e642404ada6602c1118c27ced38212451bcbf26638c17c2e70936191ef29
```

Foundation CI also passed on the functional code SHA.

## Production GitHub App prerequisites

The real GitHub App still needs to be created/configured outside the repository.

Phase F requires the installed App/token to have permissions appropriate for:

- repository metadata;
- repository administration operations that are exposed;
- contents/Git HTTPS access where applicable;
- Starring user actions.

Watch controls are additionally capability-gated through GraphQL.

## Next phase

**Phase G — Code Browser**

---

[← Documentation hub](README.md)
