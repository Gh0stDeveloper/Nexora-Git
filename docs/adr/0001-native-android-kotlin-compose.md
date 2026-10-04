# ADR-0001: Native Android with Kotlin and Jetpack Compose

- Status: Accepted
- Date: 2026-10-04

## Context

Nexora Git requires deep Android filesystem integration, secure credential storage, long-running Git operations, native C/C++ interoperability, responsive mobile UI and reliable offline behavior.

## Decision

The Android client will be implemented natively with Kotlin, Jetpack Compose, Material 3, Coroutines/Flow and AndroidX architecture libraries.

React Native, a Next.js wrapper and a WebView-first architecture are not the core application architecture.

## Consequences

Benefits include direct Android API access, straightforward SAF/Keystore integration, strong Compose state management and simpler JNI/NDK interoperability.

The main cost is an Android-specific implementation if other platforms are added later.
