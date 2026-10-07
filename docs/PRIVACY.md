# Privacy Policy

> **Current reference:** This document describes the implemented Nexora Git system. See the [Documentation Hub](README.md) for navigation.

**Product:** Nexora Git  
**Application ID:** `com.nexora.git`  
**Developer:** Ghost Developer

Nexora Git is an open-source Android client for Git and GitHub workflows.

## Data processed on the device

The application may store locally:

- GitHub account/profile metadata;
- encrypted GitHub access and refresh tokens;
- repository/workspace metadata;
- repositories and files that the user explicitly clones, imports or creates;
- application settings.

Authentication tokens are protected with Android Keystore-backed encryption. Nexora Git disables Android application backup so private repositories and authentication state are not intentionally copied into cloud backup.

## Network communication

Nexora Git communicates with GitHub when the user requests GitHub operations. GitHub receives the information required to provide those operations under GitHub's own terms and privacy policy.

A small Nexora Git Auth Broker may be used for the confidential portion of the GitHub App OAuth flow. The broker processes OAuth codes/tokens required for authentication. It is not a source-code proxy and is designed not to log request bodies or retain repository contents.

## Analytics and advertising

Nexora Git does not include an advertising SDK and does not add analytics/telemetry in the production baseline.

## Source code and local files

Repository contents remain on the device unless the user explicitly performs an operation that sends data to GitHub or another user-selected destination, such as push, issue/comment creation, release upload, sharing or exporting a file.

## Sale of data

Nexora Git does not sell user data.

## Accounts and retention

Nexora Git does not create or host a first-party user account. GitHub authentication connects an existing GitHub account. The Auth Broker is designed to process OAuth exchange/refresh requests without retaining repository contents or maintaining a Nexora Git account database. Local tokens remain on the device until sign-out, app-data removal or Android uninstallation.

## Deletion

Users can sign out to remove locally stored authentication tokens and can remove Nexora Git workspaces/local application data from the device. Data already sent to GitHub is governed by the user's GitHub account and repository controls.

## Contact and privacy inquiries

For non-sensitive privacy questions, use the public project issue tracker:

https://github.com/Gh0stDeveloper/Nexora-Git/issues

Do not place access tokens, private repository content or other sensitive information in a public issue. Security-sensitive reports must follow the private-reporting process in `SECURITY.md`.

## Changes

Material privacy changes should be documented in the repository and reflected in store disclosures before a new stable release.

---

[← Documentation hub](README.md)
