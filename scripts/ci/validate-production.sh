#!/usr/bin/env bash
set -euo pipefail

manifest="app/src/main/AndroidManifest.xml"
gradle_file="app/build.gradle.kts"
network_config="app/src/main/res/xml/network_security_config.xml"

test -s "$manifest"
test -s "$gradle_file"
test -s "$network_config"
test -s "SECURITY.md"

grep -q 'android:allowBackup="false"' "$manifest"
grep -q 'android:usesCleartextTraffic="false"' "$manifest"
grep -q 'android:networkSecurityConfig="@xml/network_security_config"' "$manifest"
grep -q 'cleartextTrafficPermitted="false"' "$network_config"
grep -q 'android:autoVerify="true"' "$manifest"
grep -q 'android:scheme="https"' "$manifest"
grep -q 'android:path="/oauth/android/callback"' "$manifest"
if grep -q 'android:scheme="nexoragit"' "$manifest"; then
  echo "Production OAuth callback must not use an unverified custom URI scheme."
  exit 1
fi

if grep -q 'android:debuggable="true"' "$manifest"; then
  echo "Production manifest must not be debuggable."
  exit 1
fi

grep -q 'versionName = "1.0.0"' "$gradle_file"
grep -q 'versionCode = 10000' "$gradle_file"
grep -q 'isMinifyEnabled = true' "$gradle_file"
grep -q 'isShrinkResources = true' "$gradle_file"

if grep -RInE   --include='*.yml' --include='*.yaml'   'uses:[[:space:]]+[^[:space:]]+@(v[0-9]+|main|master)([[:space:]]|$)'   .github/workflows; then
  echo "GitHub Actions must be pinned to immutable commit SHAs."
  exit 1
fi

if grep -RInE \
  --exclude-dir=.git \
  --exclude='*.md' \
  --exclude='*.lock' \
  '(-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----|gh[pousr]_[A-Za-z0-9_]{20,}|NEXORA_SIGNING_(STORE_PASSWORD|KEY_PASSWORD|KEYSTORE_BASE64)[[:space:]]*=[[:space:]]*["'\'' ]?[A-Za-z0-9+/=_-]{16,})' \
  app auth-broker .github scripts; then
  echo "Potential production secret committed to the repository."
  exit 1
fi

echo "Production policy validation passed."
