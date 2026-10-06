# Phase R Report — Production

## R.1 Security

Repository hardening covers backup, network transport, credential/logging policy, supply-chain pinning, release integrity and static CodeQL analysis.

## R.2 Performance

Runtime-heavy features are bounded and run off the UI thread. R8/resource shrinking and explicit APK/AAB size budgets protect release payload growth.

## R.3 Accessibility

Core Compose controls preserve semantics, scalable text and textual state. Release lint is mandatory. A physical-device TalkBack/font-scaling checklist is documented for the final store submission.

## R.4 Signing

Release signing is externally injected and never committed. The stable workflow verifies APK signatures and publishes checksums.

## R.5 GitHub Release

The repository contains a tag-driven stable release workflow. The first real `v1.0.0` publication remains intentionally gated on production OAuth/broker/signing credentials.

## R.6 Play Store

AAB build, stable versioning, privacy/data-safety guidance and bilingual listing metadata are prepared. Play Console account tasks/screenshots remain external.

## R.7 F-Droid

Metadata is prepared but the build is disabled until CMake native dependencies can be supplied without build-time network access.

## R.8 Validation

Final branch/PR checks are the remaining repository integration gate.
