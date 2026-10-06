# Account and Social

> **Current reference:** This document describes the implemented Nexora Git system. See the [Documentation Hub](README.md) for navigation.

Nexora Git includes an authenticated GitHub account and social experience built on the shared platform layer.

## Profile

The Profile destination now loads the active account directly from GitHub and shows:

- display name and login
- bio
- company
- location
- website
- public email when GitHub exposes it
- X / Twitter username
- public repositories and gists
- followers / following counts
- hireable state

Profile editing supports:

- name
- bio
- company
- location
- website
- X / Twitter username
- hireable state

GitHub remains authoritative for which profile fields the authenticated account may update.

## Multiple accounts

The existing Nexora Git account switcher remains part of Profile.

When the active account changes:

- the authenticated token changes through the existing secure auth layer
- Profile refreshes against the new active GitHub account
- Activity refreshes when its active login changes
- account-specific social data is not reused across accounts

## Organizations

The Organizations section lists organizations visible to the authenticated account with:

- login
- description
- GitHub metadata returned by the API

Visibility follows GitHub organization membership/privacy rules.

## Starred repositories

The Starred section supports:

- paginated starred repository listing
- repository metadata
- language
- star/fork counts
- navigation into the repository
- unstar from the profile surface

The underlying social gateway supports both star and unstar mutations.

## Followers and following

The social graph includes:

- followers
- accounts being followed
- follow from the Followers section
- unfollow from Followers or Following
- immediate local state reconciliation after a successful GitHub mutation

GitHub remains authoritative for account-level follower permissions and restrictions.

## Activity

The Activity destination contains:

- a native GitHub activity feed
- a notification capability surface

### Activity feed

Shows recent GitHub events for the active account, including:

- pushes
- pull request events
- issue events
- issue comments
- ref create/delete events
- releases
- forks
- stars
- generic fallback event types

Where GitHub provides them, Nexora Git displays:

- action
- ref / ref type
- issue or pull request number
- title
- public/private event visibility
- timestamp
- repository

Repository events can open the related repository directly.

### Notifications compatibility

Nexora Git authenticates with **GitHub App user access tokens** (`ghu_`).

GitHub's current REST documentation states that the authenticated-user Notifications API and notification-thread subscription endpoints **do not work with GitHub App user access tokens**.

For that reason, Nexora Git deliberately does not send requests to:

- `GET /notifications`
- `PUT /notifications`
- `PATCH /notifications/threads/{thread_id}`
- notification thread subscription endpoints

The Notifications tab instead:

- explains the upstream compatibility limitation
- provides a direct action to open GitHub's notification inbox in the browser
- never asks for or silently stores a second OAuth credential

A future optional OAuth-App companion authorization could provide a native inbox without weakening the primary GitHub App permission model, but it is intentionally outside the current authentication contract.

## API safety

The account and social layer uses the shared authenticated GitHub REST platform layer.

Safety controls include:

- strict username/repository/thread identifier validation
- bounded pagination
- repeated-pagination URL detection
- no token handling in feature/UI code
- mutation responses reconciled only after GitHub success
- account-switch refresh to avoid showing stale social data from another account

## Permissions

For the full account and social experience, the production GitHub App/user authorization should provide the account permissions required by GitHub for:

- profile read/write
- followers read/write
- starring read/write
- organization membership visibility
- repository metadata
- notifications

The exact availability of organization memberships, private activity, email, notifications, and mutations remains subject to GitHub account settings, organization policy, token type, and granted permissions.

## Validation

Validation includes:

- JVM parser coverage for profile/orgs/users/stars/activity
- Compose Profile coverage
- Compose Activity and notification-capability fallback coverage
- account-switch refresh behavior
- navigation integration
- Android CI
- Foundation CI

---

[← Documentation hub](README.md)
