# Phase O Report — Advanced GitHub

> **Historical implementation record — Complete.** This report is retained for traceability. For current product behavior and operations, use the [Documentation Hub](README.md).

## Status

**Implementation complete on `phase-o-advanced-github`; CI validation is required before merge.**

## O.1 Discussions

- typed discussion/category models;
- repository discussion/category GraphQL query;
- create-discussion GraphQL mutation;
- category-aware mobile authoring flow;
- capability/error propagation through the shared platform layer.

## O.2 Projects V2

- typed Project V2 models;
- repository-linked project listing;
- create Project V2 mutation using GitHub node IDs;
- repository/owner node identity lookup;
- mobile Project creation flow.

## O.3 GitHub Pages

- Pages site detection;
- disabled-site state instead of treating a normal 404 as a fatal screen failure;
- default-branch discovery;
- Pages enable request;
- Pages build request;
- site/source/build status UI.

## O.4 Repository security

- Dependabot alert feed;
- code-scanning alert feed;
- secret-scanning alert feed;
- open-alert aggregation;
- independent permission/capability state per feed;
- no mutation of alert state.

## O.5 Gists

- authenticated-user Gist list;
- create public/secret Gist;
- delete Gist;
- mobile filename/content/description creation flow.

## O.6 Codespaces

- authenticated-user Codespaces list;
- create Codespace for current repository;
- start;
- stop;
- delete;
- repository/default-branch aware creation.

## O.7 Integration and validation

- dedicated `AdvancedGitHubGateway`;
- Hilt binding;
- dedicated ViewModel and typed section state;
- repository-detail navigation;
- capability-aware UI;
- JVM parser tests;
- Compose rendering test;
- permission documentation;
- Advanced GitHub architecture documentation.

## Files introduced

```text
app/src/main/java/com/nexora/git/core/advanced/
├── AdvancedGitHubGateway.kt
├── AdvancedGitHubGatewayModule.kt
├── AdvancedGitHubJsonParser.kt
├── AdvancedGitHubModels.kt
└── GitHubAdvancedGitHubGateway.kt

app/src/main/java/com/nexora/git/feature/advanced/
├── AdvancedGitHubScreen.kt
└── AdvancedGitHubViewModel.kt

app/src/test/java/com/nexora/git/core/advanced/
└── AdvancedGitHubJsonParserTest.kt

app/src/androidTest/java/com/nexora/git/feature/advanced/
└── AdvancedGitHubContentTest.kt
```

## External prerequisite

The production GitHub App registration must request and receive approval for the Phase O permissions documented in `docs/GITHUB_APP.md`.

Repository implementation must remain capability-aware because organization policy and user privileges can still make an API unavailable even after the App requests the corresponding permission.

---

[← Documentation hub](README.md)
