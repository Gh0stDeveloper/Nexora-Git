#!/usr/bin/env bash
set -euo pipefail

tag="${1:-}"
test -n "$tag" || {
  echo "Release tag is required."
  exit 1
}

if [[ ! "$tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Release tag must use vMAJOR.MINOR.PATCH."
  exit 1
fi

version_name="$(sed -nE 's/^[[:space:]]*versionName = "([^"]+)".*/\1/p' app/build.gradle.kts | head -n 1)"
version_code="$(sed -nE 's/^[[:space:]]*versionCode = ([0-9]+).*/\1/p' app/build.gradle.kts | head -n 1)"

test -n "$version_name"
test -n "$version_code"
test "$tag" = "v$version_name" || {
  echo "Tag $tag does not match Android versionName $version_name."
  exit 1
}

if (( version_code <= 0 )); then
  echo "Android versionCode must be positive."
  exit 1
fi

echo "Release tag $tag matches Android $version_name ($version_code)."
