#!/usr/bin/env bash

set -Eeuo pipefail

NEXORA_STATE_FILE="${NEXORA_STATE_FILE:-/etc/nexora-git-vps.conf}"
NEXORA_DEFAULT_INSTALL_DIR="${NEXORA_DEFAULT_INSTALL_DIR:-/opt/nexora-git}"
NEXORA_REPO_URL="${NEXORA_REPO_URL:-https://github.com/Gh0stDeveloper/Nexora-Git.git}"
NEXORA_BRANCH="${NEXORA_BRANCH:-main}"

if [[ -t 1 ]]; then
  C_RESET=$'\033[0m'
  C_BOLD=$'\033[1m'
  C_BLUE=$'\033[34m'
  C_GREEN=$'\033[32m'
  C_YELLOW=$'\033[33m'
  C_RED=$'\033[31m'
else
  C_RESET=""
  C_BOLD=""
  C_BLUE=""
  C_GREEN=""
  C_YELLOW=""
  C_RED=""
fi

log_info() { printf '%s[INFO]%s %s\n' "$C_BLUE" "$C_RESET" "$*"; }
log_ok() { printf '%s[ OK ]%s %s\n' "$C_GREEN" "$C_RESET" "$*"; }
log_warn() { printf '%s[WARN]%s %s\n' "$C_YELLOW" "$C_RESET" "$*" >&2; }
log_error() { printf '%s[FAIL]%s %s\n' "$C_RED" "$C_RESET" "$*" >&2; }
die() { log_error "$*"; exit 1; }

phase() {
  local current="$1"
  local total="$2"
  shift 2
  printf '\n%s[%s/%s] %s%s\n' "$C_BOLD" "$current" "$total" "$*" "$C_RESET"
}

banner() {
  cat <<'EOF'
╔══════════════════════════════════════════════════════════════╗
║                    Nexora Git VPS Setup                     ║
║        Auth Broker · Nginx · HTTPS · Docker · Updates       ║
╚══════════════════════════════════════════════════════════════╝
EOF
}

require_root() {
  [[ "${EUID}" -eq 0 ]] || die "Run this command as root (sudo)."
}

detect_supported_os() {
  [[ -r /etc/os-release ]] || die "Cannot identify this Linux distribution."
  # shellcheck disable=SC1091
  source /etc/os-release
  local family="${ID:-} ${ID_LIKE:-}"
  if [[ "$family" != *debian* && "$family" != *ubuntu* ]]; then
    die "Supported VPS systems are Debian/Ubuntu. Detected: ${PRETTY_NAME:-unknown}."
  fi
  command -v apt-get >/dev/null 2>&1 || die "apt-get is required."
}

package_installed() {
  dpkg-query -W -f='${Status}' "$1" 2>/dev/null | grep -q "ok installed"
}

