#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="${NEXORA_UPDATE_SOURCE_DIR:-$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)}"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"
# shellcheck source=android-build-lib.sh
source "$SCRIPT_DIR/android-build-lib.sh"
# shellcheck source=android-artifacts-lib.sh
source "$SCRIPT_DIR/android-artifacts-lib.sh"
# shellcheck source=android-worker-lib.sh
source "$SCRIPT_DIR/android-worker-lib.sh"
# shellcheck source=android-ops-lib.sh
source "$SCRIPT_DIR/android-ops-lib.sh"

require_root
load_state || die "Nexora Git VPS is not installed. Run the installer first."
android_ops_ensure_layout
android_ops_ensure_autobuild_config

exec 8>"$NEXORA_UPDATE_LOCK"
flock -n 8 || die "Another Nexora Git update is already running."

banner
phase 1 6 "Preflight and update lock"
for command in git docker curl nginx flock; do
  command -v "$command" >/dev/null 2>&1 || die "Required command disappeared: $command"
done
docker info >/dev/null 2>&1 || die "Docker daemon is unavailable."
nginx -t >/dev/null || die "Current Nginx configuration is invalid."

cd "$INSTALL_DIR"
if ! git diff --quiet || ! git diff --cached --quiet; then
  die "Tracked local repository changes detected. Update aborted to avoid overwriting them."
fi

OLD_COMMIT="$(git rev-parse HEAD)"
log_info "Current revision: $OLD_COMMIT"

phase 2 6 "Fetch and compare"
git fetch --prune origin "$BRANCH"
REMOTE_COMMIT="$(git rev-parse "origin/$BRANCH")"
if [[ "$OLD_COMMIT" == "$REMOTE_COMMIT" ]]; then
  log_ok "Already on the newest $BRANCH revision; broker rebuild and Android autobuild are skipped."
  android_ops_refresh_runtime
  exit 0
fi

phase 3 6 "Checkpoint and fast-forward"
CHECKPOINT_ID="$(android_ops_checkpoint_create "pre-update-$OLD_COMMIT" "$OLD_COMMIT")"
log_ok "Operational checkpoint: $CHECKPOINT_ID"

UPDATE_ROLLBACK_ARMED=1
update_exit() {
  local rc=$?
  trap - EXIT INT TERM
  if (( rc != 0 && UPDATE_ROLLBACK_ARMED == 1 )); then
    log_warn "Update exited unsuccessfully; attempting automatic rollback."
    if android_ops_rollback_update "$CHECKPOINT_ID" "$OLD_COMMIT"; then
      log_ok "Automatic rollback completed."
    else
      log_error "Automatic rollback could not fully restore service health. Run: nexora-git recovery restore $CHECKPOINT_ID"
    fi
  fi
  exit "$rc"
}
trap update_exit EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

git merge --ff-only "origin/$BRANCH"
NEW_COMMIT="$(git rev-parse HEAD)"
[[ "$NEW_COMMIT" == "$REMOTE_COMMIT" ]] || die "Fast-forward result does not match fetched remote commit."
log_ok "Repository advanced to $NEW_COMMIT."

phase 4 6 "Build updated Auth Broker"
compose build auth-broker
compose up -d --remove-orphans auth-broker

phase 5 6 "Health and runtime refresh"
if ! wait_local_health "$LOCAL_PORT" 30; then
  compose logs --tail=100 auth-broker || true
  die "Updated Auth Broker failed its local health gate."
fi

android_ops_refresh_runtime
nginx -t >/dev/null

curl -fsS --max-time 10 "https://$DOMAIN/health" >/dev/null || {
  log_warn "Local broker is healthy but public HTTPS health check failed."
  log_warn "The update is kept because the application itself is healthy; inspect DNS/Nginx/TLS with nexora-git doctor."
}

UPDATE_ROLLBACK_ARMED=0

phase 6 6 "Autobuild and finalize"
if ! android_ops_autobuild_schedule "$NEW_COMMIT"; then
  log_warn "The update is healthy, but Android autobuild scheduling failed. Run: nexora-git android build release $NEW_COMMIT"
fi

log_ok "Update complete."
printf 'Previous:   %s\nCurrent:    %s\nCheckpoint: %s\n' "$OLD_COMMIT" "$NEW_COMMIT" "$CHECKPOINT_ID"
android_ops_autobuild_status
compose ps
