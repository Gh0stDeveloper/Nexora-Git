#!/usr/bin/env python3
"""Check active S.16 presentation without faking deferred launch screenshots.

Normal Community Readiness accepts zero screenshots only while the README
and the tracked issue explain why the maintainer deferred manual capture.
Once any screenshot exists, ALL eight must be valid and consistent. The
manual publication gate requires all screenshots explicitly.
"""

import argparse
import hashlib
from pathlib import Path
import struct
import sys
import zlib

BASE_FILES = (
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
)

SCREENSHOT_FILES = tuple(
    "docs/assets/screenshots/" + name + ".png"
    for name in (
        "home-light",
        "home-dark",
        "home-amoled",
        "repository-detail",
        "editor",
        "git-workbench",
        "pull-request",
        "actions",
    )
)
THEME_VARIANTS = SCREENSHOT_FILES[:3]

parser = argparse.ArgumentParser()
parser.add_argument(
    "--require-screenshots",
    action="store_true",
    help="Require all eight final maintainer-approved Android PNG captures.",
)
args = parser.parse_args()

missing = [p for p in BASE_FILES if not Path(p).is_file()]
if missing:
    print("S.16 repository presentation is incomplete:", file=sys.stderr)
    for path in missing:
        print(f" - missing {path}", file=sys.stderr)
    raise SystemExit(1)

readme = Path("README.md").read_text(encoding="utf-8")
settings = Path("docs/GITHUB_REPOSITORY_SETTINGS.md").read_text(encoding="utf-8")
required_readme_markers = (
    "## Get Nexora Git",
    "## Product screenshots",
    "## Community and contributing",
    "SUPPORT.md",
    "SECURITY.md",
)
for marker in required_readme_markers:
    if marker not in readme:
        print(f"Missing README marker: {marker}", file=sys.stderr)
        raise SystemExit(1)

available = [p for p in SCREENSHOT_FILES if Path(p).is_file()]
if not available:
    if args.require_screenshots:
        print("Manual screenshot release gate: 8 final PNGs required.", file=sys.stderr)
        raise SystemExit(1)
    if (
        "issues/47" not in readme
        or "screenshots" not in readme.lower()
        or "issues/47" not in settings
    ):
        print("Screenshot deferral must be documented and tracked in #47.", file=sys.stderr)
        raise SystemExit(1)
    if "docs/assets/screenshots/" in readme:
        print("README must not refer to missing screenshot image files.", file=sys.stderr)
        raise SystemExit(1)
    print("S.16 active presentation passed; all 8 official screenshots deferred to issue #47.")
    raise SystemExit(0)

if len(available) != len(SCREENSHOT_FILES):
    missing_png = sorted(set(SCREENSHOT_FILES) - set(available))
    print(
        "Partial screenshot publication is not allowed; missing: "
        + ", ".join(missing_png),
        file=sys.stderr,
    )
    raise SystemExit(1)

def validate_png(path: Path) -> tuple[int, int, str]:
    raw = path.read_bytes()
    if len(raw) < 10_000 or raw[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("missing PNG signature or image is unexpectedly small")

    offset = 8
    width = height = 0
    seen_idat = seen_iend = False
    while offset < len(raw):
        if offset + 12 > len(raw):
            raise ValueError("truncated PNG chunk")
        size = struct.unpack_from(">I", raw, offset)[0]
        kind = raw[offset + 4 : offset + 8]
        end = offset + 12 + size
        if end > len(raw):
            raise ValueError("truncated PNG payload")
        chunk = raw[offset + 8 : offset + 8 + size]
        expected_crc = struct.unpack_from(">I", raw, offset + 8 + size)[0]
        if zlib.crc32(kind + chunk) & 0xFFFFFFFF != expected_crc:
            raise ValueError(f"bad CRC for {kind!r}")
        if offset == 8:
            if kind != b"IHDR" or size != 13:
                raise ValueError("missing PNG IHDR")
            width, height = struct.unpack_from(">II", chunk)
            if not (480 <= width <= 2000 and width < height <= 4000):
                raise ValueError(f"not a plausible portrait Android screen: {width}x{height}")
        if kind == b"IDAT":
            seen_idat = True
        if kind == b"IEND":
            seen_iend = True
            if end != len(raw):
                raise ValueError("trailing data after PNG IEND")
            break
        offset = end
    if not seen_idat or not seen_iend:
        raise ValueError("missing image data or PNG terminator")
    return (width, height, hashlib.sha256(raw).hexdigest())



results = {}
for path in SCREENSHOT_FILES:
    try:
        results[path] = validate_png(Path(path))
    except ValueError as exc:
        print(f"Invalid screenshot {path}: {exc}", file=sys.stderr)
        raise SystemExit(1) from exc

if len({results[path][2] for path in THEME_VARIANTS}) != len(THEME_VARIANTS):
    print("Light/Dark/AMOLED images must be visually distinct.", file=sys.stderr)
    raise SystemExit(1)

if len({results[path][:2] for path in SCREENSHOT_FILES}) != 1:
    print("Screenshots must use consistent portrait dimensions.", file=sys.stderr)
    raise SystemExit(1)

print("S.16 all 8 screenshot files passed integrity checks.")
