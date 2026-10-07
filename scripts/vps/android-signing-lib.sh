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

NEXORA_SIGNING_ROOT="${NEXORA_SIGNING_ROOT:-/var/lib/nexora-git/signing}"
NEXORA_SIGNING_KEYSTORE="${NEXORA_SIGNING_KEYSTORE:-$NEXORA_SIGNING_ROOT/release.p12}"
NEXORA_SIGNING_SECRETS="${NEXORA_SIGNING_SECRETS:-$NEXORA_SIGNING_ROOT/secrets.env}"
NEXORA_SIGNING_METADATA="${NEXORA_SIGNING_METADATA:-$NEXORA_SIGNING_ROOT/identity.conf}"
NEXORA_SIGNING_CERTIFICATE="${NEXORA_SIGNING_CERTIFICATE:-$NEXORA_SIGNING_ROOT/certificate.pem}"
NEXORA_SIGNING_BACKUP_DIR="${NEXORA_SIGNING_BACKUP_DIR:-$NEXORA_SIGNING_ROOT/backups}"

NEXORA_SIGNING_ALIAS="${NEXORA_SIGNING_ALIAS:-nexora-release}"
NEXORA_SIGNING_STORE_TYPE="${NEXORA_SIGNING_STORE_TYPE:-PKCS12}"
NEXORA_SIGNING_VALIDITY_DAYS="${NEXORA_SIGNING_VALIDITY_DAYS:-10000}"
NEXORA_SIGNING_DNAME="${NEXORA_SIGNING_DNAME:-CN=Nexora Git Release, OU=Android, O=Nexora}"
NEXORA_SIGNING_BACKUP_ITERATIONS="${NEXORA_SIGNING_BACKUP_ITERATIONS:-600000}"

NEXORA_SIGNING_STORE_PASSWORD=""
NEXORA_SIGNING_KEY_PASSWORD=""
NEXORA_SIGNING_ID=""
NEXORA_SIGNING_CREATED_AT=""
NEXORA_SIGNING_CERT_SHA256=""
NEXORA_SIGNING_CERT_SHA1=""
NEXORA_SIGNING_KEYSTORE_SHA256=""
NEXORA_SIGNING_METADATA_ALIAS=""
NEXORA_SIGNING_METADATA_STORE_TYPE=""

android_signing_keytool() {
  load_android_config
  local java_home="${NEXORA_ANDROID_JAVA_HOME:-}"
  [[ -n "$java_home" && -x "$java_home/bin/keytool" ]] ||
    die "JDK 17 keytool is unavailable. Run: nexora-git android setup"
  printf '%s\n' "$java_home/bin/keytool"
}

android_signing_random_hex() {
  local bytes="${1:-32}"
  openssl rand -hex "$bytes"
}

android_signing_complete() {
  [[ -s "$NEXORA_SIGNING_KEYSTORE" &&
     -s "$NEXORA_SIGNING_SECRETS" &&
     -s "$NEXORA_SIGNING_METADATA" &&
     -s "$NEXORA_SIGNING_CERTIFICATE" ]]
}

android_signing_has_state() {
  [[ -d "$NEXORA_SIGNING_ROOT" ]] &&
    find "$NEXORA_SIGNING_ROOT" -mindepth 1 -maxdepth 1 ! -name backups -print -quit 2>/dev/null | grep -q .
}

android_signing_validate_alias() {
  [[ "$1" =~ ^[A-Za-z0-9._-]{1,64}$ ]]
}

