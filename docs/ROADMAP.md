# Nexora Git Roadmap

This roadmap is intentionally staged. A phase is not complete if it only contains mock UI.

## Phase 0 — Product Foundation

**Repository status: complete.** The actual GitHub App registration remains an external account-level configuration item; its repository contract is complete.

### 0.1 Product identity — Complete
- [x] final product name and positioning
- [x] recommended package/application ID
- [x] icon direction and brand rules
- [x] Apache License 2.0 and NOTICE

### 0.2 Architecture — Complete
- [x] module/dependency direction
- [x] native Android Kotlin/Compose ADR
- [x] GitHub App OAuth/PKCE ADR
- [x] libgit2/JNI real Git engine ADR
- [x] domain/error responsibility boundaries

### 0.3 GitHub App — Repository contract complete
- [x] callback architecture
- [x] baseline least-privilege permission matrix
- [x] installation/token rules
- [x] registration checklist
- [ ] create the real GitHub App in GitHub account settings

### 0.4 Security — Complete
- [x] threat model
- [x] token/credential model
- [x] logging/secret policy
- [x] root vulnerability-reporting policy
- [x] destructive-operation invariants

### 0.5 CI foundation — Complete
- [x] Foundation CI workflow
- [x] required-file validation
- [x] basic committed-secret hygiene check
- [x] GitHub Actions Dependabot baseline
- [x] conditional Gradle validation hook for Phase A
- [x] CI prepared to expand with Android/native builds in later phases

## Phase A — Android Foundation

**Status: complete.**

- [x] Kotlin/Android application project
- [x] Jetpack Compose
- [x] Material 3 design system baseline
- [x] edge-to-edge system UI handling
- [x] Navigation Compose shell
- [x] Home / Explore / Repositories / Activity / Profile destinations
- [x] Hilt dependency injection
- [x] Room database baseline
- [x] DataStore settings baseline
- [x] Retrofit + OkHttp GitHub API baseline
- [x] shared result/error model
- [x] unit test baseline
- [x] instrumentation/Compose test baseline
- [x] Gradle Wrapper committed and pinned
- [x] Android CI: assembleDebug + unit tests + lint
- [x] debug APK artifact upload

## Phase B — GitHub Authentication

**Implementation status: complete and CI validated.**

- [x] GitHub App authorization architecture
- [x] PKCE S256 generator
- [x] cryptographically random OAuth state
- [x] official GitHub authorization through Custom Tabs
- [x] strict native callback parsing
- [x] state mismatch and authorization-expiry validation
- [x] confidential Auth Broker contract
- [x] authorization-code exchange
- [x] Android Keystore-backed AES-GCM token protection
- [x] access-token expiry handling
- [x] refresh-token rotation
- [x] authenticated GitHub identity lookup
- [x] logout with best-effort remote token revocation
- [x] multiple-account persistence and switching
- [x] Room v1 → v2 migration for account metadata
- [x] Android authentication tests
- [x] Auth Broker tests, vet and build CI
- [x] deployment runbook
- [ ] register the production GitHub App (external account-level prerequisite)
- [ ] deploy the production Auth Broker with its real secret (external deployment prerequisite)

## Phase C — GitHub Platform Layer

**Status: complete and CI validated.**

- [x] authenticated REST client
- [x] authenticated GraphQL client
- [x] automatic token refresh and one-time 401 retry
- [x] REST API version centralized at `2026-03-10`
- [x] REST `Link` header pagination
- [x] GraphQL `pageInfo` model/parser
- [x] per-resource rate-limit tracking
- [x] primary/secondary rate-limit error distinction
- [x] bounded account-scoped in-memory cache
- [x] REST ETag / `If-None-Match` / 304 handling
- [x] network-first and cache-first policies
- [x] mutation cache invalidation
- [x] GitHub App accepted-permission resolver
- [x] standardized API/domain errors
- [x] official GitHub-host URL validation
- [x] platform unit tests
- [x] architecture and usage documentation

## Phase D — Real Git Engine

**Status: complete and CI validated.**

- [x] libgit2 1.9.7 native build
- [x] immutable libgit2 commit pin
- [x] Mbed TLS 3.6.7 LTS HTTPS backend
- [x] immutable Mbed TLS commit pin
- [x] Android NDK 27.2.12479018
- [x] CMake 3.22.1
- [x] arm64-v8a / armeabi-v7a / x86_64
- [x] JNI bridge
- [x] stable Kotlin `GitEngine` contract
- [x] typed native Git exception boundary
- [x] GitHub OAuth HTTPS credential provider
- [x] Kotlin + native GitHub credential host isolation
- [x] repository init on `main`
- [x] status
- [x] stage / unstage
- [x] commit
- [x] local/remote branch enumeration
- [x] create branch
- [x] safe checkout
- [x] clone
- [x] remote URL inspection
- [x] fetch
- [x] pull with merge analysis
- [x] push without implicit force
- [x] staged / unstaged / full diff
- [x] fast-forward and normal merge
- [x] conflict detection and enumeration
- [x] host-native real repository workflow tests
- [x] Android JNI/NDK compilation
- [x] JVM native-payload/security-policy tests
- [x] release JNI keep rules
- [x] native-engine documentation

