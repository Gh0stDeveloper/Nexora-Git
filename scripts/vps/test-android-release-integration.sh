#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
TEMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEMP_ROOT"' EXIT

export INSTALL_DIR="$REPO_ROOT"
export NEXORA_ANDROID_STATE_ROOT="$TEMP_ROOT/android"
export NEXORA_ANDROID_ARTIFACT_ROOT="$NEXORA_ANDROID_STATE_ROOT/artifacts"
export NEXORA_ANDROID_LOG_ROOT="$NEXORA_ANDROID_STATE_ROOT/logs"
export NEXORA_ANDROID_CONFIG_FILE="$TEMP_ROOT/android-builder.conf"
export NEXORA_SIGNING_ROOT="$TEMP_ROOT/signing"
export NEXORA_SIGNING_KEYSTORE="$NEXORA_SIGNING_ROOT/release.p12"
export NEXORA_SIGNING_SECRETS="$NEXORA_SIGNING_ROOT/secrets.env"
export NEXORA_SIGNING_METADATA="$NEXORA_SIGNING_ROOT/identity.conf"
export NEXORA_SIGNING_CERTIFICATE="$NEXORA_SIGNING_ROOT/certificate.pem"
export NEXORA_SIGNING_BACKUP_DIR="$NEXORA_SIGNING_ROOT/backups"
export NEXORA_WEB_ROOT="$TEMP_ROOT/web"
export NEXORA_WEB_RELEASE_ROOT="$NEXORA_WEB_ROOT/releases"
NEXORA_ANDROID_BUILD_USER="$(id -un)"
NEXORA_SIGNING_OWNER="$(id -un)"
NEXORA_SIGNING_GROUP="$(id -gn)"
export NEXORA_ANDROID_BUILD_USER NEXORA_SIGNING_OWNER NEXORA_SIGNING_GROUP

sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/usr/local/lib/android/sdk}}"
java_home="${JAVA_HOME:-}"
[[ -x "$sdk_root/build-tools/37.0.0/apksigner" ]] || {
  printf 'apksigner 37.0.0 is missing.\n' >&2
  exit 1
}
[[ -x "$java_home/bin/jarsigner" ]] || {
  printf 'JDK jarsigner is missing.\n' >&2
  exit 1
}

cat > "$NEXORA_ANDROID_CONFIG_FILE" <<EOF
NEXORA_ANDROID_STATE_ROOT=$NEXORA_ANDROID_STATE_ROOT
NEXORA_ANDROID_SDK_ROOT=$sdk_root
NEXORA_ANDROID_BUILD_USER=$NEXORA_ANDROID_BUILD_USER
NEXORA_ANDROID_JAVA_HOME=$java_home
NEXORA_ANDROID_BUILD_TOOLS=37.0.0
EOF

# shellcheck source=android-release-lib.sh
source "$SCRIPT_DIR/android-release-lib.sh"

android_signing_create_identity

job="20261007T030000Z-a1b2c3d4"
commit="$(git -C "$REPO_ROOT" rev-parse HEAD)"
created="2026-10-07T03:00:00Z"
job_dir="$NEXORA_ANDROID_STATE_ROOT/builds/$job"
mkdir -p "$job_dir/source/app"
cp "$REPO_ROOT/app/build.gradle.kts" "$job_dir/source/app/build.gradle.kts"
cat > "$job_dir/job.conf" <<EOF
JOB_ID=$job
MODE=release
COMMIT=$commit
CREATED_AT=$created
STATUS=COMPLETED
EOF

log_file="$(android_artifacts_init_log "$job")"
printf 'production signing integration\n' > "$log_file"

android_artifacts_stage "$job" release "$commit" "$created" "$REPO_ROOT"
android_release_sign "$job"
android_release_verify_signed "$job"

signed_dir="$(android_release_signed_dir "$job")"
[[ -s "$signed_dir/$job-signed.apk" ]]
[[ -s "$signed_dir/$job-signed.aab" ]]
[[ -L "$NEXORA_WEB_RELEASE_ROOT/current" ]]
[[ -s "$NEXORA_WEB_RELEASE_ROOT/current/Nexora-Git.apk" ]]
grep -q '"versionName": "1.0.0"' "$NEXORA_WEB_RELEASE_ROOT/current/latest.json"

printf 'Android release signing integration test passed.\n'
