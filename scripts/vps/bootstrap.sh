#!/usr/bin/env bash

set -Eeuo pipefail

INSTALL_DIR="${NEXORA_INSTALL_DIR:-/opt/nexora-git}"
REPO_URL="${NEXORA_REPO_URL:-https://github.com/Gh0stDeveloper/Nexora-Git.git}"
BRANCH="${NEXORA_BRANCH:-main}"

[[ "$EUID" -eq 0 ]] || { echo "Run as root: sudo bash" >&2; exit 1; }

if [[ ! -r /etc/os-release ]]; then
  echo "Cannot identify Linux distribution." >&2
  exit 1
fi
# shellcheck disable=SC1091
source /etc/os-release
family="${ID:-} ${ID_LIKE:-}"
if [[ "$family" != *debian* && "$family" != *ubuntu* ]]; then
  echo "Only Debian/Ubuntu VPS systems are supported." >&2
  exit 1
fi

if ! command -v git >/dev/null 2>&1; then
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -y
  apt-get install -y --no-install-recommends git ca-certificates
fi

if [[ -e "$INSTALL_DIR" && ! -d "$INSTALL_DIR/.git" ]]; then
  echo "$INSTALL_DIR exists but is not a Git repository; refusing to overwrite it." >&2
  exit 1
fi

if [[ ! -d "$INSTALL_DIR/.git" ]]; then
  git clone --branch "$BRANCH" --single-branch "$REPO_URL" "$INSTALL_DIR"
fi

exec "$INSTALL_DIR/scripts/vps/install.sh" "$@"
