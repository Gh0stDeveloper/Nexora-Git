#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
if ! declare -F log_info >/dev/null 2>&1; then
  # shellcheck source=lib.sh
  source "$SCRIPT_DIR/lib.sh"
fi
if ! declare -F android_signing_verify >/dev/null 2>&1; then
  # shellcheck source=android-signing-lib.sh
  source "$SCRIPT_DIR/android-signing-lib.sh"
fi
if ! declare -F android_artifacts_verify >/dev/null 2>&1; then
  # shellcheck source=android-artifacts-lib.sh
  source "$SCRIPT_DIR/android-artifacts-lib.sh"
fi

NEXORA_GITHUB_EXPORT_ROOT="${NEXORA_GITHUB_EXPORT_ROOT:-/root/nexora-git-github-secrets}"
NEXORA_GITHUB_SYNC_FILE="${NEXORA_GITHUB_SYNC_FILE:-$NEXORA_SIGNING_ROOT/github-sync.conf}"
NEXORA_GITHUB_ENVIRONMENT="${NEXORA_GITHUB_ENVIRONMENT:-production}"

NEXORA_RELEASE_CLIENT_ID=""
NEXORA_RELEASE_CLIENT_SECRET=""
NEXORA_RELEASE_CALLBACK_URL=""
NEXORA_RELEASE_APP_CALLBACK_URI=""
NEXORA_RELEASE_PORT=""

android_release_broker_env() {
  printf '%s/auth-broker/.env\n' "${INSTALL_DIR:-$NEXORA_DEFAULT_INSTALL_DIR}"
}

android_release_read_broker_config() {
  local env_file
  env_file="$(android_release_broker_env)"
  [[ -r "$env_file" ]] || return 1

  NEXORA_RELEASE_CLIENT_ID=""
  NEXORA_RELEASE_CLIENT_SECRET=""
  NEXORA_RELEASE_CALLBACK_URL=""
  NEXORA_RELEASE_APP_CALLBACK_URI=""
  NEXORA_RELEASE_PORT=""

  local key value
  while IFS='=' read -r key value || [[ -n "$key" ]]; do
    case "$key" in
      GITHUB_APP_CLIENT_ID) NEXORA_RELEASE_CLIENT_ID="$value" ;;
      GITHUB_APP_CLIENT_SECRET) NEXORA_RELEASE_CLIENT_SECRET="$value" ;;
      GITHUB_CALLBACK_URL) NEXORA_RELEASE_CALLBACK_URL="$value" ;;
      APP_CALLBACK_URI) NEXORA_RELEASE_APP_CALLBACK_URI="$value" ;;
      PORT) NEXORA_RELEASE_PORT="$value" ;;
    esac
  done < "$env_file"

  [[ "$NEXORA_RELEASE_CLIENT_ID" =~ ^[A-Za-z0-9._-]{3,128}$ ]] &&
    [[ -n "$NEXORA_RELEASE_CLIENT_SECRET" ]] &&
    [[ "$NEXORA_RELEASE_CLIENT_SECRET" != *$'\n'* ]] &&
    [[ "$NEXORA_RELEASE_CALLBACK_URL" == "https://$DOMAIN/oauth/callback" ]] &&
    [[ "$NEXORA_RELEASE_APP_CALLBACK_URI" == "nexoragit://oauth/callback" ]] &&
    [[ "$NEXORA_RELEASE_PORT" == "$LOCAL_PORT" ]]
}

android_release_write_broker_config() {
  local client_id="$1"
  local client_secret="$2"
  [[ "$client_id" =~ ^[A-Za-z0-9._-]{3,128}$ ]] || die "Invalid GitHub App Client ID."
  [[ -n "$client_secret" && "$client_secret" != *$'\n'* ]] || die "Invalid GitHub App Client Secret."

  local env_file env_dir temp
  env_file="$(android_release_broker_env)"
  env_dir="$(dirname "$env_file")"
  [[ -d "$env_dir" ]] || die "Auth Broker directory is missing: $env_dir"
  temp="$(mktemp "$env_dir/.env.XXXXXX")"
  umask 077

  {
    printf 'GITHUB_APP_CLIENT_ID=%s\n' "$client_id"
    printf 'GITHUB_APP_CLIENT_SECRET=%s\n' "$client_secret"
    printf 'GITHUB_CALLBACK_URL=https://%s/oauth/callback\n' "$DOMAIN"
    printf 'APP_CALLBACK_URI=nexoragit://oauth/callback\n'
    printf 'PORT=%s\n' "$LOCAL_PORT"
  } > "$temp"
  chmod 0600 "$temp"
  mv -f "$temp" "$env_file"
}

