# Phase N — Account and Social

Date: 2026-10-05

## Status

**Complete and CI validated for GitHub App-supported APIs.**

## N.1 Profile

Implemented:

- authenticated GitHub profile
- profile metrics
- bio/company/location/site/email/X metadata
- profile editing
- hireable state
- active-account refresh
- existing multi-account switching preserved

## N.2 Organizations and stars

Implemented:

- organizations
- starred repositories
- repository metadata
- repository navigation
- star/unstar gateway
- unstar from Profile

## N.3 Followers and following

Implemented:

- followers
- following
- follow
- unfollow
- local relationship reconciliation

## N.4 Activity

Implemented:

- recent authenticated-user event feed
- common GitHub event summaries
- action/ref/number/title metadata
- public/private indicator
- repository navigation

## N.5 Notifications compatibility

Implemented:

- capability-aware Notifications tab
- explicit GitHub App token compatibility guard
- no unsupported `/notifications` REST requests
- browser handoff to GitHub notification inbox
- no hidden secondary OAuth credential

Upstream limitation:

- GitHub documents the Notifications REST API and notification-thread subscription endpoints as unsupported for GitHub App user access tokens
- native GitHub notification inbox synchronization therefore requires a different credential model and is not falsely reported as available

## N.6 Architecture and validation

Implemented:

- typed `core/social` domain
- `SocialGateway`
- GitHub REST implementation
- Hilt binding
- ProfileViewModel
- ActivityViewModel
- real Profile UI
- real Activity UI
- JVM parser tests
- Compose Profile tests
- Compose Activity/feed tests
- notification capability fallback tests
- Phase N documentation

## Validation results

- **Android CI — success**
- **Foundation CI — success**
- pull-request integration validation — success
- build, lint, JVM tests and Android test APK assembly passed

## Upstream Notifications limitation

GitHub's authenticated-user Notifications REST API does not support GitHub App user access tokens. Nexora Git therefore does not issue those requests and provides an explicit browser handoff instead. A native inbox would require a separate OAuth-App credential model.

## Result

Phase N is complete within the project's GitHub App authentication contract. The next roadmap milestone is **Phase O — Advanced GitHub**.
