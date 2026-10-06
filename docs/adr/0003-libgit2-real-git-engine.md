# ADR-0003: libgit2 as the Real Local Git Engine

**Status:** Accepted  
**Scope:** Architectural decision for the current Nexora Git implementation.

- Status: Accepted
- Date: 2026-10-04

## Context

Nexora Git must support real local repositories, offline commits, branches, diffs, clone/fetch/pull/push, merges and conflict handling.

## Decision

Use:

```text
Kotlin domain
  → GitEngine
  → Libgit2GitEngine
  → JNI
  → Android NDK / C++
  → libgit2
```

GitHub REST/GraphQL remains responsible for GitHub platform features such as issues, pull requests, profiles, notifications, Actions and repository administration.

## Consequences

This provides genuine Git semantics and offline workflows without an external Termux/Git installation, at the cost of native build, ABI and JNI maintenance.

Tokens must be supplied through ephemeral credential callbacks and never persisted in Git remote URLs.

---

[← Documentation hub](../README.md)