android_signing_load_secrets_from() {
  local file="$1"
  local key value
  local seen_store=0 seen_alias=0 seen_key=0 seen_type=0

  NEXORA_SIGNING_STORE_PASSWORD=""
  NEXORA_SIGNING_KEY_PASSWORD=""

  [[ -r "$file" ]] || return 1

  while IFS='=' read -r key value || [[ -n "$key" ]]; do
    [[ -z "$key" ]] && continue
    case "$key" in
      NEXORA_SIGNING_STORE_PASSWORD)
        [[ "$value" =~ ^[A-Fa-f0-9]{64}$ ]] || return 1
        NEXORA_SIGNING_STORE_PASSWORD="$value"
        seen_store=1
        ;;
      NEXORA_SIGNING_KEY_ALIAS)
        android_signing_validate_alias "$value" || return 1
        NEXORA_SIGNING_ALIAS="$value"
        seen_alias=1
        ;;
      NEXORA_SIGNING_KEY_PASSWORD)
        [[ "$value" =~ ^[A-Fa-f0-9]{64}$ ]] || return 1
        NEXORA_SIGNING_KEY_PASSWORD="$value"
        seen_key=1
        ;;
      NEXORA_SIGNING_STORE_TYPE)
        [[ "$value" == "PKCS12" ]] || return 1
        NEXORA_SIGNING_STORE_TYPE="$value"
        seen_type=1
        ;;
      *)
        return 1
        ;;
    esac
  done < "$file"

  (( seen_store == 1 && seen_alias == 1 && seen_key == 1 && seen_type == 1 ))
}

android_signing_load_metadata_from() {
  local file="$1"
  local key value
  local seen_id=0 seen_created=0 seen_sha256=0 seen_sha1=0 seen_store_sha=0 seen_alias=0 seen_type=0

  NEXORA_SIGNING_ID=""
  NEXORA_SIGNING_CREATED_AT=""
  NEXORA_SIGNING_CERT_SHA256=""
  NEXORA_SIGNING_CERT_SHA1=""
  NEXORA_SIGNING_KEYSTORE_SHA256=""
  NEXORA_SIGNING_METADATA_ALIAS=""
  NEXORA_SIGNING_METADATA_STORE_TYPE=""

  [[ -r "$file" ]] || return 1

  while IFS='=' read -r key value || [[ -n "$key" ]]; do
    [[ -z "$key" ]] && continue
    case "$key" in
      NEXORA_SIGNING_ID)
        [[ "$value" =~ ^[A-Fa-f0-9]{32}$ ]] || return 1
        NEXORA_SIGNING_ID="$value"
        seen_id=1
        ;;
      NEXORA_SIGNING_CREATED_AT)
        [[ "$value" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]] || return 1
        NEXORA_SIGNING_CREATED_AT="$value"
        seen_created=1
        ;;
      NEXORA_SIGNING_CERT_SHA256)
        [[ "$value" =~ ^([A-Fa-f0-9]{2}:){31}[A-Fa-f0-9]{2}$ ]] || return 1
        NEXORA_SIGNING_CERT_SHA256="${value^^}"
        seen_sha256=1
        ;;
      NEXORA_SIGNING_CERT_SHA1)
        [[ "$value" =~ ^([A-Fa-f0-9]{2}:){19}[A-Fa-f0-9]{2}$ ]] || return 1
        NEXORA_SIGNING_CERT_SHA1="${value^^}"
        seen_sha1=1
        ;;
      NEXORA_SIGNING_KEYSTORE_SHA256)
        [[ "$value" =~ ^[A-Fa-f0-9]{64}$ ]] || return 1
        NEXORA_SIGNING_KEYSTORE_SHA256="${value,,}"
        seen_store_sha=1
        ;;
      NEXORA_SIGNING_KEY_ALIAS)
        android_signing_validate_alias "$value" || return 1
        NEXORA_SIGNING_METADATA_ALIAS="$value"
        seen_alias=1
        ;;
      NEXORA_SIGNING_STORE_TYPE)
        [[ "$value" == "PKCS12" ]] || return 1
        NEXORA_SIGNING_METADATA_STORE_TYPE="$value"
        seen_type=1
        ;;
      *)
        return 1
        ;;
    esac
  done < "$file"

  (( seen_id == 1 && seen_created == 1 && seen_sha256 == 1 && seen_sha1 == 1 &&
     seen_store_sha == 1 && seen_alias == 1 && seen_type == 1 ))
}

android_signing_cert_fingerprint() {
  local cert="$1"
  local digest="$2"
  openssl x509 -in "$cert" -noout -fingerprint "-$digest" |
    awk -F= '{print toupper($2)}'
}

