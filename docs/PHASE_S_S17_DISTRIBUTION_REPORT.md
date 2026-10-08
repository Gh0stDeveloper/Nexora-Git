# Phase S — S.17 Store/ASO and Distribution Evidence

**Implementation branch:** `phase-s/p2-s17-distribution`  
**Public release status:** **NO-GO** until remaining owner-controlled tasks are approved.

## Scope and actual deliverables

### S.17.1 · Play Store

- Bilingual Fastlane metadata exists for English and Spanish.
- New `scripts/ci/validate-s17-distribution.py` validates listing lengths and required legal/source disclaimers.
- Strict `--release` mode rejects missing real store graphics/screenshots and absent public HTTPS privacy URL.
- Product website source contains `/privacy` and discovery links, **not yet deployed**.
- Data safety checklist and asset delivery specification exist; **no declaration has been submitted**.

#### Follow-up: original Play Store artwork candidates (PR #51)

- Editable purple/white Git branch vectors sourced from Nexora Git's existing brand.
- High-resolution icon 512 × 512 and localized feature graphics 1024 × 500, in `en-US` and `es-MX`.
- GitHub Actions renderer and PNG integrity validation run on an isolated artwork branch, requiring the maintainer's approval before merging.
- Real Android phone screenshots remain **deferred to issue #47** until the self-hosted app and VPS are working.
- These are not claims that Play Console listing, official certificate or storefront are live.

### S.17.2 · Signing identity

- Signed-release workflow is wired to generate `SIGNING-CERTIFICATE-SHA256.txt` and `SIGNING-IDENTITY.json`.
- Pipeline refuses fingerprint mismatch between APK and AAB, then includes the evidence in release checksums.
- VPS website publisher validates `SIGNING_CERT_SHA256` from the signed release manifest and adds `signingCertificateSha256` to public `latest.json`.
- Website displays signer fingerprint alongside APK SHA-256 with a Play App Signing key distinction.
- VPS test fixture now checks the fingerprint is actually present.

**External gate remains:** publish a real signed RC; verify certificate against the authoritative VPS Signing Vault; confirm Google Play upload key vs Play app-signing certificate separately.

### S.17.3 · F-Droid

- Opt-in `NEXORA_NATIVE_SOURCE_ROOT` CMake mode accepts precisely nine pinned native upstream checkouts (revision-checked); sets offline FetchContent mode; requires libgit2 patch preapplied.
- Existing online developer/native CI mode is untouched by default.
- F-Droid metadata remains explicitly disabled and retains `NonFreeNet` disclosure.

**Verified follow-up:** The prefetch source manifest is pinned, Mbed TLS framework's gitlink is checked, and native host code built and passed CTest in a network-isolated namespace ([run #37806822320](https://github.com/Gh0stDeveloper/Nexora-Git/actions/runs/37806822320)). Public provenance artifact retained by CI.

**External/engineering gate remains:** fdroidserver-compatible Gradle/Maven acquisition, Android APK build inside an isolated network namespace and two-build reproducibility verification are **not yet demonstrated**. F-Droid metadata remains disabled; no Play/F-Droid release was published.

## Release exclusions and owner decisions

- [ ] Production VPS and HTTPS site online: issue #46.
- [ ] Owner captures Android screenshots from the working app: issue #47.
- [ ] Owner approves final Play high-res icon, 1024x500 feature graphic and screenshots.
- [ ] Data safety/permissions/privacy reviewed against real signed release and Auth Broker logs.
- [ ] Real signed RC and verified certificate/Play App Signing entry.
- [ ] F-Droid reproducibility validated and metadata build entry re-enabled (optional, separately gated).

## Reproducible checks

```bash
python3 scripts/ci/validate-s17-distribution.py
bash scripts/vps/test-web-lib.sh
bash scripts/ci/validate-production.sh
```

Release-only check intentionally fails during development:

```bash
python3 scripts/ci/validate-s17-distribution.py --release \
  --privacy-url https://OFFICIAL-SITE/privacy \
  --source-url https://github.com/Gh0stDeveloper/Nexora-Git
```

No private Signing Vault key or token is placed in the source tree. This report must be revised with exact CI run IDs and manual QA once the phase is qualified and merged.
