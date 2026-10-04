# ADR-0002: GitHub App with OAuth and PKCE

- Status: Accepted
- Date: 2026-10-04

## Context

Nexora Git is public client software. It must authorize access to GitHub without collecting GitHub credentials and without embedding confidential secrets in the APK.

## Decision

Use a GitHub App, GitHub web authorization, OAuth Authorization Code flow, PKCE, cryptographically random `state`, Android Keystore-backed local token protection and a minimal confidential auth broker only where a protected secret/token exchange is required.

## Explicitly rejected

- custom GitHub password form;
- collecting passkeys;
- PATs as the primary login UX;
- embedding a GitHub App client secret in the APK;
- plain SharedPreferences for tokens;
- fake GitHub login inside a WebView.

## Consequences

Password, 2FA, passkey and SSO authentication remain on GitHub-controlled pages. The optional backend remains narrow and is not a general GitHub API proxy.
