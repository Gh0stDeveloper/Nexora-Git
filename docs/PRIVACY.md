# Privacy Policy

**Product:** Nexora Git  
**Application ID:** `com.nexora.git`

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

Nexora Git does not include an advertising SDK and does not add analytics/telemetry in the Phase R production baseline.

## Source code and local files

Repository contents remain on the device unless the user explicitly performs an operation that sends data to GitHub or another user-selected destination, such as push, issue/comment creation, release upload, sharing or exporting a file.

## Sale of data

Nexora Git does not sell user data.

## Deletion

Users can sign out to remove locally stored authentication tokens and can remove Nexora Git workspaces/local application data from the device. Data already sent to GitHub is governed by the user's GitHub account and repository controls.

## Changes

Material privacy changes should be documented in the repository and reflected in store disclosures before a new stable release.