android_release_github_status() {
  if ! android_release_read_broker_config; then
    printf 'GitHub App:          invalid or incomplete\n'
    printf 'Broker base URL:     https://%s\n' "$DOMAIN"
    printf 'Expected callback:   https://%s/oauth/callback\n' "$DOMAIN"
    return 1
  fi

  printf 'GitHub App:          configured\n'
  printf 'Client ID:           %s\n' "$NEXORA_RELEASE_CLIENT_ID"
  printf 'Client Secret:       configured (hidden)\n'
  printf 'Broker base URL:     https://%s\n' "$DOMAIN"
  printf 'Callback URL:        %s\n' "$NEXORA_RELEASE_CALLBACK_URL"
  printf 'Android callback:    %s\n' "$NEXORA_RELEASE_APP_CALLBACK_URI"
  printf 'Production env:      %s\n' "$NEXORA_GITHUB_ENVIRONMENT"
}

android_release_github_configure() {
  require_root
  [[ -r /dev/tty ]] || die "A TTY is required to configure GitHub App credentials."

  local existing_id="" existing_secret="" client_id client_secret
  if android_release_read_broker_config; then
    existing_id="$NEXORA_RELEASE_CLIENT_ID"
    existing_secret="$NEXORA_RELEASE_CLIENT_SECRET"
  fi

  read -r -p "GitHub App Client ID${existing_id:+ [$existing_id]}: " client_id </dev/tty
  client_id="${client_id:-$existing_id}"
  [[ "$client_id" =~ ^[A-Za-z0-9._-]{3,128}$ ]] || die "GitHub App Client ID is required and has an invalid format."

  read -r -s -p "GitHub App Client Secret (blank keeps current): " client_secret </dev/tty
  printf '\n' >/dev/tty
  client_secret="${client_secret:-$existing_secret}"
  [[ -n "$client_secret" ]] || die "GitHub App Client Secret is required."

  android_release_write_broker_config "$client_id" "$client_secret"
  unset client_secret existing_secret

  compose up -d --force-recreate auth-broker
  wait_local_health "$LOCAL_PORT" 30 || die "Auth Broker failed health check after GitHub credential update."
  curl -fsS --max-time 10 "https://$DOMAIN/health" >/dev/null ||
    die "Public Auth Broker health check failed after GitHub credential update."

  log_ok "GitHub App credentials updated. Broker/callback URLs remain derived from $DOMAIN."
}

android_release_repo_slug() {
  local url="${REPO_URL:-$NEXORA_REPO_URL}"
  local slug=""
  case "$url" in
    https://github.com/*)
      slug="${url#https://github.com/}"
      ;;
    git@github.com:*)
      slug="${url#git@github.com:}"
      ;;
    *)
      return 1
      ;;
  esac
  slug="${slug%.git}"
  [[ "$slug" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]] || return 1
  printf '%s\n' "$slug"
}

android_release_secret_digest() {
  local name="$1"
  android_release_read_broker_config || die "GitHub App/broker configuration is invalid."
  android_signing_load_secrets_from "$NEXORA_SIGNING_SECRETS" || die "Signing secrets are invalid."

  case "$name" in
    NEXORA_GITHUB_CLIENT_ID)
      printf '%s' "$NEXORA_RELEASE_CLIENT_ID" | sha256sum | awk '{print $1}'
      ;;
    NEXORA_AUTH_BROKER_BASE_URL)
      printf 'https://%s' "$DOMAIN" | sha256sum | awk '{print $1}'
      ;;
    NEXORA_GITHUB_CALLBACK_URL)
      printf 'https://%s/oauth/callback' "$DOMAIN" | sha256sum | awk '{print $1}'
      ;;
    NEXORA_SIGNING_KEYSTORE_BASE64)
      base64 -w0 "$NEXORA_SIGNING_KEYSTORE" | sha256sum | awk '{print $1}'
      ;;
    NEXORA_SIGNING_STORE_PASSWORD)
      printf '%s' "$NEXORA_SIGNING_STORE_PASSWORD" | sha256sum | awk '{print $1}'
      ;;
    NEXORA_SIGNING_KEY_ALIAS)
      printf '%s' "$NEXORA_SIGNING_ALIAS" | sha256sum | awk '{print $1}'
      ;;
    NEXORA_SIGNING_KEY_PASSWORD)
      printf '%s' "$NEXORA_SIGNING_KEY_PASSWORD" | sha256sum | awk '{print $1}'
      ;;
    *)
      return 1
      ;;
  esac
}

