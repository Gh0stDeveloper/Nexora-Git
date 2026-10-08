# S.17 Android distribution assets — delivery checklist

**State: development.** All assets below must represent the real, approved application. No temporary screenshots, unreviewed generated imagery, empty PNGs, stock GitHub branding or false URLs.

## Play metadata already prepared

- [x] Name: **Nexora Git** in en-US and es-MX.
- [x] Localized short and full descriptions under `fastlane/metadata/android/`.
- [ ] Final content approval after full signed release/device qualification.
- [ ] Version-matched release notes prepared and reviewed for each locale.

## Owner-controlled assets

| Item | Required delivery | Condition |
| --- | --- | --- |
| Phone screenshots | 2–8 real PNGs per locale, in `fastlane/metadata/android/{locale}/images/phoneScreenshots/` | app + VPS working; ensure private data is absent |
| High-resolution store icon | `fastlane/metadata/android/{locale}/images/icon.png` (512 × 512) | final brand approved |
| Play feature graphic | `fastlane/metadata/android/{locale}/images/featureGraphic.png` (1024 × 500) | final brand approved, no affiliation confusion |
| Privacy policy | publicly deployed `https://<official-host>/privacy` | must load without login |
| Support/source | `https://github.com/Gh0stDeveloper/Nexora-Git` | verified repo available |
| Official website/homepage | actual TLS production endpoint | [#46](https://github.com/Gh0stDeveloper/Nexora-Git/issues/46) |
| Manual product screenshots | separately tracked as [#47](https://github.com/Gh0stDeveloper/Nexora-Git/issues/47) | use genuine Android app screenshots |

Play icon/feature art is different from the repo SVG source. Keep image source/editable original in the design project and export PNGs meeting Play specs. Only upload artwork once owner-approved; **do not force the user to produce it while app/backend remain under development**.

## Validation

Development gate (runs in CI):

```bash
python3 scripts/ci/validate-s17-distribution.py
```

Publication gate (must fail until actual assets and URLs are supplied):

```bash
python3 scripts/ci/validate-s17-distribution.py --release \
  --privacy-url "https://<official-host>/privacy" \
  --source-url "https://github.com/Gh0stDeveloper/Nexora-Git"
```

The publication gate checks existence, PNG headers/dimensions, bilingual listing constraints and HTTPS origins. Passing it **does not** replace checking imagery quality, privacy/data safety statements or actual Play Console compliance.

## Changelog policy

Create `fastlane/metadata/android/{locale}/changelogs/{versionCode}.txt` only when that build exists and the changes are independently verified. Never advertise unshipped features. Keep GitHub Release notes, store release notes and the VPS download site consistent.
