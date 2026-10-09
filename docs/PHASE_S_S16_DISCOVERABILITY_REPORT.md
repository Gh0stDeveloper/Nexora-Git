# Phase S — S.16 GitHub Discoverability & Community

> **Branch:** `phase-s/p2-s16-discoverability`  
> **Scope:** S.16 only. This branch intentionally excludes S.15 web hardening and S.17/F-Droid work so unrelated native/distribution failures cannot mask repository-presentation quality.

## Implemented repository surface

- Launch-quality README positioning and verified-build CTA.
- Real Android UI screenshot pipeline using production Compose surfaces.
- Versioned screenshot paths for Home, Repository Detail, Editor, Git workbench, Pull Requests, Actions, and Light/Dark/AMOLED variants.
- Structured bug-report and feature-request forms.
- Public support policy.
- Contribution guidance aligned to Issues, Discussions and private security reporting.
- Pull-request visual-evidence checklist.
- Repository launch-settings policy and read-back validator.
- Idempotent `gh` administration script for homepage, topics, Discussions and merged-branch deletion.
- CI gate for community/readme/screenshot assets.

## Repository metadata policy

Required launch values are defined in [GITHUB_REPOSITORY_SETTINGS.md](GITHUB_REPOSITORY_SETTINGS.md).

The connected GitHub integration can verify repository metadata but does not expose Administration mutations. External repository settings therefore remain authoritative only after GitHub API read-back succeeds. Tracking issue: [#44](https://github.com/Gh0stDeveloper/Nexora-Git/issues/44).

## Visual evidence

Screenshots are not hand-authored mockups. `.github/workflows/marketing-screenshots.yml` runs `MarketingScreenshotTest` on a Pixel 6 emulator, captures the real Compose tree, and versions the resulting PNG files under `docs/assets/screenshots/`.

The screenshot commit is intentionally isolated from the capture trigger to avoid self-triggering loops.

## Acceptance evidence

S.16 repository-contained work is accepted only when:

1. `Community Readiness` passes.
2. `Marketing Screenshots` passes and the eight required PNG files are versioned.
3. Android/CodeQL validation for the S.16 code changes is clean.
4. The external GitHub metadata validator passes after issue #44 settings are applied.

No account-level control is considered complete based only on documentation.
