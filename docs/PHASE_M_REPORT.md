# Phase M — Releases

> **Historical implementation record — Complete.** This report is retained for traceability. For current product behavior and operations, use the [Documentation Hub](README.md).

Date: 2026-10-05

## Status

**Complete and CI validated.**

## M.1 Tags

Implemented:

- list Git tags
- show commit SHA
- create lightweight tag
- delete tag
- tag-name validation
- commit-SHA validation

## M.2 Release lifecycle

Implemented:

- list releases
- published/draft/prerelease filters
- release detail
- create release
- generated release notes option
- edit release
- publish draft
- convert published release to draft
- prerelease state
- latest-release policy
- delete release
- immutable-release protection

## M.3 Release assets

Implemented:

- asset metadata
- size/download counts
- digest display
- upload via Android document picker
- rename / label update
- delete
- authenticated download

## M.4 Transfer safety

Implemented:

- app-private upload staging
- streaming upload
- streaming download
- HTTPS redirect validation
- Authorization stripping on signed external downloads
- atomic `.part` promotion
- 2 GiB upload/download safety limits
- disk-full handling
- local download path feedback

## M.5 Architecture and validation

Implemented:

- typed `core/releases` domain
- `ReleasesGateway`
- REST implementation
- release asset binary client
- Hilt binding
- list/detail ViewModels
- list/detail Compose screens
- Repository → Releases → Release navigation
- JVM parser tests
- Compose tests
- Phase M documentation

## Validation results

- **Android CI — success**
- **Foundation CI — success**
- pull-request integration validation — success
- build, lint, JVM tests and Android test APK assembly passed

## Result

Phase M is complete. The next roadmap milestone is **Phase N**.

---

[← Documentation hub](README.md)