apt_install_missing() {
  local missing=()
  local package
  for package in "$@"; do
    if ! package_installed "$package"; then
      missing+=("$package")
    fi
  done

  if (( ${#missing[@]} == 0 )); then
    log_ok "System packages already installed; skipping apt downloads."
    return
  fi

  log_info "Installing only missing packages: ${missing[*]}"
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -y
  apt-get install -y --no-install-recommends "${missing[@]}"
}

ensure_docker() {
  if command -v docker >/dev/null 2>&1; then
    log_ok "Docker already installed."
  else
    log_info "Docker not found; installing distro package docker.io."
    apt_install_missing docker.io
  fi

  systemctl enable --now docker >/dev/null
  docker info >/dev/null 2>&1 || die "Docker daemon is not healthy."

  if docker compose version >/dev/null 2>&1; then
    log_ok "Docker Compose plugin available."
    return
  fi

  if command -v docker-compose >/dev/null 2>&1; then
    log_ok "docker-compose available."
    return
  fi

  local candidate
  for candidate in docker-compose-v2 docker-compose-plugin docker-compose; do
    if apt-cache show "$candidate" >/dev/null 2>&1; then
      log_info "Installing Compose package: $candidate"
      apt-get install -y --no-install-recommends "$candidate"
      break
    fi
  done

  if ! docker compose version >/dev/null 2>&1 && ! command -v docker-compose >/dev/null 2>&1; then
    die "Docker Compose could not be installed from this distribution."
  fi
}

compose() {
  local install_dir="${INSTALL_DIR:-$NEXORA_DEFAULT_INSTALL_DIR}"
  local broker_dir="$install_dir/auth-broker"
  [[ -d "$broker_dir" ]] || die "Auth broker directory not found: $broker_dir"

  (
    cd "$broker_dir"
    if docker compose version >/dev/null 2>&1; then
      docker compose "$@"
    else
      docker-compose "$@"
    fi
  )
}

port_busy() {
  local port="$1"
  ss -H -ltn 2>/dev/null | awk '{print $4}' | grep -Eq "[:.]${port}$"
}

find_free_port() {
  local start="${1:-18080}"
  local end="${2:-18180}"
  local port
  for ((port=start; port<=end; port++)); do
    if ! port_busy "$port"; then
      printf '%s\n' "$port"
      return 0
    fi
  done
  return 1
}

valid_domain() {
  local value="$1"
  [[ "$value" =~ ^([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z]{2,63}$ ]]
}

valid_email() {
  local value="$1"
  [[ "$value" =~ ^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$ ]]
}

nginx_file_has_server_name() {
  local file="$1"
  local domain="$2"
  awk -v domain="$domain" '
    {
      line = $0
      sub(/#.*/, "", line)
      while (match(line, /server_name[[:space:]]+[^;]+;/)) {
        directive = substr(line, RSTART, RLENGTH)
        sub(/^server_name[[:space:]]+/, "", directive)
        sub(/;$/, "", directive)
        count = split(directive, names, /[[:space:]]+/)
        for (i = 1; i <= count; i++) {
          if (names[i] == domain) {
            found = 1
          }
        }
        line = substr(line, RSTART + RLENGTH)
      }
    }
    END { exit found ? 0 : 1 }
  ' "$file"
}

load_state() {
  [[ -r "$NEXORA_STATE_FILE" ]] || return 1
  # shellcheck disable=SC1090
  source "$NEXORA_STATE_FILE"
  : "${INSTALL_DIR:?Missing INSTALL_DIR in state}"
  : "${DOMAIN:?Missing DOMAIN in state}"
  : "${EMAIL:?Missing EMAIL in state}"
  : "${LOCAL_PORT:?Missing LOCAL_PORT in state}"
  : "${REPO_URL:?Missing REPO_URL in state}"
  : "${BRANCH:?Missing BRANCH in state}"
}

write_state() {
  local file="$NEXORA_STATE_FILE"
  umask 077
  {
    printf 'INSTALL_DIR=%q\n' "$INSTALL_DIR"
    printf 'DOMAIN=%q\n' "$DOMAIN"
    printf 'EMAIL=%q\n' "$EMAIL"
    printf 'LOCAL_PORT=%q\n' "$LOCAL_PORT"
    printf 'REPO_URL=%q\n' "$REPO_URL"
    printf 'BRANCH=%q\n' "$BRANCH"
  } > "$file"
  chmod 600 "$file"
}

wait_local_health() {
  local port="$1"
  local attempts="${2:-30}"
  local i
  for ((i=1; i<=attempts; i++)); do
    if curl -fsS --max-time 3 "http://127.0.0.1:${port}/health" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  return 1
}

show_port_owner() {
  local port="$1"
  ss -ltnp 2>/dev/null | grep -E "[:.]${port}[[:space:]]" || true
}

certificate_expiry() {
  local domain="$1"
  local cert="/etc/letsencrypt/live/$domain/fullchain.pem"
  if [[ -r "$cert" ]]; then
    openssl x509 -in "$cert" -noout -enddate 2>/dev/null || true
  else
    printf 'not installed\n'
  fi
}
