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

# Check screenshot bytes, not just existence or extension. Build evidence must be
# a non-trivial portrait PNG produced by the Android capture pipeline.
import hashlib
import struct
import zlib


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


screenshots = {
    path: Path(path)
    for path in required_files
    if path.endswith(".png")
}
results = {}
for name, file in screenshots.items():
    try:
        results[name] = validate_png(file)
    except ValueError as exc:
        print(f"Invalid marketing screenshot {name}: {exc}", file=sys.stderr)
        raise SystemExit(1) from exc

variants = [
    "docs/assets/screenshots/home-light.png",
    "docs/assets/screenshots/home-dark.png",
    "docs/assets/screenshots/home-amoled.png",
]
if len({results[name][2] for name in variants}) != len(variants):
    print("Theme screenshot variants are identical.", file=sys.stderr)
    raise SystemExit(1)
if len({results[name][:2] for name in screenshots}) != 1:
    print("Marketing screenshots must use one consistent device size.", file=sys.stderr)
    raise SystemExit(1)

print("S.16 repository presentation assets passed.")
