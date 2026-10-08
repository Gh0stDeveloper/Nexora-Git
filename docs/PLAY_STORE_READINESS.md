# Play Store Readiness

> **Current reference:** This document describes the implemented Nexora Git system. See the [Documentation Hub](README.md) for navigation.

## Android package

- package: `com.nexora.git`
- versionName: `1.0.0`
- versionCode: `10000`
- min SDK: 26
- target SDK: 36
- release artifact: Android App Bundle
- release signing: external upload/release key through the production workflow

## Store metadata

Reusable listing text is committed under:

- `fastlane/metadata/android/en-US/`
- `fastlane/metadata/android/es-MX/`

## Privacy and Data safety draft

Privacy policy source: `docs/PRIVACY.md`.

Production baseline:

- no advertising SDK;
- no analytics SDK;
- no source-code telemetry;
- authentication information is processed to sign into GitHub;
- repository/user data is sent to GitHub only for requested GitHub operations;
- local repositories and tokens are excluded from Android backup.

The final Play Console Data safety answers must be reviewed against the exact production Auth Broker deployment and any SDK added after this audit.

## S.17 progress and reproducible gates

The tracked implementation adds:

- localized Fastlane listing validation for `en-US` and `es-MX` (strict title/short/full description length and disclosures);
- bilingual metadata release notes policy — only generate notes for an approved signed build;
- future Android store artwork specifications and an explicit fail-closed release asset check;
- a `/privacy` website route in the **source tree** (not yet deployed or publicly verified);
- release signature and APK checksum transparency;
- F-Droid preseeded Git checkout mode, with F-Droid build metadata intentionally disabled until an independent network-free reproducibility audit.

Use `python3 scripts/ci/validate-s17-distribution.py` during development. Only after VPS/website deployment, owner-taken screenshots, finalized icon/graphics and Play Data safety signoff, run `--release` with the actual privacy and source URLs.

Tracked evidence: [Data safety worksheet](store/DATA_SAFETY_REVIEW.md), [store assets](store/ASSET_DELIVERY.md) and [signer identity](store/SIGNING_TRANSPARENCY.md).

**No Play Store, F-Droid, signing key or website publication has been performed by this phase.**

## External publication tasks

The following require the project owner's Play Console/account assets and are therefore not committed as fake placeholders:

- create/verify Play Console application;
- accept current developer agreements;
- publish a public privacy-policy URL;
- upload phone/tablet screenshots and feature graphic;
- complete content rating and Data safety forms;
- configure Play App Signing/upload key;
- upload the signed AAB produced by the release workflow;
- run Play pre-launch reports.

---

[← Documentation hub](README.md)
