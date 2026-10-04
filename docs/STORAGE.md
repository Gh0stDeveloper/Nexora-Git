# Android Storage and Project Import

## Goal

Nexora Git allows an Android developer to select an existing complete project folder without requesting broad storage access.

The primary integration uses Android's Storage Access Framework (SAF).

## Folder selection

```text
Repositories
    ↓
Open project folder
    ↓
ACTION_OPEN_DOCUMENT_TREE
    ↓
Persist read/write URI permission when granted
    ↓
Scan project
    ↓
Register workspace
    ↓
Direct path or managed workspace
```

The persisted URI grant lets Nexora Git reopen the selected tree after process/app restarts while Android still considers the permission valid.

## SAF versus POSIX filesystem

SAF folders are normally exposed as `content://` document trees.

libgit2 requires normal filesystem access for repository internals, worktree files, locks and object databases. Nexora Git therefore does **not** attempt to derive undocumented raw paths from arbitrary content providers.

## Workspace strategies

### Direct

A direct workspace is used only when Nexora Git already has a legitimate filesystem directory.

```text
Filesystem directory
      ↓
WorkspaceRegistry
      ↓
libgit2
```

Direct workspaces are never physically deleted when removed from Nexora Git; only the registry record is removed.

### Managed

A SAF `content://` project uses an app-private managed workspace:

```text
User project tree (SAF)
          ↓
non-destructive sync
          ↓
filesDir/workspaces/<id>/repo
          ↓
libgit2
```

The selected source remains user-owned.

Nexora Git's managed filesystem copy is the Git-compatible working directory.

## Persisted permissions

`SafPermissionManager`:

- requests a persistable read + write grant;
- falls back to a read-only persistent grant when the provider does not allow write access;
- records whether the source is writable;
- can release the grant when a managed workspace is removed.

A persisted grant is revalidated whenever the source is used because the user/provider can revoke access later.

## Workspace registry

Room schema v3 stores:

```text
WorkspaceEntity
├── id
├── name
├── sourceTreeUri
├── sourceDisplayName
├── sourceAuthority
├── sourceWritable
├── strategy
├── managedWorkspacePath
├── repositoryRemote
├── currentBranch
├── accountId
├── syncState
├── lastSyncedAtEpochMillis
├── lastScanAtEpochMillis
├── fileCount
├── totalBytes
├── secretWarningCount
├── largeFileWarningCount
├── syncConflictCount
└── lastOpenedAtEpochMillis
```

The v2 → v3 Room migration preserves existing workspace records.

## Non-destructive sync

`SafWorkspaceSyncEngine` mirrors the user-selected SAF tree into the managed workspace.

It preserves:

- relative paths;
- file contents;
- directory structure;
- source modification timestamps when supplied by the provider;
- safe deletions of previously mirrored files.

### Conflict protection

Nexora Git stores a sync manifest outside the Git working tree:

```text
filesDir/workspace-meta/<id>/sync-manifest.json
```

Before overwriting/deleting a managed file, the synchronizer compares it against the previous source metadata.

When both the SAF source and the managed file changed since the prior sync, Nexora Git keeps the managed file and records a sync conflict instead of overwriting it.

If a source provider does not expose a reliable modification timestamp, synchronization behaves conservatively rather than claiming a safe overwrite.

### .git protection

If the managed workspace already contains `.git`, subsequent SAF syncs never replace/delete that directory.

This prevents project refreshes from corrupting Git locks, refs, object databases or repository state created by libgit2.

An existing source `.git` may be copied during the initial import when the managed workspace has no repository metadata yet.

## Project scanner

Both SAF and direct filesystem workspaces are scanned.

Recognized project markers include:

- Android/Gradle;
- Godot;
- Node.js;
- Rust;
- Go;
- Flutter/Dart;
- Python;
- Maven;
- CMake/C++.

Detection is informational and never modifies the project.

## .gitignore awareness

The scanner loads root and nested `.gitignore` files with directory scope.

Supported baseline behavior includes:

- comments;
- escaped comment markers;
- negation with `!`;
- `*`;
- `**`;
- `?`;
- anchored paths;
- directory patterns;
- nested ignore rules.

Ignored files remain part of the local project copy. The ignore state is used to reduce false alarms before publishing.

## Secret-risk scanner

Nexora Git warns about likely secrets/signing material, including:

- `.env` variants;
- private key markers;
- GitHub token-like values;
- AWS access-key-like values;
- API key/token/password assignments;
- `.pem`;
- `.key`;
- `.jks`;
- `.keystore`;
- `.p12`;
- `.pfx`;
- SSH private-key filenames;
- Google/service-account/credential JSON files.

The scanner never prints discovered secret contents and never deletes user files.

A secret risk already ignored by Git is informational. A non-ignored secret risk is surfaced as a warning before push-oriented workflows.

## Large files

For regular GitHub Git pushes:

- files over 50 MiB are warned about;
- files over 100 MiB are classified as blocking for a normal GitHub push;
- Git LFS support remains in the Advanced Git roadmap.

Ignored large files are informational because Git should not attempt to push them.

## UI

The Repositories surface now provides:

- Open project folder;
- native Android folder picker;
- registered workspace cards;
- direct/managed strategy label;
- file and size summary;
- secret-risk count;
- large-file warning count;
- sync-conflict count;
- manual sync;
- safe workspace removal;
- post-import risk summary.

## Write-back policy

Phase E synchronization is intentionally **source → managed workspace**.

Nexora Git does not automatically write edited managed files back into an arbitrary SAF provider because that could overwrite external changes without a reliable cross-provider conflict protocol.

Explicit export/write-back can be added with editor workflows later, using the same non-destructive conflict rules.
