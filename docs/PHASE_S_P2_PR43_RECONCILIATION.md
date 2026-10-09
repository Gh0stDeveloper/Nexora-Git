# PR #43 — Reconciliation against current main

**Date:** 2026-10-08  
**Working PR:** [#43](https://github.com/Gh0stDeveloper/Nexora-Git/pull/43)  
**Original branch preserved:** `archive/phase-s-p2-launch-polish-pr43-original` at `02aa844d879891c35e7ecaa32cee15be97dd2381`

## Reconciliation policy

The original P2 branch diverged before the separately approved S.16/S.17 work. A true two-parent merge reconciled current protected `main` with the original P2 history, preserving its archive while keeping the tested production, native, signing, F-Droid, CI and website configurations from `main`.

The final diff is limited to ten files: Android Settings privacy/source actions, localized strings and existing test-call adaptations; a read-only future `/verify` website page; an HTTPS URL validation utility; an optional live-site validator; and this reconciliation record. Older submodule, Gradle, F-Droid, release/signing and duplicated store or screenshot changes were excluded from the diff, but the historical commits are preserved.

## Development merge qualification — 2026-10-08

The maintainer explicitly requested fusion of PR #43 into `main`. This authorizes **source-level integration only**, not a claim that Phase S, VPS deployment, store distribution or stable release gates are completed.

- [x] Required PR-head checks: [Android CI](https://github.com/Gh0stDeveloper/Nexora-Git/actions/runs/37873380650), [Production CI](https://github.com/Gh0stDeveloper/Nexora-Git/actions/runs/37873380654), [CodeQL](https://github.com/Gh0stDeveloper/Nexora-Git/actions/runs/37873380646), [Web CI](https://github.com/Gh0stDeveloper/Nexora-Git/actions/runs/37873380670), [Foundation CI](https://github.com/Gh0stDeveloper/Nexora-Git/actions/runs/37873380713), and [Dependency Review](https://github.com/Gh0stDeveloper/Nexora-Git/actions/runs/37873380676) completed successfully on head `3b197a691faa3fe91dc46a441322094dc9f40f76`. A later documentation-only commit requires normal protected-branch merge checks.
- [x] Mergeability reviewed: ahead of `main` with no commits behind, GitHub reports mergeable; no requested reviews or unresolved review threads at audit.
- [x] Settings Privacy URL topology **reviewed in code**: `AuthConfig.brokerBaseUrl` must be an HTTPS root origin. The managed VPS Nginx routing in `scripts/vps/web-lib.sh` uses the same domain for the Auth Broker's OAuth paths and the Next.js website's default `location /`. Therefore `brokerBaseUrl + "/privacy"` routes to the website's existing `web/app/privacy/page.tsx` **for the supported managed one-domain topology**. Do not assume this works for a custom split-host deployment.
- [x] Android instrumented-test **qualification recorded**: `SettingsContentTest` and `SettingsAccessibilityTest` were updated to compile with the changed callback API; Android CI's build/lint/unit-test job succeeded. **Instrumented Compose/device tests were not executed**, and no device-level assertion for the new Privacy or Source buttons has been recorded. This is an explicit deferred QA item, not a passing device test.
- [x] External deployment condition reviewed: source code for `/privacy`, `/verify` and HTTPS SEO/config checks is available, but the actual production VPS/site is not live and no domain is fabricated. Source merge must not imply live-site qualification.

## Deferred release/production gates — remain OPEN after merge

- [ ] On a supported device/emulator, execute Settings instrumentation and verify both Privacy/Source actions, broken-network behavior and accessibility. Verify the installed production-signed app when available; track alongside real device acceptance [#47](https://github.com/Gh0stDeveloper/Nexora-Git/issues/47) and S.18.
- [ ] On the real VPS, validate HTTPS `/privacy`, `/verify`, canonical and SEO metadata, headers and the actual app-to-site origin with `scripts/ci/validate-live-site.sh https://OFFICIAL_ORIGIN`. Website/domain prerequisites are [#46](https://github.com/Gh0stDeveloper/Nexora-Git/issues/46). If custom split domains are chosen, implement a separate verified privacy-origin setting before public release.
- [ ] Capture and approve the eight real Android screenshots [#47](https://github.com/Gh0stDeveloper/Nexora-Git/issues/47).
- [ ] Play Console artwork, Data safety, signing and listing approvals [#49](https://github.com/Gh0stDeveloper/Nexora-Git/issues/49).
- [ ] Independent F-Droid Android/Gradle reproducibility [#50](https://github.com/Gh0stDeveloper/Nexora-Git/issues/50).
- [ ] S.18 signed RC publication, real-device qualification and stable-promotion gates.

**Decision:** mergeable for ongoing development with explicitly recorded exceptions. **Public release decision: NO-GO** until those gates are proven. The source merge does not close the above issues or mark Phase S complete.
