# Phase I — Complete Daily Git Workflow

> **Historical implementation record — Complete.** This report is retained for traceability. For current product behavior and operations, use the [Documentation Hub](README.md).

Date: 2026-10-05

## Status

**Complete and CI validated.**

Phase I extends the real libgit2 engine and the Phase H mobile editor into a complete daily Git workflow for Android.

## I.1 — Daily workbench

Delivered:

- dedicated Git workspace route for local repositories
- working-tree and staged change lists
- stage / unstage per path
- safe stage-all that excludes unresolved conflicts
- unstage-all
- staged-only commit flow with GitHub noreply author defaults
- commit history
- local and remote branch listing
- create-and-switch branch flow
- safe branch checkout
- explicit branch/ref merge
- conflict list and editor handoff
- manual conflict resolution
- native use-ours / use-theirs resolution
- merge continuation that preserves both merge parents
- branch/refspec validation
- explicit normal push without hidden force behavior

## I.2 — Pull strategies and remotes

Delivered:

- merge pull
- fast-forward-only pull
- rebase pull
- persisted native rebase state across conflict resolution
- continue rebase
- abort rebase
- multi-remote list/add/rename/remove
- GitHub HTTPS remote validation for remotes created through the Android UI
- upstream assignment and removal
- selected-remote-aware pull behavior

## I.3 — Advanced push and divergence

Delivered:

- local/upstream ahead-behind calculation from the commit graph
- non-fast-forward guidance
- first-push upstream creation flow
- explicit guarded force-with-lease
- exact remote branch OID verification against the live server advertisement
- stale-lease rejection
- branch-name confirmation before destructive push
- no generic force-push action
- protected-branch and repository-rule errors remain authoritative

## Conflict safety

Conflicts support three resolution paths:

1. edit manually in the Phase H mobile editor;
2. use ours;
3. use theirs.

A merge conflict remains in Git's native merge state. After all paths are resolved, Continue merge creates the final two-parent merge commit and cleans the merge state.

A rebase conflict remains in Git's native rebase state. After resolution, Continue rebase resumes the operation; Abort restores the pre-rebase state.

## Push safety

Normal pushes never use a force refspec.

Force with lease requires all of the following:

- an explicit destination branch
- a verified remote-tracking OID
- confirmation of the branch name in the UI
- a fresh server-advertised OID match immediately before push

If the remote changed after the local verification point, the push is rejected.

## Validation

Phase I was validated with:

- JVM Git JSON parser tests
- JVM workflow safety-policy tests
- Compose instrumentation coverage for workbench summary, pull strategies, merge state and guarded lease push
- native disposable-repository smoke tests for remotes and upstream tracking
- ahead/behind tests
- FF-only pull tests
- clean rebase tests
- rebase conflict continue/abort tests
- conflicted merge continuation with a two-parent merge commit
- ours/theirs conflict-resolution tests
- force-with-lease success and stale-lease rejection tests

Final validation results:

- **Foundation CI — success**
- **Native Git CI — success**
- **Android CI — success**

## Result

Phase I is complete. The next roadmap milestone is **Phase J — Issues**.

---

[← Documentation hub](README.md)
