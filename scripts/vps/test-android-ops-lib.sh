#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
TEMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEMP_ROOT"' EXIT

export NEXORA_OPS_TEST_MODE=1
export NEXORA_OPS_SKIP_SYSTEMD=1
export NEXORA_OPS_ROOT="$TEMP_ROOT/ops"
export NEXORA_OPS_LOCK_ROOT="$NEXORA_OPS_ROOT/locks"
export NEXORA_UPDATE_LOCK="$NEXORA_OPS_LOCK_ROOT/update.lock"
export NEXORA_CHECKPOINT_ROOT="$NEXORA_OPS_ROOT/checkpoints"
export NEXORA_CHECKPOINT_KEEP=10
export NEXORA_AUTOBUILD_CONFIG="$TEMP_ROOT/etc/android-autobuild.conf"
export NEXORA_AUTOBUILD_PENDING="$NEXORA_OPS_ROOT/autobuild.pending"

export NEXORA_ANDROID_STATE_ROOT="$TEMP_ROOT/android"
export NEXORA_ANDROID_QUEUE_ROOT="$NEXORA_ANDROID_STATE_ROOT/queue"
export NEXORA_ANDROID_QUEUE_PENDING="$NEXORA_ANDROID_QUEUE_ROOT/pending"
export NEXORA_ANDROID_QUEUE_RUNNING="$NEXORA_ANDROID_QUEUE_ROOT/running"
export NEXORA_ANDROID_QUEUE_COMPLETED="$NEXORA_ANDROID_QUEUE_ROOT/completed"
export NEXORA_ANDROID_QUEUE_FAILED="$NEXORA_ANDROID_QUEUE_ROOT/failed"
export NEXORA_ANDROID_QUEUE_CANCELLED="$NEXORA_ANDROID_QUEUE_ROOT/cancelled"
export NEXORA_ANDROID_CANCEL_ROOT="$NEXORA_ANDROID_STATE_ROOT/cancel"
export NEXORA_ANDROID_LOCK_ROOT="$NEXORA_ANDROID_STATE_ROOT/locks"
export NEXORA_ANDROID_CURRENT_JOB_FILE="$NEXORA_ANDROID_STATE_ROOT/current-job"
export NEXORA_ANDROID_RESTART_MARKER="$NEXORA_ANDROID_STATE_ROOT/restart-worker.pending"
NEXORA_ANDROID_BUILD_USER="$(id -un)"
export NEXORA_ANDROID_BUILD_USER
export NEXORA_ANDROID_SKIP_SYSTEMD=1
export NEXORA_ANDROID_JAVA_HOME="/usr"
export NEXORA_ANDROID_SDK_ROOT="$TEMP_ROOT/sdk"
export NEXORA_ANDROID_GRADLE_HOME="$TEMP_ROOT/gradle"
export NEXORA_ANDROID_ARTIFACT_ROOT="$NEXORA_ANDROID_STATE_ROOT/artifacts"
export NEXORA_ANDROID_LOG_ROOT="$NEXORA_ANDROID_STATE_ROOT/logs"

export INSTALL_DIR="$TEMP_ROOT/repo"
export DOMAIN="auth.example.com"
export LOCAL_PORT="18080"
export EMAIL="ops@example.com"
export REPO_URL="https://github.com/Gh0stDeveloper/Nexora-Git.git"
export BRANCH="main"

export NEXORA_STATE_FILE="$TEMP_ROOT/etc/nexora-git-vps.conf"
export NEXORA_ANDROID_CONFIG_FILE="$TEMP_ROOT/etc/android-builder.conf"
export NEXORA_NGINX_CONFIG="$TEMP_ROOT/etc/nginx.conf"
export NEXORA_WORKER_UNIT_DEST="$TEMP_ROOT/etc/worker.service"
export NEXORA_AUTOBUILD_SERVICE_DEST="$TEMP_ROOT/etc/autobuild.service"
export NEXORA_AUTOBUILD_TIMER_DEST="$TEMP_ROOT/etc/autobuild.timer"
export NEXORA_GITHUB_SYNC_FILE="$TEMP_ROOT/signing/github-sync.conf"
export NEXORA_SIGNING_BACKUP_DIR="$TEMP_ROOT/signing/backups"

mkdir -p "$INSTALL_DIR/auth-broker" "$TEMP_ROOT/etc" "$(dirname "$NEXORA_GITHUB_SYNC_FILE")" "$NEXORA_SIGNING_BACKUP_DIR"
printf 'GITHUB_APP_CLIENT_ID=Iv1.test\nGITHUB_APP_CLIENT_SECRET=secret\n' > "$INSTALL_DIR/auth-broker/.env"
printf 'deployment-original\n' > "$NEXORA_STATE_FILE"
cat > "$NEXORA_ANDROID_CONFIG_FILE" <<EOF
NEXORA_ANDROID_STATE_ROOT=$NEXORA_ANDROID_STATE_ROOT
NEXORA_ANDROID_SDK_ROOT=$NEXORA_ANDROID_SDK_ROOT
NEXORA_ANDROID_GRADLE_HOME=$NEXORA_ANDROID_GRADLE_HOME
NEXORA_ANDROID_BUILD_USER=$NEXORA_ANDROID_BUILD_USER
NEXORA_ANDROID_JAVA_HOME=$NEXORA_ANDROID_JAVA_HOME
NEXORA_ANDROID_BUILD_TOOLS=37.0.0
EOF
printf 'nginx-original\n' > "$NEXORA_NGINX_CONFIG"
printf 'worker-original\n' > "$NEXORA_WORKER_UNIT_DEST"
printf 'sync-original\n' > "$NEXORA_GITHUB_SYNC_FILE"

