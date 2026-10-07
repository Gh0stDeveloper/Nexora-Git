# Nexora Git Documentation

> **Documentation status:** current implementation reference  
> **Product:** Nexora Git for Android  
> **Repository:** `Gh0stDeveloper/Nexora-Git`

This directory contains the technical, operational, security and historical documentation for Nexora Git. Current implementation documents describe the code that exists today. Files named `PHASE_*_REPORT.md` are retained only as implementation history and should not be used as the primary operational reference.

## Start here

| Topic | Document | Use it for |
| --- | --- | --- |
| Product scope | [PROJECT_SPEC.md](PROJECT_SPEC.md) | Product boundaries, supported workflows and design goals |
| Architecture | [ARCHITECTURE.md](ARCHITECTURE.md) | System layers, dependency direction and runtime boundaries |
| Visual system map | [SYSTEM_MAP.md](SYSTEM_MAP.md) | End-to-end Mermaid diagrams for Android, GitHub, native Git and VPS |
| Dependencies | [DEPENDENCIES.md](DEPENDENCIES.md) | Versioned libraries, native dependencies and infrastructure |
| Authentication | [AUTH.md](AUTH.md) | GitHub App OAuth, PKCE, token storage and account isolation |
| Git engine | [GIT_ENGINE.md](GIT_ENGINE.md) | libgit2/JNI architecture and local Git behavior |
| Security | [SECURITY.md](SECURITY.md) | Application security invariants and implementation policy |
| Threat model | [THREAT_MODEL.md](THREAT_MODEL.md) | Threats, assets, trust boundaries and controls |
| Deployment | [VPS_INSTALLER.md](VPS_INSTALLER.md) | Self-hosted Auth Broker installation and operations |
| VPS command reference | [NEXORA_GIT_COMMANDS.md](NEXORA_GIT_COMMANDS.md) | Every `nexora-git` command, argument, effect, safety note and example |
| Download website | [WEB.md](WEB.md) | Next.js download site, signed APK publication, Nginx routing and VPS lifecycle |
| VPS Android builds | [VPS_ANDROID_BUILDER.md](VPS_ANDROID_BUILDER.md) | Persistent Android SDK/build-host architecture, security boundaries and rollout |
| Releases | [RELEASE_PROCESS.md](RELEASE_PROCESS.md) | Signing, validation and GitHub Release procedure |
| Roadmap | [ROADMAP.md](ROADMAP.md) | Full implementation roadmap, including active Phase S hardening |
| Phase S hardening | [PHASE_S_PRODUCTION_HARDENING_ROADMAP.md](PHASE_S_PRODUCTION_HARDENING_ROADMAP.md) | Strict security, UX, performance, SEO and release-readiness pass |

## Android application

- [ANDROID_FOUNDATION.md](ANDROID_FOUNDATION.md) — Android project structure, Compose and application foundation.
- [UI_UX.md](UI_UX.md) — mobile interaction and visual design principles.
- [STORAGE.md](STORAGE.md) — Storage Access Framework, managed workspaces and synchronization.
- [CODE_BROWSER.md](CODE_BROWSER.md) — code browsing, history, blame and safe file access.
- [MOBILE_EDITOR.md](MOBILE_EDITOR.md) — editor behavior, search/replace, undo/redo and save semantics.
- [ADVANCED_MOBILE_DEVELOPMENT.md](ADVANCED_MOBILE_DEVELOPMENT.md) — Tree-sitter, symbols, diagnostics, formatting, templates and project search.
- [ACCESSIBILITY_AUDIT.md](ACCESSIBILITY_AUDIT.md) — accessibility controls and release checks.
- [PERFORMANCE_AUDIT.md](PERFORMANCE_AUDIT.md) — runtime limits, payload budgets and performance policy.

## Git and local development

- [GIT_ENGINE.md](GIT_ENGINE.md) — native libgit2 engine.
- [DAILY_GIT_WORKFLOW.md](DAILY_GIT_WORKFLOW.md) — daily stage/commit/branch/fetch/pull/push/conflict workflow.
- [ADVANCED_GIT.md](ADVANCED_GIT.md) — rebase, cherry-pick, stash, reset, revert, tags, submodules and Git LFS.
- [REPOSITORY_EXPERIENCE.md](REPOSITORY_EXPERIENCE.md) — repository discovery, import, clone and workspace integration.

