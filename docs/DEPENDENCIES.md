# Dependency & Toolchain Reference

> [Documentation hub](README.md) · [Architecture](ARCHITECTURE.md) · [Security](SECURITY.md)

This document records the principal libraries, native components and deployment dependencies used by Nexora Git. Version numbers are taken from the repository's build configuration and immutable native pins.

<p align="center">

![Android](https://img.shields.io/badge/Android-SDK%2026%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-2026.09.00-4285F4?logo=jetpackcompose&logoColor=white)
![C++](https://img.shields.io/badge/C%2B%2B-17-00599C?logo=cplusplus&logoColor=white)
![Git](https://img.shields.io/badge/libgit2-1.9.7-F05032?logo=git&logoColor=white)
![Go](https://img.shields.io/badge/Go-1.24-00ADD8?logo=go&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)
![Nginx](https://img.shields.io/badge/Nginx-TLS%20proxy-009639?logo=nginx&logoColor=white)

</p>

## Android build toolchain

| Component | Version / baseline | Purpose |
| --- | --- | --- |
| Android Gradle Plugin | 9.4.0 | Android build and packaging |
| Kotlin | 2.2.10 | Primary Android language |
| Java | 17 | JVM source/target compatibility |
| minSdk | 26 | Minimum supported Android API |
| targetSdk | 36 | Android target API |
| compileSdk | 37.0 | Build API |
| Android NDK | 27.2.12479018 | Native libgit2/JNI compilation |
| CMake | 3.22.1 | Native dependency and JNI build |
| C++ | C++17 | Native Git bridge implementation |

## Android libraries

| Library | Version | Responsibility |
| --- | ---: | --- |
| Jetpack Compose BOM | 2026.09.00 | Compose dependency alignment |
| Activity Compose | 1.13.0 | Compose activity integration |
| AndroidX Core KTX | 1.17.0 | Android Kotlin extensions |
| Lifecycle | 2.11.0 | lifecycle, runtime and ViewModel integration |
| Navigation Compose | 2.10.2 | application navigation |
| Hilt | 2.60.1 | dependency injection |
| Hilt Lifecycle ViewModel Compose | 1.4.0 | Hilt ViewModel integration for Compose |
| Room | 2.8.5 | local structured persistence |
| DataStore | 1.2.1 | preferences and active account/settings state |
| DocumentFile | 1.1.0 | Storage Access Framework integration |
| AndroidX Browser | 1.10.0 | secure GitHub authorization Custom Tabs |
| Retrofit | 3.0.0 | typed HTTP client integration |
| OkHttp | 5.5.0 | HTTP transport and interceptors |
| JUnit | 4.13.2 | JVM tests |
| AndroidX Test JUnit | 1.3.0 | Android instrumentation integration |
| Espresso | 3.7.0 | Android UI/instrumentation support |

The authoritative Android dependency catalog is `gradle/libs.versions.toml`.

## Native Git stack

| Component | Release | Immutable pin | Role |
| --- | --- | --- | --- |
| libgit2 | 1.9.7 | `49e408b3208bc3093757a1c2db938d3590f3f412` | Local Git object/index/ref/remote engine |
| Mbed TLS | 3.6.7 LTS | `068ff080b369adfac81509f9b57b2afabaf82dc5` | HTTPS/TLS backend for libgit2 |
| Tree-sitter | repository-pinned | immutable source pins in native build | AST parsing and language intelligence |

Native dependencies are compiled for:

- `arm64-v8a`
- `armeabi-v7a`
- `x86_64`

## Auth Broker

| Component | Version / mode | Purpose |
| --- | --- | --- |
| Go | 1.24 | Auth Broker implementation |
| Docker | system package / supported engine | isolated broker runtime |
| Docker Compose | Docker Compose plugin | lifecycle management |
| Nginx | distro package | shared reverse proxy |
| Certbot | distro package | Let's Encrypt certificate issuance/renewal |
| Let's Encrypt | ACME | public TLS certificate |

The broker intentionally has no third-party Go module dependencies beyond the standard library.

## CI and release tooling

Nexora Git uses GitHub Actions workflows for Android, native Git, Auth Broker, foundation, production, VPS installer and CodeQL validation. Third-party actions in production workflows are pinned to immutable revisions.

Release hardening includes:

- R8/resource shrinking;
- externally supplied signing credentials;
- APK signature verification;
- SHA-256 checksums;
- native symbol artifact separation;
- semantic release-tag validation;
- release payload size budgets.

## Dependency policy

1. Secrets never belong in dependency declarations or build files.
2. Native security-sensitive dependencies are pinned immutably.
3. Android library versions are centralized in the version catalog.
4. Third-party CI actions used for production validation are pinned.
5. New libraries require a concrete product need; avoid duplicate HTTP, persistence or DI stacks.
6. Native and network dependencies must preserve the security boundaries documented in [SECURITY.md](SECURITY.md).

---

[← Documentation hub](README.md)