android_release_secret_names() {
  printf '%s\n' \
    NEXORA_GITHUB_CLIENT_ID \
    NEXORA_AUTH_BROKER_BASE_URL \
    NEXORA_GITHUB_CALLBACK_URL \
    NEXORA_SIGNING_KEYSTORE_BASE64 \
    NEXORA_SIGNING_STORE_PASSWORD \
    NEXORA_SIGNING_KEY_ALIAS \
    NEXORA_SIGNING_KEY_PASSWORD
}

android_release_write_secret_file() {
  local name="$1"
  local destination="$2"

  android_release_read_broker_config || die "GitHub App/broker configuration is invalid."
  android_signing_load_secrets_from "$NEXORA_SIGNING_SECRETS" || die "Signing secrets are invalid."

  case "$name" in
    NEXORA_GITHUB_CLIENT_ID) printf '%s' "$NEXORA_RELEASE_CLIENT_ID" > "$destination" ;;
    NEXORA_AUTH_BROKER_BASE_URL) printf 'https://%s' "$DOMAIN" > "$destination" ;;
    NEXORA_GITHUB_CALLBACK_URL) printf 'https://%s/oauth/callback' "$DOMAIN" > "$destination" ;;
    NEXORA_SIGNING_KEYSTORE_BASE64) base64 -w0 "$NEXORA_SIGNING_KEYSTORE" > "$destination" ;;
    NEXORA_SIGNING_STORE_PASSWORD) printf '%s' "$NEXORA_SIGNING_STORE_PASSWORD" > "$destination" ;;
    NEXORA_SIGNING_KEY_ALIAS) printf '%s' "$NEXORA_SIGNING_ALIAS" > "$destination" ;;
    NEXORA_SIGNING_KEY_PASSWORD) printf '%s' "$NEXORA_SIGNING_KEY_PASSWORD" > "$destination" ;;
    *) die "Unsupported GitHub secret name: $name" ;;
  esac
  chmod 0600 "$destination"
}

android_release_export_secrets() {
  local destination="${1:-}"
  android_signing_verify || die "Signing Vault verification failed."
  android_release_read_broker_config || die "GitHub App/broker configuration is invalid."

  if [[ -z "$destination" ]]; then
    destination="$NEXORA_GITHUB_EXPORT_ROOT/$(date -u +%Y%m%dT%H%M%SZ)"
  fi
  [[ ! -e "$destination" ]] || die "GitHub secret export destination already exists: $destination"
  install -d -m 0700 "$destination"

  local name
  while IFS= read -r name; do
    android_release_write_secret_file "$name" "$destination/$name"
  done < <(android_release_secret_names)

  {
    printf 'REPOSITORY=%s\n' "$(android_release_repo_slug)"
    printf 'ENVIRONMENT=%s\n' "$NEXORA_GITHUB_ENVIRONMENT"
    printf 'EXPORTED_AT=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    android_signing_load_metadata_from "$NEXORA_SIGNING_METADATA" || die "Signing metadata is invalid."
    printf 'SIGNING_CERT_SHA256=%s\n' "$NEXORA_SIGNING_CERT_SHA256"
    while IFS= read -r name; do
      printf '%s_SHA256=%s\n' "$name" "$(android_release_secret_digest "$name")"
    done < <(android_release_secret_names)
  } > "$destination/manifest.conf"
  chmod 0600 "$destination/manifest.conf"

  log_ok "GitHub production secret bundle created: $destination"
  log_warn "The bundle contains production signing secrets. Copy/use it securely, then delete it."
}

