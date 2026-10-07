#!/usr/bin/env bash
set -Eeuo pipefail

tag="${1:-}"
channel="${2:-}"
[[ -n "$tag" ]] || {
  printf 'Release tag is required.\n' >&2
  exit 1
}
[[ "$channel" == "stable" || "$channel" == "prerelease" ]] || {
  printf 'Release channel must be stable or prerelease.\n' >&2
  exit 1
}

version_name="$(sed -nE 's/^[[:space:]]*versionName = "([^"]+)".*/\1/p' app/build.gradle.kts | head -n 1)"
version_code="$(sed -nE 's/^[[:space:]]*versionCode = ([0-9]+).*/\1/p' app/build.gradle.kts | head -n 1)"
[[ -n "$version_name" && -n "$version_code" ]] || {
  printf 'Unable to read Android version metadata.\n' >&2
  exit 1
}

case "$channel" in
  stable)
    [[ "$tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || {
      printf 'Stable release tag must use vMAJOR.MINOR.PATCH.\n' >&2
      exit 1
    }
    base="${tag#v}"
    ;;
  prerelease)
    [[ "$tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+-(beta|rc)\.[0-9]+$ ]] || {
      printf 'Prerelease tag must use vMAJOR.MINOR.PATCH-beta.N or vMAJOR.MINOR.PATCH-rc.N.\n' >&2
      exit 1
    }
    base="${tag#v}"
    base="${base%%-*}"
    ;;
esac

[[ "$base" == "$version_name" ]] || {
  printf 'Tag %s targets version %s but Android versionName is %s.\n' "$tag" "$base" "$version_name" >&2
  exit 1
}

(( version_code > 0 )) || {
  printf 'Android versionCode must be positive.\n' >&2
  exit 1
}

printf 'Release tag %s accepted for %s channel and Android %s (%s).\n' "$tag" "$channel" "$version_name" "$version_code"
