# Code Browser

> **Current reference:** This document describes the implemented Nexora Git system. See the [Documentation Hub](README.md) for navigation.

## Purpose

Nexora Git provides a local, offline-capable code browser on top of registered Android workspaces and the real libgit2 engine.

Browsing remains non-destructive by default; supported text and Markdown files can be handed off to the integrated mobile editor.

## Entry point

Every local workspace in the Repositories screen exposes **Browse code**.

```text
Repositories
    ↓
Local workspace
    ↓
Browse code
    ↓
code/{workspaceId}
```

The bottom navigation bar is hidden while the code browser is open because it is a secondary workspace surface rather than a top-level destination.

## Workspace confinement

The code browser never accepts an arbitrary absolute path from navigation state.

`WorkspacePathPolicy`:

- resolves all requested paths relative to the registered workspace root;
- canonicalizes root and candidate paths;
- rejects parent traversal;
- rejects symlinks that resolve outside the workspace;
- rejects direct access to `.git`;
- exposes normalized relative paths only after verifying containment.

This boundary applies to directory browsing, file reads, share and export operations.

## Directory browser

Directories are read from the local workspace filesystem.

Behavior:

- folders sort before files;
- names are sorted case-insensitively;
- `.git` is hidden;
- symlinks that escape the workspace are excluded;
- breadcrumbs allow direct navigation to parent segments;
- empty directories have an explicit state.

## File classification

`CodeBrowserFileSystem` classifies files as:

- text;
- Markdown;
- image;
- binary.

Binary detection examines a bounded prefix and rejects NUL-heavy/control-character content from the text renderer.

Text reads are limited to the first **2 MiB** for mobile safety. The UI explicitly shows when a preview is truncated.

Recognized language labels include:

- Kotlin / Java;
- JavaScript / TypeScript;
- Python;
- Rust;
- Go;
- C / C++;
- GDScript;
- XML / HTML;
- JSON / YAML / TOML;
- Gradle;
- Shell;
- CSS;
- SQL;
- Dockerfile / Makefile.

## Syntax highlighting

The browser retains a lightweight Compose-native fallback tokenizer for common:

- keywords;
- strings;
- numbers;
- comments.

It does not claim semantic parsing.

Tree-sitter and richer language intelligence are provided by the advanced mobile development layer.

## Markdown preview

The mobile Markdown preview supports a safe local baseline:

- headings;
- paragraphs;
- bullets;
- block quotes;
- fenced code blocks.

It does not execute embedded HTML or scripts.

## Image preview

Images are decoded locally with a sampled bitmap strategy.

The preview calculates image bounds first and increases the decode sample size until the target is within a bounded mobile dimension. This reduces memory pressure from very large source images.

## History

File history uses libgit2 through the existing JNI boundary:

```text
CodeBrowserViewModel
        ↓
GitEngine.history()
        ↓
Libgit2GitEngine
        ↓
NativeGitBridge
        ↓
libgit2 revwalk + tree diff
```

For a selected path, commits are emitted only when the path differs from at least one relevant parent tree.

History is capped by the Kotlin/native contract to a maximum of 200 entries per request. The UI requests 100.

Current-path history does not attempt rename-following. Rich rename-aware navigation can be added later without weakening the current read-only contract.

## Blame

Blame uses `git_blame_file` from libgit2.

The native response includes:

- final commit OID;
- original commit OID;
- final line start/count;
- original line/path;
- author;
- timestamp;
- boundary flag.

The UI maps hunks back onto the visible file lines and shows short OID + author + line content.

Blame is available only for text/Markdown files in a real Git workspace.

Untracked files or files without committed blame information show an unavailable/empty state rather than crashing the browser.

## Share

Share never exposes an arbitrary workspace root through FileProvider.

The selected file is copied to:

```text
cache/code-share/
```

and FileProvider exposes only that cache path through:

```text
<cache-path path="code-share/" />
```

The generated URI receives temporary read permission through Android's share intent.

## Save copy / download

The user chooses the destination through Android's `ACTION_CREATE_DOCUMENT` flow.

Nexora Git copies the selected workspace file into the returned content URI. No broad storage permission is required.

## Offline behavior

Directory browsing, file viewing, Markdown/image preview, history and blame work entirely from the local workspace and Git repository.

No GitHub API connection is required after the repository/workspace is present on device.

## Tests

Validation covers:

- canonical safe nested paths;
- parent traversal rejection;
- `.git` rejection;
- symlink escape rejection when supported by the host;
- native file history;
- native blame;
- history/blame JSON parsing;
- workspace Browse code action;
- code-browser Compose directory state;
- code/history/blame tabs.

---

[← Documentation hub](README.md)
