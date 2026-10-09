#!/usr/bin/env bash
set -Eeuo pipefail

# Official release signing must match the public VPS Signing Vault fingerprint.
: "${NEXORA_SIGNING_STORE_FILE:?Missing NEXORA_SIGNING_STORE_FILE}"
: "${NEXORA_SIGNING_STORE_PASSWORD:?Missing NEXORA_SIGNING_STORE_PASSWORD}"
: "${NEXORA_SIGNING_KEY_ALIAS:?Missing NEXORA_SIGNING_KEY_ALIAS}"
: "${NEXORA_EXPECTED_SIGNER_SHA256:?Missing trusted VPS signer fingerprint}"

actual="$(
  LC_ALL=C keytool -list -v \
    -alias "$NEXORA_SIGNING_KEY_ALIAS" \
    -keystore "$NEXORA_SIGNING_STORE_FILE" \
    -storepass "$NEXORA_SIGNING_STORE_PASSWORD" 2>/dev/null |
    awk -F': ' '/SHA256:/{print $2; exit}' |
    tr -d ':' | tr '[:lower:]' '[:upper:]'
)"
expected="$(printf '%s' "$NEXORA_EXPECTED_SIGNER_SHA256" |
  tr -d ':[:space:]' | tr '[:lower:]' '[:upper:]')"

if [[ ! "$actual" =~ ^[A-F0-9]{64}$ || ! "$expected" =~ ^[A-F0-9]{64}$ ]]; then
  printf 'Invalid/missing signing certificate SHA-256 identity.\n' >&2
  exit 1
fi
if [[ "$actual" != "$expected" ]]; then
  printf 'Trusted VPS signing certificate does not match the CI keystore; refuse public release.\n' >&2
  exit 1
fi

printf 'Signing keystore matches pinned VPS certificate SHA-256: %s\n' "$actual"
