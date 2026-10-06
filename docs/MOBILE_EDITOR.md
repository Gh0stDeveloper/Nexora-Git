# Mobile Editor

> **Current reference:** This document describes the implemented Nexora Git system. See the [Documentation Hub](README.md) for navigation.

## Purpose

The mobile editor extends the code browser into a real on-device editing workflow.

The editor is designed for developers working directly from Android and keeps all file and Git operations local to the registered workspace.

## Visual language

Nexora Git intentionally follows a **GitHub-familiar** mobile developer-tool language:

- neutral repository surfaces;
- compact bordered controls;
- restrained corner radius;
- GitHub-like light and dark background hierarchy;
- blue link/action emphasis;
- dense file and code layouts;
- compact contextual toolbars.

Nexora Git remains an independent client:

- no GitHub or Octocat application icon;
- no claim that the product is official;
- no pixel-for-pixel reproduction of GitHub Mobile;
- Nexora's source-control symbol and violet app-icon identity remain distinct.

## Editor entry point

Editable text and Markdown files expose an **Edit** action in the code browser.

```text
Repositories
    ↓
Browse code
    ↓
Text / Markdown file
    ↓
Edit
    ↓
editor/{workspaceId}?path={relativePath}
```

Binary files, images and files truncated by the browser safety limit do not expose the editor action.

## Safety boundary

The editor reuses `WorkspacePathPolicy`.

Every file path is:

- workspace-relative;
- canonicalized;
- blocked from escaping via `..`;
- blocked from escaping through symlinks;
- blocked from entering `.git`.

The editor never accepts arbitrary absolute paths from navigation state.

## Mobile file-size bound

The initial editor supports files up to **512 KiB**.

This is intentionally lower than the read-only browser limit because editing and undo/redo keep multiple text revisions in memory.

Larger files remain viewable through the code browser when supported, but must not be edited through this editor.

## Atomic save

`EditorFileStore` uses Android `AtomicFile`.

Before writing, it verifies both the file's `lastModified` value and the complete expected UTF-8 contents captured when the editor loaded or last saved.

If another process changed the file, even if metadata is ambiguous, Nexora Git refuses to overwrite it and asks the user to reload.

The write path:

```text
Editor
  ↓
EditorFileStore
  ↓
WorkspacePathPolicy
  ↓
AtomicFile
  ↓
workspace file
```

## Undo / redo

`EditorHistory` keeps a bounded revision stack.

The initial limit is 40 revisions to prevent unbounded memory growth on mobile.

Each revision preserves:

- text;
- selection start;
- selection end.

## Search and replace

The editor supports:

- literal search;
- case-sensitive or case-insensitive matching;
- next match;
- previous match;
- replace current;
- replace all;
- active-match highlighting.

Search does not modify repository state.

## Indentation

Indentation style is persisted through DataStore.

Supported modes:

- tabs;
- 2 spaces;
- 4 spaces.

The toolbar can insert indentation at the cursor or indent all selected lines.

## Syntax highlighting

The editable field uses an identity-offset `VisualTransformation`.

The initial lexical highlighter covers common:

- keywords;
- strings;
- comments;
- numbers.

Search matches are layered on top without changing text offsets.

Tree-sitter and richer semantic intelligence are integrated through the advanced mobile development layer.

## Line numbers and status

The editor surface includes:

- line numbers;
- monospaced text;
- horizontal scrolling;
- cursor line/column;
- detected language;
- indentation style;
- modified/saved state.

## Diff

There are two diff modes.

### Unsaved changes

When the buffer is dirty, Nexora Git generates a local text diff against the last saved buffer.

### Saved Git changes

When the buffer is saved in a Git workspace, Nexora Git requests a **path-scoped libgit2 diff**.

The GitEngine/JNI/native diff contract now accepts an optional repository-relative path and applies it as a libgit2 pathspec.

This prevents unrelated repository changes from being mixed into the selected file preview.

## Commit flow

The editor commit flow is:

```text
Save current buffer if needed
        ↓
Read Git status
        ↓
Show selected-file Git diff
        ↓
Warn about any OTHER staged files
        ↓
User confirms commit
        ↓
Stage selected path
        ↓
GitEngine.commit()
        ↓
libgit2 commit
```

Nexora Git does not silently unstage other work.

If other files are already staged, the confirmation screen explicitly warns that the Git commit will include them too because a Git commit consumes the current index.

The commit dialog allows the user to review/edit:

- commit message;
- author name;
- author email.

The default author name comes from the active GitHub account. The default email uses that account's GitHub no-reply form and can be edited before committing.

## Offline behavior

Editing, saving, diffing and committing operate entirely against the local workspace and libgit2 repository.

GitHub network access is not required for these actions.

## Testing

Validation covers:

- bounded undo/redo;
- case-sensitive and insensitive search;
- replace-all;
- selected-line indentation;
- local diff generation;
- path-scoped native Git diff;
- browser Edit action;
- editor Compose actions;
- search/replace Compose state.

---

[← Documentation hub](README.md)
