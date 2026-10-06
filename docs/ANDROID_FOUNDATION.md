# Android Foundation

> **Current reference:** This document describes the implemented Nexora Git system. See the [Documentation Hub](README.md) for navigation.

## Baseline

Nexora Git uses a native Android application foundation.

Current baseline:

- Android Gradle Plugin 9.4.0
- Gradle 9.6.0 in CI
- compileSdk 37.0
- targetSdk 36
- Android 17 platform package: `platforms;android-37.0`
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

The physical package layout is feature-oriented while remaining in a single Android application module. Separate Gradle feature/core modules can be introduced as implementation size justifies them without changing the domain boundaries.

## Navigation shell

Phone navigation currently exposes:

- Home
- Explore
- Repositories
- Activity
- Profile

The navigation shell is backed by the authentication and GitHub platform layers; feature screens must not rely on fake production data.

## Persistence

Room is initialized with a workspace database and DAO.

DataStore provides preference persistence.

Authentication tokens are intentionally **not** placed in DataStore; they use Android Keystore-backed protection.

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

Authenticated requests are handled by the shared GitHub platform/authentication stack rather than by the Android foundation layer.

Logging is BASIC in debug and disabled in release, with authentication/cookie headers redacted.

## CI

Android CI installs API 37.0, validates the Gradle Wrapper and then runs:

```text
:app:assembleDebug
:app:testDebugUnitTest
:app:lintDebug
```

The generated debug APK is uploaded as a short-lived workflow artifact.

## Gradle Wrapper

The repository includes the standard Gradle 9.6.0 wrapper:

```text
gradlew
gradlew.bat
gradle/wrapper/gradle-wrapper.jar
gradle/wrapper/gradle-wrapper.properties
```

The wrapper distribution is pinned to Gradle 9.6.0 and includes the official SHA-256 checksum. CI executes `./gradlew`, so local clones and GitHub Actions use the same Gradle version.

---

[← Documentation hub](README.md)
