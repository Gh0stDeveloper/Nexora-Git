#!/usr/bin/env bash
set -Eeuo pipefail

# Baseline recorded from the last green pre-P1 Production CI run.
baseline_aab_bytes=21164692
baseline_apk_bytes=42983454

target_aab_bytes=$((28 * 1024 * 1024))
target_apk_bytes=$((52 * 1024 * 1024))
hard_aab_bytes=$((36 * 1024 * 1024))
hard_apk_bytes=$((64 * 1024 * 1024))
max_growth_percent=30

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

percent_growth() {
  local current="$1"
  local baseline="$2"
  if (( current <= baseline )); then
    echo 0
  else
    echo $(( (current - baseline) * 100 / baseline ))
  fi
}

aab_growth="$(percent_growth "$aab_bytes" "$baseline_aab_bytes")"
apk_growth="$(percent_growth "$apk_bytes" "$baseline_apk_bytes")"

printf 'AAB: %s bytes (baseline %s, growth %s%%)\n' "$aab_bytes" "$baseline_aab_bytes" "$aab_growth"
printf 'APK: %s bytes (baseline %s, growth %s%%)\n' "$apk_bytes" "$baseline_apk_bytes" "$apk_growth"

if (( aab_bytes > hard_aab_bytes )); then
  echo "Release AAB exceeds the 36 MiB hard limit."
  exit 1
fi
if (( apk_bytes > hard_apk_bytes )); then
  echo "Release APK exceeds the 64 MiB hard limit."
  exit 1
fi
if (( aab_growth > max_growth_percent )); then
  echo "Release AAB grew more than 30% from the audited baseline."
  exit 1
fi
if (( apk_growth > max_growth_percent )); then
  echo "Release APK grew more than 30% from the audited baseline."
  exit 1
fi

if (( aab_bytes > target_aab_bytes )); then
  echo "::warning::Release AAB is above the 28 MiB target budget."
fi
if (( apk_bytes > target_apk_bytes )); then
  echo "::warning::Release APK is above the 52 MiB target budget."
fi

echo "Artifact target/hard budgets and growth guard passed."
