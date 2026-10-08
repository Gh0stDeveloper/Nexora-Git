# Play Data safety — evidence and operator review

**State: NOT APPROVED FOR PUBLIC STORE SUBMISSION.** This is a review worksheet, not a Play Console filing or a privacy guarantee. Complete it against the *actual signed release* and the deployed VPS/Auth Broker before checking any Data safety boxes.

## Inventory of data flows

| Area | Data | Source/destination | Why | Review |
| --- | --- | --- | --- | --- |
| GitHub web login | authorization code, state, PKCE verifier and exchanged GitHub tokens | GitHub ↔ Auth Broker ↔ Android | authenticate without the app handling a GitHub password | broker logs, retention, encryption, access |
| Account and profile | display name, username, profile photo and repository metadata | GitHub API ↔ Android | requested GitHub features | API hosts, account scopes |
| Source and local files | user-selected repositories, local workspaces and edits | local device; GitHub on explicit push/upload | local development, collaboration | upload paths, external shares, backups |
| App configuration | theme, locale, workspace metadata, account preferences | device storage | user preferences | backup and storage review |
| Diagnostics | infrastructure logs/HTTP metadata, if emitted by VPS | operator-managed infrastructure | security and support | exact log retention and access |
| Platform dependencies | Google/Android system components, browser, GitHub | respective providers | sign-in, distribution, web features | SDK inventory and provider policies |

## Current code-level baseline

- Android application declares Internet access and uses GitHub web-based authentication.
- No ad SDK or analytics SDK is part of the currently documented production baseline.
- User tokens are intended to be protected with Android Keystore and backup restrictions.
- User workspaces are local until a network/upload/share action occurs.
- **These statements are not equivalent to a final Play Data safety declaration.**

## Mandatory pre-submission review

- [ ] Inventory actual dependencies and bundled SDKs from the **signed RC AAB**, not from old documentation alone.
- [ ] Inspect the real VPS/Auth Broker request, error, reverse-proxy, crash, update and access logs for token or profile metadata exposure.
- [ ] Confirm where tokens and account info travel; distinguish Google Play's *collection*, *sharing*, *ephemeral processing* and *user-initiated transfer* definitions using current Play Console guidance.
- [ ] Determine whether the app provides account creation; if so, verify deletion and data-retention obligations for that account model.
- [ ] Confirm repository/filename contents are never sent to the operator except as explicitly required, and document any user-initiated GitHub transfers.
- [ ] Review TLS transport, encryption at rest, age audience, data retention and deletion procedures.
- [ ] Publish a **real accessible** HTTPS privacy policy at `/privacy` on the production website; do not use an example domain.
- [ ] Review all localized claims against the tested app and record review date, source commit, app version and responsible reviewer.
- [ ] Enter Play Console declarations **manually**, retain export/screenshots of the approved answers, and link the evidence here.

**Launch decision:** pending real-device acceptance, VPS deployment and manual Play Console review. No automated script should mark Data safety approved solely from source files.

See [Privacy policy source](../PRIVACY.md), [Play Store readiness](../PLAY_STORE_READINESS.md), [Store assets](ASSET_DELIVERY.md).
