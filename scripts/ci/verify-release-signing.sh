#!/usr/bin/env bash

set -Eeuo pipefail

: "${NEXORA_SIGNING_STORE_FILE:?Missing NEXORA_SIGNING_STORE_FILE}"
: "${NEXORA_SIGNING_STORE_PASSWORD:?Missing NEXORA_SIGNING_STORE_PASSWORD}"
: "${NEXORA_SIGNING_KEY_ALIAS:?Missing NEXORA_SIGNING_KEY_ALIAS}"
: "${NEXORA_AUTH_BROKER_BASE_URL:?Missing NEXORA_AUTH_BROKER_BASE_URL}"
: "${NEXORA_GITHUB_CALLBACK_URL:?Missing NEXORA_GITHUB_CALLBACK_URL}"

[[ "$NEXORA_AUTH_BROKER_BASE_URL" =~ ^https://[^/]+$ ]] || {
  printf 'Auth Broker base URL must be HTTPS without a trailing path/slash.\n' >&2
  exit 1
}
[[ "$NEXORA_GITHUB_CALLBACK_URL" == "$NEXORA_AUTH_BROKER_BASE_URL/oauth/callback" ]] || {
  printf 'GitHub callback must equal AUTH_BROKER_BASE_URL/oauth/callback.\n' >&2
  exit 1
}

apk="$(find app/build/outputs/apk/release -maxdepth 1 -type f -name '*.apk' -print -quit)"
aab="$(find app/build/outputs/bundle/release -maxdepth 1 -type f -name '*.aab' -print -quit)"
[[ -s "$apk" && -s "$aab" ]] || {
  printf 'Signed release APK/AAB not found.\n' >&2
  exit 1
}

sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/usr/local/lib/android/sdk}}"
apksigner="$sdk_root/build-tools/37.0.0/apksigner"
[[ -x "$apksigner" ]] || {
  printf 'apksigner 37.0.0 not found.\n' >&2
  exit 1
}

expected="$(
  keytool -list -v \
    -alias "$NEXORA_SIGNING_KEY_ALIAS" \
    -keystore "$NEXORA_SIGNING_STORE_FILE" \
    -storepass "$NEXORA_SIGNING_STORE_PASSWORD" 2>/dev/null |
    awk -F': ' '/SHA256:/{print toupper($2); exit}' |
    tr -d ':'
)"
"$apksigner" verify --verbose --print-certs "$apk" >/dev/null
apk_fp="$(
  LC_ALL=C keytool -printcert -jarfile "$apk" 2>/dev/null |
    awk -F': ' '/SHA256:/{print toupper($2); exit}' |
    tr -d ':'
)"
aab_fp="$(
  keytool -printcert -jarfile "$aab" 2>/dev/null |
    awk -F': ' '/SHA256:/{print toupper($2); exit}' |
    tr -d ':'
)"

[[ -n "$expected" && "$apk_fp" == "$expected" && "$aab_fp" == "$expected" ]] || {
  printf 'Release signing fingerprint parity failed.\n' >&2
  exit 1
}

jarsigner -verify -strict "$aab" >/dev/null 2>&1
printf 'Release signing and broker/callback parity verified.\n'
