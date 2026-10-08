#!/usr/bin/env bash
set -Eeuo pipefail

# The emulator runner launches individual script lines with /bin/sh.
# Keep the entire screenshot capture sequence in this Bash file.
./gradlew --no-daemon :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.nexora.git.marketing.MarketingScreenshotTest

# Debug applicationId uses the .debug suffix (see app/build.gradle.kts).
screenshot_source="/sdcard/Android/data/com.nexora.git.debug/files/screenshots/."
screenshot_target="docs/assets/screenshots"
rm -rf "$screenshot_target"
mkdir -p "$screenshot_target"
adb pull "$screenshot_source" "$screenshot_target/"

python3 scripts/ci/validate-s16-discoverability.py
