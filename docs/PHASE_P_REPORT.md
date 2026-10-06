# Phase P Report — Advanced Git

## Scope

Phase P expands the real local Git engine with advanced operations while preserving the safety and recovery guarantees introduced by the daily workflow.

## Implemented

### P.1 Explicit rebase
- [x] rebase onto arbitrary resolvable ref
- [x] clean-working-tree guard
- [x] conflict preservation
- [x] reuse existing continue/abort rebase recovery

### P.2 Cherry-pick
- [x] cherry-pick single commit
- [x] source author preservation
- [x] current-user committer
- [x] conflict state
- [x] continue
- [x] abort

### P.3 Stash
- [x] save
- [x] optional untracked files
- [x] list
- [x] apply
- [x] pop
- [x] drop
- [x] destructive-action confirmation

### P.4 Reset and revert
- [x] soft reset
- [x] mixed reset
- [x] hard reset
- [x] hard-reset confirmation
- [x] revert commit
- [x] revert conflict state
- [x] continue revert
- [x] abort revert

### P.5 Local tags
- [x] list local tags
- [x] lightweight tags
- [x] annotated tags
- [x] target/tagger metadata
- [x] create
- [x] delete with confirmation

### P.6 Submodules
- [x] enumerate submodules
- [x] initialization/status metadata
- [x] sync URL
- [x] initialize/update
- [x] eligible GitHub OAuth credential reuse

### P.7 Advanced mobile surface
- [x] Advanced tab inside Git Workspace
- [x] shared repository state
- [x] shared conflict workflow
- [x] operation recovery controls
- [x] destructive-action confirmations
- [x] status summaries for cherry-pick/revert

### P.8 Git LFS
- [x] parse standard tracking rules
- [x] add/remove standard tracking rules
- [x] detect standard pointer files
- [x] bounded mobile scan
- [x] Advanced Git UI
- [ ] authenticated LFS object transfer
- [ ] embedded clean/smudge transport

Git LFS is intentionally not marked fully complete while object transfer is unavailable. The app reports this capability boundary instead of pretending pointer-only support is equivalent to a complete LFS implementation.

## Validation

- [x] native core compiles against the pinned libgit2 revision
- [x] JNI contract compiles
- [x] parser unit coverage added
- [x] LFS JVM coverage added
- [x] Advanced Git Compose coverage added
- [x] native real-repository workflow coverage added
- [ ] final Native Git CI after full test expansion
- [ ] final Android CI
- [ ] final Foundation CI
- [ ] pull-request integration validation

## Merge gate

Phase P must not be marked complete or merged as a finished phase until:

1. Native Git, Android and Foundation CI pass on the final branch head.
2. Git LFS object transfer/clean-smudge scope is either fully implemented and tested, or the roadmap explicitly re-scopes it into a named follow-up accepted by the project.
3. The final pull request is validated against `main`.

See [Advanced Git](ADVANCED_GIT.md) for the implementation details and safety model.
