#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
if ! declare -F log_info >/dev/null 2>&1; then
  # shellcheck source=lib.sh
  source "$SCRIPT_DIR/lib.sh"
fi
if ! declare -F load_android_config >/dev/null 2>&1; then
  # shellcheck source=android-build-lib.sh
  source "$SCRIPT_DIR/android-build-lib.sh"
fi
if ! declare -F android_artifacts_stage >/dev/null 2>&1; then
  # shellcheck source=android-artifacts-lib.sh
  source "$SCRIPT_DIR/android-artifacts-lib.sh"
fi

NEXORA_ANDROID_QUEUE_ROOT="${NEXORA_ANDROID_QUEUE_ROOT:-$NEXORA_ANDROID_STATE_ROOT/queue}"
NEXORA_ANDROID_QUEUE_PENDING="${NEXORA_ANDROID_QUEUE_PENDING:-$NEXORA_ANDROID_QUEUE_ROOT/pending}"
NEXORA_ANDROID_QUEUE_RUNNING="${NEXORA_ANDROID_QUEUE_RUNNING:-$NEXORA_ANDROID_QUEUE_ROOT/running}"
NEXORA_ANDROID_QUEUE_COMPLETED="${NEXORA_ANDROID_QUEUE_COMPLETED:-$NEXORA_ANDROID_QUEUE_ROOT/completed}"
NEXORA_ANDROID_QUEUE_FAILED="${NEXORA_ANDROID_QUEUE_FAILED:-$NEXORA_ANDROID_QUEUE_ROOT/failed}"
NEXORA_ANDROID_QUEUE_CANCELLED="${NEXORA_ANDROID_QUEUE_CANCELLED:-$NEXORA_ANDROID_QUEUE_ROOT/cancelled}"
NEXORA_ANDROID_CANCEL_ROOT="${NEXORA_ANDROID_CANCEL_ROOT:-$NEXORA_ANDROID_STATE_ROOT/cancel}"
NEXORA_ANDROID_LOCK_ROOT="${NEXORA_ANDROID_LOCK_ROOT:-$NEXORA_ANDROID_STATE_ROOT/locks}"
NEXORA_ANDROID_WORKER_LOCK="${NEXORA_ANDROID_WORKER_LOCK:-$NEXORA_ANDROID_LOCK_ROOT/worker.lock}"
NEXORA_ANDROID_CURRENT_JOB_FILE="${NEXORA_ANDROID_CURRENT_JOB_FILE:-$NEXORA_ANDROID_STATE_ROOT/current-job}"
NEXORA_ANDROID_WORKER_SERVICE="${NEXORA_ANDROID_WORKER_SERVICE:-nexora-git-android-worker.service}"

ANDROID_JOB_ID=""
ANDROID_JOB_MODE=""
ANDROID_JOB_COMMIT=""
ANDROID_JOB_CREATED_AT=""
ANDROID_JOB_CLIENT_ID=""
ANDROID_JOB_BROKER_URL=""
ANDROID_JOB_CALLBACK_URL=""

android_worker_valid_job_id() {
  [[ "$1" =~ ^[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}$ ]]
}

android_worker_valid_mode() {
  [[ "$1" == "release" || "$1" == "debug" ]]
}

android_worker_make_job_id() {
  printf '%s-%s\n' "$(date -u +%Y%m%dT%H%M%SZ)" "$(openssl rand -hex 4)"
}

android_worker_mkdir() {
  local path="$1"
  local mode="$2"
  local owner="$3"
  local group="$4"

  if [[ "${EUID:-$(id -u)}" -eq 0 ]]; then
    install -d -o "$owner" -g "$group" -m "$mode" "$path"
  else
    mkdir -p "$path"
    chmod "$mode" "$path"
  fi
}

