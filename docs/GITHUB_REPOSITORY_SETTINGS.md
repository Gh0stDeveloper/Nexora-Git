# GitHub Repository Launch Settings

Phase S P2 treats GitHub repository presentation as part of the product launch surface.

## Required repository settings

Before any beta/RC is published, the repository must expose:

- **Description:** concise Android Git/GitHub positioning, maintained as the product evolves.
- **Homepage:** the production HTTPS Nexora Git website, identical to the configured production Auth Broker/site origin.
- **Topics:** at minimum `android`, `git`, `github`, `kotlin`, `jetpack-compose`, `libgit2`, `open-source`.
- **Discussions:** enabled for community questions and non-sensitive feedback.
- **Delete head branches after merge:** enabled.
- **Default branch:** `main`.
- **Main protection/ruleset:** enabled separately under the P0 release gate.

The release workflow validates these settings from the GitHub API and refuses to publish when they drift.

## Current verified account-level state at P2 implementation

At the start of P2 the API reported:

- description: configured;
- homepage: not configured;
- topics: empty;
- Discussions: disabled;
- delete branch on merge: disabled;
- default branch: `main`;
- main protection: disabled.

These are **account-level controls**. The connected GitHub integration does not expose repository-administration mutations, so they must not be marked complete merely because this document exists.

## Manual configuration

Repository administrators should open **Settings** and configure the repository About/homepage/topics, enable Discussions, enable automatic deletion of merged head branches, and enable the main ruleset/branch protection. Re-run the release gate afterwards; the API result, not this checklist, is authoritative.
