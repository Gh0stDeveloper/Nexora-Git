# Phase N — Account and Social

Date: 2026-10-05

## Status

**Implementation complete; CI validation pending.**

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

## N.5 Notifications

Implemented:

- unread/all/participating filters
- unread count
- mark thread read
- mark all read
- get thread subscription
- subscribe
- ignore
- default subscription state
- repository navigation

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
- Compose Activity tests
- Phase N documentation

Pending before completion:

- Android CI validation
- Foundation CI validation
- pull-request integration validation