# shellcheck source=android-worker-lib.sh
source "$SCRIPT_DIR/android-worker-lib.sh"
# shellcheck source=android-ops-lib.sh
source "$SCRIPT_DIR/android-ops-lib.sh"

android_worker_ensure_layout
android_ops_setup
android_ops_doctor

[[ "$(stat -c '%a' "$NEXORA_OPS_ROOT")" == "700" ]]
[[ "$(stat -c '%a' "$NEXORA_AUTOBUILD_CONFIG")" == "644" ]]
android_ops_load_autobuild_config
[[ "$NEXORA_AUTOBUILD_ENABLED" == "1" ]]
[[ "$NEXORA_AUTOBUILD_MODE" == "release" ]]
[[ "$NEXORA_AUTOBUILD_DEBOUNCE_SECONDS" == "30" ]]

commit1="0123456789abcdef0123456789abcdef01234567"
commit2="89abcdef0123456789abcdef0123456789abcdef"
android_ops_autobuild_schedule "$commit1"
android_ops_load_pending_autobuild
[[ "$AUTOBUILD_PENDING_COMMIT" == "$commit1" ]]
android_ops_autobuild_schedule "$commit2"
android_ops_load_pending_autobuild
[[ "$AUTOBUILD_PENDING_COMMIT" == "$commit2" ]]

job1="20261007T040000Z-a1b2c3d4"
created="2026-10-07T04:00:00Z"
ANDROID_JOB_CLIENT_ID="Iv1.test"
ANDROID_JOB_BROKER_URL="https://auth.example.com"
ANDROID_JOB_CALLBACK_URL="https://auth.example.com/oauth/callback"
mkdir -p "$(android_worker_job_dir "$job1")"
android_worker_write_request "$NEXORA_ANDROID_QUEUE_PENDING/$job1.job" "$job1" release "$commit1" "$created"
android_worker_write_state "$job1" release "$commit1" "$created" QUEUED ""
android_ops_mark_autobuild "$job1" "$commit1" release
android_ops_cancel_superseded_pending release "$commit2"
[[ -f "$NEXORA_ANDROID_QUEUE_CANCELLED/$job1.job" ]]
android_worker_load_state "$job1"
[[ "$STATUS" == "CANCELLED" ]]

job2="20261007T040001Z-b1c2d3e4"
mkdir -p "$(android_worker_job_dir "$job2")"
android_worker_write_state "$job2" release "$commit2" "$created" COMPLETED "0"
[[ "$(android_ops_find_existing_job release "$commit2")" == "$job2" ]]

checkpoint="$(android_ops_checkpoint_create "test-checkpoint" "$commit1")"
android_ops_checkpoint_verify "$checkpoint"
printf 'deployment-mutated\n' > "$NEXORA_STATE_FILE"
android_ops_checkpoint_restore_files "$checkpoint"
grep -q '^deployment-original$' "$NEXORA_STATE_FILE"

printf 'tamper\n' >> "$NEXORA_CHECKPOINT_ROOT/$checkpoint/deployment.conf"
if android_ops_checkpoint_verify "$checkpoint"; then
  printf 'Tampered operational checkpoint was accepted.\n' >&2
  exit 1
fi

android_ops_autobuild_disable
android_ops_load_autobuild_config
[[ "$NEXORA_AUTOBUILD_ENABLED" == "0" ]]
[[ ! -e "$NEXORA_AUTOBUILD_PENDING" ]]
android_ops_autobuild_enable debug 45
android_ops_load_autobuild_config
[[ "$NEXORA_AUTOBUILD_ENABLED" == "1" ]]
[[ "$NEXORA_AUTOBUILD_MODE" == "debug" ]]
[[ "$NEXORA_AUTOBUILD_DEBOUNCE_SECONDS" == "45" ]]

service="$SCRIPT_DIR/systemd/nexora-git-android-autobuild.service"
timer="$SCRIPT_DIR/systemd/nexora-git-android-autobuild.timer"
grep -q '^User=root$' "$service"
grep -q '^NoNewPrivileges=yes$' "$service"
grep -q '^ProtectSystem=strict$' "$service"
grep -q '^OnUnitActiveSec=30s$' "$timer"
grep -q '^Persistent=true$' "$timer"

printf 'Android operations/autobuild/recovery tests passed.\n'
