#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
if ! declare -F log_info >/dev/null 2>&1; then
  # shellcheck source=lib.sh
  source "$SCRIPT_DIR/lib.sh"
fi

NEXORA_WEB_ROOT="${NEXORA_WEB_ROOT:-/var/lib/nexora-git/web}"
NEXORA_WEB_RELEASE_ROOT="${NEXORA_WEB_RELEASE_ROOT:-$NEXORA_WEB_ROOT/releases}"
NEXORA_WEB_RELEASE_KEEP="${NEXORA_WEB_RELEASE_KEEP:-5}"
NEXORA_WEB_NGINX_CONFIG="${NEXORA_WEB_NGINX_CONFIG:-/etc/nginx/sites-available/nexora-git-auth.conf}"
NEXORA_WEB_NGINX_ENABLED="${NEXORA_WEB_NGINX_ENABLED:-/etc/nginx/sites-enabled/nexora-git-auth.conf}"

WEB_PORT="${WEB_PORT:-}"
SITE_URL="${SITE_URL:-}"

web_env_file() {
  printf '%s/auth-broker/.env\n' "${INSTALL_DIR:-$NEXORA_DEFAULT_INSTALL_DIR}"
}

web_env_value() {
  local key="$1"
  local file
  file="$(web_env_file)"
  [[ -r "$file" ]] || return 1
  awk -F= -v key="$key" '$1 == key {sub(/^[^=]*=/, ""); print; exit}' "$file"
}

web_upsert_env_value() {
  local key="$1"
  local value="$2"
  local file dir temp
  file="$(web_env_file)"
  dir="$(dirname "$file")"
  [[ -f "$file" ]] || die "Auth Broker environment does not exist: $file"
  [[ "$key" =~ ^[A-Z0-9_]+$ ]] || die "Invalid environment key."
  [[ "$value" != *$'\n'* ]] || die "Environment value must be a single line."

  temp="$(mktemp "$dir/.env.web.XXXXXX")"
  awk -F= -v key="$key" -v value="$value" '
    BEGIN { replaced = 0 }
    $1 == key {
      if (!replaced) {
        print key "=" value
        replaced = 1
      }
      next
    }
    { print }
    END {
      if (!replaced) print key "=" value
    }
  ' "$file" > "$temp"
  chmod 0600 "$temp"
  mv -f "$temp" "$file"
}

web_configure_fresh_values() {
  local existing_port=""
  if [[ -f "$(web_env_file)" ]]; then
    existing_port="$(web_env_value WEB_PORT 2>/dev/null || true)"
  fi

  if [[ "$existing_port" =~ ^[0-9]+$ ]] && (( existing_port >= 1024 && existing_port <= 65535 )); then
    WEB_PORT="$existing_port"
  elif [[ -n "${WEB_PORT:-}" && "$WEB_PORT" =~ ^[0-9]+$ ]] && (( WEB_PORT >= 1024 && WEB_PORT <= 65535 )); then
    :
  else
    WEB_PORT="$(find_free_port 18181 18280)" || die "No free local web port found in 18181-18280."
  fi
  SITE_URL="https://$DOMAIN"
}

web_ensure_config() {
  [[ -n "${DOMAIN:-}" ]] || die "DOMAIN must be loaded before website configuration."
  web_configure_fresh_values

  web_upsert_env_value WEB_PORT "$WEB_PORT"
  web_upsert_env_value SITE_URL "$SITE_URL"

  local stored_port stored_site
  stored_port="$(web_env_value WEB_PORT)"
  stored_site="$(web_env_value SITE_URL)"
  [[ "$stored_port" == "$WEB_PORT" && "$stored_site" == "$SITE_URL" ]] ||
    die "Website environment persistence failed."
}

web_ensure_layout() {
  install -d -m 0755 "$NEXORA_WEB_ROOT" "$NEXORA_WEB_RELEASE_ROOT"
}

web_wait_health() {
  local attempts="${1:-45}"
  local i
  for ((i=1; i<=attempts; i++)); do
    if curl -fsS --max-time 3 "http://127.0.0.1:$WEB_PORT/api/health" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  return 1
}

web_image_exists() {
  local image
  image="$(compose images -q web 2>/dev/null | head -n 1 || true)"
  [[ -n "$image" ]]
}

