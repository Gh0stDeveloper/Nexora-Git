#!/usr/bin/env python3
from pathlib import Path
import sys

required_files = [
    "README.md",
    "CONTRIBUTING.md",
    "CODE_OF_CONDUCT.md",
    "SECURITY.md",
    "SUPPORT.md",
    ".github/pull_request_template.md",
    ".github/ISSUE_TEMPLATE/bug.yml",
    ".github/ISSUE_TEMPLATE/feature.yml",
    ".github/ISSUE_TEMPLATE/config.yml",
    "docs/GITHUB_REPOSITORY_SETTINGS.md",
    "docs/assets/screenshots/home-light.png",
    "docs/assets/screenshots/home-dark.png",
    "docs/assets/screenshots/home-amoled.png",
    "docs/assets/screenshots/repository-detail.png",
    "docs/assets/screenshots/editor.png",
    "docs/assets/screenshots/git-workbench.png",
    "docs/assets/screenshots/pull-request.png",
    "docs/assets/screenshots/actions.png",
]

missing = [path for path in required_files if not Path(path).is_file()]
if missing:
    print("S.16 launch presentation is incomplete:", file=sys.stderr)
    for path in missing:
        print(f" - missing {path}", file=sys.stderr)
    raise SystemExit(1)

readme = Path("README.md").read_text(encoding="utf-8")
required_readme_markers = [
    "## Get Nexora Git",
    "## Product screenshots",
    "docs/assets/screenshots/home-dark.png",
    "docs/assets/screenshots/repository-detail.png",
    "docs/assets/screenshots/editor.png",
    "docs/assets/screenshots/git-workbench.png",
    "docs/assets/screenshots/pull-request.png",
    "docs/assets/screenshots/actions.png",
    "## Community and contributing",
    "SUPPORT.md",
    "SECURITY.md",
]
missing_markers = [
    marker for marker in required_readme_markers
    if marker not in readme
]
if missing_markers:
    print("README launch surface is incomplete:", file=sys.stderr)
    for marker in missing_markers:
        print(f" - missing marker: {marker}", file=sys.stderr)
    raise SystemExit(1)

for path in required_files:
    file = Path(path)
    if file.suffix.lower() == ".png" and file.stat().st_size < 10_000:
        print(
            f"Screenshot is unexpectedly small and may be invalid: {path}",
            file=sys.stderr,
        )
        raise SystemExit(1)

print("S.16 repository presentation assets passed.")
