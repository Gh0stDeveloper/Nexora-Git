# Phase 0 — Foundation Report

Date: 2026-10-04

## 0.1 Product identity

Status: **Complete (repository-side)**

Delivered:

- product name: Nexora Git
- repository naming convention
- recommended Android application ID: `com.nexora.git`
- branding rules and icon direction
- open-source licensing baseline
- independent-project trademark disclaimer

## 0.2 Architecture

Status: **Complete**

Delivered:

- Clean Architecture + feature modularization baseline
- native Android/Kotlin/Compose ADR
- GitHub App OAuth/PKCE ADR
- libgit2/JNI Git-engine ADR
- remote-vs-local responsibility boundaries

## 0.3 GitHub App

Status: **Repository contract complete; account registration pending**

Delivered:

- GitHub App configuration document
- least-privilege permission matrix
- callback architecture
- installation model
- token/secret rules
- registration checklist

Pending external account action:

- create/configure the real GitHub App in GitHub settings and provide only non-secret identifiers/callback configuration to deployment environments.

## 0.4 Security

Status: **Complete for foundation**

Delivered:

- threat model
- security invariants
- root vulnerability-reporting policy
- credential handling rules
- destructive-operation requirements
- native/supply-chain considerations

## 0.5 CI foundation

Status: **Complete after workflow merge**

The Foundation CI validates repository structure and basic secret hygiene. It also detects the future Gradle/native project and will progressively activate deeper validation as implementation phases land.

## Exit criteria

Repository-side Phase 0 work is complete when Foundation CI passes.

The only intentionally external item is actual GitHub App registration because it is an account-level GitHub configuration task, not a repository file.
