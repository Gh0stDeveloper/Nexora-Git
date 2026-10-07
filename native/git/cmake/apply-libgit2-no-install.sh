#!/usr/bin/env bash
set -Eeuo pipefail

patch_file="$1"

if git apply --reverse --check "$patch_file" >/dev/null 2>&1; then
  exit 0
fi

git apply --check "$patch_file"
git apply --whitespace=nowarn "$patch_file"