## GitHub platform and collaboration

- [PLATFORM_LAYER.md](PLATFORM_LAYER.md) — authenticated REST/GraphQL transport, pagination, rate limits and cache behavior.
- [GITHUB_APP.md](GITHUB_APP.md) — GitHub App permissions and configuration contract.
- [ISSUES.md](ISSUES.md) — Issues, comments, reactions and lifecycle operations.
- [PULL_REQUESTS.md](PULL_REQUESTS.md) — pull request authoring, review, checks and merging.
- [GITHUB_ACTIONS.md](GITHUB_ACTIONS.md) — workflows, runs, jobs, logs and artifacts.
- [RELEASES.md](RELEASES.md) — tags, releases and release assets.
- [ACCOUNT_SOCIAL.md](ACCOUNT_SOCIAL.md) — profile, organizations, stars, followers and activity.
- [ADVANCED_GITHUB.md](ADVANCED_GITHUB.md) — Discussions, Projects V2, Pages, security feeds, Gists and Codespaces.

## Authentication, security and privacy

- [AUTH.md](AUTH.md) — client-side authentication architecture.
- [AUTH_DEPLOYMENT.md](AUTH_DEPLOYMENT.md) — deployment runbook for GitHub App + Auth Broker.
- [SECURITY.md](SECURITY.md) — secure implementation requirements.
- [THREAT_MODEL.md](THREAT_MODEL.md) — risk model and trust boundaries.
- [PRODUCTION_SECURITY_AUDIT.md](PRODUCTION_SECURITY_AUDIT.md) — release hardening audit.
- [PRIVACY.md](PRIVACY.md) — privacy and data-handling policy.

The broker itself is documented in [../auth-broker/README.md](../auth-broker/README.md).

## Production and distribution

- [PHASE_S_PRODUCTION_HARDENING_ROADMAP.md](PHASE_S_PRODUCTION_HARDENING_ROADMAP.md) — active production-hardening, product-polish and stable-release qualification roadmap.
- [PRODUCTION_READINESS.md](PRODUCTION_READINESS.md) — repository-level production readiness.
- [RELEASE_PROCESS.md](RELEASE_PROCESS.md) — signed release workflow.
- [PLAY_STORE_READINESS.md](PLAY_STORE_READINESS.md) — Play Store metadata and publication requirements.
- [FDROID_READINESS.md](FDROID_READINESS.md) — optional F-Droid packaging constraints.
- [VPS_INSTALLER.md](VPS_INSTALLER.md) — self-hosted Auth Broker deployment.
- [NEXORA_GIT_COMMANDS.md](NEXORA_GIT_COMMANDS.md) — complete operator reference for every `nexora-git` CLI command.
- [WEB.md](WEB.md) — official self-hosted project/download website and signed APK publication.
- [VPS_ANDROID_BUILDER.md](VPS_ANDROID_BUILDER.md) — reusable Android build-host environment and signing/build service rollout.
- [BRANDING.md](BRANDING.md) — product identity and branding rules.

## Architecture Decision Records

The ADRs record decisions that should remain stable unless explicitly superseded:

1. [ADR-0001 — Native Android with Kotlin and Compose](adr/0001-native-android-kotlin-compose.md)
2. [ADR-0002 — GitHub App OAuth with PKCE](adr/0002-github-app-oauth-pkce.md)
3. [ADR-0003 — libgit2 as the real local Git engine](adr/0003-libgit2-real-git-engine.md)

## Historical implementation reports

`PHASE_0_REPORT.md` through `PHASE_R_REPORT.md` document how the original implementation was delivered and validated. They are retained for traceability. Phase S is intentionally maintained as an active roadmap rather than a completed historical report until its release gates have been satisfied.

## Documentation conventions

- Commands must be copy/paste-safe and identify when root privileges are required.
- Security-sensitive examples must use placeholders, never real secrets.
- Diagrams use GitHub-rendered Mermaid where possible.
- Version numbers must come from repository build files or immutable dependency pins.
- Current technical documents should describe the implemented product, not an obsolete development stage.
- External account operations such as creating production credentials are deployment tasks, not incomplete implementation work.
