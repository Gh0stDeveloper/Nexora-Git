# Android Storage and Project Import

## Goal

Nexora Git must allow the user to select an existing project folder on Android and treat it as a complete project.

The application should use the Android Storage Access Framework instead of requesting unrestricted filesystem access.

## Folder selection

Primary flow:

```text
Add project
    ↓
Select folder
    ↓
ACTION_OPEN_DOCUMENT_TREE
    ↓
Persist URI permission
    ↓
Scan project
    ↓
Create/open Git workspace
```

## SAF versus POSIX filesystem

A critical Android constraint is that Storage Access Framework directories are commonly exposed through `content://` URIs rather than normal POSIX paths.

libgit2 expects filesystem-oriented repository access.

Therefore Nexora Git must support an abstraction that does not assume every user-selected directory can be passed directly to libgit2.

## Workspace strategies

### Direct workspace

Use direct filesystem access only when the selected location is safely available as a compatible filesystem path.

### Managed workspace

When direct access is not possible:

```text
User folder (SAF)
      ↕
Workspace synchronizer
      ↕
App-managed filesystem workspace
      ↕
libgit2
```

The synchronization layer must preserve:

- relative paths
- directories
- file contents
- timestamps when useful
- deletes
- rename/move detection where practical

It must never silently overwrite newer data.

## Import project flow

Before first commit/push:

1. Scan files.
2. Detect existing `.git`.
3. Detect project type.
4. Evaluate `.gitignore`.
5. Detect potentially sensitive files.
6. Detect large files.
7. Show a summary.
8. Let the user review.
9. Initialize/attach repository.
10. Stage.
11. Commit.
12. Configure remote.
13. Push.

## Project detection examples

Detect common markers such as:

- `AndroidManifest.xml`
- `build.gradle`
- `settings.gradle`
- `gradle.properties`
- `package.json`
- `Cargo.toml`
- `go.mod`
- `pom.xml`
- `pubspec.yaml`
- `CMakeLists.txt`
- `project.godot`
- `README.md`
- `.gitignore`

Project detection is informational and must not alter files automatically.

## Secret-risk scanner

Before publishing/importing a project, warn about potentially sensitive files such as:

- `.env`
- `*.jks`
- `*.keystore`
- private SSH keys
- `*.pem`
- `*.p12`
- `*.pfx`
- credentials files
- service account files
- obvious token/key files

The scanner must warn, not silently delete user data.

## Local workspace records

Suggested model:

```text
WorkspaceEntity
├── id
├── name
├── sourceTreeUri
├── managedWorkspacePath
├── repositoryRemote
├── currentBranch
├── accountId
├── syncState
└── lastOpenedAt
```

URI access must be validated when reopening a project because Android/user actions can revoke previously granted access.