web_source_changed() {
  local old_commit="$1"
  local new_commit="$2"
  ! git -C "$INSTALL_DIR" diff --quiet "$old_commit" "$new_commit" -- web auth-broker/compose.yaml
}

web_deploy() {
  local rebuild="${1:-0}"
  web_ensure_config
  web_ensure_layout

  if [[ "$rebuild" == "1" ]] || ! web_image_exists; then
    log_info "Building Nexora Git download website."
    compose build web
  else
    log_ok "Website source unchanged and image available; Docker rebuild skipped."
  fi

  compose up -d --remove-orphans auth-broker web
  web_wait_health 45 || {
    compose logs --tail=120 web || true
    die "Nexora Git website failed its local health check."
  }
  log_ok "Nexora Git website healthy on 127.0.0.1:$WEB_PORT."
}

web_proxy_block() {
  local upstream="$1"
  cat <<EOF
        proxy_pass http://127.0.0.1:$upstream;
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_connect_timeout 5s;
        proxy_read_timeout 60s;
        proxy_send_timeout 60s;
EOF
}

web_render_rate_limit_zones() {
  cat <<'EOF'
limit_req_zone $binary_remote_addr zone=nexora_oauth_callback:10m rate=30r/m;
limit_req_zone $binary_remote_addr zone=nexora_oauth_sensitive:10m rate=10r/m;
EOF
}

web_render_rate_limited_response() {
  cat <<'EOF'
    location @nexora_oauth_rate_limited {
        default_type application/json;
        add_header Cache-Control "no-store" always;
        add_header Retry-After "60" always;
        return 429 '{"error":"rate_limited"}';
    }
EOF
}

web_render_nginx_http() {
  local destination="$1"
  cat > "$destination" <<EOF
$(web_render_rate_limit_zones)

server {
    listen 80;
    listen [::]:80;
    server_name $DOMAIN;

    client_max_body_size 1m;

    location = /health {
$(web_proxy_block "$LOCAL_PORT")
    }

    location = /oauth/callback {
        limit_req zone=nexora_oauth_callback burst=10 nodelay;
        limit_req_status 429;
        error_page 429 = @nexora_oauth_rate_limited;
$(web_proxy_block "$LOCAL_PORT")
    }

    location ^~ /v1/oauth/ {
        limit_req zone=nexora_oauth_sensitive burst=5 nodelay;
        limit_req_status 429;
        error_page 429 = @nexora_oauth_rate_limited;
$(web_proxy_block "$LOCAL_PORT")
    }

$(web_render_rate_limited_response)

    location = /download/nexora-git.apk {
        alias $NEXORA_WEB_RELEASE_ROOT/current/Nexora-Git.apk;
        default_type application/vnd.android.package-archive;
        add_header Content-Disposition 'attachment; filename="Nexora-Git.apk"' always;
        add_header Cache-Control "no-store" always;
        add_header X-Content-Type-Options "nosniff" always;
    }

    location / {
$(web_proxy_block "$WEB_PORT")
    }
}
EOF
}

web_render_nginx_https() {
  local destination="$1"
  cat > "$destination" <<EOF
server {
    listen 80;
    listen [::]:80;
    server_name $DOMAIN;
    return 301 https://\$host\$request_uri;
}

server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name $DOMAIN;

    ssl_certificate /etc/letsencrypt/live/$DOMAIN/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/$DOMAIN/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_session_cache shared:NexoraGitSSL:10m;
    ssl_session_timeout 1d;
    ssl_session_tickets off;

    add_header Strict-Transport-Security "max-age=31536000" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-Frame-Options "DENY" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;
    add_header Permissions-Policy "camera=(), microphone=(), geolocation=()" always;

    client_max_body_size 1m;

    location = /health {
$(web_proxy_block "$LOCAL_PORT")
    }

    location = /oauth/callback {
        limit_req zone=nexora_oauth_callback burst=10 nodelay;
        limit_req_status 429;
        error_page 429 = @nexora_oauth_rate_limited;
$(web_proxy_block "$LOCAL_PORT")
    }

    location ^~ /v1/oauth/ {
        limit_req zone=nexora_oauth_sensitive burst=5 nodelay;
        limit_req_status 429;
        error_page 429 = @nexora_oauth_rate_limited;
$(web_proxy_block "$LOCAL_PORT")
    }

$(web_render_rate_limited_response)

    location = /download/nexora-git.apk {
        alias $NEXORA_WEB_RELEASE_ROOT/current/Nexora-Git.apk;
        default_type application/vnd.android.package-archive;
        add_header Content-Disposition 'attachment; filename="Nexora-Git.apk"' always;
        add_header Cache-Control "no-store" always;
        add_header X-Content-Type-Options "nosniff" always;
    }

    location / {
$(web_proxy_block "$WEB_PORT")
    }
}
EOF
}

