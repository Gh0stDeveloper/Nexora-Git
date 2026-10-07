#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"
# shellcheck source=android-build-lib.sh
source "$SCRIPT_DIR/android-build-lib.sh"
# shellcheck source=android-signing-lib.sh
source "$SCRIPT_DIR/android-signing-lib.sh"
# shellcheck source=android-worker-lib.sh
source "$SCRIPT_DIR/android-worker-lib.sh"
# shellcheck source=android-release-lib.sh
source "$SCRIPT_DIR/android-release-lib.sh"
# shellcheck source=android-ops-lib.sh
source "$SCRIPT_DIR/android-ops-lib.sh"
# shellcheck source=web-lib.sh
source "$SCRIPT_DIR/web-lib.sh"

RECONFIGURE=0
if [[ "${1:-}" == "--reconfigure" ]]; then
  RECONFIGURE=1
elif [[ -n "${1:-}" ]]; then
  die "Unknown option: $1"
fi

require_root
detect_supported_os
banner

TOTAL_PHASES=15
phase 1 "$TOTAL_PHASES" "Repository installation"

SOURCE_ROOT="$(git -C "$SCRIPT_DIR/../.." rev-parse --show-toplevel 2>/dev/null || true)"
INSTALL_DIR="$NEXORA_DEFAULT_INSTALL_DIR"
REPO_URL="$NEXORA_REPO_URL"
BRANCH="$NEXORA_BRANCH"

if [[ "$SOURCE_ROOT" != "$INSTALL_DIR" ]]; then
  if [[ -e "$INSTALL_DIR" && ! -d "$INSTALL_DIR/.git" ]]; then
    die "$INSTALL_DIR exists but is not a Git repository. Refusing to overwrite it."
  fi

  if [[ ! -d "$INSTALL_DIR/.git" ]]; then
    command -v git >/dev/null 2>&1 || apt_install_missing git ca-certificates
    log_info "Cloning Nexora Git into $INSTALL_DIR."
    git clone --branch "$BRANCH" --single-branch "$REPO_URL" "$INSTALL_DIR"
  else
    log_ok "Existing Nexora Git clone detected."
  fi

  exec "$INSTALL_DIR/scripts/vps/install.sh" "$@"
fi

EXISTING=0
if load_state 2>/dev/null && [[ -f "$INSTALL_DIR/auth-broker/.env" ]]; then
  EXISTING=1
  log_ok "Existing VPS configuration detected for $DOMAIN."
fi

phase 2 "$TOTAL_PHASES" "System dependencies"
apt_install_missing ca-certificates git curl nginx certbot python3-certbot-nginx iproute2 openssl unzip openjdk-17-jdk-headless util-linux
ensure_docker

if ! systemctl is-active --quiet nginx; then
  if port_busy 80 || port_busy 443; then
    log_error "Nginx is not active, but port 80 or 443 is already occupied."
    show_port_owner 80
    show_port_owner 443
    die "Free those ports or migrate the existing web server before continuing. Nothing was stopped automatically."
  fi
  systemctl enable --now nginx
fi
nginx -t >/dev/null
log_ok "Nginx is installed and healthy. Existing sites were left intact."

phase 3 "$TOTAL_PHASES" "Interactive configuration"
if (( EXISTING == 1 && RECONFIGURE == 0 )); then
  log_info "Reusing domain, email, local port and existing broker credentials."
else
  current_domain="${DOMAIN:-}"
  current_email="${EMAIL:-}"

  while true; do
    read -r -p "Domain for Nexora Git Auth Broker${current_domain:+ [$current_domain]}: " input_domain
    DOMAIN="${input_domain:-$current_domain}"
    DOMAIN="${DOMAIN,,}"
    valid_domain "$DOMAIN" && break
    log_warn "Enter a valid FQDN such as auth.example.com (no https:// and no path)."
  done

  while true; do
    read -r -p "Email for Let's Encrypt${current_email:+ [$current_email]}: " input_email
    EMAIL="${input_email:-$current_email}"
    valid_email "$EMAIL" && break
    log_warn "Enter a valid email address."
  done

  if [[ -z "${LOCAL_PORT:-}" ]] || (( RECONFIGURE == 1 && EXISTING == 0 )); then
    LOCAL_PORT="$(find_free_port 18080 18180)" || die "No free local broker port found in 18080-18180."
  fi
  log_ok "Local broker port: 127.0.0.1:$LOCAL_PORT"

  WEB_PORT="$(web_env_value WEB_PORT 2>/dev/null || true)"
  if [[ ! "$WEB_PORT" =~ ^[0-9]+$ ]] || (( WEB_PORT < 1024 || WEB_PORT > 65535 )); then
    WEB_PORT="$(find_free_port 18181 18280)" || die "No free local website port found in 18181-18280."
  fi
  SITE_URL="https://$DOMAIN"
  log_ok "Local website port: 127.0.0.1:$WEB_PORT"

  read -r -p "GitHub App Client ID: " GITHUB_APP_CLIENT_ID
  [[ -n "$GITHUB_APP_CLIENT_ID" ]] || die "GitHub App Client ID is required."

  read -r -s -p "GitHub App Client Secret (hidden): " GITHUB_APP_CLIENT_SECRET
  printf '\n'
  [[ -n "$GITHUB_APP_CLIENT_SECRET" ]] || die "GitHub App Client Secret is required."
  [[ "$GITHUB_APP_CLIENT_SECRET" != *$'\n'* ]] || die "Client secret must be a single line."

  CALLBACK_URL="https://$DOMAIN/oauth/callback"
  umask 077
  cat > "$INSTALL_DIR/auth-broker/.env" <<EOF
