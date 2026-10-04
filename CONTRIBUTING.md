# Contributing to Nexora Git

Nexora Git is developed in public. Contributions must preserve the project's architecture, security boundaries and mobile-first goals.

## Read first

- `docs/PROJECT_SPEC.md`
- `docs/ARCHITECTURE.md`
- `docs/AUTH.md`
- `docs/GIT_ENGINE.md`
- `docs/SECURITY.md`
- `docs/ROADMAP.md`
- `docs/adr/`

## Branch naming

Examples:

```text
feature/auth-pkce
feature/git-clone
fix/oauth-callback
docs/storage-model
chore/dependencies
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

## Pull requests

A PR should have one clear purpose, include tests for behavior changes, avoid unrelated formatting churn, update relevant documentation and pass required CI.

Security-sensitive changes involving authentication, tokens, JNI/native code, repository writes, release signing or destructive Git operations require strict review.

Never include real tokens, passwords, signing keys or private repository data in tests or fixtures.
