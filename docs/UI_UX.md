# UI / UX Direction

> **Current reference:** This document describes the implemented Nexora Git system. See the [Documentation Hub](README.md) for navigation.

## Product identity

Nexora Git must remain clearly identifiable as an independent product while intentionally using a GitHub-familiar developer-tool visual language.

The interface should feel immediately familiar to GitHub users through information density, neutral surfaces, bordered controls, repository hierarchy, compact toolbars and code-centric navigation. It must not use official GitHub logos, the Octocat as the application identity, or claim to be an official GitHub product.

## Design system

Recommended foundation:

- Jetpack Compose
- Material 3 primitives
- GitHub-familiar neutral surface hierarchy with custom Nexora design tokens
- edge-to-edge implemented correctly with system insets
- light, dark and AMOLED themes
- compact 4–12 dp corner-radius scale
- bordered repository/editor surfaces
- vector icons instead of emoji controls
- adaptive layouts

## Navigation

Phone bottom navigation:

```text
Home | Explore | Repositories | Activity | Profile
```

A contextual create/import action provides:

- New repository
- Import project
- Clone repository
- New issue
- New gist when implemented

## Home

Prioritize actionable development state:

- Continue coding.
- Modified local workspaces.
- Review requests.
- Pull requests.
- Issues.
- Notifications.
- Recent repositories.
- Failed/running Actions when relevant.

## Repository screen

Primary mobile tabs:

```text
Code | Issues | Pulls | Actions | More
```

Less frequent actions should be moved into contextual menus or bottom sheets rather than filling the screen with controls.

## Code and files

The file experience should include:

- breadcrumbs
- file tree/list
- branch selector
- changed-file state
- syntax-aware viewer
- Markdown
- images
- history
- blame
- share/copy link where applicable

## Git changes screen

```text
Changes

Staged
 ├── file A
 └── file B

Modified
 ├── file C
 └── file D

Untracked
 └── file E
```

Each file should expose diff, stage/unstage and contextual operations.

## Branches

Provide a dedicated branch manager:

- current branch
- local/remote branches
- ahead/behind state
- create
- switch
- rename
- delete
- merge

## Conflict resolution

Mobile conflict UI should present:

```text
LOCAL | RESULT | REMOTE
```

with actions:

- use local
- use remote
- use both
- edit result
- mark resolved

## Editor

Initial editor requirements:

- line numbers
- syntax highlighting
- search
- replace
- undo/redo
- configurable indentation
- horizontal scrolling when required
- diff before commit
- safe handling of large/binary files

Tree-sitter-based language intelligence is integrated; LSP support remains provider-neutral and only activates when a trusted provider is explicitly available.

## Responsive layouts

### Phones

- bottom navigation
- single-pane screens
- bottom sheets
- compact headers

### Tablets / foldables

- navigation rail
- list-detail layouts
- file tree + editor
- pull request files + diff/review panel

## System bars

The application must correctly respect status/navigation bar insets. Content and interactive controls must never unintentionally render underneath system controls.

## Accessibility

Every production screen should support:

- TalkBack semantics
- readable contrast
- scalable text
- sufficiently large touch targets
- keyboard navigation where applicable
- state indication that does not rely on color alone
- reduced motion preference where reasonable

---

[← Documentation hub](README.md)
