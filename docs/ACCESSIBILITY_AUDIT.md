# Accessibility Audit

## Repository-verifiable controls

Nexora Git uses Material 3 components for primary buttons, icon buttons, navigation items, fields, dialogs and chips. These components retain platform semantics and minimum interactive target behavior.

The current UI also follows these rules:

- icon-only actions use explicit content descriptions;
- decorative icons adjacent to readable labels use `contentDescription = null` to avoid duplicate TalkBack speech;
- text uses scalable Compose typography instead of fixed-pixel text;
- loading, success, failure, branch and workflow states include text rather than relying on color alone;
- edge-to-edge layouts consume system-bar content padding;
- core editor/browser/repository Compose tests assert important labels and action descriptions.

`lintRelease` is a mandatory production gate.

## Manual store-submission checklist

Before uploading a production build, test the signed APK on at least one physical phone with:

1. TalkBack enabled;
2. font size and display size increased;
3. gesture navigation and three-button navigation;
4. light, dark and AMOLED themes;
5. landscape where supported;
6. hardware keyboard focus navigation where applicable.

Verify authentication, repositories, code browser, editor, Git workspace, Issues, Pull Requests, Actions, Releases, Profile and destructive confirmation dialogs.

Manual assistive-technology verification is intentionally a release checklist because it cannot be represented faithfully by a source-only CI test.