android_worker_ensure_layout() {
  load_android_config

  local owner="$NEXORA_ANDROID_BUILD_USER"
  local group
  if id "$owner" >/dev/null 2>&1; then
    group="$(id -gn "$owner")"
  else
    owner="$(id -un)"
    group="$(id -gn)"
  fi

  android_worker_mkdir "$NEXORA_ANDROID_QUEUE_ROOT" 0750 "$owner" "$group"
  android_worker_mkdir "$NEXORA_ANDROID_QUEUE_PENDING" 0750 "$owner" "$group"
  android_worker_mkdir "$NEXORA_ANDROID_QUEUE_RUNNING" 0750 "$owner" "$group"
  android_worker_mkdir "$NEXORA_ANDROID_QUEUE_COMPLETED" 0750 "$owner" "$group"
  android_worker_mkdir "$NEXORA_ANDROID_QUEUE_FAILED" 0750 "$owner" "$group"
  android_worker_mkdir "$NEXORA_ANDROID_QUEUE_CANCELLED" 0750 "$owner" "$group"
  android_worker_mkdir "$NEXORA_ANDROID_CANCEL_ROOT" 0750 "$owner" "$group"
  android_worker_mkdir "$NEXORA_ANDROID_LOCK_ROOT" 0750 "$owner" "$group"
  android_worker_mkdir "$NEXORA_ANDROID_STATE_ROOT/builds" 0750 "$owner" "$group"
  android_artifacts_ensure_layout
}

android_worker_job_dir() {
  local job_id="$1"
  android_worker_valid_job_id "$job_id" || return 1
  printf '%s/builds/%s\n' "$NEXORA_ANDROID_STATE_ROOT" "$job_id"
}

android_worker_state_file() {
  local job_id="$1"
  printf '%s/job.conf\n' "$(android_worker_job_dir "$job_id")"
}

android_worker_write_state() {
  local job_id="$1"
  local mode="$2"
  local commit="$3"
  local created_at="$4"
  local status="$5"
  local exit_code="${6:-}"

  android_worker_valid_job_id "$job_id" || die "Invalid Android build job ID."
  android_worker_valid_mode "$mode" || die "Invalid Android build mode."
  [[ "$commit" =~ ^[a-f0-9]{40}$ ]] || die "Invalid Android build commit SHA."
  [[ "$created_at" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]] ||
    die "Invalid Android build creation timestamp."
  [[ "$status" =~ ^[A-Z_]+$ ]] || die "Invalid Android build status."
  [[ -z "$exit_code" || "$exit_code" =~ ^[0-9]+$ ]] || die "Invalid Android build exit code."

  local job_dir state_file temp
  job_dir="$(android_worker_job_dir "$job_id")"
  state_file="$job_dir/job.conf"
  mkdir -p "$job_dir"
  temp="$(mktemp "$job_dir/.job.conf.XXXXXX")"

  {
    printf 'JOB_ID=%s\n' "$job_id"
    printf 'MODE=%s\n' "$mode"
    printf 'COMMIT=%s\n' "$commit"
    printf 'CREATED_AT=%s\n' "$created_at"
    printf 'STATUS=%s\n' "$status"
    printf 'UPDATED_AT=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    printf 'EXIT_CODE=%s\n' "$exit_code"
  } > "$temp"

  chmod 0640 "$temp"
  mv -f "$temp" "$state_file"
}

