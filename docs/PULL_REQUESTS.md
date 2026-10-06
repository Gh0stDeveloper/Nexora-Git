# Pull Requests and Review

> **Current reference:** This document describes the implemented Nexora Git system. See the [Documentation Hub](README.md) for navigation.

Nexora Git includes a native GitHub pull request and code review workflow.

## Pull request discovery

From a repository, open **Pull requests**.

The screen supports:

- open / closed / all pull request states
- repository-scoped listing
- draft and merged state
- head/base branch visibility
- commit/change statistics
- pull request creation

## Create and edit

New pull requests support:

- title
- description
- head branch
- base branch
- draft creation
- maintainer modification preference

Existing pull requests support:

- title/body edits
- base branch changes
- close/reopen
- draft ↔ ready-for-review transitions

Draft creation and normal edits use GitHub REST. Draft/ready transitions use the official GitHub GraphQL mutations because the REST update endpoint does not expose that state transition.

## Changed files

The Files tab displays:

- filename/status
- additions/deletions
- rename source path
- GitHub patch hunks when available

Inline review comments use the current pull request head SHA plus explicit path, line and LEFT/RIGHT side. This avoids silently anchoring comments to a stale commit.

## Reviews

The Reviews tab includes:

- submitted reviews
- inline review comments
- editing/deleting the active user's own inline comments
- approve
- request changes
- general review comment

GitHub remains authoritative for who may approve, request changes, edit comments or merge.

## Checks

The Checks tab reads check runs for the current pull request head commit and shows:

- check name
- status
- conclusion
- passed/failed/running summary

## Merge

Nexora Git reads repository merge settings and only exposes enabled merge methods:

- merge commit
- squash
- rebase

A merge request includes the exact pull request head SHA. If the head changes before GitHub processes the request, GitHub rejects the merge rather than merging an unreviewed revision.

## GitHub App permissions

Production GitHub App configuration should grant, as applicable:

- Pull requests: read/write
- Checks: read
- Contents: read/write for repository changes and merge-related workflows
- Metadata: read

Repository rules and branch protection remain authoritative.

## Validation

Validation includes:

- JVM parser tests for pull requests/files/reviews/comments/checks/merge results
- Compose list/creation coverage
- Compose detail/review/merge coverage
- repository-detail navigation callback coverage
- Foundation CI
- Android CI

---

[← Documentation hub](README.md)
