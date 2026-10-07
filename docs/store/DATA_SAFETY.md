# Google Play Data Safety Review

This worksheet maps the current production baseline of Nexora Git (`com.nexora.git`) to Google Play Data Safety concepts. It is an engineering review artifact for the Play Console submission. Final console answers must be rechecked against the exact signed build and deployed Auth Broker.

## Product baseline

- No advertising SDK.
- No analytics or behavioral telemetry SDK.
- Android backup is disabled for sensitive application data.
- GitHub tokens are encrypted at rest with an Android Keystore-backed key.
- App and broker network traffic is HTTPS-only in production.
- The Auth Broker performs OAuth exchange/refresh and is not a repository-content proxy.
- Nexora Git does not create a first-party cloud account database.
- Repository contents remain local unless the user explicitly performs a network operation such as push, issue/comment creation, release upload or sharing.

## Conservative Play declarations

Google Play defines collection as transmitting user data off the device, including transmission to third-party services. Ephemeral processing still belongs in the engineering review even when it qualifies for special treatment in the displayed Data Safety section.

Review these categories as collected for app functionality when the corresponding GitHub feature is used:

| Play data category | Nexora Git behavior | Required/optional | Retention |
| --- | --- | --- | --- |
| User IDs / account identifiers | GitHub account identifiers and authenticated API context are transmitted to GitHub; OAuth codes/tokens pass through the Auth Broker as required for authentication. | GitHub-connected features require it; local Git can be used without a GitHub operation. | Tokens are stored encrypted on-device; broker exchange is designed as transient processing. |
| Personal info supplied to GitHub | Profile fields such as name, public email, bio/company/location/site may be read or updated when the user uses profile features. | Optional feature use. | GitHub is authoritative; Nexora Git caches only what the app needs locally. |
| Files and documents / user content | Source files, release assets and repository content leave the device only when the user explicitly pushes, uploads or shares them. | Optional and user initiated. | Governed by the selected GitHub repository/destination after transfer; local copies remain under user control. |
| App activity/content authored by the user | Issues, comments, pull-request reviews, workflow dispatch inputs and release metadata are sent when the user explicitly submits them. | Optional and user initiated. | Governed by GitHub after submission. |

## Sharing analysis

Transfers to GitHub that are the direct result of a specific user action—such as push, upload, issue/comment creation or file sharing—must be evaluated under Google Play's user-initiated action sharing exception. Do not use that exception to hide collection/transmission from the Data Safety analysis.

The Auth Broker is first-party infrastructure for the confidential OAuth exchange. It must not log request bodies, tokens or repository contents.

## Data not collected by the production baseline

- Advertising ID for ads or profiling.
- Location.
- Contacts.
- SMS/call logs.
- Health/fitness data.
- Financial/payment data.
- Photos/videos/audio unless the user intentionally commits or uploads such files as repository content.
- Crash analytics or behavioral telemetry SDK events.

## Security practices

For the Play Console security questions:

- **Data encrypted in transit:** yes for production network traffic; cleartext production traffic is disabled.
- **Deletion mechanism:** local credentials can be deleted by signing out or clearing/uninstalling app data. Data already sent to GitHub is deleted through the user's GitHub account/repository controls.
- **Account deletion:** Nexora Git does not create a first-party account. GitHub account deletion is managed by GitHub.
- **Independent security review:** do not claim one unless an external qualified review has actually occurred.

## Submission gate

Before every Play production submission:

1. inspect the signed AAB dependency inventory/SBOM;
2. verify no new analytics, advertising or data-collection SDK was added;
3. compare this document with `docs/PRIVACY.md` and the public `/privacy` page;
4. review Auth Broker and reverse-proxy logging behavior in the production deployment;
5. update Play Console Data Safety answers if any data flow changed.

The developer remains responsible for the final Play Console declaration because Google Play evaluates the complete distributed build and its third-party components.
