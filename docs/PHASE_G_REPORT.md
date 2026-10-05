# Phase G — Code Browser Report

Date: 2026-10-04

## Status

**Implementation complete and functional code CI validated: Android CI, Native Git CI and Foundation CI are green.**

## Delivered

### Directories

- workspace-rooted navigation;
- breadcrumbs;
- folders-first sorting;
- hidden Git internals;
- explicit empty state;
- canonical path confinement.

### File viewer

- text viewer;
- binary detection;
- 2 MiB text preview bound;
- language labeling;
- file size/line metadata;
- refresh behavior.

### Syntax highlighting

- lightweight Compose-native highlighting;
- keywords;
- strings;
- numbers;
- comments;
- multiple common development languages.

Tree-sitter remains intentionally scheduled for Phase Q.

### Markdown

- headings;
- paragraphs;
- bullets;
- quotes;
- fenced code blocks;
- no HTML/script execution.

### Images

- local image preview;
- bounds-first decoding;
- sampled bitmap loading;
- bounded mobile preview dimension.

### Git history

- libgit2 revwalk;
- path-filtered tree diffs;
- unrelated commits excluded;
- OID/short OID;
- commit summary/message;
- author;
- commit timestamp;
- parent count;
- request safety cap.

### Git blame

- libgit2 `git_blame_file`;
- line ranges;
- commit identity;
- original path/line;
- author;
- timestamp;
- boundary state;
- line-oriented Compose presentation.

### Share and save copy

- constrained FileProvider cache;
- no direct workspace-root exposure;
- temporary Android URI grants;
- Storage Access Framework destination export.

### Security

- no arbitrary absolute-path navigation;
- traversal rejection;
- symlink escape rejection;
- `.git` browsing/sharing blocked;
- no broad storage permission;
- browser remains read-only.

### Tests

- native real-repository history/blame tests;
- Git JSON parser tests;
- path-confinement JVM tests;
- Compose browser tests;
- existing repositories UI updated for Browse code.

## Next phase

After final CI and merge:

**Phase H — Mobile Editor**.
