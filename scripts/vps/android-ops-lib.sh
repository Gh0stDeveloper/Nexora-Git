#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
if ! declare -F log_info >/dev/null 2>&1; then
  # shellcheck source=lib.sh
  source "$SCRIPT_DIR/lib.sh"
fi
if ! declare -F android_worker_enqueue >/dev/null 2>&1; then
  # shellcheck source=android-worker-lib.sh
  source "$SCRIPT_DIR/android-worker-lib.sh"
fi

NEXORA_OPS_ROOT="${NEXORA_OPS_ROOT:-/var/lib/nexora-git/operations}"
NEXORA_OPS_LOCK_ROOT="${NEXORA_OPS_LOCK_ROOT:-$NEXORA_OPS_ROOT/locks}"
NEXORA_UPDATE_LOCK="${NEXORA_UPDATE_LOCK:-$NEXORA_OPS_LOCK_ROOT/update.lock}"
NEXORA_CHECKPOINT_ROOT="${NEXORA_CHECKPOINT_ROOT:-$NEXORA_OPS_ROOT/checkpoints}"
NEXORA_CHECKPOINT_KEEP="${NEXORA_CHECKPOINT_KEEP:-10}"

NEXORA_AUTOBUILD_CONFIG="${NEXORA_AUTOBUILD_CONFIG:-/etc/nexora-git/android-autobuild.conf}"
NEXORA_AUTOBUILD_PENDING="${NEXORA_AUTOBUILD_PENDING:-$NEXORA_OPS_ROOT/autobuild.pending}"
NEXORA_AUTOBUILD_TIMER="${NEXORA_AUTOBUILD_TIMER:-nexora-git-android-autobuild.timer}"
NEXORA_AUTOBUILD_SERVICE="${NEXORA_AUTOBUILD_SERVICE:-nexora-git-android-autobuild.service}"
NEXORA_AUTOBUILD_ENABLED="${NEXORA_AUTOBUILD_ENABLED:-1}"
NEXORA_AUTOBUILD_MODE="${NEXORA_AUTOBUILD_MODE:-release}"
NEXORA_AUTOBUILD_DEBOUNCE_SECONDS="${NEXORA_AUTOBUILD_DEBOUNCE_SECONDS:-30}"

NEXORA_NGINX_CONFIG="${NEXORA_NGINX_CONFIG:-/etc/nginx/sites-available/nexora-git-auth.conf}"
NEXORA_WORKER_UNIT_DEST="${NEXORA_WORKER_UNIT_DEST:-/etc/systemd/system/nexora-git-android-worker.service}"
NEXORA_AUTOBUILD_SERVICE_DEST="${NEXORA_AUTOBUILD_SERVICE_DEST:-/etc/systemd/system/nexora-git-android-autobuild.service}"
NEXORA_AUTOBUILD_TIMER_DEST="${NEXORA_AUTOBUILD_TIMER_DEST:-/etc/systemd/system/nexora-git-android-autobuild.timer}"

android_ops_require_or_test_root() {
  if [[ "${NEXORA_OPS_TEST_MODE:-0}" != "1" ]]; then
    require_root
  fi
}

android_ops_ensure_layout() {
  android_ops_require_or_test_root
  install -d -m 0700 "$NEXORA_OPS_ROOT" "$NEXORA_OPS_LOCK_ROOT" "$NEXORA_CHECKPOINT_ROOT"
}

android_ops_validate_config() {
  [[ "$NEXORA_AUTOBUILD_ENABLED" == "0" || "$NEXORA_AUTOBUILD_ENABLED" == "1" ]] &&
    android_worker_valid_mode "$NEXORA_AUTOBUILD_MODE" &&
    [[ "$NEXORA_AUTOBUILD_DEBOUNCE_SECONDS" =~ ^[0-9]+$ ]] &&
    (( NEXORA_AUTOBUILD_DEBOUNCE_SECONDS >= 5 && NEXORA_AUTOBUILD_DEBOUNCE_SECONDS <= 3600 ))
}