android_release_record_sync() {
  local repo_slug="$1"
  local dir temp
  dir="$(dirname "$NEXORA_GITHUB_SYNC_FILE")"
  install -d -m 0700 "$dir"
  temp="$(mktemp "$dir/.github-sync.XXXXXX")"

  {
    printf 'REPOSITORY=%q\n' "$repo_slug"
    printf 'ENVIRONMENT=%q\n' "$NEXORA_GITHUB_ENVIRONMENT"
    printf 'SYNCED_AT=%q\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    local name
    while IFS= read -r name; do
      printf '%s_SHA256=%q\n' "$name" "$(android_release_secret_digest "$name")"
    done < <(android_release_secret_names)
  } > "$temp"

  chmod 0600 "$temp"
  mv -f "$temp" "$NEXORA_GITHUB_SYNC_FILE"
}

android_release_apply_secrets() {
  require_root
  command -v gh >/dev/null 2>&1 ||
    die "GitHub CLI (gh) is not installed. Use 'nexora-git github secrets export' or install/authenticate gh first."
  gh auth status >/dev/null 2>&1 ||
    die "GitHub CLI is not authenticated. Run 'gh auth login' as root, then retry."

  android_signing_verify || die "Signing Vault verification failed."
  android_release_read_broker_config || die "GitHub App/broker configuration is invalid."

  local repo_slug
  repo_slug="$(android_release_repo_slug)" || die "Could not derive GitHub repository from REPO_URL."

  gh api --method PUT "repos/$repo_slug/environments/$NEXORA_GITHUB_ENVIRONMENT" >/dev/null

  local temp_dir name
  temp_dir="$(mktemp -d "$NEXORA_SIGNING_ROOT/.github-apply.XXXXXX")"
  chmod 0700 "$temp_dir"
  trap 'rm -rf "$temp_dir"' RETURN

  while IFS= read -r name; do
    android_release_write_secret_file "$name" "$temp_dir/$name"
    gh secret set "$name" \
      --env "$NEXORA_GITHUB_ENVIRONMENT" \
      --repo "$repo_slug" < "$temp_dir/$name"
  done < <(android_release_secret_names)

  android_release_record_sync "$repo_slug"
  rm -rf "$temp_dir"
  trap - RETURN

  log_ok "GitHub production secrets synchronized from the VPS Signing Vault and broker configuration."
}

android_release_check_sync() {
  [[ -r "$NEXORA_GITHUB_SYNC_FILE" ]] || return 1
  # shellcheck disable=SC1090
  source "$NEXORA_GITHUB_SYNC_FILE"

  local repo_slug
  repo_slug="$(android_release_repo_slug)" || return 1
  [[ "${REPOSITORY:-}" == "$repo_slug" && "${ENVIRONMENT:-}" == "$NEXORA_GITHUB_ENVIRONMENT" ]] || return 1

  local name expected_var expected actual
  while IFS= read -r name; do
    expected_var="${name}_SHA256"
    expected="${!expected_var:-}"
    [[ "$expected" =~ ^[a-f0-9]{64}$ ]] || return 1
    actual="$(android_release_secret_digest "$name")"
    [[ "$actual" == "$expected" ]] || return 1
  done < <(android_release_secret_names)
}

android_release_secrets_status() {
  if android_release_check_sync; then
    # shellcheck disable=SC1090
    source "$NEXORA_GITHUB_SYNC_FILE"
    printf 'Local parity record: synchronized\n'
    printf 'Repository:          %s\n' "$REPOSITORY"
    printf 'Environment:         %s\n' "$ENVIRONMENT"
    printf 'Last sync:           %s\n' "$SYNCED_AT"
  else
    printf 'Local parity record: not synchronized or current values changed\n'
  fi

  if command -v gh >/dev/null 2>&1 && gh auth status >/dev/null 2>&1; then
    local repo_slug
    repo_slug="$(android_release_repo_slug)" || return 0
    printf '\nGitHub secret names:\n'
    gh secret list --env "$NEXORA_GITHUB_ENVIRONMENT" --repo "$repo_slug" || true
  else
    printf 'GitHub CLI:          unavailable or unauthenticated\n'
  fi
}

