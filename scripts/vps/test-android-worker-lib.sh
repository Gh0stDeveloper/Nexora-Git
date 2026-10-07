#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
TEMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEMP_ROOT"' EXIT

export NEXORA_ANDROID_STATE_ROOT="$TEMP_ROOT/android"
export NEXORA_ANDROID_QUEUE_ROOT="$NEXORA_ANDROID_STATE_ROOT/queue"
export NEXORA_ANDROID_QUEUE_PENDING="$NEXORA_ANDROID_QUEUE_ROOT/pending"
export NEXORA_ANDROID_QUEUE_RUNNING="$NEXORA_ANDROID_QUEUE_ROOT/running"
export NEXORA_ANDROID_QUEUE_COMPLETED="$NEXORA_ANDROID_QUEUE_ROOT/completed"
export NEXORA_ANDROID_QUEUE_FAILED="$NEXORA_ANDROID_QUEUE_ROOT/failed"
export NEXORA_ANDROID_QUEUE_CANCELLED="$NEXORA_ANDROID_QUEUE_ROOT/cancelled"
export NEXORA_ANDROID_CANCEL_ROOT="$NEXORA_ANDROID_STATE_ROOT/cancel"
export NEXORA_ANDROID_LOCK_ROOT="$NEXORA_ANDROID_STATE_ROOT/locks"
export NEXORA_ANDROID_WORKER_LOCK="$NEXORA_ANDROID_LOCK_ROOT/worker.lock"
export NEXORA_ANDROID_CURRENT_JOB_FILE="$NEXORA_ANDROID_STATE_ROOT/current-job"
export NEXORA_ANDROID_GRADLE_HOME="$TEMP_ROOT/gradle"
export NEXORA_ANDROID_BUILD_USER="$(id -un)"
export NEXORA_ANDROID_SKIP_SYSTEMD=1
export NEXORA_ANDROID_JAVA_HOME="/usr"
export NEXORA_ANDROID_SDK_ROOT="$TEMP_ROOT/sdk"

# shellcheck source=android-worker-lib.sh
source "$SCRIPT_DIR/android-worker-lib.sh"

android_worker_ensure_layout
for dir in pending running completed failed cancelled; do
  [[ -d "$NEXORA_ANDROID_QUEUE_ROOT/$dir" ]]
done

job1="20261007T010000Z-a1b2c3d4"
created="2026-10-07T01:00:00Z"
commit="0123456789abcdef0123456789abcdef01234567"
job1_dir="$(android_worker_job_dir "$job1")"
mkdir -p "$job1_dir/source"
cat > "$job1_dir/source/gradlew" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
chmod +x "$job1_dir/source/gradlew"

ANDROID_JOB_CLIENT_ID="Iv1.testclient"
ANDROID_JOB_BROKER_URL="https://auth.example.com"
ANDROID_JOB_CALLBACK_URL="https://auth.example.com/oauth/callback"
android_worker_write_request "$NEXORA_ANDROID_QUEUE_PENDING/$job1.job" "$job1" "debug" "$commit" "$created"
android_worker_write_state "$job1" "debug" "$commit" "$created" "QUEUED" ""

android_worker_process_one
[[ -f "$NEXORA_ANDROID_QUEUE_COMPLETED/$job1.job" ]]
android_worker_load_state "$job1"
[[ "$STATUS" == "COMPLETED" ]]
[[ "$EXIT_CODE" == "0" ]]

job2="20261007T010001Z-b1c2d3e4"
job2_dir="$(android_worker_job_dir "$job2")"
mkdir -p "$job2_dir/source"
cat > "$job2_dir/source/gradlew" <<'EOF'
#!/usr/bin/env bash
exit 7
EOF
chmod +x "$job2_dir/source/gradlew"
android_worker_write_request "$NEXORA_ANDROID_QUEUE_PENDING/$job2.job" "$job2" "debug" "$commit" "$created"
android_worker_write_state "$job2" "debug" "$commit" "$created" "QUEUED" ""
android_worker_process_one
[[ -f "$NEXORA_ANDROID_QUEUE_FAILED/$job2.job" ]]
android_worker_load_state "$job2"
[[ "$STATUS" == "FAILED" ]]
[[ "$EXIT_CODE" == "7" ]]

job3="20261007T010002Z-c1d2e3f4"
job3_dir="$(android_worker_job_dir "$job3")"
mkdir -p "$job3_dir/source"
cp "$job1_dir/source/gradlew" "$job3_dir/source/gradlew"
android_worker_write_request "$NEXORA_ANDROID_QUEUE_PENDING/$job3.job" "$job3" "debug" "$commit" "$created"
android_worker_write_state "$job3" "debug" "$commit" "$created" "QUEUED" ""
android_worker_cancel "$job3"
[[ -f "$NEXORA_ANDROID_QUEUE_CANCELLED/$job3.job" ]]
android_worker_load_state "$job3"
[[ "$STATUS" == "CANCELLED" ]]

job4="20261007T010003Z-d1e2f3a4"
job4_dir="$(android_worker_job_dir "$job4")"
mkdir -p "$job4_dir/source"
cp "$job1_dir/source/gradlew" "$job4_dir/source/gradlew"
android_worker_write_request "$NEXORA_ANDROID_QUEUE_RUNNING/$job4.job" "$job4" "debug" "$commit" "$created"
android_worker_write_state "$job4" "debug" "$commit" "$created" "RUNNING" ""
android_worker_recover_orphans
[[ -f "$NEXORA_ANDROID_QUEUE_PENDING/$job4.job" ]]
android_worker_load_state "$job4"
[[ "$STATUS" == "QUEUED_RECOVERED" ]]

android_worker_valid_job_id "$job1"
! android_worker_valid_job_id "../../bad"
android_worker_valid_mode release
android_worker_valid_mode debug
! android_worker_valid_mode signed

unit="$SCRIPT_DIR/systemd/nexora-git-android-worker.service"
grep -q '^User=nexora-build$' "$unit"
grep -q '^Restart=always$' "$unit"
grep -q '^KillMode=control-group$' "$unit"
grep -q '^NoNewPrivileges=yes$' "$unit"
grep -q '^ProtectSystem=strict$' "$unit"
if grep -q 'LoadCredential.*signing' "$unit"; then
  printf 'Background build worker must not receive Signing Vault credentials.\n' >&2
  exit 1
fi

printf 'Android background worker tests passed.\n'
