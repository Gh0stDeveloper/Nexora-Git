# Releases

> **Current reference:** This document describes the implemented Nexora Git system. See the [Documentation Hub](README.md) for navigation.

Nexora Git includes a native GitHub Releases workflow.

## Entry point

Open a repository and choose **Releases**.

The screen presents both:

- GitHub Releases
- Git tags

Published releases, drafts and prereleases can be filtered independently.

## Tags

Nexora Git supports:

- list tags
- show target commit SHA
- create lightweight tags
- delete tags

Lightweight tag creation uses GitHub's Git references endpoint and creates a `refs/tags/<name>` reference pointing at the selected commit SHA.

Deleting a tag does not delete the referenced commit.

## Releases

Release creation supports:

- tag name
- target branch or commit
- release name
- Markdown release notes
- draft
- prerelease
- generated release notes
- latest-release policy

Existing releases support:

- edit tag/name/body/target
- draft ↔ published transitions
- prerelease state
- latest-release policy
- delete release

GitHub remains authoritative for repository permissions, immutable releases and repository policy.

## Immutable releases

When GitHub reports a release as immutable, Nexora Git disables:

- release edits
- publishing/draft conversion
- asset upload
- asset rename
- asset delete
- release delete

Asset downloads remain available.

## Release assets

Release detail supports:

- asset list
- MIME type metadata
- file size
- download count
- digest when GitHub provides one
- uploader metadata
- upload
- rename / label update
- delete
- download

### Upload safety

Asset upload uses Android's document picker.

Nexora Git:

1. reads the selected content URI
2. copies it to an app-private temporary file while enforcing a 2 GiB limit
3. uploads the raw binary file to GitHub's official release asset upload host
4. deletes the temporary file after completion
5. maps disk-full and GitHub errors to user-facing failures

No broad filesystem permission is required.

### Download safety

Release asset download uses GitHub's authenticated asset endpoint with `application/octet-stream`.

Nexora Git:

1. authenticates only the GitHub API request
2. accepts either direct binary content or an HTTPS redirect
3. never forwards the GitHub Authorization header to an external signed download host
4. streams data directly to a `.part` file
5. enforces a 2 GiB download limit
6. atomically promotes the completed download
7. removes partial files when a transfer fails

Release assets are stored in the app-specific downloads directory under:

`NexoraGit/release-assets`

## Permissions

For full release authoring, the production GitHub App should grant repository **Contents: read/write**. Read-only access is sufficient for public/private release and tag browsing when the account otherwise has access.

If a release target modifies workflow files relative to the default branch, GitHub may additionally require workflow-write authorization for release creation.

## Validation

Validation includes:

- JVM parser tests for tags, releases and assets
- Compose tests for Releases
- Compose tests for Release Detail
- repository navigation coverage
- Android CI
- Foundation CI

---

[← Documentation hub](README.md)