web_refresh_nginx() {
  web_ensure_config
  local temp
  temp="$(mktemp)"

  if [[ -r "/etc/letsencrypt/live/$DOMAIN/fullchain.pem" && -r "/etc/letsencrypt/live/$DOMAIN/privkey.pem" ]]; then
    web_render_nginx_https "$temp"
  else
    web_render_nginx_http "$temp"
  fi

  if [[ -f "$NEXORA_WEB_NGINX_CONFIG" ]] && ! cmp -s "$temp" "$NEXORA_WEB_NGINX_CONFIG"; then
    cp -a "$NEXORA_WEB_NGINX_CONFIG" "${NEXORA_WEB_NGINX_CONFIG}.bak.$(date +%Y%m%d%H%M%S)"
  fi
  install -m 0644 "$temp" "$NEXORA_WEB_NGINX_CONFIG"
  rm -f "$temp"
  ln -sfn "$NEXORA_WEB_NGINX_CONFIG" "$NEXORA_WEB_NGINX_ENABLED"
  nginx -t >/dev/null
  systemctl reload nginx
}

web_release_version_info() {
  local job_id="$1"
  local gradle="$NEXORA_ANDROID_STATE_ROOT/builds/$job_id/source/app/build.gradle.kts"
  [[ -r "$gradle" ]] || return 1

  local version_name version_code
  version_name="$(sed -n 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' "$gradle" | head -n 1)"
  version_code="$(sed -n 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*\([0-9][0-9]*\).*/\1/p' "$gradle" | head -n 1)"
  [[ "$version_name" =~ ^[0-9A-Za-z._+-]{1,64}$ && "$version_code" =~ ^[0-9]+$ ]] || return 1
  printf '%s|%s\n' "$version_name" "$version_code"
}

web_prune_releases() {
  local keep="${1:-$NEXORA_WEB_RELEASE_KEEP}"
  if [[ ! "$keep" =~ ^[0-9]+$ ]] || (( keep < 1 || keep > 50 )); then
    die "Website release retention must be between 1 and 50."
  fi

  local current=""
  if [[ -L "$NEXORA_WEB_RELEASE_ROOT/current" ]]; then
    current="$(basename "$(readlink "$NEXORA_WEB_RELEASE_ROOT/current")")"
  fi

  local ids=()
  mapfile -t ids < <(
    find "$NEXORA_WEB_RELEASE_ROOT" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' 2>/dev/null |
      grep -E '^[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}$' |
      sort -r
  )

  local kept=0 id
  for id in "${ids[@]}"; do
    if [[ "$id" == "$current" ]] || (( kept < keep )); then
      kept=$((kept + 1))
      continue
    fi
    rm -rf "${NEXORA_WEB_RELEASE_ROOT:?}/$id"
  done
}

