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

NEXORA_ANDROID_ARTIFACT_ROOT="${NEXORA_ANDROID_ARTIFACT_ROOT:-$NEXORA_ANDROID_STATE_ROOT/artifacts}"
NEXORA_ANDROID_LOG_ROOT="${NEXORA_ANDROID_LOG_ROOT:-$NEXORA_ANDROID_STATE_ROOT/logs}"
NEXORA_ANDROID_RETENTION_DAYS="${NEXORA_ANDROID_RETENTION_DAYS:-30}"
NEXORA_ANDROID_RETENTION_KEEP="${NEXORA_ANDROID_RETENTION_KEEP:-20}"
NEXORA_ANDROID_MAX_APK_BYTES="${NEXORA_ANDROID_MAX_APK_BYTES:-262144000}"
NEXORA_ANDROID_MAX_AAB_BYTES="${NEXORA_ANDROID_MAX_AAB_BYTES:-209715200}"

android_artifact_valid_job_id() {
  [[ "$1" =~ ^[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}$ ]]
}

android_artifacts_ensure_layout() {
  load_android_config

  local owner="$NEXORA_ANDROID_BUILD_USER"
  local group
  if id "$owner" >/dev/null 2>&1; then
    group="$(id -gn "$owner")"
  else
    owner="$(id -un)"
    group="$(id -gn)"
  fi

  if [[ "${EUID:-$(id -u)}" -eq 0 ]]; then
    install -d -o "$owner" -g "$group" -m 0750 "$NEXORA_ANDROID_ARTIFACT_ROOT" "$NEXORA_ANDROID_LOG_ROOT"
  else
    mkdir -p "$NEXORA_ANDROID_ARTIFACT_ROOT" "$NEXORA_ANDROID_LOG_ROOT"
    chmod 0750 "$NEXORA_ANDROID_ARTIFACT_ROOT" "$NEXORA_ANDROID_LOG_ROOT"
  fi
}

android_artifacts_job_dir() {
  local job_id="$1"
  android_artifact_valid_job_id "$job_id" || return 1
  printf '%s/%s\n' "$NEXORA_ANDROID_ARTIFACT_ROOT" "$job_id"
}

android_artifacts_log_file() {
  local job_id="$1"
  android_artifact_valid_job_id "$job_id" || return 1
  printf '%s/%s.log\n' "$NEXORA_ANDROID_LOG_ROOT" "$job_id"
}

android_artifacts_init_log() {
  local job_id="$1"
  local log_file
  android_artifacts_ensure_layout
  log_file="$(android_artifacts_log_file "$job_id")"

  if [[ ! -e "$log_file" ]]; then
    : > "$log_file"
    chmod 0640 "$log_file"
  fi

  printf '%s\n' "$log_file"
}

android_artifacts_validate_size() {
  local kind="$1"
  local path="$2"
  local bytes limit

  bytes="$(stat -c '%s' "$path")"
  case "$kind" in
    apk) limit="$NEXORA_ANDROID_MAX_APK_BYTES" ;;
    aab) limit="$NEXORA_ANDROID_MAX_AAB_BYTES" ;;
    *) return 0 ;;
  esac

  if (( bytes > limit )); then
    log_error "Android $kind artifact exceeds configured size budget: $path ($bytes > $limit bytes)"
    return 1
  fi
}

android_artifacts_copy_one() {
  local source="$1"
  local destination="$2"
  local kind="$3"

  [[ -s "$source" ]] || return 1
  android_artifacts_validate_size "$kind" "$source" || return 1
  install -m 0640 "$source" "$destination"
}

android_artifacts_find_first() {
  local root="$1"
  local pattern="$2"
  find "$root" -maxdepth 1 -type f -name "$pattern" -print -quit 2>/dev/null || true
}

android_artifacts_write_manifest() {
  local job_id="$1"
  local mode="$2"
  local commit="$3"
  local created_at="$4"
  local staged_at="$5"
  local artifact_dir="$6"
  local signing_state="$7"
  local log_file="$8"

  local manifest="$artifact_dir/manifest.json"
  local entries=""
  local file filename kind bytes digest comma=""

  while IFS= read -r file; do
    filename="$(basename "$file")"
    case "$filename" in
      *.apk) kind="apk" ;;
      *.aab) kind="aab" ;;
      *native-symbols*.zip) kind="native-symbols" ;;
      *) continue ;;
    esac
    bytes="$(stat -c '%s' "$file")"
    digest="$(sha256sum "$file" | awk '{print $1}')"
    entries+="${comma}    {\"name\": \"$filename\", \"kind\": \"$kind\", \"bytes\": $bytes, \"sha256\": \"$digest\"}"
    comma=$',\n'
  done < <(find "$artifact_dir" -maxdepth 1 -type f \( -name '*.apk' -o -name '*.aab' -o -name '*native-symbols*.zip' \) | sort)

  local log_sha=""
  if [[ -s "$log_file" ]]; then
    log_sha="$(sha256sum "$log_file" | awk '{print $1}')"
  fi

  cat > "$manifest" <<EOF
{
  "schema_version": 1,
  "job_id": "$job_id",
  "mode": "$mode",
  "commit": "$commit",
  "created_at": "$created_at",
  "staged_at": "$staged_at",
  "signing_state": "$signing_state",
  "build_log_sha256": "$log_sha",
  "artifacts": [
$entries
  ]
}
EOF
  chmod 0640 "$manifest"

  (
    cd "$artifact_dir"
    local_checksum="$(mktemp .SHA256SUMS.XXXXXX)"
    find . -maxdepth 1 -type f ! -name SHA256SUMS.txt ! -name '.SHA256SUMS.*' -printf '%f\n' |
      sort |
      while IFS= read -r filename; do
        sha256sum "$filename"
      done > "$local_checksum"
    chmod 0640 "$local_checksum"
    mv "$local_checksum" SHA256SUMS.txt
  )
}