android_worker_load_request() {
  local file="$1"
  local key value
  local seen_id=0 seen_mode=0 seen_commit=0 seen_created=0 seen_client=0 seen_broker=0 seen_callback=0

  ANDROID_JOB_ID=""
  ANDROID_JOB_MODE=""
  ANDROID_JOB_COMMIT=""
  ANDROID_JOB_CREATED_AT=""
  ANDROID_JOB_CLIENT_ID=""
  ANDROID_JOB_BROKER_URL=""
  ANDROID_JOB_CALLBACK_URL=""

  [[ -r "$file" ]] || return 1

  while IFS='=' read -r key value || [[ -n "$key" ]]; do
    [[ -z "$key" ]] && continue
    case "$key" in
      JOB_ID)
        android_worker_valid_job_id "$value" || return 1
        ANDROID_JOB_ID="$value"
        seen_id=1
        ;;
      MODE)
        android_worker_valid_mode "$value" || return 1
        ANDROID_JOB_MODE="$value"
        seen_mode=1
        ;;
      COMMIT)
        [[ "$value" =~ ^[a-f0-9]{40}$ ]] || return 1
        ANDROID_JOB_COMMIT="$value"
        seen_commit=1
        ;;
      CREATED_AT)
        [[ "$value" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]] || return 1
        ANDROID_JOB_CREATED_AT="$value"
        seen_created=1
        ;;
      GITHUB_CLIENT_ID)
        [[ "$value" =~ ^[A-Za-z0-9._-]{3,128}$ ]] || return 1
        ANDROID_JOB_CLIENT_ID="$value"
        seen_client=1
        ;;
      AUTH_BROKER_BASE_URL)
        [[ "$value" =~ ^https://[^[:space:]]+$ ]] || return 1
        ANDROID_JOB_BROKER_URL="$value"
        seen_broker=1
        ;;
      GITHUB_CALLBACK_URL)
        [[ "$value" =~ ^https://[^[:space:]]+$ ]] || return 1
        ANDROID_JOB_CALLBACK_URL="$value"
        seen_callback=1
        ;;
      *)
        return 1
        ;;
    esac
  done < "$file"

  (( seen_id == 1 && seen_mode == 1 && seen_commit == 1 && seen_created == 1 &&
     seen_client == 1 && seen_broker == 1 && seen_callback == 1 ))
}

android_worker_read_public_config() {
  local broker_env="$INSTALL_DIR/auth-broker/.env"
  [[ -r "$broker_env" ]] || die "Auth Broker environment is missing: $broker_env"

  local client_id callback expected_callback
  client_id="$(awk -F= '$1 == "GITHUB_APP_CLIENT_ID" {sub(/^[^=]*=/, ""); print; exit}' "$broker_env")"
  callback="$(awk -F= '$1 == "GITHUB_CALLBACK_URL" {sub(/^[^=]*=/, ""); print; exit}' "$broker_env")"
  expected_callback="https://$DOMAIN/oauth/callback"

  [[ "$client_id" =~ ^[A-Za-z0-9._-]{3,128}$ ]] ||
    die "GitHub App Client ID is missing or invalid in the Auth Broker environment."
  [[ "$callback" == "$expected_callback" ]] ||
    die "Auth Broker callback does not match the managed VPS domain."

  ANDROID_JOB_CLIENT_ID="$client_id"
  ANDROID_JOB_BROKER_URL="https://$DOMAIN"
  ANDROID_JOB_CALLBACK_URL="$expected_callback"
}

android_worker_write_request() {
  local destination="$1"
  local job_id="$2"
  local mode="$3"
  local commit="$4"
  local created_at="$5"
  local temp

  temp="$(mktemp "$NEXORA_ANDROID_QUEUE_PENDING/.request.XXXXXX")"
  {
    printf 'JOB_ID=%s\n' "$job_id"
    printf 'MODE=%s\n' "$mode"
    printf 'COMMIT=%s\n' "$commit"
    printf 'CREATED_AT=%s\n' "$created_at"
    printf 'GITHUB_CLIENT_ID=%s\n' "$ANDROID_JOB_CLIENT_ID"
    printf 'AUTH_BROKER_BASE_URL=%s\n' "$ANDROID_JOB_BROKER_URL"
    printf 'GITHUB_CALLBACK_URL=%s\n' "$ANDROID_JOB_CALLBACK_URL"
  } > "$temp"
  chmod 0640 "$temp"
  mv "$temp" "$destination"
}

android_worker_create_snapshot() {
  local job_id="$1"
  local commit="$2"
  local job_dir source_dir
  job_dir="$(android_worker_job_dir "$job_id")"
  source_dir="$job_dir/source"

  mkdir -p "$source_dir"
  git -C "$INSTALL_DIR" archive --format=tar "$commit" | tar -xf - -C "$source_dir"

  if [[ "${EUID:-$(id -u)}" -eq 0 ]] && id "$NEXORA_ANDROID_BUILD_USER" >/dev/null 2>&1; then
    chown -R "$NEXORA_ANDROID_BUILD_USER:$(id -gn "$NEXORA_ANDROID_BUILD_USER")" "$job_dir"
  fi
  chmod 0750 "$job_dir" "$source_dir"
}

android_worker_enqueue() {
  local mode="${1:-release}"
  local ref="${2:-HEAD}"

  require_root
  load_state || die "Nexora Git VPS is not installed."
  load_android_config
  android_worker_ensure_layout
  android_doctor || die "Android build host is not healthy."

  android_worker_valid_mode "$mode" || die "Build mode must be release or debug."

  local commit
  commit="$(git -C "$INSTALL_DIR" rev-parse --verify "${ref}^{commit}" 2>/dev/null || true)"
  [[ "$commit" =~ ^[a-f0-9]{40}$ ]] || die "Cannot resolve Android build ref: $ref"

  android_worker_read_public_config

  local job_id created_at request
  job_id="$(android_worker_make_job_id)"
  created_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  request="$NEXORA_ANDROID_QUEUE_PENDING/$job_id.job"

  android_worker_create_snapshot "$job_id" "$commit"
  android_worker_write_request "$request" "$job_id" "$mode" "$commit" "$created_at"
  android_worker_write_state "$job_id" "$mode" "$commit" "$created_at" "QUEUED" ""

  if [[ "${NEXORA_ANDROID_SKIP_SYSTEMD:-0}" != "1" ]]; then
    systemctl start "$NEXORA_ANDROID_WORKER_SERVICE"
  fi

  printf '%s\n' "$job_id"
}

android_worker_load_state() {
  local job_id="$1"
  local file
  file="$(android_worker_state_file "$job_id")"
  [[ -r "$file" ]] || return 1

  local key value
  JOB_ID=""
  MODE=""
  COMMIT=""
  CREATED_AT=""
  STATUS=""
  UPDATED_AT=""
  EXIT_CODE=""

  while IFS='=' read -r key value || [[ -n "$key" ]]; do
    case "$key" in
      JOB_ID) JOB_ID="$value" ;;
      MODE) MODE="$value" ;;
      COMMIT) COMMIT="$value" ;;
      CREATED_AT) CREATED_AT="$value" ;;
      STATUS) STATUS="$value" ;;
      UPDATED_AT) UPDATED_AT="$value" ;;
      EXIT_CODE) EXIT_CODE="$value" ;;
      *) return 1 ;;
    esac
  done < "$file"

  android_worker_valid_job_id "$JOB_ID" &&
    android_worker_valid_mode "$MODE" &&
    [[ "$COMMIT" =~ ^[a-f0-9]{40}$ ]] &&
    [[ "$STATUS" =~ ^[A-Z_]+$ ]]
}

