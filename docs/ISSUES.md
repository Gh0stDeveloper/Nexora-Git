# Issues

> **Current reference:** This document describes the implemented Nexora Git system. See the [Documentation Hub](README.md) for navigation.

Nexora Git includes a native GitHub Issues workflow built on the authenticated GitHub REST platform layer.

## Entry point

Open a repository and choose **Issues**. The entry point is shown only when GitHub Issues are enabled for that repository.

## Issue list

The Issues screen supports:

- open / closed / all state filters
- text search scoped to the current repository
- label filters
- assignee filters
- milestone filters
- sort/direction through the typed domain contract
- safe paginated loading
- Pull Request exclusion when GitHub returns Issues and Pull Requests together

Search uses GitHub's repository-scoped issue search. Non-search listing uses the repository Issues endpoint.

## Create and edit

New issues can include:

- title
- description
- labels
- assignees
- milestone

Existing issues support:

- title edits
- description edits
- label replacement
- assignee replacement
- milestone assignment/removal
- close
- reopen

GitHub remains authoritative for repository permissions and repository rules.

## Comments

Issue detail supports:

- paginated comment loading
- add comment
- edit own comment
- delete own comment

The UI only exposes edit/delete controls for comments authored by the active GitHub account. Server-side GitHub authorization remains authoritative.

## Reactions

Nexora Git supports GitHub issue and issue-comment reactions:

- +1
- -1
- laugh
- confused
- heart
- hooray
- rocket
- eyes

Reaction counts are parsed from GitHub's issue/comment payloads. Reaction mutations go through the same authenticated REST layer and cache invalidation path as other mutations.

## Metadata

Repository labels, assignable users and milestones are loaded from GitHub and reused for filtering, issue creation and issue editing.

## Safety and correctness

- repository owner/name identifiers are validated before requests
- issue/comment/reaction ids must be positive
- issue/comment text is validated before mutation
- pagination is bounded and repeated next URLs are rejected
- Pull Requests are excluded from Issue lists
- all requests use the existing GitHub OAuth token flow
- REST mutations invalidate account-scoped response cache through the platform layer
- GitHub permission/rate-limit/server errors are mapped to user-facing Issue errors

## Tests

Validation includes:

- JVM JSON parser tests
- search-response Pull Request exclusion test
- issue metadata/reaction parsing tests
- Compose test for issue list/filter/create surface
- Compose test for issue detail/comment/reaction surface
- repository-detail navigation callback coverage

---

[← Documentation hub](README.md)
