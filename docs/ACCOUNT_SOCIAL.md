# Account and Social

Phase N adds the authenticated GitHub account and social experience to Nexora Git.

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

The Activity destination now contains two real sections:

### Notifications

Supports:

- unread notifications
- all notifications
- participating notifications
- unread count
- mark one thread read
- mark all notifications read
- inspect thread subscription
- subscribe to a thread
- ignore a thread
- return a thread to the non-subscribed/non-ignored state
- open the notification repository in Nexora Git

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

## API safety

Phase N uses the shared authenticated GitHub REST platform layer.

Safety controls include:

- strict username/repository/thread identifier validation
- bounded pagination
- repeated-pagination URL detection
- no token handling in feature/UI code
- mutation responses reconciled only after GitHub success
- account-switch refresh to avoid showing stale social data from another account

## Permissions

For the full Phase N experience, the production GitHub App/user authorization should provide the account permissions required by GitHub for:

- profile read/write
- followers read/write
- starring read/write
- organization membership visibility
- repository metadata
- notifications

The exact availability of organization memberships, private activity, email, notifications, and mutations remains subject to GitHub account settings, organization policy, token type, and granted permissions.

## Validation

Phase N includes:

- JVM parser coverage for profile/orgs/users/stars/activity/notifications/subscriptions
- Compose Profile coverage
- Compose Activity/Notifications coverage
- account-switch refresh behavior
- navigation integration
- Android CI
- Foundation CI
