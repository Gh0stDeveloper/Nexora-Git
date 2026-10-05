# Phase I — Complete Daily Git Workflow

Date: 2026-10-05

## Status

**In progress — first functional slice implemented on `phase-i-daily-git-workflow`.**

Phase I is being built on top of the completed Phase H editor branch so the daily Git workflow can use the same real libgit2 repositories and the same mobile editor for conflict resolution.

## Delivered in I.1

- dedicated Git workspace route for local repositories
- working-tree and staged change lists
- stage / unstage per path
- safe stage-all that excludes unresolved conflicts
- unstage-all
- staged-only commit flow with GitHub noreply author defaults
- commit history
- local and remote branch listing
- create-and-switch branch flow
- safe branch checkout through the existing libgit2 checkout
- origin URL discovery from the real repository
- fetch origin
- pull using merge strategy
- explicit merge of local or remote refs
- conflict list
- conflict-to-editor flow
- conflict marker check before marking a file resolved
- explicit current-branch push to a selected remote branch
- no force push path
- branch/refspec validation
- JVM policy tests

## Safety invariants

- push refspecs never receive the `+` force prefix
- invalid branch names are rejected before native Git is called
- unresolved conflict files are not included by Stage all
- a conflict cannot be marked resolved while standard conflict markers remain
- branch checkout continues to use the native safe checkout behavior
- credentials remain runtime-only through the existing Git credential provider

## Still required before Phase I is complete

### I.2 Pull strategies and remote management
- fast-forward-only pull
- rebase pull strategy
- explicit multi-remote list/add/rename/remove
- upstream tracking controls

### I.3 Advanced push and divergence UX
- ahead/behind information
- non-fast-forward rejection guidance
- upstream creation flow
- optional force-with-lease design with explicit destructive confirmation
- protected-branch-aware guidance where GitHub data is available

### I.4 Validation
- Compose instrumentation coverage for Git workspace
- native tests for any new remote/pull primitives
- Android CI / Native Git CI / Foundation CI
- final Phase I documentation and roadmap completion

Phase I must not be marked complete until these remaining items are implemented and CI validated.
