#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

valid_domain "auth.example.com"
valid_domain "git-auth.nexora.dev"

for invalid_domain in "https://auth.example.com" "localhost" "bad_domain.example.com"; do
  if valid_domain "$invalid_domain"; then
    printf 'Expected invalid domain to fail: %s\n' "$invalid_domain" >&2
    exit 1
  fi
done

valid_email "admin@example.com"
if valid_email "not-an-email"; then
  printf 'Expected invalid email to fail.\n' >&2
  exit 1
fi

free="$(find_free_port 25000 25100)"
[[ "$free" =~ ^[0-9]+$ ]]

grep -Fq "exec \"\$INSTALL_DIR/scripts/vps/update.sh\" \"\$@\"" "$SCRIPT_DIR/nexora-git"
if grep -Fq "temp=\"\$(mktemp)\"" "$SCRIPT_DIR/nexora-git"; then
  printf 'Manager must not detach update.sh from lib.sh via a temp copy.\n' >&2
  exit 1
fi

printf 'VPS helper tests passed.\n'
