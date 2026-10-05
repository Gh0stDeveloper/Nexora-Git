# GitHub App Configuration

## Status

The repository-side configuration contract is complete.

Actual GitHub App registration is an account-level GitHub operation. When created, the App settings must match this document.

## Public name

```text
Nexora Git
```

## Authentication architecture

```text
Android
  → generate state + PKCE verifier/challenge
  → GitHub authorization
  → callback
  → validate state
  → token exchange
  → encrypted Android session
```

## Callback design

Use an exact production HTTPS broker callback:

```text
https://AUTH_HOST/oauth/callback
```

The broker then forwards the short-lived code and state to the fixed native callback `nexoragit://oauth/callback`. Keep development and production callbacks separate and do not use wildcard callback domains.

## Permission strategy

Start with least privilege and add permissions only when the associated feature ships.

| Permission area | Access | Purpose |
|---|---:|---|
| Metadata | Read | Repository identity/basic metadata |
| Administration | Read/Write when repository management ships | Create repositories and modify supported repository settings |
| Contents | Read/Write | Repository content operations and Git HTTPS authorization where applicable |
| Issues | Read/Write | Issues/comments |
| Pull requests | Read/Write | PRs/reviews/merge workflows |
| Actions | Read | Workflow/run/job visibility |
| Workflows | Read/Write only when needed | Dispatch or workflow modification |
| Commit statuses / Checks | Read | CI state |
| Discussions | Read/Write only when shipped | Discussions |
| Starring (user permission) | Read/Write | Read and change the authenticated user's starred repositories |

Account permissions should be added only for implemented profile, identity or social features.

### Repository watch state

GitHub's REST repository-subscription mutation endpoints do not support GitHub App user access tokens. Nexora Git therefore does not use those endpoints for watch/unwatch.

Phase F queries `viewerCanSubscribe` / `viewerSubscription` and uses the GraphQL `updateSubscription` mutation when GitHub reports the capability is available. The UI disables watch mutations when that capability is not available.

## Installation model

The app must distinguish selected-repository installations, all-repository installations and organization policy restrictions.

The UI should differentiate:

- repository not found;
- user lacks access;
- GitHub App not installed for repository;
- App installed but missing a required permission.

## Token handling

- Never commit application secrets.
- Never embed confidential client secrets in the APK.
- Never write access/refresh tokens to logs.
- Never persist access tokens in Git remote URLs.
- Protect local session material with Android Keystore-backed encryption.
- Clear pending PKCE state after success, failure or timeout.

## Environment contract

Future confidential infrastructure may use:

```text
GITHUB_APP_CLIENT_ID
GITHUB_APP_CLIENT_SECRET
GITHUB_CALLBACK_URL
```

Only the client ID is public configuration. Confidential values remain outside source control and outside the APK.

## Registration checklist

- [ ] Create GitHub App in the intended owner account.
- [ ] Set public name and description.
- [ ] Configure homepage.
- [ ] Configure exact callback URL.
- [ ] Enable user authorization.
- [ ] Configure least-privilege repository permissions.
- [ ] Configure account permissions only as implemented.
- [ ] Store confidential credentials outside the repository.
- [ ] Test PKCE/state behavior.
- [ ] Test selected-repository installation.
- [ ] Test organization restrictions.
- [ ] Record only non-secret identifiers in deployment configuration.

Unchecked items require the real GitHub App to be created/configured in GitHub account settings.