android_signing_create_identity() {
  local owner="${NEXORA_SIGNING_OWNER:-root}"
  local group="${NEXORA_SIGNING_GROUP:-root}"

  command -v openssl >/dev/null 2>&1 || die "openssl is required for the Signing Vault."
  command -v sha256sum >/dev/null 2>&1 || die "sha256sum is required for the Signing Vault."
  android_signing_validate_alias "$NEXORA_SIGNING_ALIAS" || die "Invalid signing alias."

  if android_signing_has_state; then
    die "Signing Vault already contains identity state. Refusing to generate or overwrite a release key."
  fi

  local keytool
  keytool="$(android_signing_keytool)"

  install -d -o "$owner" -g "$group" -m 0700 "$NEXORA_SIGNING_ROOT"
  install -d -o "$owner" -g "$group" -m 0700 "$NEXORA_SIGNING_BACKUP_DIR"

  local temp_dir
  temp_dir="$(mktemp -d "$NEXORA_SIGNING_ROOT/.create.XXXXXX")"
  chmod 0700 "$temp_dir"

  local store_password identity_id created_at
  store_password="$(android_signing_random_hex 32)"
  identity_id="$(android_signing_random_hex 16)"
  created_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

  "$keytool" -genkeypair -noprompt \
    -alias "$NEXORA_SIGNING_ALIAS" \
    -keyalg RSA \
    -keysize 4096 \
    -sigalg SHA256withRSA \
    -validity "$NEXORA_SIGNING_VALIDITY_DAYS" \
    -dname "$NEXORA_SIGNING_DNAME" \
    -keystore "$temp_dir/release.p12" \
    -storetype "$NEXORA_SIGNING_STORE_TYPE" \
    -storepass "$store_password" \
    -keypass "$store_password" >/dev/null

  "$keytool" -exportcert -rfc \
    -alias "$NEXORA_SIGNING_ALIAS" \
    -keystore "$temp_dir/release.p12" \
    -storetype "$NEXORA_SIGNING_STORE_TYPE" \
    -storepass "$store_password" \
    -file "$temp_dir/certificate.pem" >/dev/null

  local cert_sha256 cert_sha1 store_sha256
  cert_sha256="$(android_signing_cert_fingerprint "$temp_dir/certificate.pem" sha256)"
  cert_sha1="$(android_signing_cert_fingerprint "$temp_dir/certificate.pem" sha1)"
  store_sha256="$(sha256sum "$temp_dir/release.p12" | awk '{print $1}')"

  {
    printf 'NEXORA_SIGNING_STORE_PASSWORD=%s\n' "$store_password"
    printf 'NEXORA_SIGNING_KEY_ALIAS=%s\n' "$NEXORA_SIGNING_ALIAS"
    printf 'NEXORA_SIGNING_KEY_PASSWORD=%s\n' "$store_password"
    printf 'NEXORA_SIGNING_STORE_TYPE=%s\n' "$NEXORA_SIGNING_STORE_TYPE"
  } > "$temp_dir/secrets.env"

  {
    printf 'NEXORA_SIGNING_ID=%s\n' "$identity_id"
    printf 'NEXORA_SIGNING_CREATED_AT=%s\n' "$created_at"
    printf 'NEXORA_SIGNING_CERT_SHA256=%s\n' "$cert_sha256"
    printf 'NEXORA_SIGNING_CERT_SHA1=%s\n' "$cert_sha1"
    printf 'NEXORA_SIGNING_KEYSTORE_SHA256=%s\n' "$store_sha256"
    printf 'NEXORA_SIGNING_KEY_ALIAS=%s\n' "$NEXORA_SIGNING_ALIAS"
    printf 'NEXORA_SIGNING_STORE_TYPE=%s\n' "$NEXORA_SIGNING_STORE_TYPE"
  } > "$temp_dir/identity.conf"

  chmod 0600 "$temp_dir/release.p12" "$temp_dir/secrets.env" "$temp_dir/identity.conf"
  chmod 0644 "$temp_dir/certificate.pem"

  install -o "$owner" -g "$group" -m 0600 "$temp_dir/release.p12" "$NEXORA_SIGNING_KEYSTORE"
  install -o "$owner" -g "$group" -m 0600 "$temp_dir/secrets.env" "$NEXORA_SIGNING_SECRETS"
  install -o "$owner" -g "$group" -m 0600 "$temp_dir/identity.conf" "$NEXORA_SIGNING_METADATA"
  install -o "$owner" -g "$group" -m 0644 "$temp_dir/certificate.pem" "$NEXORA_SIGNING_CERTIFICATE"
  rm -rf "$temp_dir"

  android_signing_verify || die "New Signing Vault failed post-generation verification."
  log_ok "Created Android release signing identity. This key will be reused for future builds."
  log_ok "SHA-256 certificate fingerprint: $NEXORA_SIGNING_CERT_SHA256"
}

