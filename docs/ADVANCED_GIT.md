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

Phase P includes local clean/smudge behavior and authenticated GitHub LFS object transfer.

### Tracking and pointer model

Nexora Git can:

- read standard `.gitattributes` LFS tracking rules;
- add `filter=lfs diff=lfs merge=lfs -text` rules;
- remove tracking rules;
- detect and validate standard Git LFS pointer files;
- bound workspace scanning to protect mobile performance.

### Native clean/smudge filter

A custom libgit2 filter named `lfs` is registered during Git engine initialization.

On **clean** (working tree → object database), the filter:

1. computes SHA-256 with Mbed TLS;
2. stores the raw object under `.git/lfs/objects/aa/bb/<sha256>`;
3. passes the standard LFS pointer to libgit2.

On **smudge** (object database → working tree), the filter:

1. parses the pointer;
2. validates the expected size;
3. restores the cached raw object when present;
4. leaves the pointer intact when the object is not yet local.

Leaving a missing pointer intact is deliberate: data is never fabricated.

### GitHub LFS transfer

`GitLfsTransport` implements the Git LFS basic transfer protocol for authenticated GitHub HTTPS remotes.

- batch requests use `application/vnd.git-lfs+json`;
- the active GitHub OAuth token authenticates only the GitHub batch endpoint;
- server-provided upload/download action headers are honored;
- the GitHub token is not copied to presigned external object URLs;
- downloaded data is verified by size and SHA-256 before entering the cache;
- upload supports optional server verification actions;
- objects are processed in bounded batches.

Push uploads cached LFS objects before publishing Git refs, preventing a ref from being published before its local LFS objects are offered to the server.

The Advanced Git UI also exposes explicit **Download LFS** and **Upload LFS** actions. Repository clone attempts an immediate hydration pass; if the remote object transfer is temporarily unavailable, the clone remains usable with standard pointer files and can be hydrated later from the Advanced tab.

Current authenticated object transfer is intentionally limited to GitHub HTTPS remotes. Other LFS servers are not given GitHub credentials.

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