## Phase E — Android Project Storage

**Status: complete and CI validated.**

- [x] SAF folder picker
- [x] persisted URI permissions
- [x] read-only permission fallback
- [x] Room v3 workspace registry
- [x] direct-filesystem strategy
- [x] managed-workspace strategy
- [x] non-destructive source → managed sync engine
- [x] sync manifest outside Git working tree
- [x] sync conflict detection
- [x] managed `.git` protection
- [x] project scanner
- [x] nested `.gitignore` awareness
- [x] secret/signing-material warnings
- [x] 50 MiB / 100 MiB large-file policy
- [x] workspace UI and folder picker
- [x] manual sync/removal UI
- [x] storage unit and Compose tests
- [x] storage architecture documentation

## Phase F — Repository Experience

**Status: complete and CI validated.**

- [x] account-scoped repository list
- [x] Room v4 offline repository metadata cache
- [x] REST pagination across authenticated repositories
- [x] repository details
- [x] offline repository-detail snapshot fallback
- [x] create repository
- [x] import local project into real Git
- [x] clone through libgit2 into app-private workspace
- [x] clone by repository card or validated GitHub HTTPS URL
- [x] fork repository
- [x] star / unstar
- [x] watch / unwatch through GraphQL subscription state
- [x] capability-aware watch controls
- [x] permission-aware repository settings
- [x] direct / managed / remote-clone workspace integration
- [x] repository list and detail Compose UI
- [x] actionable API / permission / rate-limit errors
- [x] repository parser / URL / cache tests
- [x] repository Compose tests
- [x] repository experience documentation

## Phase G — Code Browser

**Status: complete and CI validated.**

- [x] workspace-rooted directory navigation
- [x] breadcrumbs and empty states
- [x] canonical traversal/symlink confinement
- [x] hidden and blocked `.git` internals
- [x] text / Markdown / image / binary file classification
- [x] bounded text viewer
- [x] lightweight syntax highlighting
- [x] Markdown preview
- [x] sampled image preview
- [x] libgit2 file history
- [x] libgit2 blame
- [x] share through constrained FileProvider cache
- [x] save copy through Android document destination
- [x] code browser navigation from workspaces
- [x] native / JVM / Compose tests
- [x] code browser documentation

## Phase H — Mobile Editor

**Status: complete and CI validated.**

- [x] editable text and Markdown files
- [x] 512 KiB mobile editor safety limit
- [x] line numbers
- [x] editable syntax highlighting
- [x] literal search
- [x] match-case option
- [x] replace current / replace all
- [x] bounded undo / redo
- [x] tabs / 2 spaces / 4 spaces
- [x] persisted indentation preference
- [x] atomic save
- [x] external modification detection
- [x] unsaved local diff
- [x] path-scoped libgit2 diff
- [x] save-before-commit
- [x] existing staged-file warning
- [x] real libgit2 commit flow
- [x] GitHub-familiar editor visual language
- [x] distinct Nexora violet application icon
- [x] editor unit / native / Compose tests
- [x] mobile editor documentation

## Phase I — Complete Daily Git Workflow

**Status: in progress on `phase-i-daily-git-workflow`.**

### I.1 Daily workbench — Implemented
- [x] dedicated Git workspace UI
- [x] changes and staged-change views
- [x] stage / unstage per path
- [x] safe stage-all / unstage-all
- [x] staged-only commit flow
- [x] commit history
- [x] local and remote branch listing
- [x] create-and-switch branch flow
- [x] safe checkout
- [x] origin URL discovery
- [x] fetch origin
- [x] pull with merge strategy
- [x] explicit branch/ref merge
- [x] conflict list and editor handoff
- [x] conflict-marker check before marking resolved
- [x] explicit non-force push refspec
- [x] branch/refspec safety policy and JVM tests

### I.2 Pull strategies and remotes — Pending
- [ ] fast-forward-only pull
- [ ] rebase pull
- [ ] multi-remote list/add/rename/remove
- [ ] upstream tracking controls

### I.3 Advanced push and divergence — Pending
- [ ] ahead/behind state
- [ ] non-fast-forward rejection guidance
- [ ] upstream creation flow
- [ ] guarded force-with-lease design
- [ ] protected-branch-aware guidance where available

### I.4 Validation — Pending
- [ ] Git workspace Compose instrumentation tests
- [ ] native tests for new remote/pull primitives
- [ ] Android CI validation
- [ ] Native Git CI validation
- [ ] Foundation CI validation
- [ ] final Phase I documentation

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
