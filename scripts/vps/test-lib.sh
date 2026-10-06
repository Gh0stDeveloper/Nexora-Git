#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

valid_domain "auth.example.com"
valid_domain "git-auth.nexora.dev"
! valid_domain "https://auth.example.com"
! valid_domain "localhost"
! valid_domain "bad_domain.example.com"

valid_email "admin@example.com"
! valid_email "not-an-email"

free="$(find_free_port 25000 25100)"
[[ "$free" =~ ^[0-9]+$ ]]

printf 'VPS helper tests passed.\n'
