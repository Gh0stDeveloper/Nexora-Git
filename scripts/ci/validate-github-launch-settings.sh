#!/usr/bin/env bash
set -Eeuo pipefail

expected_homepage="${1:-}"
[[ "$expected_homepage" =~ ^https://[^/]+/?$ ]] || {
  echo "Expected homepage must be an HTTPS origin." >&2
  exit 2
}
expected_homepage="${expected_homepage%/}"

json="$(gh api "repos/$GITHUB_REPOSITORY")"

python3 - "$expected_homepage" "$json" <<'PY'
import json
import sys

expected_homepage = sys.argv[1]
repo = json.loads(sys.argv[2])
errors = []

description = (repo.get("description") or "").strip()
if len(description) < 40:
    errors.append("repository description is missing or too short")

homepage = (repo.get("homepage") or "").rstrip("/")
if homepage != expected_homepage:
    errors.append(f"homepage must be {expected_homepage!r}, got {homepage!r}")

topics = set(repo.get("topics") or [])
required_topics = {
    "android",
    "git",
    "github",
    "kotlin",
    "jetpack-compose",
    "libgit2",
    "open-source",
}
missing = sorted(required_topics - topics)
if missing:
    errors.append("missing repository topics: " + ", ".join(missing))

if repo.get("has_discussions") is not True:
    errors.append("GitHub Discussions must be enabled")

if repo.get("delete_branch_on_merge") is not True:
    errors.append("delete_branch_on_merge must be enabled")

if repo.get("default_branch") != "main":
    errors.append("default branch must be main")

if errors:
    print("GitHub launch settings are incomplete:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print("GitHub repository launch settings passed.")
PY
