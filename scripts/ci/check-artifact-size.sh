#!/usr/bin/env bash
set -euo pipefail

max_aab_bytes=$((200 * 1024 * 1024))
max_apk_bytes=$((250 * 1024 * 1024))

aab="$(find app/build/outputs/bundle/release -maxdepth 1 -type f -name '*.aab' -print -quit 2>/dev/null || true)"
apk="$(find app/build/outputs/apk/release -maxdepth 1 -type f -name '*.apk' -print -quit 2>/dev/null || true)"

test -n "$aab" || {
  echo "Release AAB not found."
  exit 1
}

test -n "$apk" || {
  echo "Release APK not found."
  exit 1
}

aab_bytes="$(stat -c '%s' "$aab")"
apk_bytes="$(stat -c '%s' "$apk")"

echo "AAB: $aab_bytes bytes"
echo "APK: $apk_bytes bytes"

if (( aab_bytes > max_aab_bytes )); then
  echo "Release AAB exceeds the 200 MiB production budget."
  exit 1
fi

if (( apk_bytes > max_apk_bytes )); then
  echo "Release APK exceeds the 250 MiB production budget."
  exit 1
fi

echo "Artifact size budgets passed."
