# Contributing to Nexora Git

Thank you for contributing to Nexora Git. Contributions should preserve the project's mobile-first product goals, native Git architecture and explicit security boundaries.

## Before you start

Read the references relevant to your change:

- [Documentation hub](docs/README.md)
- [Project specification](docs/PROJECT_SPEC.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Authentication](docs/AUTH.md)
- [Git engine](docs/GIT_ENGINE.md)
- [Security](docs/SECURITY.md)
- [Threat model](docs/THREAT_MODEL.md)
- [Architecture decisions](docs/adr/)

## Development baseline

The Android project uses JDK 17, Kotlin, Jetpack Compose, the Android NDK and CMake. Native Git code is compiled from the pinned libgit2/Mbed TLS sources defined by the repository.

Typical Android validation:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

Changes to native Git, Auth Broker, release automation or VPS scripts must also run the corresponding repository CI workflow.

## Branch naming

Use a short, descriptive branch:

```text
feat/repository-search
fix/oauth-callback-validation
docs/vps-operations
refactor/git-conflict-state
ci/native-cache
```

## Commit convention

Use Conventional Commits:

```text
feat:
fix:
docs:
refactor:
test:
build:
ci:
chore:
```

Keep commits scoped and reviewable. Avoid mixing unrelated formatting churn with behavior changes.

## Pull requests

A pull request should:

1. explain the problem and the implemented solution;
2. keep one primary purpose;
3. include tests for behavior changes;
4. update documentation when behavior, architecture or operations change;
5. pass the relevant CI workflows;
6. avoid credentials, private repository data and generated secrets;
7. document any migration, destructive operation or compatibility impact.

Use the repository pull request template and complete the security checklist honestly.

## Architecture rules

- Feature code depends on stable domain contracts instead of calling JNI directly.
- GitHub REST/GraphQL APIs do not replace real local Git operations.
- GitHub credentials must never be embedded in remote URLs.
- The Android APK must never contain the GitHub App client secret.
- Storage operations must preserve workspace/path confinement.
- New network stacks, persistence layers or DI frameworks require a concrete architectural justification.
- Destructive Git/repository operations require explicit user intent and clear recovery semantics.

## Security-sensitive changes

Authentication, token handling, JNI/native code, path handling, repository writes, release signing, CI supply-chain changes and VPS deployment logic require stricter review.

Never commit:

- access or refresh tokens;
- GitHub App client secrets;
- signing keystores/passwords;
- private repository fixtures;
- production `.env` files;
- credentials in screenshots or logs.

Security vulnerabilities should follow [SECURITY.md](SECURITY.md), not a public issue containing exploit details.

## Documentation

Current technical documentation belongs under `docs/`. Historical implementation reports remain available for traceability, but new operational guidance should be topic-based and linked from [docs/README.md](docs/README.md).

When adding a dependency, update [docs/DEPENDENCIES.md](docs/DEPENDENCIES.md) when it materially changes the supported stack.

## License

By contributing, you agree that your contribution is provided under the repository's [Apache License 2.0](LICENSE).
