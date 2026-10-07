#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
TEMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEMP_ROOT"' EXIT

export NEXORA_ANDROID_STATE_ROOT="$TEMP_ROOT/android"
export NEXORA_ANDROID_ARTIFACT_ROOT="$NEXORA_ANDROID_STATE_ROOT/artifacts"
export NEXORA_ANDROID_LOG_ROOT="$NEXORA_ANDROID_STATE_ROOT/logs"
NEXORA_ANDROID_BUILD_USER="$(id -un)"
export NEXORA_ANDROID_BUILD_USER
export NEXORA_ANDROID_RETENTION_KEEP=1
export NEXORA_ANDROID_RETENTION_DAYS=1

# shellcheck source=android-artifacts-lib.sh
source "$SCRIPT_DIR/android-artifacts-lib.sh"

job1="20261007T020000Z-a1b2c3d4"
commit1="0123456789abcdef0123456789abcdef01234567"
created="2026-10-07T02:00:00Z"
source1="$TEMP_ROOT/source1"
mkdir -p "$source1/app/build/outputs/apk/release"
mkdir -p "$source1/app/build/outputs/bundle/release"
mkdir -p "$source1/app/build/outputs/native-debug-symbols/release"
printf 'apk-data' > "$source1/app/build/outputs/apk/release/app-release-unsigned.apk"
printf 'aab-data' > "$source1/app/build/outputs/bundle/release/app-release.aab"
printf 'symbols' > "$source1/app/build/outputs/native-debug-symbols/release/native-debug-symbols.zip"

log1="$(android_artifacts_init_log "$job1")"
printf 'test build log\n' > "$log1"
android_artifacts_stage "$job1" release "$commit1" "$created" "$source1"
android_artifacts_verify "$job1"

artifact1="$(android_artifacts_job_dir "$job1")"
[[ -s "$artifact1/NexoraGit-${commit1:0:12}-release-unsigned.apk" ]]
[[ -s "$artifact1/NexoraGit-${commit1:0:12}-release-unsigned.aab" ]]
[[ -s "$artifact1/NexoraGit-${commit1:0:12}-release-native-symbols.zip" ]]
grep -q '"signing_state": "UNSIGNED_RELEASE"' "$artifact1/manifest.json"
grep -q '"kind": "apk"' "$artifact1/manifest.json"
grep -q '"kind": "aab"' "$artifact1/manifest.json"

cp "$artifact1/NexoraGit-${commit1:0:12}-release-unsigned.apk" "$TEMP_ROOT/original.apk"
printf 'tamper' >> "$artifact1/NexoraGit-${commit1:0:12}-release-unsigned.apk"
if (android_artifacts_verify "$job1") >/dev/null 2>&1; then
  printf 'Checksum verification accepted a modified artifact.\n' >&2
  exit 1
fi
cp "$TEMP_ROOT/original.apk" "$artifact1/NexoraGit-${commit1:0:12}-release-unsigned.apk"
android_artifacts_verify "$job1"

job2="20261007T020001Z-b1c2d3e4"
commit2="abcdef0123456789abcdef0123456789abcdef01"
source2="$TEMP_ROOT/source2"
mkdir -p "$source2/app/build/outputs/apk/debug"
printf 'debug-apk' > "$source2/app/build/outputs/apk/debug/app-debug.apk"
android_artifacts_init_log "$job2" >/dev/null
android_artifacts_stage "$job2" debug "$commit2" "$created" "$source2"
artifact2="$(android_artifacts_job_dir "$job2")"
grep -q '"signing_state": "DEBUG_DEFAULT"' "$artifact2/manifest.json"
android_artifacts_verify "$job2"

mkdir -p "$NEXORA_ANDROID_STATE_ROOT/builds/$job1/source" "$NEXORA_ANDROID_STATE_ROOT/builds/$job2/source"
cat > "$NEXORA_ANDROID_STATE_ROOT/builds/$job1/job.conf" <<EOF
STATUS=COMPLETED
EOF
cat > "$NEXORA_ANDROID_STATE_ROOT/builds/$job2/job.conf" <<EOF
STATUS=COMPLETED
EOF
touch -d '10 days ago' "$NEXORA_ANDROID_STATE_ROOT/builds/$job1/job.conf"
touch -d '10 days ago' "$NEXORA_ANDROID_STATE_ROOT/builds/$job2/job.conf"

candidate="$(android_artifacts_retention_candidates 1 1)"
[[ "$candidate" == "$job1" ]]
android_artifacts_apply_retention 0 1 1 >/dev/null
[[ ! -d "$NEXORA_ANDROID_STATE_ROOT/builds/$job1/source" ]]
[[ ! -d "$NEXORA_ANDROID_ARTIFACT_ROOT/$job1" ]]
[[ ! -e "$NEXORA_ANDROID_LOG_ROOT/$job1.log" ]]
[[ -s "$NEXORA_ANDROID_STATE_ROOT/builds/$job1/job.conf" ]]
[[ -d "$NEXORA_ANDROID_ARTIFACT_ROOT/$job2" ]]

printf 'Android artifact lifecycle tests passed.\n'