android_worker_show_job() {
  local job_id="$1"
  android_worker_valid_job_id "$job_id" || die "Invalid Android build job ID."
  android_worker_load_state "$job_id" || die "Android build job not found or state is invalid: $job_id"

  printf 'Job:       %s\n' "$JOB_ID"
  printf 'Mode:      %s\n' "$MODE"
  printf 'Commit:    %s\n' "$COMMIT"
  printf 'Created:   %s\n' "$CREATED_AT"
  printf 'Updated:   %s\n' "$UPDATED_AT"
  printf 'Status:    %s\n' "$STATUS"
  printf 'Exit code: %s\n' "${EXIT_CODE:-not available}"
}

android_worker_list_jobs() {
  local limit="${1:-20}"
  if [[ ! "$limit" =~ ^[0-9]+$ ]] || (( limit < 1 || limit > 200 )); then
    die "Build list limit must be between 1 and 200."
  fi

  local build_root="$NEXORA_ANDROID_STATE_ROOT/builds"
  [[ -d "$build_root" ]] || {
    printf 'No Android build jobs found.\n'
    return
  }

  local ids=()
  mapfile -t ids < <(
    find "$build_root" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' 2>/dev/null |
      grep -E '^[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}$' |
      sort -r |
      head -n "$limit"
  )

  if (( ${#ids[@]} == 0 )); then
    printf 'No Android build jobs found.\n'
    return
  fi

  printf '%-26s %-8s %-18s %-9s %s\n' "JOB" "MODE" "STATUS" "EXIT" "COMMIT"
  local id
  for id in "${ids[@]}"; do
    if android_worker_load_state "$id"; then
      printf '%-26s %-8s %-18s %-9s %.12s\n' "$JOB_ID" "$MODE" "$STATUS" "${EXIT_CODE:--}" "$COMMIT"
    else
      printf '%-26s %-8s %-18s %-9s %s\n' "$id" "-" "INVALID_STATE" "-" "-"
    fi
  done
}

android_worker_queue_count() {
  local dir="$1"
  find "$dir" -maxdepth 1 -type f -name '*.job' -printf '.' 2>/dev/null | wc -c
}

android_worker_service_status() {
  android_worker_ensure_layout

  local state="not-managed"
  if [[ "${NEXORA_ANDROID_SKIP_SYSTEMD:-0}" != "1" ]] && command -v systemctl >/dev/null 2>&1; then
    state="$(systemctl is-active "$NEXORA_ANDROID_WORKER_SERVICE" 2>/dev/null || true)"
  fi

  printf 'Worker:    %s\n' "$state"
  printf 'Pending:   %s\n' "$(android_worker_queue_count "$NEXORA_ANDROID_QUEUE_PENDING")"
  printf 'Running:   %s\n' "$(android_worker_queue_count "$NEXORA_ANDROID_QUEUE_RUNNING")"
  printf 'Completed: %s\n' "$(android_worker_queue_count "$NEXORA_ANDROID_QUEUE_COMPLETED")"
  printf 'Failed:    %s\n' "$(android_worker_queue_count "$NEXORA_ANDROID_QUEUE_FAILED")"
  printf 'Cancelled: %s\n' "$(android_worker_queue_count "$NEXORA_ANDROID_QUEUE_CANCELLED")"

  if [[ -r "$NEXORA_ANDROID_CURRENT_JOB_FILE" ]]; then
    printf 'Current:   %s\n' "$(cat "$NEXORA_ANDROID_CURRENT_JOB_FILE")"
  else
    printf 'Current:   none\n'
  fi
}

android_worker_cancel() {
  local job_id="${1:-}"
  android_worker_valid_job_id "$job_id" || die "Usage: nexora-git android cancel <JOB_ID>"
  android_worker_ensure_layout

  local pending="$NEXORA_ANDROID_QUEUE_PENDING/$job_id.job"
  local running="$NEXORA_ANDROID_QUEUE_RUNNING/$job_id.job"
  local cancelled="$NEXORA_ANDROID_QUEUE_CANCELLED/$job_id.job"

  if [[ -f "$pending" ]]; then
    android_worker_load_request "$pending" || die "Queued request is invalid: $job_id"
    mv "$pending" "$cancelled"
    android_worker_write_state "$job_id" "$ANDROID_JOB_MODE" "$ANDROID_JOB_COMMIT" "$ANDROID_JOB_CREATED_AT" "CANCELLED" "0"
    log_ok "Cancelled queued Android build: $job_id"
    return
  fi

  if [[ -f "$running" ]]; then
    touch "$NEXORA_ANDROID_CANCEL_ROOT/$job_id"
    chmod 0640 "$NEXORA_ANDROID_CANCEL_ROOT/$job_id"

    if [[ "${NEXORA_ANDROID_SKIP_SYSTEMD:-0}" != "1" ]]; then
      systemctl kill --kill-who=all --signal=TERM "$NEXORA_ANDROID_WORKER_SERVICE" || true
    fi
    log_ok "Cancellation requested for running Android build: $job_id"
    return
  fi

  if [[ -f "$NEXORA_ANDROID_QUEUE_COMPLETED/$job_id.job" ||
        -f "$NEXORA_ANDROID_QUEUE_FAILED/$job_id.job" ||
        -f "$NEXORA_ANDROID_QUEUE_CANCELLED/$job_id.job" ]]; then
    die "Android build is already in a terminal state: $job_id"
  fi

  die "Android build job not found: $job_id"
}

android_worker_recover_orphans() {
  android_worker_ensure_layout

  local request job_id
  shopt -s nullglob
  for request in "$NEXORA_ANDROID_QUEUE_RUNNING"/*.job; do
    job_id="$(basename "$request" .job)"
    if ! android_worker_load_request "$request"; then
      mv "$request" "$NEXORA_ANDROID_QUEUE_FAILED/$job_id.job"
      continue
    fi

    if [[ -f "$NEXORA_ANDROID_CANCEL_ROOT/$job_id" ]]; then
      mv "$request" "$NEXORA_ANDROID_QUEUE_CANCELLED/$job_id.job"
      rm -f "$NEXORA_ANDROID_CANCEL_ROOT/$job_id"
      android_worker_write_state "$job_id" "$ANDROID_JOB_MODE" "$ANDROID_JOB_COMMIT" "$ANDROID_JOB_CREATED_AT" "CANCELLED" "143"
    else
      mv "$request" "$NEXORA_ANDROID_QUEUE_PENDING/$job_id.job"
      android_worker_write_state "$job_id" "$ANDROID_JOB_MODE" "$ANDROID_JOB_COMMIT" "$ANDROID_JOB_CREATED_AT" "QUEUED_RECOVERED" ""
    fi
  done
  shopt -u nullglob
  rm -f "$NEXORA_ANDROID_CURRENT_JOB_FILE"
}

android_worker_next_request() {
  find "$NEXORA_ANDROID_QUEUE_PENDING" -maxdepth 1 -type f -name '*.job' -printf '%f\n' 2>/dev/null |
    sort |
    head -n 1
}

android_worker_gradle() {
  local job_id="$1"
  local source_dir
  source_dir="$(android_worker_job_dir "$job_id")/source"
  [[ -x "$source_dir/gradlew" ]] || {
    log_error "Gradle wrapper missing in build snapshot: $job_id"
    return 2
  }

  local tasks=()
  if [[ "$ANDROID_JOB_MODE" == "release" ]]; then
    tasks=(
      :app:assembleRelease
      :app:bundleRelease
      :app:testDebugUnitTest
      :app:lintRelease
    )
  else
    tasks=(
      :app:assembleDebug
      :app:testDebugUnitTest
    )
  fi

  (
    cd "$source_dir"
    env -i \
      HOME="$NEXORA_ANDROID_STATE_ROOT/worker" \
      USER="$NEXORA_ANDROID_BUILD_USER" \
      LOGNAME="$NEXORA_ANDROID_BUILD_USER" \
      LANG=C.UTF-8 \
      LC_ALL=C.UTF-8 \
      PATH="$NEXORA_ANDROID_JAVA_HOME/bin:$NEXORA_ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$NEXORA_ANDROID_SDK_ROOT/platform-tools:/usr/bin:/bin" \
      JAVA_HOME="$NEXORA_ANDROID_JAVA_HOME" \
      ANDROID_SDK_ROOT="$NEXORA_ANDROID_SDK_ROOT" \
      ANDROID_HOME="$NEXORA_ANDROID_SDK_ROOT" \
      GRADLE_USER_HOME="$NEXORA_ANDROID_GRADLE_HOME" \
      NEXORA_GITHUB_CLIENT_ID="$ANDROID_JOB_CLIENT_ID" \
      NEXORA_AUTH_BROKER_BASE_URL="$ANDROID_JOB_BROKER_URL" \
      NEXORA_GITHUB_CALLBACK_URL="$ANDROID_JOB_CALLBACK_URL" \
      ./gradlew --no-daemon "${tasks[@]}"
  )
}

android_worker_finish_job() {
  local request="$1"
  local destination_dir="$2"
  local status="$3"
  local exit_code="$4"

  [[ -f "$request" ]] || return 0
  local filename
  filename="$(basename "$request")"
  mv "$request" "$destination_dir/$filename"
  android_worker_write_state "$ANDROID_JOB_ID" "$ANDROID_JOB_MODE" "$ANDROID_JOB_COMMIT" "$ANDROID_JOB_CREATED_AT" "$status" "$exit_code"
  rm -f "$NEXORA_ANDROID_CANCEL_ROOT/$ANDROID_JOB_ID"
}

ANDROID_WORKER_CURRENT_JOB=""

android_worker_signal() {
  if [[ -n "$ANDROID_WORKER_CURRENT_JOB" ]]; then
    local running="$NEXORA_ANDROID_QUEUE_RUNNING/$ANDROID_WORKER_CURRENT_JOB.job"
    if [[ -f "$NEXORA_ANDROID_CANCEL_ROOT/$ANDROID_WORKER_CURRENT_JOB" && -f "$running" ]]; then
      if android_worker_load_request "$running"; then
        android_worker_finish_job "$running" "$NEXORA_ANDROID_QUEUE_CANCELLED" "CANCELLED" "143"
      fi
    fi
  fi
  exit 143
}

android_worker_process_one() {
  local filename
  filename="$(android_worker_next_request)"
  [[ -n "$filename" ]] || return 1

  local pending="$NEXORA_ANDROID_QUEUE_PENDING/$filename"
  local running="$NEXORA_ANDROID_QUEUE_RUNNING/$filename"
  mv "$pending" "$running"

  if ! android_worker_load_request "$running"; then
    local bad_id
    bad_id="$(basename "$filename" .job)"
    log_error "Invalid Android build queue request: $filename"
    mv "$running" "$NEXORA_ANDROID_QUEUE_FAILED/$filename"
    [[ -d "$NEXORA_ANDROID_STATE_ROOT/builds/$bad_id" ]] &&
      printf 'STATUS=INVALID_REQUEST\n' > "$NEXORA_ANDROID_STATE_ROOT/builds/$bad_id/job.conf"
    return 0
  fi

  ANDROID_WORKER_CURRENT_JOB="$ANDROID_JOB_ID"
  printf '%s\n' "$ANDROID_JOB_ID" > "$NEXORA_ANDROID_CURRENT_JOB_FILE"
  chmod 0640 "$NEXORA_ANDROID_CURRENT_JOB_FILE"

  if [[ -f "$NEXORA_ANDROID_CANCEL_ROOT/$ANDROID_JOB_ID" ]]; then
    android_worker_finish_job "$running" "$NEXORA_ANDROID_QUEUE_CANCELLED" "CANCELLED" "0"
    rm -f "$NEXORA_ANDROID_CURRENT_JOB_FILE"
    ANDROID_WORKER_CURRENT_JOB=""
    return 0
  fi

  android_worker_write_state "$ANDROID_JOB_ID" "$ANDROID_JOB_MODE" "$ANDROID_JOB_COMMIT" "$ANDROID_JOB_CREATED_AT" "RUNNING" ""
  log_info "Android build started: $ANDROID_JOB_ID ($ANDROID_JOB_MODE @ $ANDROID_JOB_COMMIT)"

  local build_log
  build_log="$(android_artifacts_init_log "$ANDROID_JOB_ID")"
  {
    printf '[%s] job=%s mode=%s commit=%s status=STARTED\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$ANDROID_JOB_ID" "$ANDROID_JOB_MODE" "$ANDROID_JOB_COMMIT"
  } >> "$build_log"

  local result=0
  if android_worker_gradle "$ANDROID_JOB_ID" 2>&1 | tee -a "$build_log"; then
    result=0
  else
    result="${PIPESTATUS[0]}"
  fi

  if [[ -f "$NEXORA_ANDROID_CANCEL_ROOT/$ANDROID_JOB_ID" ]]; then
    android_worker_finish_job "$running" "$NEXORA_ANDROID_QUEUE_CANCELLED" "CANCELLED" "$result"
    log_warn "Android build cancelled: $ANDROID_JOB_ID"
  elif (( result == 0 )); then
    local source_dir
    source_dir="$(android_worker_job_dir "$ANDROID_JOB_ID")/source"
    if android_artifacts_stage "$ANDROID_JOB_ID" "$ANDROID_JOB_MODE" "$ANDROID_JOB_COMMIT" "$ANDROID_JOB_CREATED_AT" "$source_dir"; then
      android_worker_finish_job "$running" "$NEXORA_ANDROID_QUEUE_COMPLETED" "COMPLETED" "0"
      printf '[%s] job=%s status=COMPLETED artifacts=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$ANDROID_JOB_ID" "$(android_artifacts_job_dir "$ANDROID_JOB_ID")" >> "$build_log"
      log_ok "Android build completed: $ANDROID_JOB_ID"
    else
      result=90
      android_worker_finish_job "$running" "$NEXORA_ANDROID_QUEUE_FAILED" "FAILED" "$result"
      printf '[%s] job=%s status=FAILED stage_exit=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$ANDROID_JOB_ID" "$result" >> "$build_log"
      log_error "Android build outputs failed artifact staging: $ANDROID_JOB_ID"
    fi
  else
    android_worker_finish_job "$running" "$NEXORA_ANDROID_QUEUE_FAILED" "FAILED" "$result"
    log_error "Android build failed: $ANDROID_JOB_ID (exit $result)"
  fi

  rm -f "$NEXORA_ANDROID_CURRENT_JOB_FILE"
  ANDROID_WORKER_CURRENT_JOB=""
  android_artifacts_apply_retention 0 "$NEXORA_ANDROID_RETENTION_KEEP" "$NEXORA_ANDROID_RETENTION_DAYS" >/dev/null || true
  return 0
}

android_worker_run_forever() {
  load_android_config
  android_worker_ensure_layout

  exec 9>"$NEXORA_ANDROID_WORKER_LOCK"
  if ! flock -n 9; then
    log_warn "Another Android build worker already holds the worker lock."
    return 0
  fi

  trap android_worker_signal TERM INT
  android_worker_recover_orphans

  while true; do
    if ! android_worker_process_one; then
      sleep 1
    fi
  done
}

android_worker_install_service() {
  require_root
  android_worker_ensure_layout

  command -v systemctl >/dev/null 2>&1 || die "systemd is required for detached Android builds."
  command -v flock >/dev/null 2>&1 || die "flock is required for the Android build worker."

  install -d -m 0755 /usr/local/libexec
  ln -sfn "$INSTALL_DIR/scripts/vps/android-worker.sh" /usr/local/libexec/nexora-git-android-worker

  install -m 0644 \
    "$INSTALL_DIR/scripts/vps/systemd/nexora-git-android-worker.service" \
    /etc/systemd/system/nexora-git-android-worker.service

  systemctl daemon-reload
  systemctl enable --now "$NEXORA_ANDROID_WORKER_SERVICE"
  systemctl is-active --quiet "$NEXORA_ANDROID_WORKER_SERVICE" ||
    die "Android build worker service failed to start."
  log_ok "Android build worker is active and will survive SSH disconnects and host reboots."
}

android_worker_doctor() {
  local failed=0
  android_worker_ensure_layout

  command -v flock >/dev/null 2>&1 || {
    log_error "flock is unavailable."
    failed=1
  }

  if [[ "${NEXORA_ANDROID_SKIP_SYSTEMD:-0}" != "1" ]]; then
    if systemctl is-enabled --quiet "$NEXORA_ANDROID_WORKER_SERVICE" 2>/dev/null; then
      log_ok "Android worker service enabled at boot."
    else
      log_error "Android worker service is not enabled."
      failed=1
    fi

    if systemctl is-active --quiet "$NEXORA_ANDROID_WORKER_SERVICE" 2>/dev/null; then
      log_ok "Android worker service active."
    else
      log_error "Android worker service is not active."
      failed=1
    fi
  fi

  android_artifacts_doctor || failed=1

  for path in \
    "$NEXORA_ANDROID_QUEUE_PENDING" \
    "$NEXORA_ANDROID_QUEUE_RUNNING" \
    "$NEXORA_ANDROID_QUEUE_COMPLETED" \
    "$NEXORA_ANDROID_QUEUE_FAILED" \
    "$NEXORA_ANDROID_QUEUE_CANCELLED" \
    "$NEXORA_ANDROID_CANCEL_ROOT" \
    "$NEXORA_ANDROID_LOCK_ROOT"; do
    [[ -d "$path" ]] || {
      log_error "Android worker path missing: $path"
      failed=1
    }
  done

  return "$failed"
}