android_signing_verify_paths() {
  local failed=0
  local expected actual path

  if [[ ! -d "$NEXORA_SIGNING_ROOT" ]]; then
    log_error "Signing Vault missing: $NEXORA_SIGNING_ROOT"
    return 1
  fi

  actual="$(stat -c '%a' "$NEXORA_SIGNING_ROOT" 2>/dev/null || true)"
  if [[ "$actual" == "700" ]]; then
    log_ok "Signing Vault permissions: 0700"
  else
    log_error "Signing Vault permissions must be 0700 (actual: ${actual:-unknown})"
    failed=1
  fi

  for path in "$NEXORA_SIGNING_KEYSTORE" "$NEXORA_SIGNING_SECRETS" "$NEXORA_SIGNING_METADATA"; do
    expected="600"
    actual="$(stat -c '%a' "$path" 2>/dev/null || true)"
    if [[ "$actual" == "$expected" ]]; then
      log_ok "Protected file permissions: $path"
    else
      log_error "Protected file must be mode 0600: $path (actual: ${actual:-missing})"
      failed=1
    fi
  done

  actual="$(stat -c '%a' "$NEXORA_SIGNING_CERTIFICATE" 2>/dev/null || true)"
  if [[ "$actual" == "644" ]]; then
    log_ok "Public certificate file permissions: 0644"
  else
    log_error "Certificate file must be mode 0644 (actual: ${actual:-missing})"
    failed=1
  fi

  return "$failed"
}

android_signing_verify() {
  android_signing_complete || {
    log_error "Signing Vault is incomplete."
    return 1
  }

  local failed=0
  android_signing_verify_paths || failed=1

  if ! android_signing_load_secrets_from "$NEXORA_SIGNING_SECRETS"; then
    log_error "Signing secret file has an invalid or unexpected format."
    return 1
  fi
  local secrets_alias="$NEXORA_SIGNING_ALIAS"
  local secrets_type="$NEXORA_SIGNING_STORE_TYPE"

  if [[ "$NEXORA_SIGNING_KEY_PASSWORD" != "$NEXORA_SIGNING_STORE_PASSWORD" ]]; then
    log_error "PKCS#12 key password must match the store password for this Signing Vault."
    return 1
  fi

  if ! android_signing_load_metadata_from "$NEXORA_SIGNING_METADATA"; then
    log_error "Signing metadata file has an invalid or unexpected format."
    return 1
  fi

  if [[ "$secrets_alias" != "$NEXORA_SIGNING_METADATA_ALIAS" ]]; then
    log_error "Signing alias mismatch between secret and metadata files."
    failed=1
  fi
  if [[ "$secrets_type" != "$NEXORA_SIGNING_METADATA_STORE_TYPE" ]]; then
    log_error "Signing store type mismatch between secret and metadata files."
    failed=1
  fi

  local keytool
  keytool="$(android_signing_keytool)"
  if "$keytool" -list \
      -alias "$secrets_alias" \
      -keystore "$NEXORA_SIGNING_KEYSTORE" \
      -storetype "$secrets_type" \
      -storepass "$NEXORA_SIGNING_STORE_PASSWORD" >/dev/null 2>&1; then
    log_ok "Release keystore opens successfully and contains alias: $secrets_alias"
  else
    log_error "Release keystore could not be opened with the stored signing credentials."
    return 1
  fi

  local temp_cert
  temp_cert="$(mktemp)"
  if ! "$keytool" -exportcert -rfc \
      -alias "$secrets_alias" \
      -keystore "$NEXORA_SIGNING_KEYSTORE" \
      -storetype "$secrets_type" \
      -storepass "$NEXORA_SIGNING_STORE_PASSWORD" \
      -file "$temp_cert" >/dev/null 2>&1; then
    rm -f "$temp_cert"
    log_error "Unable to export the certificate from the release keystore."
    return 1
  fi

  local current_sha256 current_sha1 stored_cert_sha256 current_store_sha256
  current_sha256="$(android_signing_cert_fingerprint "$temp_cert" sha256)"
  current_sha1="$(android_signing_cert_fingerprint "$temp_cert" sha1)"
  stored_cert_sha256="$(android_signing_cert_fingerprint "$NEXORA_SIGNING_CERTIFICATE" sha256)"
  current_store_sha256="$(sha256sum "$NEXORA_SIGNING_KEYSTORE" | awk '{print $1}')"
  rm -f "$temp_cert"

  if [[ "$current_sha256" != "$NEXORA_SIGNING_CERT_SHA256" ||
        "$stored_cert_sha256" != "$NEXORA_SIGNING_CERT_SHA256" ]]; then
    log_error "Release certificate SHA-256 fingerprint does not match Signing Vault metadata."
    failed=1
  else
    log_ok "Release certificate SHA-256 fingerprint verified."
  fi

  if [[ "$current_sha1" != "$NEXORA_SIGNING_CERT_SHA1" ]]; then
    log_error "Release certificate SHA-1 fingerprint does not match Signing Vault metadata."
    failed=1
  fi

  if [[ "$current_store_sha256" != "$NEXORA_SIGNING_KEYSTORE_SHA256" ]]; then
    log_error "Release keystore file checksum does not match Signing Vault metadata."
    failed=1
  else
    log_ok "Release keystore SHA-256 checksum verified."
  fi

  if openssl x509 -in "$NEXORA_SIGNING_CERTIFICATE" -checkend 31536000 -noout >/dev/null 2>&1; then
    log_ok "Release certificate remains valid for more than one year."
  else
    log_error "Release certificate expires within one year or is invalid."
    failed=1
  fi

  return "$failed"
}