android_ops_load_autobuild_config() {
  NEXORA_AUTOBUILD_ENABLED="${NEXORA_AUTOBUILD_ENABLED:-1}"
  NEXORA_AUTOBUILD_MODE="${NEXORA_AUTOBUILD_MODE:-release}"
  NEXORA_AUTOBUILD_DEBOUNCE_SECONDS="${NEXORA_AUTOBUILD_DEBOUNCE_SECONDS:-30}"

  if [[ -r "$NEXORA_AUTOBUILD_CONFIG" ]]; then
    # shellcheck disable=SC1090
    source "$NEXORA_AUTOBUILD_CONFIG"
  fi
  android_ops_validate_config
}

android_ops_write_autobuild_config() {
  local enabled="$1"
  local mode="$2"
  local debounce="${3:-30}"
  [[ "$enabled" == "0" || "$enabled" == "1" ]] || die "Autobuild enabled value must be 0 or 1."
  android_worker_valid_mode "$mode" || die "Autobuild mode must be release or debug."
  if [[ ! "$debounce" =~ ^[0-9]+$ ]] || (( debounce < 5 || debounce > 3600 )); then
    die "Autobuild debounce must be between 5 and 3600 seconds."
  fi

  local dir temp
  dir="$(dirname "$NEXORA_AUTOBUILD_CONFIG")"
  install -d -m 0755 "$dir"
  temp="$(mktemp "$dir/.android-autobuild.XXXXXX")"
  {
    printf 'NEXORA_AUTOBUILD_ENABLED=%q\n' "$enabled"
    printf 'NEXORA_AUTOBUILD_MODE=%q\n' "$mode"
    printf 'NEXORA_AUTOBUILD_DEBOUNCE_SECONDS=%q\n' "$debounce"
  } > "$temp"
  chmod 0644 "$temp"
  mv -f "$temp" "$NEXORA_AUTOBUILD_CONFIG"

  NEXORA_AUTOBUILD_ENABLED="$enabled"
  NEXORA_AUTOBUILD_MODE="$mode"
  NEXORA_AUTOBUILD_DEBOUNCE_SECONDS="$debounce"
}

android_ops_ensure_autobuild_config() {
  if [[ -r "$NEXORA_AUTOBUILD_CONFIG" ]]; then
    android_ops_load_autobuild_config || die "Android autobuild configuration is invalid."
    return
  fi
  android_ops_write_autobuild_config 1 release 30
}

android_ops_autobuild_marker() {
  local job_id="$1"
  printf '%s/autobuild.conf\n' "$(android_worker_job_dir "$job_id")"
}

android_ops_job_origin() {
  local job_id="$1"
  if [[ -s "$(android_ops_autobuild_marker "$job_id")" ]]; then
    printf 'autobuild\n'
  else
    printf 'manual\n'
  fi
}

android_ops_mark_autobuild() {
  local job_id="$1"
  local commit="$2"
  local mode="$3"
  local marker
  marker="$(android_ops_autobuild_marker "$job_id")"
  {
    printf 'ORIGIN=autobuild\n'
    printf 'COMMIT=%s\n' "$commit"
    printf 'MODE=%s\n' "$mode"
  } > "$marker"
  chmod 0640 "$marker"
}

android_ops_find_existing_job() {
  local mode="$1"
  local commit="$2"
  local root="$NEXORA_ANDROID_STATE_ROOT/builds"
  [[ -d "$root" ]] || return 1

  local state id state_mode state_commit state_status
  while IFS= read -r state; do
    id="$(basename "$(dirname "$state")")"
    state_mode="$(awk -F= '$1=="MODE"{print $2; exit}' "$state")"
    state_commit="$(awk -F= '$1=="COMMIT"{print $2; exit}' "$state")"
    state_status="$(awk -F= '$1=="STATUS"{print $2; exit}' "$state")"
    if [[ "$state_mode" == "$mode" && "$state_commit" == "$commit" ]]; then
      case "$state_status" in
        QUEUED|QUEUED_RECOVERED|RUNNING|COMPLETED)
          printf '%s\n' "$id"
          return 0
          ;;
      esac
    fi
  done < <(find "$root" -mindepth 2 -maxdepth 2 -type f -name job.conf -print | sort -r)
  return 1
}