android_release_signed_dir() {
  local job_id="$1"
  android_artifact_valid_job_id "$job_id" || return 1
  printf '%s/signed\n' "$(android_artifacts_job_dir "$job_id")"
}

android_release_normalize_fingerprint() {
  printf '%s' "$1" | tr -d ':' | tr '[:lower:]' '[:upper:]'
}

android_release_apk_fingerprint() {
  local apk="$1"
  local apksigner
  apksigner="$(android_component_path build-tools)"
  "$apksigner" verify --verbose --print-certs "$apk" 2>/dev/null |
    awk -F': ' '/Signer #1 certificate SHA-256 digest:/ {print toupper($2); exit}'
}

android_release_aab_fingerprint() {
  local aab="$1"
  local keytool
  keytool="$(android_signing_keytool)"
  LC_ALL=C "$keytool" -printcert -jarfile "$aab" 2>/dev/null |
    awk -F': ' '/SHA256:/ {print toupper($2); exit}'
}

android_release_verify_signed() {
  local job_id="$1"
  android_artifact_valid_job_id "$job_id" || die "Invalid Android build job ID."
  android_signing_verify || die "Signing Vault verification failed."

  local signed_dir
  signed_dir="$(android_release_signed_dir "$job_id")"
  [[ -d "$signed_dir" && -s "$signed_dir/SHA256SUMS.txt" && -s "$signed_dir/signing-manifest.conf" ]] ||
    die "Signed release artifacts are missing: $job_id"

  if awk '{print $2}' "$signed_dir/SHA256SUMS.txt" | grep -Eq '(^/|\.\.|/)'; then
    die "Signed artifact checksum manifest contains unsafe paths."
  fi
  (
    cd "$signed_dir"
    sha256sum --check --strict SHA256SUMS.txt >/dev/null
  ) || die "Signed release checksum verification failed: $job_id"

  # shellcheck disable=SC1090,SC1091
  source "$signed_dir/signing-manifest.conf"
  [[ "${JOB_ID:-}" == "$job_id" ]] || die "Signed release manifest job mismatch."
  [[ "${SIGNING_CERT_SHA256:-}" == "$NEXORA_SIGNING_CERT_SHA256" ]] ||
    die "Signed release manifest does not match the current Signing Vault fingerprint."

  local apk aab apk_fp aab_fp expected
  apk="$signed_dir/${SIGNED_APK:-}"
  aab="$signed_dir/${SIGNED_AAB:-}"
  [[ -s "$apk" && -s "$aab" ]] || die "Signed APK/AAB files are missing."

  local apksigner jarsigner
  apksigner="$(android_component_path build-tools)"
  jarsigner="$NEXORA_ANDROID_JAVA_HOME/bin/jarsigner"
  "$apksigner" verify --verbose --print-certs "$apk" >/dev/null ||
    die "Signed APK verification failed."
  "$jarsigner" -verify -strict "$aab" >/dev/null 2>&1 ||
    die "Signed AAB/JAR verification failed."

  apk_fp="$(android_release_apk_fingerprint "$apk")"
  aab_fp="$(android_release_aab_fingerprint "$aab")"
  expected="$(android_release_normalize_fingerprint "$NEXORA_SIGNING_CERT_SHA256")"
  [[ "$(android_release_normalize_fingerprint "$apk_fp")" == "$expected" ]] ||
    die "Signed APK certificate fingerprint does not match the Signing Vault."
  [[ "$(android_release_normalize_fingerprint "$aab_fp")" == "$expected" ]] ||
    die "Signed AAB certificate fingerprint does not match the Signing Vault."

  log_ok "Signed APK/AAB checksums and certificate fingerprint verified: $job_id"
}