ensure_android_signing_vault() {
  if android_signing_complete; then
    log_info "Existing Android Signing Vault detected; verifying and reusing it."
    android_signing_verify || die "Existing Signing Vault is invalid. Refusing to regenerate the release identity."
    return
  fi

  if android_signing_has_state; then
    die "Signing Vault is incomplete. Refusing to generate a replacement key over existing state."
  fi

  android_signing_create_identity
}

android_signing_status() {
  if ! android_signing_complete; then
    printf 'Signing state:       not configured\n'
    printf 'Vault:               %s\n' "$NEXORA_SIGNING_ROOT"
    return 1
  fi

  android_signing_load_metadata_from "$NEXORA_SIGNING_METADATA" ||
    die "Signing metadata is invalid."

  printf 'Signing state:       configured\n'
  printf 'Identity ID:         %s\n' "$NEXORA_SIGNING_ID"
  printf 'Created:             %s\n' "$NEXORA_SIGNING_CREATED_AT"
  printf 'Store type:          %s\n' "$NEXORA_SIGNING_METADATA_STORE_TYPE"
  printf 'Alias:               %s\n' "$NEXORA_SIGNING_METADATA_ALIAS"
  printf 'SHA-256:             %s\n' "$NEXORA_SIGNING_CERT_SHA256"
  printf 'Vault:               %s\n' "$NEXORA_SIGNING_ROOT"
  printf 'Secrets:             protected; never printed by status\n'
}

android_signing_fingerprint() {
  android_signing_complete || die "Signing Vault is not configured."
  android_signing_load_metadata_from "$NEXORA_SIGNING_METADATA" ||
    die "Signing metadata is invalid."
  printf 'SHA-256: %s\n' "$NEXORA_SIGNING_CERT_SHA256"
  printf 'SHA-1:   %s\n' "$NEXORA_SIGNING_CERT_SHA1"
}

android_signing_certificate() {
  android_signing_complete || die "Signing Vault is not configured."
  cat "$NEXORA_SIGNING_CERTIFICATE"
}