android_ops_cancel_superseded_pending() {
  local mode="$1"
  local new_commit="$2"
  local request id marker

  shopt -s nullglob
  for request in "$NEXORA_ANDROID_QUEUE_PENDING"/*.job; do
    id="$(basename "$request" .job)"
    marker="$(android_ops_autobuild_marker "$id")"
    [[ -s "$marker" ]] || continue

    if android_worker_load_request "$request" &&
       [[ "$ANDROID_JOB_MODE" == "$mode" && "$ANDROID_JOB_COMMIT" != "$new_commit" ]]; then
      mv "$request" "$NEXORA_ANDROID_QUEUE_CANCELLED/$id.job"
      android_worker_write_state "$id" "$ANDROID_JOB_MODE" "$ANDROID_JOB_COMMIT" "$ANDROID_JOB_CREATED_AT" "CANCELLED" "0"
      log_info "Coalesced superseded pending autobuild: $id"
    fi
  done
  shopt -u nullglob
}

android_ops_autobuild_queue_commit() {
  local commit="$1"
  android_ops_load_autobuild_config || die "Android autobuild configuration is invalid."
  [[ "$NEXORA_AUTOBUILD_ENABLED" == "1" ]] || {
    log_info "Android autobuild is disabled; no build queued."
    return 0
  }
  [[ "$commit" =~ ^[a-f0-9]{40}$ ]] || die "Invalid autobuild commit SHA."

  local existing
  existing="$(android_ops_find_existing_job "$NEXORA_AUTOBUILD_MODE" "$commit" || true)"
  if [[ -n "$existing" ]]; then
    log_ok "Autobuild already represented by job $existing; duplicate skipped."
    printf '%s\n' "$existing"
    return 0
  fi

  android_ops_cancel_superseded_pending "$NEXORA_AUTOBUILD_MODE" "$commit"

  local job_id
  job_id="$(android_worker_enqueue "$NEXORA_AUTOBUILD_MODE" "$commit" autobuild)"
  log_ok "Autobuild queued for $commit as $job_id."
  printf '%s\n' "$job_id"
}

android_ops_write_pending_autobuild() {
  local commit="$1"
  local mode="$2"
  local requested_epoch="$3"
  local temp
  temp="$(mktemp "$NEXORA_OPS_ROOT/.autobuild.pending.XXXXXX")"
  {
    printf 'COMMIT=%s\n' "$commit"
    printf 'MODE=%s\n' "$mode"
    printf 'REQUESTED_EPOCH=%s\n' "$requested_epoch"
  } > "$temp"
  chmod 0600 "$temp"
  mv -f "$temp" "$NEXORA_AUTOBUILD_PENDING"
}

android_ops_load_pending_autobuild() {
  local file="$NEXORA_AUTOBUILD_PENDING"
  [[ -r "$file" ]] || return 1

  AUTOBUILD_PENDING_COMMIT=""
  AUTOBUILD_PENDING_MODE=""
  AUTOBUILD_PENDING_EPOCH=""

  local key value
  while IFS='=' read -r key value || [[ -n "$key" ]]; do
    case "$key" in
      COMMIT) AUTOBUILD_PENDING_COMMIT="$value" ;;
      MODE) AUTOBUILD_PENDING_MODE="$value" ;;
      REQUESTED_EPOCH) AUTOBUILD_PENDING_EPOCH="$value" ;;
      *) return 1 ;;
    esac
  done < "$file"

  [[ "$AUTOBUILD_PENDING_COMMIT" =~ ^[a-f0-9]{40}$ ]] &&
    android_worker_valid_mode "$AUTOBUILD_PENDING_MODE" &&
    [[ "$AUTOBUILD_PENDING_EPOCH" =~ ^[0-9]+$ ]]
}

android_ops_autobuild_schedule() {
  local commit="$1"
  android_ops_ensure_layout
  android_ops_ensure_autobuild_config
  android_ops_load_autobuild_config || die "Android autobuild configuration is invalid."

  [[ "$NEXORA_AUTOBUILD_ENABLED" == "1" ]] || {
    log_info "Autobuild disabled; successful update will not queue Android compilation."
    return 0
  }
  [[ "$commit" =~ ^[a-f0-9]{40}$ ]] || die "Invalid update commit for autobuild."

  android_ops_write_pending_autobuild "$commit" "$NEXORA_AUTOBUILD_MODE" "$(date +%s)"
  log_ok "Autobuild request scheduled for $commit ($NEXORA_AUTOBUILD_MODE)."

  if [[ "${NEXORA_OPS_SKIP_SYSTEMD:-0}" != "1" ]]; then
    systemctl start "$NEXORA_AUTOBUILD_TIMER"
  fi
}

android_ops_autobuild_run_pending() {
  android_ops_ensure_layout
  android_ops_ensure_autobuild_config
  android_ops_load_autobuild_config || die "Android autobuild configuration is invalid."

  [[ "$NEXORA_AUTOBUILD_ENABLED" == "1" ]] || {
    rm -f "$NEXORA_AUTOBUILD_PENDING"
    return 0
  }
  android_ops_load_pending_autobuild || return 0

  if [[ "$AUTOBUILD_PENDING_MODE" != "$NEXORA_AUTOBUILD_MODE" ]]; then
    log_warn "Pending autobuild mode changed; scheduling with current mode $NEXORA_AUTOBUILD_MODE."
    AUTOBUILD_PENDING_MODE="$NEXORA_AUTOBUILD_MODE"
  fi

  local now age
  now="$(date +%s)"
  age=$((now - AUTOBUILD_PENDING_EPOCH))
  if (( age < NEXORA_AUTOBUILD_DEBOUNCE_SECONDS )); then
    log_info "Autobuild debounce active ($age/$NEXORA_AUTOBUILD_DEBOUNCE_SECONDS seconds)."
    return 0
  fi

  [[ "$(git -C "$INSTALL_DIR" cat-file -t "$AUTOBUILD_PENDING_COMMIT" 2>/dev/null || true)" == "commit" ]] || {
    log_error "Pending autobuild commit is no longer available locally: $AUTOBUILD_PENDING_COMMIT"
    return 1
  }

  android_ops_autobuild_queue_commit "$AUTOBUILD_PENDING_COMMIT" >/dev/null
  rm -f "$NEXORA_AUTOBUILD_PENDING"
}

android_ops_autobuild_status() {
  android_ops_ensure_autobuild_config
  android_ops_load_autobuild_config || die "Android autobuild configuration is invalid."

  printf 'Enabled:   %s\n' "$([[ "$NEXORA_AUTOBUILD_ENABLED" == "1" ]] && printf yes || printf no)"
  printf 'Mode:      %s\n' "$NEXORA_AUTOBUILD_MODE"
  printf 'Debounce:  %s seconds\n' "$NEXORA_AUTOBUILD_DEBOUNCE_SECONDS"

  if android_ops_load_pending_autobuild; then
    printf 'Pending:   %s (%s)\n' "$AUTOBUILD_PENDING_COMMIT" "$AUTOBUILD_PENDING_MODE"
  else
    printf 'Pending:   none\n'
  fi

  if [[ "${NEXORA_OPS_SKIP_SYSTEMD:-0}" != "1" ]] && command -v systemctl >/dev/null 2>&1; then
    printf 'Timer:     %s\n' "$(systemctl is-active "$NEXORA_AUTOBUILD_TIMER" 2>/dev/null || true)"
  fi
}

android_ops_autobuild_enable() {
  local mode="${1:-release}"
  local debounce="${2:-30}"
  android_ops_require_or_test_root
  android_ops_ensure_layout
  android_ops_write_autobuild_config 1 "$mode" "$debounce"
  if [[ "${NEXORA_OPS_SKIP_SYSTEMD:-0}" != "1" ]]; then
    systemctl enable --now "$NEXORA_AUTOBUILD_TIMER"
  fi
  log_ok "Android autobuild enabled: mode=$mode debounce=${debounce}s."
}

android_ops_autobuild_disable() {
  android_ops_require_or_test_root
  android_ops_ensure_layout
  android_ops_load_autobuild_config || true
  android_ops_write_autobuild_config 0 "${NEXORA_AUTOBUILD_MODE:-release}" "${NEXORA_AUTOBUILD_DEBOUNCE_SECONDS:-30}"
  rm -f "$NEXORA_AUTOBUILD_PENDING"
  if [[ "${NEXORA_OPS_SKIP_SYSTEMD:-0}" != "1" ]]; then
    systemctl disable --now "$NEXORA_AUTOBUILD_TIMER" >/dev/null 2>&1 || true
  fi
  log_ok "Android autobuild disabled."
}

android_ops_checkpoint_valid_id() {
  [[ "$1" =~ ^[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}$ ]]
}

android_ops_checkpoint_id() {
  printf '%s-%s\n' "$(date -u +%Y%m%dT%H%M%SZ)" "$(openssl rand -hex 4)"
}

android_ops_checkpoint_map() {
  printf '%s|%s|%s\n' "deployment.conf" "$NEXORA_STATE_FILE" "0600"
  printf '%s|%s|%s\n' "android-builder.conf" "$NEXORA_ANDROID_CONFIG_FILE" "0644"
  printf '%s|%s|%s\n' "android-autobuild.conf" "$NEXORA_AUTOBUILD_CONFIG" "0644"
  printf '%s|%s|%s\n' "auth-broker.env" "$INSTALL_DIR/auth-broker/.env" "0600"
  printf '%s|%s|%s\n' "nginx.conf" "$NEXORA_NGINX_CONFIG" "0644"
  printf '%s|%s|%s\n' "worker.service" "$NEXORA_WORKER_UNIT_DEST" "0644"
  printf '%s|%s|%s\n' "autobuild.service" "$NEXORA_AUTOBUILD_SERVICE_DEST" "0644"
  printf '%s|%s|%s\n' "autobuild.timer" "$NEXORA_AUTOBUILD_TIMER_DEST" "0644"
  printf '%s|%s|%s\n' "github-sync.conf" "$NEXORA_GITHUB_SYNC_FILE" "0600"
}

android_ops_checkpoint_create() {
  local reason="${1:-manual}"
  local commit="${2:-}"
  android_ops_require_or_test_root
  android_ops_ensure_layout

  [[ "$reason" != *$'\n'* ]] || die "Checkpoint reason must be a single line."
  if [[ -z "$commit" ]]; then
    commit="$(git -C "$INSTALL_DIR" rev-parse HEAD)"
  fi
  [[ "$commit" =~ ^[a-f0-9]{40}$ ]] || die "Invalid checkpoint Git commit."

  local id dir name source mode
  id="$(android_ops_checkpoint_id)"
  dir="$NEXORA_CHECKPOINT_ROOT/$id"
  install -d -m 0700 "$dir"

  while IFS='|' read -r name source mode; do
    [[ -e "$source" ]] || continue
    install -m "$mode" "$source" "$dir/$name"
  done < <(android_ops_checkpoint_map)

  {
    printf 'CHECKPOINT_ID=%q\n' "$id"
    printf 'CREATED_AT=%q\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    printf 'GIT_COMMIT=%q\n' "$commit"
    printf 'REASON=%q\n' "$reason"
  } > "$dir/manifest.conf"
  chmod 0600 "$dir/manifest.conf"

  (
    cd "$dir"
    local_checksum="$(mktemp .SHA256SUMS.XXXXXX)"
    find . -maxdepth 1 -type f ! -name SHA256SUMS ! -name '.SHA256SUMS.*' -printf '%f\n' |
      sort |
      while IFS= read -r name; do sha256sum "$name"; done > "$local_checksum"
    chmod 0600 "$local_checksum"
    mv "$local_checksum" SHA256SUMS
  )

  android_ops_checkpoint_prune "$NEXORA_CHECKPOINT_KEEP" >/dev/null
  printf '%s\n' "$id"
}

android_ops_checkpoint_allowed_name() {
  case "$1" in
    deployment.conf|android-builder.conf|android-autobuild.conf|auth-broker.env|nginx.conf|worker.service|autobuild.service|autobuild.timer|github-sync.conf|manifest.conf|SHA256SUMS)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

android_ops_checkpoint_verify() {
  local id="$1"
  android_ops_checkpoint_valid_id "$id" || return 1
  local dir="$NEXORA_CHECKPOINT_ROOT/$id"
  [[ -d "$dir" && -s "$dir/manifest.conf" && -s "$dir/SHA256SUMS" ]] || return 1

  local path name
  while IFS= read -r path; do
    name="$(basename "$path")"
    android_ops_checkpoint_allowed_name "$name" || return 1
    [[ -f "$path" && ! -L "$path" ]] || return 1
  done < <(find "$dir" -mindepth 1 -maxdepth 1 -print)

  if awk '{print $2}' "$dir/SHA256SUMS" | grep -Eq '(^/|\.\.|/)'; then
    return 1
  fi

  (
    cd "$dir"
    sha256sum --check --strict SHA256SUMS >/dev/null
  )
}

android_ops_checkpoint_prune() {
  local keep="${1:-$NEXORA_CHECKPOINT_KEEP}"
  if [[ ! "$keep" =~ ^[0-9]+$ ]] || (( keep < 1 || keep > 100 )); then
    die "Checkpoint retention must be between 1 and 100."
  fi

  local ids=()
  mapfile -t ids < <(
    find "$NEXORA_CHECKPOINT_ROOT" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' |
      grep -E '^[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}$' |
      sort -r
  )

  local i
  for ((i=keep; i<${#ids[@]}; i++)); do
    rm -rf "${NEXORA_CHECKPOINT_ROOT:?}/${ids[$i]}"
  done
}

android_ops_checkpoint_list() {
  local limit="${1:-20}"
  if [[ ! "$limit" =~ ^[0-9]+$ ]] || (( limit < 1 || limit > 100 )); then
    die "Checkpoint list limit must be between 1 and 100."
  fi

  local ids=()
  mapfile -t ids < <(
    find "$NEXORA_CHECKPOINT_ROOT" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' 2>/dev/null |
      grep -E '^[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}$' |
      sort -r |
      head -n "$limit"
  )

  (( ${#ids[@]} > 0 )) || {
    printf 'No operational checkpoints found.\n'
    return
  }

  local id manifest commit reason
  printf '%-26s %-12s %s\n' "CHECKPOINT" "COMMIT" "REASON"
  for id in "${ids[@]}"; do
    manifest="$NEXORA_CHECKPOINT_ROOT/$id/manifest.conf"
    commit="$(sed -n 's/^GIT_COMMIT=//p' "$manifest" | tr -d "'")"
    reason="$(sed -n 's/^REASON=//p' "$manifest" | tr -d "'")"
    printf '%-26s %.12s %s\n' "$id" "$commit" "$reason"
  done
}

android_ops_checkpoint_restore_files() {
  local id="$1"
  android_ops_checkpoint_verify "$id" || return 1
  local dir="$NEXORA_CHECKPOINT_ROOT/$id"
  local name destination mode

  while IFS='|' read -r name destination mode; do
    [[ -f "$dir/$name" ]] || continue
    install -d -m 0755 "$(dirname "$destination")"
    install -m "$mode" "$dir/$name" "$destination"
  done < <(android_ops_checkpoint_map)
}

android_ops_checkpoint_restore() {
  local id="$1"
  android_ops_require_or_test_root
  android_ops_checkpoint_verify "$id" || die "Operational checkpoint is invalid: $id"

  local manifest="$NEXORA_CHECKPOINT_ROOT/$id/manifest.conf"
  # shellcheck disable=SC1090
  source "$manifest"
  [[ "${CHECKPOINT_ID:-}" == "$id" && "${GIT_COMMIT:-}" =~ ^[a-f0-9]{40}$ ]] ||
    die "Operational checkpoint metadata is invalid."

  if [[ "${NEXORA_OPS_TEST_MODE:-0}" == "1" ]]; then
    android_ops_checkpoint_restore_files "$id" || die "Checkpoint file restore failed."
    return
  fi

  [[ -d "$INSTALL_DIR/.git" ]] || die "Managed repository is unavailable."
  if ! git -C "$INSTALL_DIR" diff --quiet || ! git -C "$INSTALL_DIR" diff --cached --quiet; then
    die "Tracked repository changes detected. Recovery restore refuses to overwrite them."
  fi
  git -C "$INSTALL_DIR" cat-file -e "${GIT_COMMIT}^{commit}" ||
    die "Checkpoint commit is not available in the managed repository."

  systemctl stop "$NEXORA_AUTOBUILD_TIMER" >/dev/null 2>&1 || true
  systemctl stop "$NEXORA_ANDROID_WORKER_SERVICE" >/dev/null 2>&1 || true

  android_ops_checkpoint_restore_files "$id" || die "Checkpoint file restore failed."
  git -C "$INSTALL_DIR" reset --hard "$GIT_COMMIT"
  load_state || die "Restored deployment state is invalid."

  android_ops_refresh_runtime
  nginx -t
  systemctl reload nginx
  compose build auth-broker
  compose up -d --remove-orphans auth-broker
  wait_local_health "$LOCAL_PORT" 30 || die "Restored broker failed its local health check."

  log_ok "Operational checkpoint restored: $id ($GIT_COMMIT)"
}

android_ops_recovery_status() {
  android_ops_ensure_layout
  local checkpoint_count signing_backup_count latest
  checkpoint_count="$(find "$NEXORA_CHECKPOINT_ROOT" -mindepth 1 -maxdepth 1 -type d -name '????????T??????Z-*' | wc -l)"
  signing_backup_count="$(find "$NEXORA_SIGNING_BACKUP_DIR" -maxdepth 1 -type f -name '*.nxbk' 2>/dev/null | wc -l)"
  latest="$(find "$NEXORA_CHECKPOINT_ROOT" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' | sort -r | head -n 1)"

  printf 'Operational checkpoints: %s\n' "$checkpoint_count"
  printf 'Latest checkpoint:       %s\n' "${latest:-none}"
  printf 'Encrypted signing backups: %s\n' "$signing_backup_count"
  printf 'Checkpoint retention:    %s\n' "$NEXORA_CHECKPOINT_KEEP"
}

android_ops_request_worker_reload() {
  [[ "${NEXORA_OPS_SKIP_SYSTEMD:-0}" != "1" ]] || return 0

  if [[ -r "$NEXORA_ANDROID_CURRENT_JOB_FILE" ]]; then
    local group
    group="$(id -gn "$NEXORA_ANDROID_BUILD_USER")"
    install -o "$NEXORA_ANDROID_BUILD_USER" -g "$group" -m 0640 /dev/null "$NEXORA_ANDROID_RESTART_MARKER"
    log_info "Android worker update deferred until the current build finishes."
  else
    systemctl restart "$NEXORA_ANDROID_WORKER_SERVICE"
  fi
}

android_ops_install_autobuild_units() {
  android_ops_require_or_test_root
  android_ops_ensure_layout
  android_ops_ensure_autobuild_config

  [[ "${NEXORA_OPS_SKIP_SYSTEMD:-0}" == "1" ]] && return 0

  install -m 0644 "$INSTALL_DIR/scripts/vps/systemd/nexora-git-android-autobuild.service" "$NEXORA_AUTOBUILD_SERVICE_DEST"
  install -m 0644 "$INSTALL_DIR/scripts/vps/systemd/nexora-git-android-autobuild.timer" "$NEXORA_AUTOBUILD_TIMER_DEST"
  systemctl daemon-reload

  android_ops_load_autobuild_config || die "Android autobuild configuration is invalid."
  if [[ "$NEXORA_AUTOBUILD_ENABLED" == "1" ]]; then
    systemctl enable --now "$NEXORA_AUTOBUILD_TIMER"
  else
    systemctl disable --now "$NEXORA_AUTOBUILD_TIMER" >/dev/null 2>&1 || true
  fi
}

android_ops_refresh_runtime() {
  android_ops_require_or_test_root
  [[ "${NEXORA_OPS_TEST_MODE:-0}" == "1" ]] && return 0

  ln -sfn "$INSTALL_DIR/scripts/vps/nexora-git" /usr/local/bin/nexora-git
  chmod +x "$INSTALL_DIR/scripts/vps/nexora-git" "$INSTALL_DIR/scripts/vps/update.sh" "$INSTALL_DIR/scripts/vps/android-worker.sh"

  android_worker_install_service
  android_ops_install_autobuild_units
  android_ops_request_worker_reload
}

android_ops_rollback_update() {
  local checkpoint_id="$1"
  local old_commit="$2"

  log_warn "Rolling back update to $old_commit using checkpoint $checkpoint_id."
  git -C "$INSTALL_DIR" reset --hard "$old_commit" || return 1
  android_ops_checkpoint_restore_files "$checkpoint_id" || return 1
  load_state || return 1

  compose build auth-broker || return 1
  compose up -d --remove-orphans auth-broker || return 1
  wait_local_health "$LOCAL_PORT" 30 || return 1
  android_ops_refresh_runtime || return 1
  log_ok "Update rollback restored $old_commit and operational configuration."
}

android_ops_setup() {
  android_ops_require_or_test_root
  android_ops_ensure_layout
  android_ops_ensure_autobuild_config
  android_ops_install_autobuild_units
}

android_ops_doctor() {
  local failed=0
  android_ops_ensure_layout

  local mode
  mode="$(stat -c '%a' "$NEXORA_OPS_ROOT" 2>/dev/null || true)"
  if [[ "$mode" == "700" ]]; then
    log_ok "Operations root permissions: 0700"
  else
    log_error "Operations root permissions must be 0700 (actual: ${mode:-missing})"
    failed=1
  fi

  if android_ops_load_autobuild_config; then
    log_ok "Android autobuild configuration valid."
  else
    log_error "Android autobuild configuration invalid."
    failed=1
  fi

  if [[ "${NEXORA_OPS_SKIP_SYSTEMD:-0}" != "1" ]]; then
    if [[ "$NEXORA_AUTOBUILD_ENABLED" == "1" ]]; then
      systemctl is-enabled --quiet "$NEXORA_AUTOBUILD_TIMER" 2>/dev/null ||
        { log_error "Android autobuild timer is not enabled."; failed=1; }
      systemctl is-active --quiet "$NEXORA_AUTOBUILD_TIMER" 2>/dev/null ||
        { log_error "Android autobuild timer is not active."; failed=1; }
    fi
  fi

  return "$failed"
}
