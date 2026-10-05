# Repository Experience

## Purpose

Phase F turns the platform, Git engine and Android storage foundations into a usable repository workflow.

The feature combines:

- GitHub repository discovery and administration;
- normalized offline repository metadata;
- real libgit2 cloning;
- local-project Git import;
- repository social actions;
- permission-aware settings.

## Repository list

The authenticated repository list is loaded from GitHub through the shared platform client.

```text
GitHub /user/repos
       ↓
REST Link pagination
       ↓
RepositoryGateway
       ↓
Room github_repositories
       ↓
Repositories UI
```

The list requests repositories for the active GitHub identity across owner, collaborator and organization-member affiliations.

Pagination follows the validated GitHub `Link` header and includes a safety cap plus repeated-URL detection.

## Offline metadata

Phase F introduces Room schema v4 with `github_repositories`.

The cache is isolated by GitHub account ID and stores only normalized metadata:

- repository ID and node ID;
- owner/name;
- description;
- visibility/private/fork/archive state;
- language/default branch;
- clean clone and web URLs;
- star/fork/open-issue counts;
- size and timestamps;
- permission flags.

Tokens are never stored in this table.

If repository-list or detail requests fail because of connectivity, server errors or rate limits, Nexora Git may show cached metadata.

Mutations remain online-only.

## Repository details

The detail screen exposes:

- owner/name;
- visibility;
- description;
- default branch;
- language;
- stars/forks/open issues/watchers;
- feature flags;
- effective repository permission level;
- offline snapshot state.

Administration controls are shown only when the repository reports admin permission.

## Create repository

Personal repositories can be created from the Android UI.

Supported Phase F fields:

- name;
- description;
- private/public state;
- initialize with README.

The resulting repository is immediately normalized into the account cache.

## Clone

Remote clones use the Phase D libgit2 engine.

```text
RepositorySummary
      ↓
RepositoryWorkspaceCoordinator
      ↓
WorkspaceRegistry.prepareRemoteClone
      ↓
GitEngine.clone
      ↓
libgit2 HTTPS credential callback
      ↓
WorkspaceRegistry.completeRemoteClone
```

A clone workspace uses the `REMOTE_CLONE` strategy and belongs to the app.

Failed partial clone workspaces are removed.

Existing complete clones are reused rather than duplicated.

### Clone URL safety

Manual clone-by-URL accepts only canonical HTTPS GitHub repository URLs:

```text
https://github.com/owner/repository
https://github.com/owner/repository.git
```

Rejected examples include:

- embedded credentials;
- non-HTTPS protocols;
- hosts other than `github.com`;
- custom ports;
- query/fragment values;
- extra path components.

The OAuth token is never embedded in the remote URL.

## Import local project

Projects selected in Phase E can be promoted to Git repositories.

If `.git` is absent, Nexora Git runs real `GitEngine.init()`.

If `.git` already exists, it is preserved and Nexora Git reads status instead of reinitializing the repository.

The workspace keeps its original storage strategy:

- `DIRECT`;
- `MANAGED`.

## Fork

Fork creation uses the GitHub repository fork API.

GitHub may finish preparing a fork asynchronously after accepting the request, so the UI describes the operation as a fork request rather than assuming every object is instantly ready for clone.

## Star

Star/unstar is a GitHub user action and is executed through the authenticated GitHub platform layer.

The production GitHub App must include the appropriate Starring user permission for the operation.

## Watch

GitHub App user access tokens are not used with the REST repository-subscription mutation endpoints.

Nexora Git uses GraphQL repository fields:

- `viewerCanSubscribe`;
- `viewerSubscription`.

When GitHub reports subscription capability, the app uses `updateSubscription` to move between subscribed and unsubscribed states.

If the capability is unavailable, the watch control is disabled rather than pretending the mutation succeeded.

## Repository settings

Phase F exposes a deliberately bounded settings surface:

- description;
- homepage;
- Issues enabled/disabled;
- Wiki enabled/disabled;
- delete branch after merge.

The control is shown only when GitHub reports admin permission.

Destructive repository deletion, visibility migration and ownership transfer are intentionally not included in Phase F.

## Error behavior

Repository UI translates platform errors into user-facing states:

- authentication required;
- missing GitHub permission;
- primary/secondary rate limit;
- network unavailable;
- repository not found;
- validation errors;
- server errors;
- cached/offline metadata.

Raw access tokens, native error numbers and sensitive response bodies are not shown.

## UI structure

```text
Repositories
├── Create repository
├── Clone URL
├── Open local folder
├── GitHub repositories
│   ├── details
│   └── clone to device
└── On this device
    ├── DIRECT
    ├── MANAGED
    └── REMOTE_CLONE
```

Repository detail:

```text
Repository
├── identity / visibility
├── metadata / statistics
├── Star
├── Watch
├── Fork
├── Clone
└── Settings (admin only)
```

## Security invariants

- repository metadata cache is scoped to account ID;
- authentication secrets are never cached with repository metadata;
- manual clone URLs cannot redirect credentials to another host;
- Git HTTPS uses the Phase D credential callback;
- imported user folders are never deleted as part of removing a direct workspace;
- permission-dependent UI is capability-gated;
- mutations do not silently fall back to cached success.

## Next phase

Phase G builds the **Code Browser** on top of repository/workspace selection.
