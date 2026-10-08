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

## Verified development-state configuration (2026-10-07)

The repository API and active branch ruleset were read back after the maintainer's GitHub configuration:

- Description: exactly the reviewed project description above.
- Topics: all **nine** reviewed Topics are present.
- Discussions: enabled.
- Automatic deletion of merged branches: enabled.
- Default branch: `main`.
- Active `main` ruleset `nexora-git`: blocks deletion and non-fast-forward updates; requires a pull request and resolved review conversations; requires up-to-date checks.
- Ruleset required status checks: Analyze Kotlin/Java, Analyze Go Auth Broker, Analyze C/C++, Analyze TypeScript/JavaScript, Review dependency changes and Validate repository foundation.

**External deployment deferral:** the product website is still under development, so the Homepage field is deliberately empty. Do not set a fabricated domain or claim launch readiness. Track the owner, risk, rationale, and final verification in [the homepage launch issue](https://github.com/Gh0stDeveloper/Nexora-Git/issues/46). The release gate continues to require a real HTTPS origin before the corresponding public release. This is an approved **P2 development deferral, not an implemented website**.

## Manual configuration

Repository administrators should open **Settings** and configure the repository About/homepage/topics, enable Discussions, enable automatic deletion of merged head branches, and enable the main ruleset/branch protection. Re-run the release gate afterwards; the API result, not this checklist, is authoritative.


## Automated administration path

When an administrator has a `gh` session with repository **Administration: write** permission, all four mutable launch settings can be applied and verified in one command:

```bash
bash scripts/github/apply-launch-settings.sh "https://<production-origin>"
```

The script is idempotent. It configures the homepage, enables Discussions, enables automatic deletion of merged head branches, replaces repository topics with the reviewed S.16 topic set, and then runs the read-back validator.

The normal connected GitHub integration intentionally cannot perform this Administration mutation, so the script must be executed from an administrator-authenticated environment.