android_release_sign() {
  local job_id="$1"
  android_artifact_valid_job_id "$job_id" || die "Usage: nexora-git android sign <JOB_ID>"
  android_artifacts_verify "$job_id"
  android_signing_verify || die "Signing Vault verification failed."
  load_android_config

  local artifact_dir unsigned_apk unsigned_aab signed_dir
  artifact_dir="$(android_artifacts_job_dir "$job_id")"
  unsigned_apk="$(find "$artifact_dir" -maxdepth 1 -type f -name '*-release-unsigned.apk' -print -quit)"
  unsigned_aab="$(find "$artifact_dir" -maxdepth 1 -type f -name '*-release-unsigned.aab' -print -quit)"
  [[ -s "$unsigned_apk" && -s "$unsigned_aab" ]] ||
    die "Build $job_id is not a staged unsigned release APK/AAB pair."

  signed_dir="$(android_release_signed_dir "$job_id")"
  if [[ -d "$signed_dir" ]]; then
    android_release_verify_signed "$job_id"
    log_ok "Signed release already exists and matches the Signing Vault: $job_id"
    return
  fi

  android_signing_load_secrets_from "$NEXORA_SIGNING_SECRETS" || die "Signing secrets are invalid."
  android_signing_load_metadata_from "$NEXORA_SIGNING_METADATA" || die "Signing metadata is invalid."

  local apksigner jarsigner
  apksigner="$(android_component_path build-tools)"
  jarsigner="$NEXORA_ANDROID_JAVA_HOME/bin/jarsigner"
  [[ -x "$apksigner" && -x "$jarsigner" ]] || die "Android/Java signing tools are unavailable."

  local stage signed_apk signed_aab
  stage="$(mktemp -d "$artifact_dir/.signed-staging.XXXXXX")"
  chmod 0700 "$stage"
  signed_apk="$stage/${job_id}-signed.apk"
  signed_aab="$stage/${job_id}-signed.aab"

  env \
    NEXORA_RELEASE_STORE_PASS="$NEXORA_SIGNING_STORE_PASSWORD" \
    NEXORA_RELEASE_KEY_PASS="$NEXORA_SIGNING_KEY_PASSWORD" \
    "$apksigner" sign \
      --ks "$NEXORA_SIGNING_KEYSTORE" \
      --ks-type "$NEXORA_SIGNING_STORE_TYPE" \
      --ks-key-alias "$NEXORA_SIGNING_ALIAS" \
      --ks-pass env:NEXORA_RELEASE_STORE_PASS \
      --key-pass env:NEXORA_RELEASE_KEY_PASS \
      --v1-signing-enabled true \
      --v2-signing-enabled true \
      --v3-signing-enabled true \
      --out "$signed_apk" \
      "$unsigned_apk"

  cp "$unsigned_aab" "$signed_aab"
  env \
    NEXORA_RELEASE_STORE_PASS="$NEXORA_SIGNING_STORE_PASSWORD" \
    NEXORA_RELEASE_KEY_PASS="$NEXORA_SIGNING_KEY_PASSWORD" \
    "$jarsigner" \
      -keystore "$NEXORA_SIGNING_KEYSTORE" \
      -storetype "$NEXORA_SIGNING_STORE_TYPE" \
      -storepass:env NEXORA_RELEASE_STORE_PASS \
      -keypass:env NEXORA_RELEASE_KEY_PASS \
      -sigalg SHA256withRSA \
      -digestalg SHA-256 \
      "$signed_aab" \
      "$NEXORA_SIGNING_ALIAS" >/dev/null

  local apk_fp aab_fp expected
  apk_fp="$(android_release_apk_fingerprint "$signed_apk")"
  aab_fp="$(android_release_aab_fingerprint "$signed_aab")"
  expected="$(android_release_normalize_fingerprint "$NEXORA_SIGNING_CERT_SHA256")"
  [[ "$(android_release_normalize_fingerprint "$apk_fp")" == "$expected" ]] ||
    die "APK signing produced an unexpected certificate fingerprint."
  [[ "$(android_release_normalize_fingerprint "$aab_fp")" == "$expected" ]] ||
    die "AAB signing produced an unexpected certificate fingerprint."

  chmod 0640 "$signed_apk" "$signed_aab"
  {
    printf 'JOB_ID=%q\n' "$job_id"
    printf 'SIGNED_AT=%q\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    printf 'SIGNING_CERT_SHA256=%q\n' "$NEXORA_SIGNING_CERT_SHA256"
    printf 'SIGNED_APK=%q\n' "$(basename "$signed_apk")"
    printf 'SIGNED_AAB=%q\n' "$(basename "$signed_aab")"
    printf 'UNSIGNED_APK_SHA256=%q\n' "$(sha256sum "$unsigned_apk" | awk '{print $1}')"
    printf 'UNSIGNED_AAB_SHA256=%q\n' "$(sha256sum "$unsigned_aab" | awk '{print $1}')"
  } > "$stage/signing-manifest.conf"
  chmod 0640 "$stage/signing-manifest.conf"

  (
    cd "$stage"
    sha256sum "$(basename "$signed_apk")" "$(basename "$signed_aab")" signing-manifest.conf > SHA256SUMS.txt
    chmod 0640 SHA256SUMS.txt
  )

  mv "$stage" "$signed_dir"
  android_release_verify_signed "$job_id"
  log_ok "Release signed with the persistent Nexora Git identity: $signed_dir"
}

