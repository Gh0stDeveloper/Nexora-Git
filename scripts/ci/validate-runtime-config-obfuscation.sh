#!/usr/bin/env bash
set -Eeuo pipefail

: "${NEXORA_GITHUB_CLIENT_ID:?Missing NEXORA_GITHUB_CLIENT_ID sentinel}"
: "${NEXORA_AUTH_BROKER_BASE_URL:?Missing NEXORA_AUTH_BROKER_BASE_URL sentinel}"
: "${NEXORA_GITHUB_CALLBACK_URL:?Missing NEXORA_GITHUB_CALLBACK_URL sentinel}"

apk="$(find app/build/outputs/apk/debug -maxdepth 1 -type f -name '*.apk' -print -quit)"
[[ -s "$apk" ]] || {
  printf 'Debug APK not found.\n' >&2
  exit 1
}

if grep -q 'buildConfigField("String", "GITHUB_CLIENT_ID"' app/build.gradle.kts ||
   grep -q 'BuildConfig.GITHUB_CLIENT_ID' app/src/main/java/com/nexora/git/core/auth/AuthConfig.kt; then
  printf 'Runtime OAuth configuration is still exposed through BuildConfig.\n' >&2
  exit 1
fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
unzip -q "$apk" -d "$tmp/apk"

mapfile -t runtime_libs < <(find "$tmp/apk/lib" -type f -name 'libnexoragit.so' | sort)
if (( ${#runtime_libs[@]} != 3 )); then
  printf 'Expected native runtime library for exactly 3 Android ABIs; found %s.\n' "${#runtime_libs[@]}" >&2
  exit 1
fi

sentinels=(
  "$NEXORA_GITHUB_CLIENT_ID"
  "$NEXORA_AUTH_BROKER_BASE_URL"
  "$NEXORA_GITHUB_CALLBACK_URL"
  "$NEXORA_AUTH_BROKER_BASE_URL/oauth/android/callback"
)

for file in "$tmp"/apk/classes*.dex "${runtime_libs[@]}"; do
  [[ -f "$file" ]] || continue
  for value in "${sentinels[@]}"; do
    if strings "$file" | grep -Fqx "$value"; then
      printf 'Plain runtime configuration leaked into %s: %s\n' "$file" "$value" >&2
      exit 1
    fi
  done
done

for so in "${runtime_libs[@]}"; do
  if readelf -Ws "$so" | grep -q 'Java_com_nexora_git_core_auth_SecureRuntimeConfigNative'; then
    printf 'Native runtime library exposes name-based JNI symbols instead of RegisterNatives.\n' >&2
    exit 1
  fi
  readelf -Ws "$so" | grep -q 'JNI_OnLoad' || {
    printf 'Native runtime library is missing JNI_OnLoad registration entrypoint.\n' >&2
    exit 1
  }
done

printf 'Runtime configuration obfuscation validation passed.\n'
