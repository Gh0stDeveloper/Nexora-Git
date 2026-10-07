#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
TEMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEMP_ROOT"' EXIT

export INSTALL_DIR="$TEMP_ROOT/repo"
export DOMAIN="nexora.example.com"
export LOCAL_PORT="18080"
export NEXORA_WEB_ROOT="$TEMP_ROOT/web-state"
export NEXORA_WEB_RELEASE_ROOT="$NEXORA_WEB_ROOT/releases"
export NEXORA_WEB_NGINX_CONFIG="$TEMP_ROOT/nginx.conf"
export NEXORA_WEB_NGINX_ENABLED="$TEMP_ROOT/nginx-enabled.conf"
export NEXORA_ANDROID_STATE_ROOT="$TEMP_ROOT/android"
mkdir -p "$INSTALL_DIR/auth-broker"

cat > "$INSTALL_DIR/auth-broker/.env" <<'EOF'
GITHUB_APP_CLIENT_ID=Iv1.test
GITHUB_APP_CLIENT_SECRET=super-secret-must-survive
GITHUB_CALLBACK_URL=https://nexora.example.com/oauth/callback
APP_CALLBACK_URI=https://nexora.example.com/oauth/android/callback
PORT=18080
WEB_PORT=18181
SITE_URL=https://old.example.com
EOF
chmod 0600 "$INSTALL_DIR/auth-broker/.env"

# shellcheck source=web-lib.sh
source "$SCRIPT_DIR/web-lib.sh"

web_ensure_config
[[ "$(web_env_value WEB_PORT)" == "18181" ]]
[[ "$(web_env_value SITE_URL)" == "https://nexora.example.com" ]]
grep -q '^GITHUB_APP_CLIENT_SECRET=super-secret-must-survive$' "$INSTALL_DIR/auth-broker/.env"

web_render_nginx_https "$TEMP_ROOT/rendered.conf"
grep -q 'location = /health' "$TEMP_ROOT/rendered.conf"
grep -q 'location = /oauth/callback' "$TEMP_ROOT/rendered.conf"
grep -q 'location ^~ /v1/oauth/' "$TEMP_ROOT/rendered.conf"
grep -q 'location = /download/nexora-git.apk' "$TEMP_ROOT/rendered.conf"
grep -q 'proxy_pass http://127.0.0.1:18181;' "$TEMP_ROOT/rendered.conf"
grep -q 'proxy_pass http://127.0.0.1:18080;' "$TEMP_ROOT/rendered.conf"
grep -q "limit_req_zone \\$binary_remote_addr zone=nexora_oauth_callback:10m rate=30r/m;" "$TEMP_ROOT/rendered.conf"
grep -q "limit_req_zone \\$binary_remote_addr zone=nexora_oauth_sensitive:10m rate=10r/m;" "$TEMP_ROOT/rendered.conf"
grep -q 'limit_req zone=nexora_oauth_callback burst=10 nodelay;' "$TEMP_ROOT/rendered.conf"
grep -q 'limit_req zone=nexora_oauth_sensitive burst=5 nodelay;' "$TEMP_ROOT/rendered.conf"
grep -q 'location @nexora_oauth_rate_limited' "$TEMP_ROOT/rendered.conf"
grep -q 'Retry-After "60"' "$TEMP_ROOT/rendered.conf"

job="20261007T040000Z-a1b2c3d4"
commit="0123456789abcdef0123456789abcdef01234567"
signed="$TEMP_ROOT/signed"
mkdir -p "$signed" "$NEXORA_ANDROID_STATE_ROOT/builds/$job/source/app"
printf 'signed-apk-fixture' > "$signed/$job-signed.apk"
cat > "$signed/signing-manifest.conf" <<EOF
JOB_ID=$job
SIGNED_AT=2026-10-07T04:00:00Z
SIGNED_APK=$job-signed.apk
SIGNED_AAB=$job-signed.aab
EOF
cat > "$NEXORA_ANDROID_STATE_ROOT/builds/$job/job.conf" <<EOF
JOB_ID=$job
MODE=release
COMMIT=$commit
STATUS=COMPLETED
EOF
cat > "$NEXORA_ANDROID_STATE_ROOT/builds/$job/source/app/build.gradle.kts" <<'EOF'
android {
  defaultConfig {
    versionCode = 10000
    versionName = "1.0.0"
  }
}
EOF

android_artifact_valid_job_id() {
  [[ "$1" =~ ^[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}$ ]]
}

web_publish_signed_release "$job" "$signed"
[[ -L "$NEXORA_WEB_RELEASE_ROOT/current" ]]
[[ "$(readlink "$NEXORA_WEB_RELEASE_ROOT/current")" == "$job" ]]
[[ -s "$NEXORA_WEB_RELEASE_ROOT/current/Nexora-Git.apk" ]]
[[ -s "$NEXORA_WEB_RELEASE_ROOT/current/latest.json" ]]
grep -q '"versionName": "1.0.0"' "$NEXORA_WEB_RELEASE_ROOT/current/latest.json"
grep -q '"versionCode": 10000' "$NEXORA_WEB_RELEASE_ROOT/current/latest.json"
grep -q '"downloadUrl": "/download/nexora-git.apk"' "$NEXORA_WEB_RELEASE_ROOT/current/latest.json"
if grep -Rqs 'super-secret-must-survive' "$NEXORA_WEB_RELEASE_ROOT"; then
  printf 'Broker secret leaked into public release metadata.\n' >&2
  exit 1
fi

printf 'Website VPS integration tests passed.\n'