android_release_show_signed() {
  local job_id="$1"
  local signed_dir
  signed_dir="$(android_release_signed_dir "$job_id")"
  [[ -d "$signed_dir" ]] || die "No signed release exists for build: $job_id"
  android_release_verify_signed "$job_id"
  printf 'Signed directory: %s\n' "$signed_dir"
  find "$signed_dir" -maxdepth 1 -type f -printf '  %f  %s bytes\n' | sort
}

android_release_workflow_parity() {
  local workflow="${INSTALL_DIR:-$NEXORA_DEFAULT_INSTALL_DIR}/.github/workflows/release.yml"
  [[ -r "$workflow" ]] || return 1
  grep -q 'environment: production' "$workflow" || return 1

  local name
  while IFS= read -r name; do
    grep -q "secrets\\.$name" "$workflow" || return 1
  done < <(android_release_secret_names)

  grep -q 'NEXORA_AUTH_BROKER_BASE_URL' "$workflow" || return 1
  grep -q 'NEXORA_GITHUB_CALLBACK_URL' "$workflow" || return 1
}

android_release_parity() {
  local job_id="${1:-}"
  android_release_read_broker_config || die "GitHub App/broker configuration is invalid."
  android_signing_verify || die "Signing Vault verification failed."
  android_release_workflow_parity || die "Stable Release workflow is missing required production secret wiring."
  android_release_check_sync || die "GitHub production secrets are not synchronized with the current VPS configuration."

  if [[ -n "$job_id" ]]; then
    android_release_verify_signed "$job_id"
  fi

  android_signing_load_metadata_from "$NEXORA_SIGNING_METADATA" || die "Signing metadata is invalid."
  log_ok "VPS/GitHub production configuration parity is recorded and current."
  printf 'Broker:      https://%s\n' "$DOMAIN"
  printf 'Callback:    https://%s/oauth/callback\n' "$DOMAIN"
  printf 'Certificate: %s\n' "$NEXORA_SIGNING_CERT_SHA256"
  [[ -n "$job_id" ]] && printf 'Signed job:   %s\n' "$job_id"
}

android_release_doctor() {
  local failed=0
  load_android_config

  if android_release_read_broker_config; then
    log_ok "GitHub App public configuration matches the managed VPS domain."
  else
    log_error "GitHub App/broker configuration is invalid or does not match the managed domain."
    failed=1
  fi

  android_signing_verify || failed=1

  local apksigner jarsigner keytool
  apksigner="$(android_component_path build-tools)"
  jarsigner="$NEXORA_ANDROID_JAVA_HOME/bin/jarsigner"
  keytool="$NEXORA_ANDROID_JAVA_HOME/bin/keytool"
  if [[ -x "$apksigner" ]]; then
    log_ok "apksigner available"
  else
    log_error "apksigner missing"
    failed=1
  fi

  if [[ -x "$jarsigner" ]]; then
    log_ok "jarsigner available"
  else
    log_error "jarsigner missing"
    failed=1
  fi

  if [[ -x "$keytool" ]]; then
    log_ok "keytool available"
  else
    log_error "keytool missing"
    failed=1
  fi

  if android_release_workflow_parity; then
    log_ok "Stable Release workflow references all required production secrets."
  else
    log_error "Stable Release workflow production secret wiring is incomplete."
    failed=1
  fi

  return "$failed"
}
