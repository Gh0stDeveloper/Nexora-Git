# PR #43 — Reconciliation against current main

**Date:** 2026-10-08  
**Working PR:** [#43](https://github.com/Gh0stDeveloper/Nexora-Git/pull/43)  
**Original branch preserved:** `archive/phase-s-p2-launch-polish-pr43-original` at `02aa844d879891c35e7ecaa32cee15be97dd2381`

## Merge policy

The historical P2 launch-polish branch began before the now-merged
S.16/S.17 pull requests. It included old workflows, native Git submodules,
website files, public release gates and draft Play Store metadata. Blindly
merging all changes would replace tested production, security, signing and
F-Droid behavior with older implementations.

Reconcile using a **true two-parent merge commit** with current `main` as
the winning base for existing overlapping files. Preserve original PR history
through the first merge parent and keep the read-only archive branch.

## Intentionally retained for review on PR #43

- Android Settings privacy/source navigation and English/Mexican-Spanish
  strings, plus associated Compose instrumentation test updates.
- Future read-only `/verify` page displaying the APK checksum and signer
  certificate already supported by the current website release type.
- A strict HTTPS site URL utility to be wired into the website during S.15.
- An optional operator-side live website validator (not invoked by CI until
  the real production origin exists).

These are **draft changes**, not launch-ready acceptance evidence. In
particular, Settings must not assume the HTTPS Auth Broker and the product
website share an origin without confirming the operator's deployment.

## Superseded / excluded from the reconciled PR diff

- Old `.gitmodules`, nine Git submodule entries, native CMake overrides
  and the older F-Droid workflow: S.17 now has an independently qualified
  pinned source-lock/preseed design and an offline native CI, with Android
  `fdroidserver` reproducibility still outstanding (#50).
- Old release workflow, dependency-review, Production/Web CI modifications,
  signing, VPS and privacy implementation: the current protected `main`
  owns the authoritative configurations; do not silently roll them back.
- Outdated GitHub repository settings and screenshot gates: S.16 already
  defers the official website (#46) and owner-captured images (#47).
- Duplicate store-readiness, Data Safety, artwork and changelog proposals:
  the currently versioned S.17 validator and review checklist are canonical.
  No Play submission or first signed RC is complete (#49).
- Original release notes and other code that assumes signed RC assets or
  production websites exist: not accepted until the real release gate.

## Conditions before PR #43 may merge

- [ ] All required Android, Production, CodeQL, Web, Foundation and
      Dependency Review checks on the reconciled branch pass.
- [ ] Run or explicitly qualify the Settings instrumented tests on a
      supported Android device/emulator.
- [ ] Review Settings Privacy URL origin against the real VPS topology.
- [ ] Complete and review website HTTPS/canonical/SEO functionality when
      the product website is ready; do not require a fake domain.
- [ ] Keep S.16 screenshots and S.17 Play/F-Droid/RC release exceptions open.

**Status:** reconciliation only. Keep PR #43 as a draft until its remaining
development and deployment-specific requirements are independently verified.
