#!/usr/bin/env bash
set -Eeuo pipefail

stable_tag="${1:-}"
[[ "$stable_tag" =~ ^v([0-9]+\.[0-9]+\.[0-9]+)$ ]] || {
  printf 'Stable promotion requires vMAJOR.MINOR.PATCH.\n' >&2
  exit 1
}
version="${BASH_REMATCH[1]}"
qualification="docs/release/qualifications/${version}.md"

[[ -s "$qualification" ]] || {
  printf 'Stable release blocked: missing qualification record %s.\n' "$qualification" >&2
  exit 1
}

candidate="$(sed -nE 's/^Candidate:[[:space:]]*(v[^[:space:]]+).*$/\1/p' "$qualification" | head -n 1)"
status="$(sed -nE 's/^Status:[[:space:]]*([A-Z]+).*$/\1/p' "$qualification" | head -n 1)"

[[ "$status" == "APPROVED" ]] || {
  printf 'Stable release blocked: qualification Status must be APPROVED.\n' >&2
  exit 1
}
[[ "$candidate" =~ ^v${version}-rc\.[0-9]+$ ]] || {
  printf 'Stable release blocked: Candidate must reference v%s-rc.N.\n' "$version" >&2
  exit 1
}

git rev-parse --verify "refs/tags/$candidate" >/dev/null 2>&1 || {
  printf 'Stable release blocked: candidate tag %s does not exist.\n' "$candidate" >&2
  exit 1
}

candidate_sha="$(git rev-list -n 1 "$candidate")"
stable_sha="$(git rev-list -n 1 "$stable_tag")"
git merge-base --is-ancestor "$candidate_sha" "$stable_sha" || {
  printf 'Stable release blocked: RC commit is not an ancestor of stable tag.\n' >&2
  exit 1
}

required=(
  "Clean install"
  "Login and logout"
  "Multiple accounts"
  "Public and private repositories"
  "Clone and import"
  "Editor save"
  "Commit pull push"
  "Conflict workflow"
  "Issues"
  "Pull Requests"
  "Actions"
  "Releases"
  "Offline reopen"
  "Process death"
  "Low storage"
  "Network interruption"
  "English and Spanish"
  "Theme switching"
  "TalkBack"
)
for item in "${required[@]}"; do
  grep -Fq -- "- [x] $item" "$qualification" || {
    printf 'Stable release blocked: qualification item not approved: %s\n' "$item" >&2
    exit 1
  }
done

printf 'Stable promotion gate passed using qualified candidate %s.\n' "$candidate"
