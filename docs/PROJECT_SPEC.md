# Nexora Git — Product Specification

## 1. Objective

Nexora Git is an open-source Android application that combines a feature-rich GitHub client with a real Git workspace.

Its primary target is developers who program from Android devices and need to manage complete repositories without depending on a desktop computer, Termux or an external Git installation.

## 2. Product principles

1. Native Android application.
2. Mobile-first interaction model.
3. Real Git operations, not API simulations.
4. Official GitHub authentication flows.
5. Least-privilege access.
6. Secure local credential handling.
7. Offline Git where technically possible.
8. No mandatory terminal or external application.
9. Modular architecture.
10. Public source code and auditable security-sensitive components.
11. Original branding and interface.
12. Progressive support for GitHub features according to documented public APIs.

## 3. Primary capabilities

### GitHub account

- Sign in with GitHub.
- Profile.
- Multiple accounts.
- Organizations.
- Followers and following.
- Stars and subscriptions.
- Notifications.

### Repositories

- List public/private repositories available to the account.
- Create repositories.
- Clone repositories.
- Import a complete local folder.
- Repository metadata.
- Files and directories.
- Fork.
- Star / unstar.
- Watch / unwatch.
- Archive where permission allows.
- Releases.
- Tags.

### Local Git

- init
- clone
- status
- stage / unstage
- commit
- log
- diff
- branch
- checkout / switch
- fetch
- pull
- push
- merge
- conflict detection and resolution
- remotes

Advanced phases add:

- rebase
- cherry-pick
- stash
- revert
- reset
- submodules
- Git LFS

### Collaboration

- Issues.
- Pull requests.
- Reviews.
- Inline review comments.
- Merge.
- Discussions where API support and permissions allow.
- GitHub Projects.

### Automation

- GitHub Actions workflows.
- Workflow runs.
- Jobs and steps.
- Logs.
- Re-run / cancel where permitted.
- Artifacts.
- Releases.

### Mobile development experience

- Project browser.
- File viewer.
- Markdown renderer.
- Code editor.
- Syntax highlighting.
- Search and replace.
- Diff viewer.
- Project detection.
- Git status.
- Secret-risk warnings before publishing.
- Large-file warnings.
- Offline workspace.

## 4. Technology baseline

### Android

- Kotlin
- Jetpack Compose
- Material 3
- Coroutines / Flow
- Hilt
- Room
- DataStore
- WorkManager
- Paging 3
- Coil

### Networking

- OkHttp
- Retrofit
- Kotlin serialization

### GitHub platform

- GitHub App
- OAuth web flow
- PKCE
- REST API
- GraphQL API

### Local Git

- libgit2
- Android NDK
- C/C++
- JNI wrapper
- Kotlin GitEngine abstraction

### Android storage

- Storage Access Framework
- ACTION_OPEN_DOCUMENT_TREE
- Persistable URI permissions
- App-managed Git workspaces when direct POSIX access is not available

## 5. Authentication rule

Nexora Git must never implement a screen that captures the user's GitHub password or passkey.

Users may authenticate on GitHub using any method GitHub supports, including password, 2FA, SSO or passkeys. Those credentials remain exclusively between the user and GitHub.

The application uses GitHub App user authorization through OAuth with PKCE.

See `AUTH.md`.

## 6. Git rule

Remote GitHub APIs do not replace a Git client.

All local repository operations must go through the local Git engine. REST and GraphQL are used for GitHub platform resources such as issues, pull requests, Actions, profiles and repository administration.

See `GIT_ENGINE.md`.

## 7. MVP acceptance criteria

The MVP is complete only when a user can:

1. Authenticate with GitHub through the official GitHub authorization page.
2. List accessible repositories.
3. Create a repository.
4. Clone a repository to an Android workspace.
5. Select an existing Android project folder.
6. Initialize/import that project into Git.
7. Inspect changed and untracked files.
8. Stage and unstage files.
9. Commit.
10. Create and switch branches.
11. Fetch.
12. Pull.
13. Push.
14. Browse repository files.
15. Edit text files.
16. Create/view issues.
17. View/create pull requests.
18. View notifications.
19. Search.
20. Continue local Git work offline.

A button or mock screen without a working underlying operation is not considered implemented.

## 8. Completion standard

Every production feature must include, where applicable:

- UI.
- Domain logic.
- Real API or Git implementation.
- Loading state.
- Empty state.
- Error state.
- Permission handling.
- Offline behavior.
- Tests.
- Accessibility.
- Documentation.

## 9. Distribution goals

Initial:

- GitHub Releases with signed APK.

Later:

- Google Play.
- F-Droid, if project/dependency policy is compatible.

## 10. Branding

Nexora Git is an independent project integrating with GitHub.

The application must not present itself as an official GitHub application, and GitHub/Octocat branding must not be used as Nexora Git's own product identity.