android_artifacts_stage() {
  local job_id="$1"
  local mode="$2"
  local commit="$3"
  local created_at="$4"
  local source_dir="$5"

  android_artifact_valid_job_id "$job_id" || {
    log_error "Invalid artifact job ID: $job_id"
    return 1
  }
  [[ "$mode" == "release" || "$mode" == "debug" ]] || {
    log_error "Invalid artifact build mode: $mode"
    return 1
  }
  [[ "$commit" =~ ^[a-f0-9]{40}$ ]] || {
    log_error "Invalid artifact commit SHA."
    return 1
  }

  android_artifacts_ensure_layout

  local final_dir staging_dir log_file staged_at short_commit
  final_dir="$(android_artifacts_job_dir "$job_id")"
  staging_dir="$NEXORA_ANDROID_ARTIFACT_ROOT/.staging-$job_id"
  log_file="$(android_artifacts_log_file "$job_id")"
  staged_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  short_commit="${commit:0:12}"

  rm -rf "$staging_dir"
  mkdir -m 0750 "$staging_dir"

  local apk aab symbols signing_state
  if [[ "$mode" == "release" ]]; then
    apk="$(android_artifacts_find_first "$source_dir/app/build/outputs/apk/release" '*.apk')"
    aab="$(android_artifacts_find_first "$source_dir/app/build/outputs/bundle/release" '*.aab')"
    [[ -n "$apk" && -n "$aab" ]] || {
      rm -rf "$staging_dir"
      log_error "Release build completed but APK/AAB outputs were not both found."
      return 1
    }

    android_artifacts_copy_one "$apk" "$staging_dir/NexoraGit-$short_commit-release-unsigned.apk" apk || {
      rm -rf "$staging_dir"
      return 1
    }
    android_artifacts_copy_one "$aab" "$staging_dir/NexoraGit-$short_commit-release-unsigned.aab" aab || {
      rm -rf "$staging_dir"
      return 1
    }
    signing_state="UNSIGNED_RELEASE"
  else
    apk="$(android_artifacts_find_first "$source_dir/app/build/outputs/apk/debug" '*.apk')"
    [[ -n "$apk" ]] || {
      rm -rf "$staging_dir"
      log_error "Debug build completed but APK output was not found."
      return 1
    }

    android_artifacts_copy_one "$apk" "$staging_dir/NexoraGit-$short_commit-debug.apk" apk || {
      rm -rf "$staging_dir"
      return 1
    }
    signing_state="DEBUG_DEFAULT"
  fi

  symbols="$(android_artifacts_find_first "$source_dir/app/build/outputs/native-debug-symbols/${mode}" '*.zip')"
  if [[ -n "$symbols" ]]; then
    install -m 0640 "$symbols" "$staging_dir/NexoraGit-$short_commit-$mode-native-symbols.zip"
  fi

  android_artifacts_write_manifest "$job_id" "$mode" "$commit" "$created_at" "$staged_at" "$staging_dir" "$signing_state" "$log_file"

  if [[ -e "$final_dir" ]]; then
    rm -rf "$staging_dir"
    log_error "Artifact destination already exists; refusing to overwrite: $final_dir"
    return 1
  fi

  mv "$staging_dir" "$final_dir"
  log_ok "Android artifacts staged atomically: $final_dir"
}

android_artifacts_verify() {
  local job_id="$1"
  android_artifact_valid_job_id "$job_id" || die "Invalid Android build job ID."

  local artifact_dir checksum_file
  artifact_dir="$(android_artifacts_job_dir "$job_id")"
  checksum_file="$artifact_dir/SHA256SUMS.txt"

  [[ -d "$artifact_dir" && -s "$checksum_file" && -s "$artifact_dir/manifest.json" ]] ||
    die "Artifacts are missing or incomplete for build: $job_id"

  if awk '{print $2}' "$checksum_file" | grep -Eq '(^/|\.\.|/)'; then
    die "Artifact checksum manifest contains unsafe paths."
  fi

  (
    cd "$artifact_dir"
    sha256sum --check --strict SHA256SUMS.txt >/dev/null
  ) || die "Artifact checksum verification failed: $job_id"

  log_ok "Artifact checksums verified: $job_id"
}