GITHUB_APP_CLIENT_ID=$GITHUB_APP_CLIENT_ID
GITHUB_APP_CLIENT_SECRET=$GITHUB_APP_CLIENT_SECRET
GITHUB_CALLBACK_URL=$CALLBACK_URL
APP_CALLBACK_URI=nexoragit://oauth/callback
PORT=$LOCAL_PORT
WEB_PORT=$WEB_PORT
SITE_URL=$SITE_URL
EOF
  chmod 600 "$INSTALL_DIR/auth-broker/.env"

  REPO_URL="$NEXORA_REPO_URL"
  BRANCH="$NEXORA_BRANCH"
  write_state
  unset GITHUB_APP_CLIENT_SECRET
  log_ok "Broker secrets stored outside Git in a root-readable .env file."
fi

web_ensure_config
web_ensure_layout

phase 4 "$TOTAL_PHASES" "Auth Broker and download website containers"
if port_busy "$LOCAL_PORT"; then
  if compose ps 2>/dev/null | grep -Eq 'auth-broker.*(Up|running)'; then
    log_info "Broker port is already owned by the existing Nexora container; reusing it."
  else
    show_port_owner "$LOCAL_PORT"
    die "Configured local port $LOCAL_PORT is occupied by another process. Run: nexora-git reconfigure"
  fi
fi

compose build auth-broker
web_deploy 1
wait_local_health "$LOCAL_PORT" 30 || {
  compose logs --tail=80 auth-broker || true
  die "Auth Broker did not pass its local health check."
}
log_ok "Auth Broker is healthy on 127.0.0.1:$LOCAL_PORT."

phase 5 "$TOTAL_PHASES" "Nginx website and Auth Broker routing"
NGINX_AVAILABLE="/etc/nginx/sites-available/nexora-git-auth.conf"
NGINX_ENABLED="/etc/nginx/sites-enabled/nexora-git-auth.conf"

conflicts=""
while IFS= read -r candidate; do
  [[ -z "$candidate" ]] && continue
  [[ "$(readlink -f "$candidate" 2>/dev/null || printf '%s' "$candidate")" == "$(readlink -f "$NGINX_AVAILABLE" 2>/dev/null || printf '%s' "$NGINX_AVAILABLE")" ]] && continue
  conflicts+="$candidate"$'\n'
done < <(grep -RslF "server_name $DOMAIN;" /etc/nginx/sites-enabled 2>/dev/null || true)

if [[ -n "$conflicts" ]]; then
  printf '%s' "$conflicts" >&2
  die "Another enabled Nginx site already owns $DOMAIN. No configuration was overwritten."
fi

if [[ -f "$NEXORA_WEB_NGINX_CONFIG" ]]; then
  cp -a "$NEXORA_WEB_NGINX_CONFIG" "${NEXORA_WEB_NGINX_CONFIG}.bak.$(date +%Y%m%d%H%M%S)"
fi

if [[ -r "/etc/letsencrypt/live/$DOMAIN/fullchain.pem" && -r "/etc/letsencrypt/live/$DOMAIN/privkey.pem" ]]; then
  web_render_nginx_https "$NEXORA_WEB_NGINX_CONFIG"
else
  web_render_nginx_http "$NEXORA_WEB_NGINX_CONFIG"
fi

ln -sfn "$NEXORA_WEB_NGINX_CONFIG" "$NEXORA_WEB_NGINX_ENABLED"
nginx -t
systemctl reload nginx
log_ok "Nginx routes website traffic and preserves dedicated OAuth/API broker paths."

