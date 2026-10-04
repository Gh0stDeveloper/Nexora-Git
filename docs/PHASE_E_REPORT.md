# Phase E — Android Project Storage Report

Date: 2026-10-04

## Status

**Implementation complete. Final CI validation is required before merge.**

## Delivered

### SAF folder access

- native `ACTION_OPEN_DOCUMENT_TREE` flow through `ActivityResultContracts.OpenDocumentTree`;
- persistent URI permission handling;
- read/write grant when available;
- persistent read-only fallback;
- permission release when a managed workspace is removed.

Nexora Git does not use broad storage permission or undocumented `_data` path conversion.

### Workspace registry

Room schema v3 extends workspace records with:

- source display metadata;
- source provider authority;
- writable state;
- direct/managed strategy;
- sync state;
- scan/sync timestamps;
- file/byte counts;
- secret warning count;
- large-file warning count;
- sync conflict count.

The v2 → v3 migration preserves existing records and declares schema defaults consistently with Room.

### Direct filesystem strategy

`registerDirectDirectory()` scans and registers a legitimate filesystem directory directly.

Removing a direct workspace deletes only the registry record. The user's directory is never recursively deleted.

### Managed workspace strategy

SAF `content://` trees are mirrored to:

```text
filesDir/workspaces/<workspace-id>/repo
```

libgit2 can therefore work with a real filesystem path even when Android exposes the original project only through a document provider.

### Non-destructive synchronization

The managed sync engine:

- preserves relative paths and file contents;
- preserves source modification times when available;
- copies through a temporary file before replacement;
- validates canonical destination paths;
- rejects traversal-style source names;
- safely removes previously mirrored source files only when the managed copy has not diverged;
- preserves local managed changes when the source has not changed;
- records explicit conflicts when source and managed versions both changed;
- keeps unresolved conflict baselines across later syncs;
- never replaces/deletes an existing managed `.git` directory.

Sync metadata lives outside the repository:

```text
filesDir/workspace-meta/<workspace-id>/sync-manifest.json
```

so Nexora metadata never appears as an untracked Git project file.

### Project scanner

SAF and direct filesystem scanners collect:

- relative paths;
- file sizes;
- modification times;
- Git ignore status;
- project type markers;
- risk information.

Detected project families include:

- Android/Gradle;
- Godot;
- Node.js;
- Rust;
- Go;
- Flutter/Dart;
- Python;
- Maven;
- CMake/C++.

### .gitignore

The scanner supports scoped root/nested ignore files with baseline behavior for:

- comments;
- escaped comments;
- negation;
- wildcards;
- double-star patterns;
- directory rules;
- anchored rules.

Ignored files are still part of the local workspace but do not generate normal push-risk warnings.

### Secret-risk scanner

The scanner detects likely:

- environment files;
- signing keystores;
- PEM/key/P12/PFX files;
- SSH private keys;
- service-account and credential JSON files;
- private-key markers;
- GitHub-token-like values;
- AWS access keys;
- obvious API key/token/password assignments.

Detected secret contents are never logged or stored.

Files inside `.git` are excluded from risk-content scanning.

### Large-file scanner

Current regular GitHub Git behavior is represented as:

- >50 MiB: warning;
- >100 MiB: blocking risk for a normal GitHub push;
- ignored files: informational only.

Git LFS remains scheduled for the Advanced Git phase.

### Repositories UI

The Repositories surface now includes:

- Open project folder;
- workspace cards;
- managed/direct strategy;
- file count and project size;
- risk counts;
- sync-conflict count;
- manual sync;
- safe removal;
- post-import risk review.

### Tests

Phase E includes:

- `.gitignore` matching tests;
- nested ignore scope;
- negation;
- secret warning behavior;
- ignored-secret informational behavior;
- >100 MiB blocking risk;
- real temporary-directory project scan;
- project type detection;
- Compose workspace UI smoke coverage.

## Write-back policy

Phase E intentionally synchronizes **source → managed workspace**.

Automatic managed → SAF write-back is not enabled because document providers do not provide a universal conflict/atomic-write contract. Future editor/export workflows can add explicit write-back without weakening the non-destructive storage boundary.

## Next phase

After final CI and merge, the next roadmap stage is:

**Phase F — Repository Experience**.
