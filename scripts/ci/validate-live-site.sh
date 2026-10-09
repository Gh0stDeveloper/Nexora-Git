#!/usr/bin/env bash
set -Eeuo pipefail

site="${1:-}"
[[ -n "$site" ]] || {
  echo "Usage: validate-live-site.sh https://production.example" >&2
  exit 2
}

python3 - "$site" <<'PY'
import sys
from urllib.parse import urlsplit
value = sys.argv[1]
parsed = urlsplit(value)
if (
    parsed.scheme != "https"
    or not parsed.hostname
    or parsed.username
    or parsed.password
    or parsed.path not in ("", "/")
    or parsed.query
    or parsed.fragment
):
    raise SystemExit("Production site URL must be an HTTPS origin.")
PY

site="${site%/}"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

curl -fsS --max-time 15 -D "$tmp/headers" "$site/" -o "$tmp/home"
curl -fsS --max-time 15 "$site/privacy" -o "$tmp/privacy"
curl -fsS --max-time 15 "$site/verify" -o "$tmp/verify"
curl -fsS --max-time 15 "$site/robots.txt" -o "$tmp/robots"
curl -fsS --max-time 15 "$site/sitemap.xml" -o "$tmp/sitemap"

grep -Fq "$site" "$tmp/home"
grep -Fq '"@type":"MobileApplication"' "$tmp/home"
grep -Fq 'Privacy Policy' "$tmp/privacy"
grep -Fq 'Verify a signed build' "$tmp/verify"
grep -Fq "Sitemap: $site/sitemap.xml" "$tmp/robots"
grep -Fq "$site/privacy" "$tmp/sitemap"
grep -Fq "$site/verify" "$tmp/sitemap"

grep -Eiq '^strict-transport-security: *max-age=' "$tmp/headers"
grep -Eiq '^x-content-type-options: *nosniff' "$tmp/headers"
grep -Eiq '^x-frame-options: *DENY' "$tmp/headers"
grep -Eiq '^referrer-policy: *strict-origin-when-cross-origin' "$tmp/headers"
grep -Eiq '^permissions-policy:' "$tmp/headers"
grep -Eiq '^content-security-policy:' "$tmp/headers"

if grep -Eiq '^x-powered-by:' "$tmp/headers"; then
  echo "Production site discloses X-Powered-By." >&2
  exit 1
fi

if grep -Eiq '^server: .*nginx/[0-9]' "$tmp/headers"; then
  echo "Production site discloses the Nginx version." >&2
  exit 1
fi

if grep -Rqs 'nexora-git\.invalid' "$tmp"; then
  echo "Deprecated .invalid fallback is present on the production site." >&2
  exit 1
fi

echo "Live production website validation passed for $site."
