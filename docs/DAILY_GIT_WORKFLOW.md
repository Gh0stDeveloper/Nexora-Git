# Daily Git Workflow

Nexora Git Phase I provides a complete local Git workbench backed by the real libgit2 engine.

## Changes and commits

The Git workspace separates working-tree changes from staged changes. Individual paths can be staged or unstaged, and Stage all intentionally excludes unresolved conflicts.

Commits operate only on the index. A commit is blocked while unresolved merge or rebase conflicts remain.

## Branches and upstreams

The branch manager supports:

- local and remote branch discovery
- create and switch
- safe checkout
- explicit merge
- upstream assignment and removal
- ahead/behind calculation against the configured upstream

Ahead/behind is calculated by libgit2 from the local and upstream commit graphs.

## Remotes

The Android UI supports multiple remotes:

- list
- add
- rename
- remove
- select for fetch/pull/push

For credential isolation, a remote added through the Android UI must use an official GitHub HTTPS repository URL. Existing repositories can still contain other remotes; credentials are supplied only through the existing runtime credential provider.

## Pull strategies

Three explicit strategies are available.

### Merge

Fetches the selected remote and integrates the selected remote-tracking branch using fast-forward when possible or a merge commit when histories diverge.

### Fast-forward only

Fetches first and refuses the pull if the local branch cannot be advanced without creating a merge commit.

### Rebase

Fetches first. A clean divergence is replayed with libgit2 rebase operations. If a conflict occurs, the repository retains its rebase state so the user can edit and stage conflicted files, then Continue or Abort.

## Conflict resolution

Conflicted paths are surfaced in the workbench and open directly in the Phase H mobile editor.

Before a path can be marked resolved, Nexora Git checks that standard conflict markers are no longer present. Resolution stages the path through libgit2.

## Push safety

Normal Push never adds a force refspec.

After a successful first push, Nexora Git fetches the selected remote and attempts to configure the created destination as the branch upstream.

### Force with lease

History rewriting is a separate action. It requires:

1. an exact remote-tracking OID from a verified local tracking ref;
2. explicit branch-target matching in the UI;
3. typing the branch name in a destructive-action confirmation;
4. a live remote advertisement check in the native layer immediately before push.

The native layer compares the server-advertised destination OID with the expected OID. If they differ, the operation is rejected rather than overwriting a newer remote update.

Generic force push is not exposed.

## Repository rules

GitHub branch protection and repository rules remain authoritative. Nexora Git does not attempt to bypass a rejected protected-branch update.

## Recovery and validation

Native workflow tests exercise real disposable repositories for:

- remotes
- upstream tracking
- ahead/behind
- fast-forward-only pull
- clean rebase
- rebase conflict continuation
- rebase abort
- force-with-lease success
- stale lease rejection

Android tests cover the workbench summary, pull strategies and guarded lease-push UI.
