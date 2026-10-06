# Phase L — GitHub Actions

Date: 2026-10-05

## Status

**Implementation complete; CI validation pending.**

## L.1 Workflows

Implemented:

- repository workflow list
- active/disabled state
- workflow selection
- manual workflow dispatch
- Git ref selection
- generic workflow inputs

## L.2 Workflow runs

Implemented:

- repository run list
- workflow-scoped run list
- status/conclusion filters
- run detail
- run number/attempt
- event
- head branch/SHA
- actor

## L.3 Jobs and steps

Implemented:

- jobs
- job state/conclusion
- runner metadata
- labels
- steps
- step state/conclusion

## L.4 Logs

Implemented:

- authenticated GitHub job-log request
- HTTPS signed redirect validation
- token stripping before external download
- 2 MiB in-app preview cap
- truncation signal
- selectable monospace log viewer

## L.5 Run controls

Implemented:

- cancel workflow run
- re-run all jobs
- re-run failed jobs
- state-aware control enablement

## L.6 Artifacts

Implemented:

- run artifacts
- size and expiration metadata
- expired-artifact blocking
- streaming ZIP download
- atomic partial-file handling
- 2 GiB safety limit
- disk-full handling
- local download path feedback

## L.7 Architecture and validation

Implemented:

- typed `core/actions` models
- `GitHubActionsGateway`
- GitHub REST implementation
- secure binary downloader
- Hilt binding
- list/detail ViewModels
- Compose screens
- Repository → Actions → Run navigation
- JVM parser tests
- dispatch parser tests
- Compose tests
- Phase L documentation

Pending before completion:

- Android CI validation
- Foundation CI validation
- pull-request integration validation