phase 6 "$TOTAL_PHASES" "HTTPS certificate"
if [[ -r "/etc/letsencrypt/live/$DOMAIN/fullchain.pem" && -r "/etc/letsencrypt/live/$DOMAIN/privkey.pem" ]]; then
  log_ok "Existing Let's Encrypt certificate detected; issuance skipped."
else
  log_info "Requesting Let's Encrypt certificate for $DOMAIN."
  certbot certonly --nginx \
    --non-interactive \
    --agree-tos \
    --no-eff-email \
    --email "$EMAIL" \
    -d "$DOMAIN"
fi

web_render_nginx_https "$NEXORA_WEB_NGINX_CONFIG"
if systemctl list-unit-files certbot.timer >/dev/null 2>&1; then
  systemctl enable --now certbot.timer >/dev/null || true
fi
nginx -t
systemctl reload nginx
log_ok "HTTPS vhost is active with deterministic TLS configuration."

phase 7 "$TOTAL_PHASES" "Firewall and command installation"
if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -q '^Status: active'; then
  ufw allow 'Nginx Full' >/dev/null
  log_ok "Active UFW detected; Nginx HTTP/HTTPS profile allowed."
else
  log_info "UFW is not active; firewall policy was not changed."
fi

ln -sfn "$INSTALL_DIR/scripts/vps/nexora-git" /usr/local/bin/nexora-git
chmod +x \
  "$INSTALL_DIR/scripts/vps/nexora-git" \
  "$INSTALL_DIR/scripts/vps/update.sh" \
  "$INSTALL_DIR/scripts/vps/install.sh" \
  "$INSTALL_DIR/scripts/vps/test-android-build-lib.sh" \
  "$INSTALL_DIR/scripts/vps/test-android-signing-lib.sh" \
  "$INSTALL_DIR/scripts/vps/android-worker.sh" \
  "$INSTALL_DIR/scripts/vps/test-android-worker-lib.sh" \
  "$INSTALL_DIR/scripts/vps/test-android-artifacts-lib.sh" \
  "$INSTALL_DIR/scripts/vps/test-android-github-lib.sh" \
  "$INSTALL_DIR/scripts/vps/test-android-release-integration.sh" \
  "$INSTALL_DIR/scripts/vps/test-android-ops-lib.sh" \
  "$INSTALL_DIR/scripts/vps/test-web-lib.sh"
log_ok "Installed command: nexora-git"

phase 8 "$TOTAL_PHASES" "Android build foundation"
ensure_android_foundation

phase 9 "$TOTAL_PHASES" "Android Signing Vault"
ensure_android_signing_vault

phase 10 "$TOTAL_PHASES" "Android background build worker"
android_worker_install_service

phase 11 "$TOTAL_PHASES" "Android artifact lifecycle"
android_artifacts_ensure_layout
android_artifacts_doctor

phase 12 "$TOTAL_PHASES" "GitHub release signing and parity foundation"
android_release_doctor

phase 13 "$TOTAL_PHASES" "Autobuild, recovery and operational hardening"
android_ops_setup
android_ops_doctor

phase 14 "$TOTAL_PHASES" "Website deployment verification"
web_doctor

phase 15 "$TOTAL_PHASES" "Final verification"
curl -fsS --max-time 10 "https://$DOMAIN/health" >/dev/null || die "Public Auth Broker HTTPS health check failed."
curl -fsS --max-time 10 "https://$DOMAIN/api/health" >/dev/null || die "Public website HTTPS health check failed."
compose ps
printf '\n'
log_ok "Nexora Git Auth Broker VPS installation completed."
printf 'Domain:      https://%s\n' "$DOMAIN"
printf 'Callback:    https://%s/oauth/callback\n' "$DOMAIN"
printf 'Broker port: 127.0.0.1:%s\n' "$LOCAL_PORT"
printf 'Web port:    127.0.0.1:%s\n' "$WEB_PORT"
printf 'Repository:  %s (%s)\n' "$INSTALL_DIR" "$BRANCH"
printf '\nUseful commands:\n'
printf '  nexora-git status\n'
printf '  nexora-git update\n'
printf '  nexora-git doctor\n'
printf '  nexora-git android status\n'
printf '  nexora-git android doctor\n'
printf '  nexora-git android signing status\n'
printf '  nexora-git android signing fingerprint\n'
printf '  nexora-git android signing backup\n'
printf '  nexora-git android build release\n'
printf '  nexora-git android builds\n'
printf '  nexora-git android worker status\n'
printf '  nexora-git android retention status\n'
printf '  nexora-git github status\n'
printf '  nexora-git github secrets export\n'
printf '  nexora-git android autobuild status\n'
printf '  nexora-git recovery status\n'
printf '  nexora-git web status\n'
printf '  nexora-git web logs\n'
printf '  nexora-git logs\n'
