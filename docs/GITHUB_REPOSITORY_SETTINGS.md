# GitHub Repository Launch Settings

Phase S P2 treats GitHub repository presentation as part of the product launch surface.

## Required repository settings

Before any beta/RC is published, the repository must expose:

- **Description:** `Advanced open-source Git and GitHub client for Android. Manage repositories, branches, commits, pull requests, issues, Actions, releases and complete local projects directly from your phone.`
- **Homepage:** the production HTTPS Nexora Git website, identical to the configured production Auth Broker/site origin.
- **Topics:** `android`, `git`, `github`, `kotlin`, `jetpack-compose`, `libgit2`, `open-source`, `git-client`, `code-editor`.
- **Discussions:** **decision: enable it** for community questions, ideas and non-sensitive feedback. Bugs remain in Issues and vulnerabilities remain in private security reporting.
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
