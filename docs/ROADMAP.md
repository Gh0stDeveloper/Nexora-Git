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

**Status: complete and CI validated.**

### I.1 Daily workbench — Complete
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
- [x] fetch remote
- [x] pull with merge strategy
- [x] explicit branch/ref merge
- [x] conflict list and editor handoff
- [x] manual conflict resolution
- [x] use-ours / use-theirs conflict resolution
- [x] continue conflicted merge with two-parent commit
- [x] conflict-marker check before marking resolved
- [x] explicit non-force push refspec
- [x] branch/refspec safety policy and JVM tests

### I.2 Pull strategies and remotes — Complete
- [x] fast-forward-only pull
- [x] rebase pull
- [x] persisted rebase conflict state
- [x] continue rebase
- [x] abort rebase
- [x] multi-remote list/add/rename/remove
- [x] upstream tracking controls
- [x] selected-remote-aware pull behavior

### I.3 Advanced push and divergence — Complete
- [x] ahead/behind state
- [x] non-fast-forward rejection guidance
- [x] upstream creation flow after first push
- [x] guarded force-with-lease
- [x] exact remote OID lease verification immediately before push
- [x] stale lease rejection
- [x] destructive branch-name confirmation
- [x] protected-branch / repository-rule guidance without bypass behavior

### I.4 Validation — Complete
- [x] Git workspace Compose instrumentation tests
- [x] JVM parser and safety-policy tests
- [x] native tests for remotes/upstream/ahead-behind
- [x] native tests for FF-only / rebase / continue / abort
- [x] native tests for conflicted merge continuation
- [x] native tests for ours / theirs resolution
- [x] native tests for force-with-lease and stale-lease rejection
- [x] Android CI validation
- [x] Native Git CI validation
- [x] Foundation CI validation
- [x] final Phase I documentation

## Phase J — Issues

**Status: complete and CI validated.**

### J.1 Discovery — Complete
- [x] list open / closed / all
- [x] repository-scoped search
- [x] label filters
- [x] assignee filters
- [x] milestone filters
- [x] bounded pagination
- [x] Pull Request exclusion

### J.2 Authoring and lifecycle — Complete
- [x] create issue
- [x] edit title/body
- [x] labels
- [x] assignees
- [x] milestones
- [x] close
- [x] reopen

### J.3 Comments and reactions — Complete
- [x] list comments
- [x] create comment
- [x] edit own comment
- [x] delete own comment
- [x] issue reactions
- [x] comment reactions
- [x] reaction summaries

### J.4 Validation — Complete
- [x] typed Issue gateway
- [x] GitHub REST integration
- [x] navigation and Compose surfaces
- [x] JVM parser tests
- [x] Compose tests
- [x] Issues documentation
- [x] Android CI validation
- [x] Foundation CI validation
- [x] pull-request integration validation

## Phase K — Pull Requests and Review

**Status: complete and CI validated.**

### K.1 Pull request lifecycle — Complete
- [x] list/details
- [x] create
- [x] edit title/body/base
- [x] close/reopen
- [x] draft creation
- [x] draft ↔ ready-for-review state

### K.2 Files and review comments — Complete
- [x] changed files
- [x] patch hunks
- [x] inline review comments
- [x] edit/delete own inline comments

### K.3 Review and checks — Complete
- [x] submitted reviews
- [x] approve
- [x] request changes
- [x] general review comment
- [x] check runs for head SHA
- [x] check-state summary

### K.4 Merge — Complete
- [x] repository-aware merge methods
- [x] merge commit
- [x] squash merge
- [x] rebase merge
- [x] expected head SHA guard

### K.5 Validation — Complete
- [x] typed Pull Request gateway
- [x] REST + GraphQL integration
- [x] navigation and Compose surfaces
- [x] JVM parser tests
- [x] Compose tests
- [x] Pull Requests documentation
- [x] Android CI validation
- [x] Foundation CI validation
- [x] pull-request integration validation

## Phase L — GitHub Actions

**Status: complete and CI validated.**

### L.1 Workflows — Complete
- [x] workflow list
- [x] active/disabled state
- [x] workflow selection
- [x] workflow dispatch
- [x] Git ref input
- [x] generic workflow inputs

### L.2 Runs — Complete
- [x] repository runs
- [x] workflow-scoped runs
- [x] status/conclusion filters
- [x] run details
- [x] run number and attempt
- [x] event / head branch / head SHA / actor

### L.3 Jobs and steps — Complete
- [x] jobs
- [x] runner metadata
- [x] labels
- [x] steps
- [x] status and conclusions

### L.4 Logs — Complete
- [x] job logs
- [x] secure authenticated redirect handling
- [x] token stripping on signed external URLs
- [x] 2 MiB memory-safe preview
- [x] truncation indicator

### L.5 Run controls — Complete
- [x] cancel
- [x] re-run all jobs
- [x] re-run failed jobs
- [x] state-aware actions

### L.6 Artifacts — Complete
- [x] artifact list
- [x] expiration metadata
- [x] streaming ZIP download
- [x] atomic partial-file handling
- [x] disk-full handling
- [x] 2 GiB safety limit