android_signing_backup_internal() {
  local destination="$1"
  local passphrase="$2"

  android_signing_verify || die "Signing Vault verification failed; backup aborted."
  [[ "${#passphrase}" -ge 16 ]] || die "Backup passphrase must contain at least 16 characters."

  local destination_dir
  destination_dir="$(dirname "$destination")"
  install -d -m 0700 "$destination_dir"
  [[ ! -e "$destination" ]] || die "Backup destination already exists: $destination"

  local work staging archive encrypted
  work="$(mktemp -d)"
  staging="$work/staging"
  archive="$work/vault.tar.gz"
  encrypted="$work/vault.enc"
  mkdir -m 0700 "$staging"

  install -m 0600 "$NEXORA_SIGNING_KEYSTORE" "$staging/release.p12"
  install -m 0600 "$NEXORA_SIGNING_SECRETS" "$staging/secrets.env"
  install -m 0600 "$NEXORA_SIGNING_METADATA" "$staging/identity.conf"
  install -m 0644 "$NEXORA_SIGNING_CERTIFICATE" "$staging/certificate.pem"
  (
    cd "$staging"
    sha256sum release.p12 secrets.env identity.conf certificate.pem > SHA256SUMS
    chmod 0600 SHA256SUMS
    tar -czf "$archive" release.p12 secrets.env identity.conf certificate.pem SHA256SUMS
  )

  openssl enc -aes-256-cbc -salt -pbkdf2 \
    -iter "$NEXORA_SIGNING_BACKUP_ITERATIONS" -md sha256 \
    -in "$archive" -out "$encrypted" -pass fd:3 3<<<"$passphrase"

  install -m 0600 "$encrypted" "$destination"
  sha256sum "$destination" > "$destination.sha256"
  chmod 0600 "$destination.sha256"
  rm -rf "$work"

  printf '%s\n' "$destination"
}

