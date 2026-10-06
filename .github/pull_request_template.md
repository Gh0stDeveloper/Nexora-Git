## Summary

Describe the problem, the solution and why this change belongs in Nexora Git.

## Scope

- [ ] Android / Compose UI
- [ ] Git engine / JNI / native
- [ ] GitHub REST / GraphQL
- [ ] Authentication / security
- [ ] Storage / workspace
- [ ] Auth Broker / VPS
- [ ] Release / distribution
- [ ] Documentation
- [ ] CI / build

## Validation

List the exact tests, builds or workflows used to validate the change.

```text
Example:
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

## Behavior and compatibility

Describe migrations, changed permissions, API assumptions, destructive operations, storage changes or compatibility considerations. Write `None` when not applicable.

## Security checklist

- [ ] No credentials, signing material or private repository data are committed.
- [ ] Tokens are not written to logs, analytics, remote URLs or Git config.
- [ ] Authentication changes preserve the GitHub App + OAuth + PKCE boundary.
- [ ] Destructive operations require explicit user intent and safe failure behavior.
- [ ] Path/storage changes preserve confinement and do not expose `.git` metadata accidentally.
- [ ] Native/JNI changes handle ownership, exceptions and errors safely.
- [ ] VPS/Auth Broker changes preserve TLS, secret isolation and existing-site coexistence.

## Documentation

- [ ] User-visible behavior is documented.
- [ ] Architecture/security documentation is updated when boundaries changed.
- [ ] New material dependencies are reflected in `docs/DEPENDENCIES.md`.
- [ ] Commands/examples contain no real secrets.

## Screenshots / evidence

Add screenshots, logs or workflow links when they materially help review. Redact credentials and private data.
