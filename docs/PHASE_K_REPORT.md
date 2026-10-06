# Phase K — Pull Requests and Review

Date: 2026-10-05

## Status

**Implementation complete; CI validation pending.**

## K.1 Pull request lifecycle

Implemented:

- list open / closed / all pull requests
- pull request details
- create pull request
- edit title/body/base
- close/reopen
- create as draft
- convert ready pull request to draft
- mark draft ready for review

## K.2 Files and review comments

Implemented:

- changed-file list
- patch hunk display
- inline review comments
- LEFT/RIGHT line targeting
- edit own review comment
- delete own review comment

## K.3 Reviews and checks

Implemented:

- list submitted reviews
- approve
- request changes
- general review comment
- list check runs for the current head SHA
- check-state summary

## K.4 Merge

Implemented:

- repository-aware merge method availability
- merge commit
- squash merge
- rebase merge
- expected head SHA guard
- merge confirmation UX

## K.5 Architecture and validation

Implemented:

- typed `core/pulls` domain
- `PullRequestGateway`
- REST + GraphQL implementation
- Hilt binding
- list/detail ViewModels
- list/detail Compose screens
- Repository → Pull requests → Pull request navigation
- JVM parser tests
- Compose tests
- Phase K documentation

Pending before completion:

- Android CI validation
- Foundation CI validation
- pull-request integration validation
