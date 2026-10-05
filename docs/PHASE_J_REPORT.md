# Phase J — Issues

Date: 2026-10-05

## Status

**Implementation complete; CI validation pending.**

## J.1 — Issue discovery

Implemented:

- repository Issues entry point
- list open / closed / all issues
- repository-scoped issue search
- labels filter
- assignee filter
- milestone filter
- typed sort/direction contract
- bounded REST pagination
- Pull Request exclusion from mixed GitHub issue payloads

## J.2 — Issue authoring and lifecycle

Implemented:

- create issue
- edit title/body
- set labels
- set assignees
- set/remove milestone
- close issue
- reopen issue

## J.3 — Comments and reactions

Implemented:

- paginated comments
- add comment
- edit own comment
- delete own comment
- issue reactions
- comment reactions
- reaction summaries

## J.4 — Architecture and validation

Implemented:

- dedicated `core/issues` domain
- `IssueGateway` contract
- GitHub REST implementation through `GitHubPlatformClient`
- Hilt binding
- list/detail ViewModels
- list/detail Compose screens
- Repository → Issues → Issue navigation
- JVM parser tests
- Compose UI tests
- Issues documentation

Pending before completion:

- Android CI success
- Foundation CI success
- final roadmap status update
