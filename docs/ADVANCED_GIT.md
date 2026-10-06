# Advanced Git

Phase P extends Nexora Git from the daily Git workflow into explicit local history-rewriting and repository-maintenance operations.

## Architecture

Advanced Git remains inside the existing `GitEngine` abstraction.

- Kotlin exposes typed requests/results.
- `Libgit2GitEngine` keeps native work on `Dispatchers.IO`.
- `NativeGitBridge` is the JNI boundary.
- `native/git/src/advanced_git.cpp` contains the new libgit2 operations.
- `GitWorkspaceViewModel` owns operation state and recovery.
- The Git workspace includes an **Advanced** tab instead of creating a disconnected second repository UI.

The implementation uses the same pinned libgit2 revision as the rest of the app.

## Explicit rebase

The existing pull-with-rebase workflow remains unchanged. Phase P adds an explicit local rebase onto any resolvable branch, tag or commit.

Safety rules:

- repository operation state must be idle;
- working tree must be clean;
- conflicts preserve libgit2 rebase state;
- the existing continue/abort rebase flow resumes or restores the operation.

## Cherry-pick

A single commit can be cherry-picked by SHA or resolvable ref.

For a clean application Nexora Git:

1. applies the commit with libgit2;
2. preserves the source commit author;
3. records the active user as committer;
4. creates the new commit on HEAD.

If conflicts occur, `CHERRY_PICK_HEAD` and repository state remain available. The user resolves files through the existing conflict workflow and can then continue or abort.

## Stash

The Advanced tab supports:

- stash tracked changes;
- optionally include untracked files;
- list local stashes;
- apply without removing;
- pop;
- drop with destructive confirmation.

Stashes are native libgit2 stash objects, not application-side snapshots.

## Reset

Supported modes:

- **soft** — move HEAD and preserve index/worktree;
- **mixed** — move HEAD and reset index;
- **hard** — move HEAD and reset index/worktree.

Hard reset always requires an additional UI confirmation because uncommitted changes may be permanently lost.

## Revert

Revert is implemented as an inverse commit through libgit2.

- clean revert produces a new commit;
- conflicts preserve `REVERT_HEAD`;
- resolved conflicts can be continued;
- the operation can be aborted.

This is intentionally separate from reset because revert preserves published history.

## Local tags

Phase P local tag support is independent from the GitHub Release/tag APIs implemented earlier.

Supported local operations:

- list lightweight and annotated tags;
- inspect target OID;
- inspect annotation/tagger metadata;
- create lightweight tag;
- create annotated tag;
- delete local tag with confirmation.

Remote tag publication still uses normal Git push semantics.

## Submodules

Nexora Git can:

- enumerate declared submodules;
- inspect URL, path and repository/worktree OIDs;
- inspect initialization state;
- sync the configured URL;
- initialize and update a submodule;
- reuse active GitHub OAuth credentials for eligible GitHub HTTPS submodule URLs.

Submodule operations run through libgit2 and do not shell out to an external Git executable.

## Git LFS

The current Phase P implementation provides a capability-aware LFS foundation:

- read standard `.gitattributes` LFS tracking rules;
- add standard `filter=lfs diff=lfs merge=lfs -text` rules;
- remove LFS tracking rules;
- detect and validate standard Git LFS pointer files;
- display tracked patterns and detected pointers in the Advanced Git UI;
- bound pointer scanning to protect mobile performance.

### Transfer boundary

Bundled libgit2 does not itself implement the Git LFS clean/smudge object-transfer protocol. Nexora Git therefore **does not claim that an LFS object has been downloaded or uploaded when only its pointer is available**.

Object transfer remains explicitly capability-gated until an embedded, authenticated LFS transport and clean/smudge integration are present and validated. This avoids silently committing large raw files or presenting pointer files as hydrated assets.

## Recovery and destructive-action policy

Nexora Git distinguishes operations that can be resumed from operations that immediately mutate local history.

Recovery-aware states:

- merge;
- rebase;
- cherry-pick;
- revert.

Explicit confirmations are required for:

- hard reset;
- dropping a stash;
- deleting a local tag;
- force-with-lease push (from the existing daily workflow).

## Validation

Phase P adds:

- native repository workflow coverage for rebase, cherry-pick, stash, reset, revert, tags and submodule enumeration;
- JVM parsing tests for advanced native payloads;
- JVM tests for LFS rules and pointer detection;
- Compose coverage for the Advanced Git surface.

The native test creates real temporary repositories and validates resulting files/history instead of mocking libgit2.
