#!/usr/bin/env bash
set -Eeuo pipefail

site_url="${1:-}"
repository="${2:-${GITHUB_REPOSITORY:-Gh0stDeveloper/Nexora-Git}}"

[[ "$site_url" =~ ^https://[^/]+/?$ ]] || {
  echo "Usage: $0 https://production.example [owner/repo]" >&2
  exit 2
}
site_url="${site_url%/}"

command -v gh >/dev/null 2>&1 || {
  echo "GitHub CLI (gh) is required." >&2
  exit 1
}
gh auth status >/dev/null 2>&1 || {
  echo "GitHub CLI is not authenticated." >&2
  exit 1
}

echo "Applying Nexora Git launch settings to $repository"

gh api   --method PATCH   -H "Accept: application/vnd.github+json"   "repos/$repository"   -f homepage="$site_url"   -F has_discussions=true   -F delete_branch_on_merge=true   >/dev/null

python3 - <<'PY' |
import json
print(json.dumps({
    "names": [
        "android",
        "git",
        "github",
        "kotlin",
        "jetpack-compose",
        "libgit2",
        "open-source",
        "git-client",
        "code-editor",
    ]
}))
PY
gh api   --method PUT   -H "Accept: application/vnd.github+json"   "repos/$repository/topics"   --input -   >/dev/null

GITHUB_REPOSITORY="$repository"   bash scripts/ci/validate-github-launch-settings.sh "$site_url"

echo "GitHub launch settings applied and verified."
