# Nexora Git

> Advanced open-source Git and GitHub client for Android. Code, commit, branch, push, review and manage complete repositories directly from your phone.

Nexora Git is an Android-first Git workspace designed for developers who work from mobile devices. It combines a full GitHub client with a real local Git engine, project-folder import, code browsing and editing, repository management, collaboration tools, GitHub Actions and offline Git workflows.

## Product vision

Nexora Git is not a WebView wrapper and is not intended to visually clone GitHub Mobile. The goal is to provide a professional Android experience that combines:

- GitHub account and repository management.
- Real local Git operations.
- Importing a complete Android folder as a Git project.
- Clone, fetch, pull, push, commit, branch, merge, diff and conflict workflows.
- Code browsing and editing.
- Issues, pull requests, reviews, Actions, releases and notifications.
- Offline work on cloned repositories.
- A mobile-first UI built for developers.

## Core architecture decisions

The project starts with two non-negotiable technical decisions:

### GitHub authentication

Nexora Git uses a **GitHub App + OAuth web flow + PKCE** architecture.

The app must never ask for or store a user's GitHub password or passkey. Authentication credentials, 2FA and passkeys are handled only by GitHub's official sign-in pages. Nexora Git receives authorized tokens after the OAuth flow.

A minimal authentication broker may be used to keep the GitHub App client secret outside the public APK.

See [Authentication](docs/AUTH.md).

### Real Git engine

Nexora Git performs Git operations through a real local Git engine based on **libgit2**, integrated into Android through the **NDK/C++ and JNI**.

The GitHub REST/GraphQL APIs are used for GitHub platform features; they do not replace local Git.

See [Git Engine](docs/GIT_ENGINE.md).

## Recommended technology stack

- Kotlin
- Jetpack Compose
- Material 3
- Coroutines + Flow
- Hilt
- Room
- DataStore
- Android Keystore
- OkHttp / Retrofit
- GitHub REST API
- GitHub GraphQL API
- GitHub App
- OAuth Authorization Code flow
- PKCE
- Android Storage Access Framework
- libgit2
- Android NDK / C++ / JNI
- WorkManager
- Coil
- Paging 3

## Documentation

- [Project specification](docs/PROJECT_SPEC.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Authentication](docs/AUTH.md)
- [Git engine](docs/GIT_ENGINE.md)
- [Android storage](docs/STORAGE.md)
- [Security](docs/SECURITY.md)
- [UI/UX](docs/UI_UX.md)
- [Roadmap](docs/ROADMAP.md)
- [Product identity](docs/BRANDING.md)
- [GitHub App configuration](docs/GITHUB_APP.md)
- [Threat model](docs/THREAT_MODEL.md)
- [Phase 0 report](docs/PHASE_0_REPORT.md)
- [Android foundation](docs/ANDROID_FOUNDATION.md)
- [Phase A report](docs/PHASE_A_REPORT.md)
- [Authentication deployment](docs/AUTH_DEPLOYMENT.md)
- [Phase B report](docs/PHASE_B_REPORT.md)
- [GitHub platform layer](docs/PLATFORM_LAYER.md)
- [Phase C report](docs/PHASE_C_REPORT.md)
- [Phase D report](docs/PHASE_D_REPORT.md)
- [Phase E report](docs/PHASE_E_REPORT.md)
- [Auth Broker](auth-broker/README.md)
- [Architecture decisions](docs/adr/)

## Initial scope

The first usable release should allow a developer to complete this workflow entirely from Android:

```text
Sign in with GitHub
        ↓
Create / clone / import repository
        ↓
Browse and edit files
        ↓
Stage changes
        ↓
Commit
        ↓
Create / switch branch
        ↓
Fetch / pull / push
        ↓
Create pull request
        ↓
Review / merge
        ↓
Inspect GitHub Actions
        ↓
Download release artifacts
```

## Open source

Nexora Git is intended to be developed publicly so the community can inspect the source code, contribute improvements, audit security-sensitive components and download official releases.

The project must use its own branding and must not imply that it is an official GitHub application.

## Status

**Current stage:** Phase E — Android Project Storage complete; Phase F — Repository Experience is next.

Nexora Git now includes SAF folder selection, persistent tree permissions, direct and managed workspace strategies, Room-backed workspace records, non-destructive managed synchronization, project scanning, `.gitignore` awareness, secret-risk warnings and GitHub large-file warnings.

---

Built for developers who want a complete Git workflow from Android.
