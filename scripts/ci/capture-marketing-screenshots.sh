#!/usr/bin/env bash
set -Eeuo pipefail

# The emulator runner launches individual script lines with /bin/sh.
# Keep the entire screenshot capture sequence in this Bash file.
./gradlew --no-daemon :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.nexora.git.marketing.MarketingScreenshotTest

# Android Gradle may uninstall/clear the debug package after instrumentation.
# The instrumentation explicitly exports each real screenshot to this public
# emulator-only staging directory before test teardown.
screenshot_source="/sdcard/Download/NexoraGitMarketing/."
screenshot_target="docs/assets/screenshots"
rm -rf "$screenshot_target"
mkdir -p "$screenshot_target"
adb pull "$screenshot_source" "$screenshot_target/"

python3 scripts/ci/validate-s16-discoverability.py
