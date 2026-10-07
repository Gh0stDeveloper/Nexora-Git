#!/usr/bin/env bash
set -Eeuo pipefail

mapfile -t artifacts < <(
  find app/build/outputs -type f \( -name '*.apk' -o -name '*.aab' \) -print 2>/dev/null | sort
)

if (( ${#artifacts[@]} == 0 )); then
  printf 'No APK/AAB artifacts found for secret scan.\n' >&2
  exit 1
fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

patterns_file="$tmp/patterns.txt"
cat > "$patterns_file" <<'EOF'
GITHUB_APP_CLIENT_SECRET=
NEXORA_SIGNING_STORE_PASSWORD=
NEXORA_SIGNING_KEY_PASSWORD=
NEXORA_SIGNING_KEYSTORE_BASE64=
EOF

scan_complete_private_key_block() {
  local file="$1"
  python3 - "$file" <<'PY'
import re
import sys

path = sys.argv[1]
data = open(path, "rb").read()

pattern = re.compile(
    rb"-----BEGIN (?P<kind>(?:RSA |EC |OPENSSH )?PRIVATE KEY)-----"
    rb"[\r\n]+"
    rb"(?P<body>(?:[A-Za-z0-9+/=]{16,}[\r\n]+){2,})"
    rb"-----END (?P=kind)-----"
)

raise SystemExit(0 if pattern.search(data) else 1)
PY
}

scan_exact_value() {
  local root="$1"
  local label="$2"
  local value="$3"
  [[ -n "$value" ]] || return 0

  while IFS= read -r -d '' file; do
    if strings "$file" 2>/dev/null | grep -Fq -- "$value"; then
      printf 'Forbidden secret value leaked into %s (%s).\n' "$label" "$file" >&2
      return 1
    fi
  done < <(find "$root" -type f -print0)
}

for artifact in "${artifacts[@]}"; do
  name="$(basename "$artifact")"
  root="$tmp/${name//[^A-Za-z0-9._-]/_}"
  mkdir -p "$root"
  unzip -qq "$artifact" -d "$root"

  while IFS= read -r -d '' file; do
    if strings "$file" 2>/dev/null | grep -Eiq -- 'gh[pousr]_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{20,}'; then
      printf 'GitHub credential-shaped token found in %s (%s).\n' "$artifact" "$file" >&2
      exit 1
    fi

    if scan_complete_private_key_block "$file"; then
      printf 'Complete private-key PEM block found in %s (%s).\n' "$artifact" "$file" >&2
      exit 1
    fi

    while IFS= read -r pattern; do
      [[ -n "$pattern" ]] || continue
      if strings "$file" 2>/dev/null | grep -Fq -- "$pattern"; then
        printf 'Forbidden credential material found in %s (%s): %s\n' "$artifact" "$file" "$pattern" >&2
        exit 1
      fi
    done < "$patterns_file"
  done < <(find "$root" -type f -print0)

  scan_exact_value "$root" "$artifact" "${NEXORA_FORBIDDEN_GITHUB_APP_CLIENT_SECRET:-}"
  scan_exact_value "$root" "$artifact" "${NEXORA_FORBIDDEN_SIGNING_STORE_PASSWORD:-}"
  scan_exact_value "$root" "$artifact" "${NEXORA_FORBIDDEN_SIGNING_KEY_PASSWORD:-}"
done

printf 'Android artifact secret scan passed for %s artifact(s).\n' "${#artifacts[@]}"
