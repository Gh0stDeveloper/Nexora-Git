# Nexora Git Roadmap

This roadmap is intentionally staged. A phase is not complete if it only contains mock UI.

## Phase 0 — Product Foundation

### 0.1 Product identity
- final branding
- package/application IDs
- icon direction
- license review

### 0.2 Architecture
- module graph
- dependency rules
- domain contracts
- error model

### 0.3 GitHub App
- register application
- callback configuration
- baseline permission matrix

### 0.4 Security
- threat model
- token model
- logging policy
- secret policy

### 0.5 CI foundation
- Gradle validation
- lint
- unit tests
- native build checks
- debug APK artifact

## Phase A — Android Foundation

- Kotlin project
- Jetpack Compose
- Material 3 design system
- navigation
- Hilt
- Room
- DataStore
- networking
- common result/error types
- test modules

## Phase B — GitHub Authentication

- GitHub App authorization
- PKCE generator
- state validation
- browser authorization
- callback handling
- auth broker contract
- token exchange
- Keystore-backed local session
- refresh/expiry handling
- logout
- multiple-account architecture

## Phase C — GitHub Platform Layer

- REST client
- GraphQL client
- API version centralization
- pagination
- rate-limit manager
- cache
- permissions resolver
- standardized API errors

## Phase D — Real Git Engine

- libgit2 Android build
- NDK/CMake
- JNI bridge
- GitEngine Kotlin contract
- init
- status
- stage/unstage
- commit
- branches
- checkout
- clone
- fetch
- pull
- push
- diff
- merge
- conflicts
- native tests

## Phase E — Android Project Storage

- SAF folder picker
- persisted URI permissions
- workspace registry
- direct-filesystem strategy
- managed-workspace strategy
- sync engine
- project scanner
- .gitignore awareness
- secret-risk warnings
- large-file warnings

## Phase F — Repository Experience

- repository list
- repository details
- create repository
- import local project
- clone
- fork
- star/watch
- repository settings allowed by permissions

## Phase G — Code Browser

- directories
- file viewer
- syntax highlighting
- Markdown
- images
- history
- blame
- share/download operations

## Phase H — Mobile Editor

- editor core
- line numbers
- syntax highlighting
- search/replace
- undo/redo
- indentation settings
- save
- diff
- commit flow

## Phase I — Complete Daily Git Workflow

- changes
- staging
- commit history
- branch manager
- remotes
- pull strategies
- merge
- conflict resolution
- advanced push handling

## Phase J — Issues

- list/search/filter
- create/edit
- labels
- assignees
- milestones
- comments
- reactions
- close/reopen

## Phase K — Pull Requests and Review

- list/details
- create
- changed files
- review comments
- approve/request changes
- checks
- merge methods
- draft state

## Phase L — GitHub Actions

- workflows
- workflow runs
- jobs
- steps
- logs
- dispatch
- cancel
- re-run
- artifacts

## Phase M — Releases

- tags
- releases
- drafts
- prereleases
- release assets
- downloads

## Phase N — Account and Social

- profile
- organizations
- stars
- followers/following
- activity
- notifications

## Phase O — Advanced GitHub

Where supported by current APIs and permissions:

- Discussions
- Projects
- Pages
- repository security views
- Gists
- Codespaces management

## Phase P — Advanced Git

- rebase
- cherry-pick
- stash
- reset
- revert
- tags
- submodules
- Git LFS

## Phase Q — Advanced Mobile Development

- Tree-sitter
- richer language intelligence
- optional LSP architecture
- formatters
- project templates
- advanced search

## Phase R — Production

- security audit
- performance audit
- accessibility audit
- release signing
- stable GitHub Release
- Play Store readiness
- optional F-Droid readiness

## Definition of Done

A feature can be marked complete only when applicable UI, implementation, errors, permissions, tests, accessibility and documentation are present.