android_signing_restore_internal() {
  local backup="$1"
  local passphrase="$2"

  [[ -r "$backup" ]] || die "Signing backup not readable: $backup"
  [[ "${#passphrase}" -ge 16 ]] || die "Backup passphrase must contain at least 16 characters."

  local work archive staging
  work="$(mktemp -d)"
  archive="$work/vault.tar.gz"
  staging="$work/staging"
  mkdir -m 0700 "$staging"

  if ! openssl enc -d -aes-256-cbc -pbkdf2 \
      -iter "$NEXORA_SIGNING_BACKUP_ITERATIONS" -md sha256 \
      -in "$backup" -out "$archive" -pass fd:3 3<<<"$passphrase" 2>/dev/null; then
    rm -rf "$work"
    die "Signing backup decryption failed."
  fi

  local names expected
  names="$(tar -tzf "$archive" 2>/dev/null | sort || true)"
  expected="$(printf '%s\n' SHA256SUMS certificate.pem identity.conf release.p12 secrets.env | sort)"
  if [[ "$names" != "$expected" ]]; then
    rm -rf "$work"
    die "Signing backup contains unexpected paths or is incomplete."
  fi

  if tar -tvzf "$archive" | awk '{print substr($1,1,1)}' | grep -qv '^-'; then
    rm -rf "$work"
    die "Signing backup contains non-regular files."
  fi

  tar -xzf "$archive" -C "$staging" --no-same-owner --no-same-permissions
  chmod 0600 "$staging/release.p12" "$staging/secrets.env" "$staging/identity.conf" "$staging/SHA256SUMS"
  chmod 0644 "$staging/certificate.pem"

  (
    cd "$staging"
    sha256sum -c SHA256SUMS >/dev/null
  ) || {
    rm -rf "$work"
    die "Signing backup checksum validation failed."
  }

  if ! android_signing_load_secrets_from "$staging/secrets.env"; then
    rm -rf "$work"
    die "Signing backup secret metadata is invalid."
  fi
  local backup_alias="$NEXORA_SIGNING_ALIAS"
  local backup_type="$NEXORA_SIGNING_STORE_TYPE"

  if ! android_signing_load_metadata_from "$staging/identity.conf"; then
    rm -rf "$work"
    die "Signing backup identity metadata is invalid."
  fi
  local backup_fingerprint="$NEXORA_SIGNING_CERT_SHA256"

  local keytool
  keytool="$(android_signing_keytool)"
  if ! "$keytool" -list \
      -alias "$backup_alias" \
      -keystore "$staging/release.p12" \
      -storetype "$backup_type" \
      -storepass "$NEXORA_SIGNING_STORE_PASSWORD" >/dev/null 2>&1; then
    rm -rf "$work"
    die "Signing backup keystore cannot be opened with its stored credentials."
  fi

  local backup_cert_fingerprint
  backup_cert_fingerprint="$(android_signing_cert_fingerprint "$staging/certificate.pem" sha256)"
  if [[ "$backup_cert_fingerprint" != "$backup_fingerprint" ]]; then
    rm -rf "$work"
    die "Signing backup certificate does not match its identity metadata."
  fi

  if android_signing_complete; then
    android_signing_load_metadata_from "$NEXORA_SIGNING_METADATA" ||
      die "Existing Signing Vault metadata is invalid."
    local current_fingerprint="$NEXORA_SIGNING_CERT_SHA256"
    rm -rf "$work"

    if [[ "$current_fingerprint" == "$backup_fingerprint" ]]; then
      log_ok "Backup contains the same signing identity already installed; no files were replaced."
      return 0
    fi
    die "Backup contains a different signing identity. Refusing to overwrite the installed release key."
  fi

  if android_signing_has_state; then
    rm -rf "$work"
    die "Signing Vault contains partial state. Refusing automatic restore over existing files."
  fi

  local owner="${NEXORA_SIGNING_OWNER:-root}"
  local group="${NEXORA_SIGNING_GROUP:-root}"
  install -d -o "$owner" -g "$group" -m 0700 "$NEXORA_SIGNING_ROOT"
  install -d -o "$owner" -g "$group" -m 0700 "$NEXORA_SIGNING_BACKUP_DIR"
  install -o "$owner" -g "$group" -m 0600 "$staging/release.p12" "$NEXORA_SIGNING_KEYSTORE"
  install -o "$owner" -g "$group" -m 0600 "$staging/secrets.env" "$NEXORA_SIGNING_SECRETS"
  install -o "$owner" -g "$group" -m 0600 "$staging/identity.conf" "$NEXORA_SIGNING_METADATA"
  install -o "$owner" -g "$group" -m 0644 "$staging/certificate.pem" "$NEXORA_SIGNING_CERTIFICATE"
  rm -rf "$work"

  android_signing_verify || die "Restored Signing Vault failed verification."
  log_ok "Signing identity restored and verified."
}

android_signing_read_backup_passphrase() {
  local mode="$1"
  [[ -r /dev/tty ]] || die "A TTY is required to enter the encrypted backup passphrase."

  local first second
  read -r -s -p "Signing backup passphrase: " first </dev/tty
  printf '\n' >/dev/tty
  [[ "${#first}" -ge 16 ]] || die "Backup passphrase must contain at least 16 characters."

  if [[ "$mode" == "create" ]]; then
    read -r -s -p "Confirm backup passphrase: " second </dev/tty
    printf '\n' >/dev/tty
    [[ "$first" == "$second" ]] || die "Backup passphrases do not match."
  fi

  printf '%s' "$first"
}

android_signing_backup() {
  local destination="${1:-}"
  android_signing_complete || die "Signing Vault is not configured."

  if [[ -z "$destination" ]]; then
    local stamp
    stamp="$(date -u +%Y%m%dT%H%M%SZ)"
    destination="$NEXORA_SIGNING_BACKUP_DIR/nexora-git-signing-$stamp.nxbk"
  fi

  local passphrase
  passphrase="$(android_signing_read_backup_passphrase create)"
  local written
  written="$(android_signing_backup_internal "$destination" "$passphrase")"
  unset passphrase
  log_ok "Encrypted signing backup created: $written"
  log_info "Store this backup and its passphrase separately from the VPS."
}

android_signing_restore() {
  local backup="${1:-}"
  [[ -n "$backup" ]] || die "Usage: nexora-git android signing restore <backup.nxbk>"

  local passphrase
  passphrase="$(android_signing_read_backup_passphrase restore)"
  android_signing_restore_internal "$backup" "$passphrase"
  unset passphrase
}
