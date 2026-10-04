# Android Foundation

## Baseline

Nexora Git uses a native Android application foundation.

Current baseline:

- Android Gradle Plugin 9.4.0
- Gradle 9.6.0 in CI
- compileSdk 36
- targetSdk 36
- minSdk 26
- Java 17
- AGP built-in Kotlin
- Kotlin/Compose compiler 2.2.10
- Jetpack Compose BOM 2026.09.00

## Foundation libraries

- Jetpack Compose + Material 3
- Navigation Compose
- Hilt
- Room
- DataStore
- OkHttp
- Retrofit
- Coroutines / Flow through AndroidX/Kotlin dependencies

## Current structure

```text
app/src/main/java/com/nexora/git/
├── MainActivity.kt
├── NexoraGitApplication.kt
├── core/
│   ├── common/
│   ├── database/
│   ├── network/
│   └── settings/
├── feature/
│   ├── activity/
│   ├── explore/
│   ├── home/
│   ├── profile/
│   └── repositories/
└── ui/
    ├── components/
    ├── navigation/
    └── theme/
```

The physical package layout is intentionally feature-oriented while remaining in a single Android application module during the foundation phase. Separate Gradle feature/core modules can be introduced as implementation size justifies them without changing the domain boundaries.

## Navigation shell

Phone navigation currently exposes:

- Home
- Explore
- Repositories
- Activity
- Profile

These screens are architectural shells, not fake completed GitHub features. Real GitHub data is introduced in the authentication/platform phases.

## Persistence

Room is initialized with a workspace database and DAO.

DataStore provides preference persistence.

Authentication tokens are intentionally **not** placed in DataStore; their secure storage belongs to the authentication phase and must use Keystore-backed protection.

## Networking

The initial Retrofit/OkHttp stack targets:

```text
https://api.github.com/
```

All GitHub REST requests receive:

```text
Accept: application/vnd.github+json
X-GitHub-Api-Version: 2026-03-10
User-Agent: Nexora-Git-Android
```

No authorization interceptor is implemented during Phase A. Authentication is added in Phase B.

Logging is BASIC in debug and disabled in release, with authentication/cookie headers redacted.

## CI

Android CI installs API 36 and Gradle 9.6.0, then runs:

```text
:app:assembleDebug
:app:testDebugUnitTest
:app:lintDebug
```

The generated debug APK is uploaded as a short-lived workflow artifact.

## Build wrapper note

CI pins Gradle directly so the repository can be validated without relying on a binary wrapper artifact during this bootstrap phase. A standard Gradle wrapper will be committed as soon as its binary wrapper JAR can be added through the normal source-development workflow.
