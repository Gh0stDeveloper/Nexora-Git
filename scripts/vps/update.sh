#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

require_root
load_state || die "Nexora Git VPS is not installed. Run the installer first."

banner
phase 1 5 "Preflight"
for command in git docker curl nginx; do
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

phase 2 5 "Fetch update"
git fetch --prune origin "$BRANCH"
REMOTE_COMMIT="$(git rev-parse "origin/$BRANCH")"
if [[ "$OLD_COMMIT" == "$REMOTE_COMMIT" ]]; then
  log_ok "Already on the newest $BRANCH revision."
else
  git merge --ff-only "origin/$BRANCH"
  log_ok "Repository advanced to $REMOTE_COMMIT."
fi

phase 3 5 "Build changed service"
if ! compose build auth-broker; then
  log_error "New broker build failed; restoring $OLD_COMMIT."
  git reset --hard "$OLD_COMMIT"
  compose build auth-broker
  compose up -d --remove-orphans auth-broker
  die "Update failed and the previous revision was restored."
fi

compose up -d --remove-orphans auth-broker

phase 4 5 "Health gate"
if ! wait_local_health "$LOCAL_PORT" 30; then
  log_error "New revision failed local health check; rolling back."
  compose logs --tail=100 auth-broker || true
  git reset --hard "$OLD_COMMIT"
  compose build auth-broker
  compose up -d --remove-orphans auth-broker

  if wait_local_health "$LOCAL_PORT" 30; then
    die "Update was rolled back successfully to $OLD_COMMIT."
  fi
  die "Update failed and rollback health check also failed. Run: nexora-git doctor"
fi

curl -fsS --max-time 10 "https://$DOMAIN/health" >/dev/null || {
  log_warn "Local broker is healthy but public HTTPS health check failed."
  log_warn "The update is kept because the application itself is healthy; inspect DNS/Nginx/TLS with nexora-git doctor."
}

phase 5 5 "Finalize"
NEW_COMMIT="$(git rev-parse HEAD)"
log_ok "Update complete."
printf 'Previous: %s\nCurrent:  %s\n' "$OLD_COMMIT" "$NEW_COMMIT"
compose ps