### L.7 Validation — Complete
- [x] typed Actions gateway
- [x] secure binary downloader
- [x] navigation and Compose surfaces
- [x] JVM parser tests
- [x] dispatch input tests
- [x] Compose tests
- [x] GitHub Actions documentation
- [x] Android CI validation
- [x] Foundation CI validation
- [x] pull-request integration validation

## Phase M — Releases

**Status: complete and CI validated.**

### M.1 Tags — Complete
- [x] list tags
- [x] commit SHA
- [x] create lightweight tag
- [x] delete tag
- [x] tag/SHA validation

### M.2 Release lifecycle — Complete
- [x] list releases
- [x] published/draft/prerelease filters
- [x] release details
- [x] create release
- [x] generated release notes option
- [x] edit release
- [x] publish draft
- [x] convert to draft
- [x] prerelease state
- [x] latest-release policy
- [x] preserve latest policy unless explicitly changed
- [x] delete release
- [x] immutable-release protection

### M.3 Release assets — Complete
- [x] asset metadata
- [x] upload
- [x] rename / label update
- [x] delete
- [x] download
- [x] download counts / digest

### M.4 Transfer safety — Complete
- [x] app-private upload staging
- [x] streaming binary transfer
- [x] secure signed-download redirects
- [x] token stripping on external redirect
- [x] atomic partial downloads
- [x] 2 GiB safety limits
- [x] disk-full handling

### M.5 Validation — Complete
- [x] typed Releases gateway
- [x] REST + binary integration
- [x] navigation and Compose surfaces
- [x] JVM parser tests
- [x] Compose tests
- [x] Releases documentation
- [x] Android CI validation
- [x] Foundation CI validation
- [x] pull-request integration validation

## Phase N — Account and Social

**Status: complete and CI validated for GitHub App-supported APIs. GitHub's native Notifications REST inbox remains unavailable under GitHub App user access tokens, so Nexora Git capability-guards it instead of issuing unsupported requests.**

### N.1 Profile — Complete
- [x] authenticated GitHub profile
- [x] profile metadata and metrics
- [x] edit profile
- [x] hireable state
- [x] active-account refresh
- [x] multi-account management preserved

### N.2 Organizations and stars — Complete
- [x] organizations
- [x] starred repositories
- [x] repository navigation
- [x] star/unstar API
- [x] unstar from Profile

### N.3 Followers and following — Complete
- [x] followers
- [x] following
- [x] follow
- [x] unfollow
- [x] local relationship reconciliation

### N.4 Activity — Complete
- [x] recent account activity
- [x] common GitHub event summaries
- [x] action/ref/number/title metadata
- [x] public/private state
- [x] repository navigation

### N.5 Notifications compatibility — Complete within the GitHub App auth contract
- [x] detect the primary auth model as GitHub App user access token
- [x] do not call unsupported GitHub Notifications REST endpoints
- [x] explain the upstream limitation in-app
- [x] browser handoff to GitHub notification inbox
- [x] no silent secondary credential
- [x] document that a native notification inbox would require a separate OAuth-App credential model

### N.6 Validation — Complete
- [x] typed Social gateway
- [x] REST integration for supported account/social APIs
- [x] Profile and Activity ViewModels
- [x] Profile and Activity Compose surfaces
- [x] JVM parser tests
- [x] Compose tests
- [x] Account and Social documentation
- [x] Android CI validation
- [x] Foundation CI validation
- [x] pull-request integration validation

## Phase O — Advanced GitHub

**Implementation status: complete on the Phase O branch; CI validation pending before merge.**

### O.1 Discussions — Complete
- [x] discussion categories
- [x] recent repository discussions
- [x] create discussion
- [x] permission/capability errors

### O.2 Projects V2 — Complete
- [x] repository-linked Projects V2
- [x] project metadata and item counts
- [x] create repository-linked Project V2
- [x] repository/owner node identity handling

### O.3 GitHub Pages — Complete
- [x] detect Pages state
- [x] source/build metadata
- [x] enable Pages from the real default branch
- [x] request Pages build
- [x] disabled-site capability state

### O.4 Repository security — Complete
- [x] Dependabot alerts
- [x] code-scanning alerts
- [x] secret-scanning alerts
- [x] independent feed permission handling
- [x] read-only security review surface

### O.5 Gists — Complete
- [x] authenticated-user Gist list
- [x] create public/secret Gist
- [x] delete Gist

### O.6 Codespaces — Complete
- [x] authenticated-user Codespaces list
- [x] create Codespace for current repository
- [x] start / stop
- [x] delete
- [x] default-branch aware creation

### O.7 Validation — In progress
- [x] typed Advanced GitHub gateway
- [x] REST + GraphQL integration
- [x] repository navigation and Compose surface
- [x] JVM parser tests
- [x] Compose test
- [x] permission documentation
- [x] Advanced GitHub documentation
- [ ] Android CI validation
- [ ] Foundation CI validation
- [ ] pull-request integration validation

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