web_publish_signed_release() {
  local job_id="$1"
  local signed_dir="$2"
  android_artifact_valid_job_id "$job_id" || die "Invalid build ID for website publication."
  [[ -d "$signed_dir" ]] || die "Signed artifact directory is missing: $signed_dir"
  web_ensure_layout

  local apk
  apk="$(find "$signed_dir" -maxdepth 1 -type f -name '*-signed.apk' -print -quit)"
  [[ -s "$apk" ]] || die "Signed APK is missing for website publication."

  local state_file="$NEXORA_ANDROID_STATE_ROOT/builds/$job_id/job.conf"
  [[ -r "$state_file" ]] || die "Build state missing for website publication."
  local commit
  commit="$(awk -F= '$1=="COMMIT"{print $2; exit}' "$state_file")"
  [[ "$commit" =~ ^[a-f0-9]{40}$ ]] || die "Build commit is invalid."

  local version_info version_name version_code
  version_info="$(web_release_version_info "$job_id")" || die "Cannot determine Android version for website publication."
  IFS='|' read -r version_name version_code <<< "$version_info"

  local signed_at
  signed_at="$(sed -n 's/^SIGNED_AT=//p' "$signed_dir/signing-manifest.conf" | tr -d "'" | head -n 1)"
  [[ "$signed_at" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]] ||
    die "Signed release timestamp is invalid."

  local sha size final stage
  sha="$(sha256sum "$apk" | awk '{print $1}')"
  size="$(stat -c '%s' "$apk")"
  final="$NEXORA_WEB_RELEASE_ROOT/$job_id"

  if [[ -d "$final" ]]; then
    [[ -s "$final/Nexora-Git.apk" && "$(sha256sum "$final/Nexora-Git.apk" | awk '{print $1}')" == "$sha" ]] ||
      die "Published website release exists with different APK content: $job_id"
  else
    stage="$(mktemp -d "$NEXORA_WEB_RELEASE_ROOT/.staging-$job_id.XXXXXX")"
    chmod 0755 "$stage"
    install -m 0644 "$apk" "$stage/Nexora-Git.apk"
    cat > "$stage/latest.json" <<EOF
{
  "versionName": "$version_name",
  "versionCode": $version_code,
  "jobId": "$job_id",
  "commit": "$commit",
  "signedAt": "$signed_at",
  "sha256": "$sha",
  "sizeBytes": $size,
  "downloadUrl": "/download/nexora-git.apk"
}
EOF
    chmod 0644 "$stage/latest.json"
    mv "$stage" "$final"
  fi

  ln -sfn "$job_id" "$NEXORA_WEB_RELEASE_ROOT/current"
  web_prune_releases "$NEXORA_WEB_RELEASE_KEEP"
  log_ok "Published signed APK to the Nexora Git download website: $job_id"
}

web_status() {
  load_state || die "Nexora Git VPS is not installed."
  web_ensure_config

  printf 'Website:      https://%s/\n' "$DOMAIN"
  printf 'Local port:   127.0.0.1:%s\n' "$WEB_PORT"
  local container_state="stopped"
  local health_state="failed"
  if compose ps --status running --services 2>/dev/null | grep -qx web; then
    container_state="running"
  fi
  if web_wait_health 1; then
    health_state="ok"
  fi
  printf 'Container:    %s\n' "$container_state"
  printf 'Local health: %s\n' "$health_state"
  if [[ -L "$NEXORA_WEB_RELEASE_ROOT/current" ]]; then
    printf 'Latest APK:   %s\n' "$(basename "$(readlink "$NEXORA_WEB_RELEASE_ROOT/current")")"
  else
    printf 'Latest APK:   not published\n'
  fi
}

web_doctor() {
  local failed=0
  web_ensure_config
  web_ensure_layout

  if web_wait_health 3; then
    log_ok "Website local health OK"
  else
    log_error "Website local health failed"
    failed=1
  fi

  if curl -fsS --max-time 10 "https://$DOMAIN/" >/dev/null 2>&1; then
    log_ok "Website public HTTPS root OK"
  else
    log_error "Website public HTTPS root failed"
    failed=1
  fi

  if grep -q 'location = /oauth/callback' "$NEXORA_WEB_NGINX_CONFIG" 2>/dev/null &&
     grep -q 'location ^~ /v1/oauth/' "$NEXORA_WEB_NGINX_CONFIG" 2>/dev/null &&
     grep -q 'location = /download/nexora-git.apk' "$NEXORA_WEB_NGINX_CONFIG" 2>/dev/null; then
    log_ok "Nginx website/Auth Broker route partition present"
  else
    log_error "Nginx website/Auth Broker route partition incomplete"
    failed=1
  fi

  if [[ -L "$NEXORA_WEB_RELEASE_ROOT/current" ]]; then
    local current_apk="$NEXORA_WEB_RELEASE_ROOT/current/Nexora-Git.apk"
    if [[ -s "$current_apk" ]]; then
      log_ok "Published signed APK available"
    else
      log_error "Website release pointer exists but APK is missing"
      failed=1
    fi
  else
    log_warn "No signed APK published to the website yet."
  fi

  return "$failed"
}
