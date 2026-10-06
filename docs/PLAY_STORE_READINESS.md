# Play Store Readiness

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

Phase R baseline:

- no advertising SDK;
- no analytics SDK;
- no source-code telemetry;
- authentication information is processed to sign into GitHub;
- repository/user data is sent to GitHub only for requested GitHub operations;
- local repositories and tokens are excluded from Android backup.

The final Play Console Data safety answers must be reviewed against the exact production Auth Broker deployment and any SDK added after this audit.

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
