#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
TEMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEMP_ROOT"' EXIT

export NEXORA_ANDROID_CONFIG_FILE="$TEMP_ROOT/android-builder.conf"
export NEXORA_ANDROID_BUILD_USER="$(id -un)"
export NEXORA_SIGNING_OWNER="$(id -un)"
export NEXORA_SIGNING_GROUP="$(id -gn)"
export NEXORA_SIGNING_ROOT="$TEMP_ROOT/vault"
export NEXORA_SIGNING_KEYSTORE="$NEXORA_SIGNING_ROOT/release.p12"
export NEXORA_SIGNING_SECRETS="$NEXORA_SIGNING_ROOT/secrets.env"
export NEXORA_SIGNING_METADATA="$NEXORA_SIGNING_ROOT/identity.conf"
export NEXORA_SIGNING_CERTIFICATE="$NEXORA_SIGNING_ROOT/certificate.pem"
export NEXORA_SIGNING_BACKUP_DIR="$NEXORA_SIGNING_ROOT/backups"

java_bin="$(readlink -f "$(command -v java)")"
java_home="$(dirname "$(dirname "$java_bin")")"
printf 'NEXORA_ANDROID_JAVA_HOME=%q\n' "$java_home" > "$NEXORA_ANDROID_CONFIG_FILE"

# shellcheck source=android-signing-lib.sh
source "$SCRIPT_DIR/android-signing-lib.sh"

android_signing_create_identity
android_signing_verify
android_signing_complete

first_fingerprint="$(android_signing_cert_fingerprint "$NEXORA_SIGNING_CERTIFICATE" sha256)"
first_keystore_hash="$(sha256sum "$NEXORA_SIGNING_KEYSTORE" | awk '{print $1}')"

ensure_android_signing_vault
second_keystore_hash="$(sha256sum "$NEXORA_SIGNING_KEYSTORE" | awk '{print $1}')"
[[ "$first_keystore_hash" == "$second_keystore_hash" ]]

[[ "$(stat -c '%a' "$NEXORA_SIGNING_ROOT")" == "700" ]]
[[ "$(stat -c '%a' "$NEXORA_SIGNING_KEYSTORE")" == "600" ]]
[[ "$(stat -c '%a' "$NEXORA_SIGNING_SECRETS")" == "600" ]]
[[ "$(stat -c '%a' "$NEXORA_SIGNING_METADATA")" == "600" ]]
[[ "$(stat -c '%a' "$NEXORA_SIGNING_CERTIFICATE")" == "644" ]]

backup="$TEMP_ROOT/signing-backup.nxbk"
passphrase="phase-b-ci-backup-passphrase-123456"
android_signing_backup_internal "$backup" "$passphrase" >/dev/null
[[ -s "$backup" ]]
[[ -s "$backup.sha256" ]]

export NEXORA_SIGNING_ROOT="$TEMP_ROOT/restored-vault"
export NEXORA_SIGNING_KEYSTORE="$NEXORA_SIGNING_ROOT/release.p12"
export NEXORA_SIGNING_SECRETS="$NEXORA_SIGNING_ROOT/secrets.env"
export NEXORA_SIGNING_METADATA="$NEXORA_SIGNING_ROOT/identity.conf"
export NEXORA_SIGNING_CERTIFICATE="$NEXORA_SIGNING_ROOT/certificate.pem"
export NEXORA_SIGNING_BACKUP_DIR="$NEXORA_SIGNING_ROOT/backups"

android_signing_restore_internal "$backup" "$passphrase"
android_signing_verify
restored_fingerprint="$(android_signing_cert_fingerprint "$NEXORA_SIGNING_CERTIFICATE" sha256)"
[[ "$first_fingerprint" == "$restored_fingerprint" ]]

# Restoring the same identity is idempotent and must not replace files.
restored_hash_before="$(sha256sum "$NEXORA_SIGNING_KEYSTORE" | awk '{print $1}')"
android_signing_restore_internal "$backup" "$passphrase"
restored_hash_after="$(sha256sum "$NEXORA_SIGNING_KEYSTORE" | awk '{print $1}')"
[[ "$restored_hash_before" == "$restored_hash_after" ]]

printf 'Android Signing Vault tests passed.\n'