android_artifacts_show() {
  local job_id="$1"
  android_artifact_valid_job_id "$job_id" || die "Usage: nexora-git android artifacts <JOB_ID>"

  local artifact_dir
  artifact_dir="$(android_artifacts_job_dir "$job_id")"
  [[ -d "$artifact_dir" ]] || die "No staged artifacts for build: $job_id"

  printf 'Artifact directory: %s\n' "$artifact_dir"
  printf '\nFiles:\n'
  find "$artifact_dir" -maxdepth 1 -type f -printf '  %f  %s bytes\n' | sort
  printf '\nManifest:\n'
  cat "$artifact_dir/manifest.json"
}

android_artifacts_show_log() {
  local job_id="$1"
  local lines="${2:-200}"
  android_artifact_valid_job_id "$job_id" || die "Usage: nexora-git android log <JOB_ID> [LINES]"
  if [[ ! "$lines" =~ ^[0-9]+$ ]] || (( lines < 1 || lines > 5000 )); then
    die "Log line count must be between 1 and 5000."
  fi

  local log_file
  log_file="$(android_artifacts_log_file "$job_id")"
  [[ -r "$log_file" ]] || die "Build log not found: $job_id"
  tail -n "$lines" "$log_file"
}

android_artifacts_retention_candidates() {
  local keep="$1"
  local days="$2"
  local build_root="$NEXORA_ANDROID_STATE_ROOT/builds"
  [[ -d "$build_root" ]] || return 0

  local ids=()
  mapfile -t ids < <(
    find "$build_root" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' 2>/dev/null |
      grep -E '^[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}$' |
      sort -r
  )

  local index=0 id state_file status
  for id in "${ids[@]}"; do
    state_file="$build_root/$id/job.conf"
    [[ -r "$state_file" ]] || continue
    status="$(awk -F= '$1 == "STATUS" {print $2; exit}' "$state_file")"
    case "$status" in
      COMPLETED|FAILED|CANCELLED)
        index=$((index + 1))
        if (( index > keep )) && find "$state_file" -mtime "+$days" -print -quit | grep -q .; then
          printf '%s\n' "$id"
        fi
        ;;
    esac
  done
}

android_artifacts_apply_retention() {
  local dry_run="${1:-0}"
  local keep="${2:-$NEXORA_ANDROID_RETENTION_KEEP}"
  local days="${3:-$NEXORA_ANDROID_RETENTION_DAYS}"

  [[ "$keep" =~ ^[0-9]+$ ]] || die "Retention keep count must be numeric."
  [[ "$days" =~ ^[0-9]+$ ]] || die "Retention age must be numeric."
  (( keep >= 1 && keep <= 500 )) || die "Retention keep count must be between 1 and 500."
  (( days >= 1 && days <= 3650 )) || die "Retention age must be between 1 and 3650 days."

  local candidates=()
  mapfile -t candidates < <(android_artifacts_retention_candidates "$keep" "$days")

  if (( ${#candidates[@]} == 0 )); then
    printf 'No terminal Android builds qualify for retention cleanup.\n'
    return
  fi

  local id job_dir
  for id in "${candidates[@]}"; do
    printf '%s %s\n' "$([[ "$dry_run" == "1" ]] && printf WOULD_PRUNE || printf PRUNE)" "$id"
    if [[ "$dry_run" != "1" ]]; then
      job_dir="$NEXORA_ANDROID_STATE_ROOT/builds/$id"
      rm -rf "$job_dir/source"
      rm -rf "${NEXORA_ANDROID_ARTIFACT_ROOT:?}/$id"
      rm -f "${NEXORA_ANDROID_LOG_ROOT:?}/$id.log"
    fi
  done
}

android_artifacts_retention_status() {
  printf 'Keep newest terminal builds: %s\n' "$NEXORA_ANDROID_RETENTION_KEEP"
  printf 'Minimum age before pruning:  %s days\n' "$NEXORA_ANDROID_RETENTION_DAYS"
  printf 'Policy removes source/artifacts/logs only; job.conf history remains.\n'
  printf 'Eligible now: '
  android_artifacts_retention_candidates "$NEXORA_ANDROID_RETENTION_KEEP" "$NEXORA_ANDROID_RETENTION_DAYS" | wc -l
}

android_artifacts_doctor() {
  android_artifacts_ensure_layout
  local failed=0

  for path in "$NEXORA_ANDROID_ARTIFACT_ROOT" "$NEXORA_ANDROID_LOG_ROOT"; do
    if [[ -d "$path" ]]; then
      log_ok "Persistent Android output path available: $path"
    else
      log_error "Persistent Android output path missing: $path"
      failed=1
    fi
  done

  [[ "$NEXORA_ANDROID_RETENTION_KEEP" =~ ^[0-9]+$ ]] || failed=1
  [[ "$NEXORA_ANDROID_RETENTION_DAYS" =~ ^[0-9]+$ ]] || failed=1
  return "$failed"
}
