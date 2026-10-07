#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
TEMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEMP_ROOT"' EXIT

export INSTALL_DIR="$TEMP_ROOT/repo"
export DOMAIN="auth.example.com"
export LOCAL_PORT="18080"
export REPO_URL="https://github.com/Gh0stDeveloper/Nexora-Git.git"
export NEXORA_GITHUB_EXPORT_ROOT="$TEMP_ROOT/exports"
export NEXORA_GITHUB_SYNC_FILE="$TEMP_ROOT/signing/github-sync.conf"
export NEXORA_ANDROID_CONFIG_FILE="$TEMP_ROOT/android-builder.conf"
export NEXORA_SIGNING_ROOT="$TEMP_ROOT/signing"
export NEXORA_SIGNING_KEYSTORE="$NEXORA_SIGNING_ROOT/release.p12"
export NEXORA_SIGNING_SECRETS="$NEXORA_SIGNING_ROOT/secrets.env"
export NEXORA_SIGNING_METADATA="$NEXORA_SIGNING_ROOT/identity.conf"
export NEXORA_SIGNING_CERTIFICATE="$NEXORA_SIGNING_ROOT/certificate.pem"
export NEXORA_SIGNING_BACKUP_DIR="$NEXORA_SIGNING_ROOT/backups"
NEXORA_SIGNING_OWNER="$(id -un)"
NEXORA_SIGNING_GROUP="$(id -gn)"
export NEXORA_SIGNING_OWNER NEXORA_SIGNING_GROUP

mkdir -p "$INSTALL_DIR/auth-broker" "$INSTALL_DIR/.github/workflows"
cp "$SCRIPT_DIR/../../.github/workflows/release.yml" "$INSTALL_DIR/.github/workflows/release.yml"

java_bin="$(readlink -f "$(command -v java)")"
java_home="$(dirname "$(dirname "$java_bin")")"
printf 'NEXORA_ANDROID_JAVA_HOME=%q\n' "$java_home" > "$NEXORA_ANDROID_CONFIG_FILE"

# shellcheck source=android-release-lib.sh
source "$SCRIPT_DIR/android-release-lib.sh"

android_release_write_broker_config "Iv1.testclient" "test-client-secret-not-exported"
android_release_read_broker_config
[[ "$NEXORA_RELEASE_CLIENT_ID" == "Iv1.testclient" ]]
[[ "$NEXORA_RELEASE_CALLBACK_URL" == "https://auth.example.com/oauth/callback" ]]
[[ "$(android_release_repo_slug)" == "Gh0stDeveloper/Nexora-Git" ]]

android_signing_create_identity

export_dir="$TEMP_ROOT/exported"
android_release_export_secrets "$export_dir"
[[ "$(stat -c '%a' "$export_dir")" == "700" ]]
for name in $(android_release_secret_names); do
  [[ -s "$export_dir/$name" ]]
  [[ "$(stat -c '%a' "$export_dir/$name")" == "600" ]]
done
[[ ! -e "$export_dir/GITHUB_APP_CLIENT_SECRET" ]]
if grep -Rqs 'test-client-secret-not-exported' "$export_dir"; then
  printf 'GitHub App Client Secret leaked into the Actions secret bundle.\n' >&2
  exit 1
fi

android_release_record_sync "Gh0stDeveloper/Nexora-Git"
android_release_check_sync

android_release_write_broker_config "Iv1.changedclient" "test-client-secret-not-exported"
if android_release_check_sync; then
  printf 'Parity record remained valid after changing Client ID.\n' >&2
  exit 1
fi

printf 'Android GitHub parity/export tests passed.\n'
