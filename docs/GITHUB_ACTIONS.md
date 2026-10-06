# GitHub Actions

> **Current reference:** This document describes the implemented Nexora Git system. See the [Documentation Hub](README.md) for navigation.

Nexora Git includes a native GitHub Actions control surface.

## Workflows

From a repository, open **GitHub Actions**.

Nexora Git lists repository workflows with:

- workflow name
- workflow file path
- active/disabled state
- workflow-scoped run filtering

Active workflows expose **Dispatch**. The dispatch dialog accepts:

- Git ref
- optional workflow inputs

Inputs use one `key=value` pair per line. Nexora Git validates input names locally and GitHub remains authoritative for whether the workflow supports `workflow_dispatch` and which inputs are accepted.

## Workflow runs

The run browser supports:

- all repository runs
- one selected workflow
- status/conclusion filtering
- run number and attempt
- event
- branch and head SHA
- triggering actor
- current status and conclusion

## Run details

A run detail includes:

- jobs
- job status/conclusion
- runner metadata
- labels
- steps
- step status/conclusion
- artifacts

Run controls include:

- cancel
- re-run all jobs
- re-run failed jobs

Buttons are enabled only for states where the operation is meaningful, while GitHub remains authoritative for permissions and state transitions.

## Logs

Job logs use GitHub's authenticated log-download endpoint.

Security behavior:

1. Nexora Git authenticates only the initial request to `api.github.com`.
2. Redirect following is disabled for that authenticated request.
3. The signed HTTPS redirect URL is validated.
4. The redirected request is sent **without the GitHub Authorization header**.
5. The in-app log preview is capped at 2 MiB to protect Android memory.
6. A truncation notice is shown when a larger log is returned.

This prevents an OAuth/GitHub App token from being forwarded to GitHub's external signed-download host.

## Artifacts

Run artifacts show:

- name
- compressed size
- availability/expired state
- expiration timestamp when provided

Downloads:

- use the same credential-isolating redirect flow as logs
- stream bytes directly to disk
- write to a temporary `.part` file
- promote the complete file only after a successful transfer
- reject expired artifacts
- enforce a 2 GiB safety limit
- surface disk-full errors

Artifacts are stored in Nexora Git's app-specific downloads directory under:

`NexoraGit/actions-artifacts`

## GitHub App permissions

For the complete GitHub Actions experience, the production GitHub App should grant, as applicable:

- Actions: read/write
- Contents: read
- Metadata: read

Repository policy, organization policy, Actions configuration, environment protection rules, and GitHub permissions remain authoritative.

## Validation

Validation includes:

- JVM parser tests for workflows/runs/jobs/steps/artifacts
- JVM tests for dispatch input parsing
- Compose coverage for workflows/runs
- Compose coverage for run controls/jobs/steps/artifacts
- Repository → Actions navigation coverage
- Android CI
- Foundation CI

---

[← Documentation hub](README.md)
