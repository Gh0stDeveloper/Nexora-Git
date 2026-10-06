# Advanced GitHub

Phase O adds GitHub features that are outside the daily repository, Issues, Pull Requests, Actions and Releases workflow.

## Scope

The Android app exposes an **Advanced GitHub** workspace from repository details.

Implemented capabilities:

- Discussions
  - list recent repository discussions;
  - list repository discussion categories;
  - create a discussion in an existing category.
- Projects V2
  - list Projects linked to the repository;
  - create a Project V2 linked to the current repository when the authenticated user and GitHub App are permitted to do so.
- GitHub Pages
  - detect whether Pages is enabled;
  - show build/source/site state;
  - enable Pages from the repository default branch;
  - request a Pages build.
- Repository security
  - open Dependabot alerts;
  - open code-scanning alerts;
  - open secret-scanning alerts;
  - independent capability/error state for every security feed.
- Gists
  - list the authenticated user's Gists;
  - create public or secret Gists;
  - delete a Gist.
- Codespaces
  - list Codespaces for the authenticated user;
  - create a Codespace for the current repository/default branch;
  - start and stop Codespaces;
  - delete Codespaces.

## Architecture

Phase O follows the shared platform architecture introduced in Phase C.

```text
AdvancedGitHubScreen
        ↓
AdvancedGitHubViewModel
        ↓
AdvancedGitHubGateway
        ↓
GitHubAdvancedGitHubGateway
        ↓
GitHubPlatformClient
   ┌────┴────┐
   ↓         ↓
 REST      GraphQL
```

Authentication, token refresh, API versioning, caching, rate-limit tracking and GitHub error mapping remain centralized in `GitHubPlatformClient`.

Phase O does not create a second HTTP stack and never stores a second credential.

## API mapping

| Capability | API |
|---|---|
| Discussions | GitHub GraphQL |
| Projects V2 | GitHub GraphQL |
| Pages | GitHub REST |
| Dependabot alerts | GitHub REST |
| Code scanning alerts | GitHub REST |
| Secret scanning alerts | GitHub REST |
| Gists | GitHub REST |
| Codespaces | GitHub REST |

The REST layer continues to use the repository's centralized GitHub REST version contract.

## Permission contract

GitHub App access remains least-privilege. A feature is usable only when both the GitHub App permission and the authenticated user's own GitHub authorization allow the operation.

Phase O may require:

| Permission | Minimum access used by Phase O |
|---|---:|
| Discussions (repository) | Read/Write |
| Projects (organization/account as applicable) | Read/Write for creation |
| Pages (repository) | Read/Write |
| Dependabot alerts (repository) | Read |
| Code scanning alerts (repository) | Read |
| Secret scanning alerts (repository) | Read |
| Gists (user/account) | Write for create/delete |
| Codespaces (repository) | Read/Write |

Read-only public-resource behavior is still determined by GitHub. Mutations never assume that public visibility implies write permission.

When the installed GitHub App is upgraded with additional permissions, GitHub may require the installation owner to approve those permissions before the new capability becomes available.

## Capability-aware behavior

Advanced GitHub intentionally treats permission availability as runtime state.

Examples:

- a repository can support Discussions but have Pages disabled;
- the authenticated user can see Dependabot alerts but not secret-scanning alerts;
- a GitHub App can list Gists but be unable to create/delete them until the Gists user permission is approved;
- Codespaces can be unavailable because of repository, organization, billing or policy restrictions.

Security feeds are isolated from one another. A denied secret-scanning request does not erase visible Dependabot or code-scanning results.

The UI surfaces the mapped GitHub error instead of silently falling back to another credential.

## Pages safety

Enabling Pages uses the repository's real default branch obtained from GitHub and the root path `/`.

Nexora Git does not guess that every repository uses `main`.

A build request is available only after GitHub reports the site as configured.

## Codespaces safety

Codespace creation uses the current repository and its actual default branch.

Start, stop and delete operations use the Codespace name returned by GitHub and validate the name before issuing a request.

Nexora Git does not attempt to bypass organization Codespaces policy, billing restrictions or machine availability.

## Gist safety

Gist creation accepts one explicitly named file per mobile creation flow.

The filename is validated before submission. Delete requires the exact Gist identifier returned by GitHub.

Secret Gists are represented as **Secret**, not as private repositories. GitHub's Gist visibility semantics remain authoritative.

## Security view behavior

The Phase O security surface is intentionally read-only.

It aggregates the currently visible open alerts from:

- Dependabot;
- code scanning;
- secret scanning.

Alert mutation/resolution workflows are not performed implicitly. This prevents Nexora Git from changing a repository's security posture while the user is only reviewing findings.

## Tests

Phase O includes:

- JVM parser coverage for Discussions and Projects GraphQL payloads;
- JVM parser coverage for Pages, security feeds, Gists and Codespaces REST payloads;
- Compose coverage proving that all six Advanced GitHub sections render from typed state;
- Android CI compilation, lint, unit-test and instrumentation-APK compilation gates.

## Known external prerequisites

Repository code can define the permission contract, but it cannot approve GitHub App permission changes on behalf of an installation owner.

Production completion still requires the registered Nexora Git GitHub App to request the permissions documented above and each affected installation to approve them where GitHub requires re-authorization.
