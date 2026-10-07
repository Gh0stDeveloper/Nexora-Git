#!/usr/bin/env python3
from __future__ import annotations

import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
tag = sys.argv[1] if len(sys.argv) > 1 else ""
if not tag.startswith("v"):
    raise SystemExit("Expected release tag argument.")

channel = "stable"
if "-beta." in tag:
    channel = "beta"
elif "-rc." in tag:
    channel = "rc"

errors: list[str] = []

for locale in ("en-US", "es-MX"):
    root = ROOT / "fastlane/metadata/android" / locale
    files = {
        "title": (root / "title.txt", 30),
        "short description": (root / "short_description.txt", 80),
        "full description": (root / "full_description.txt", 4000),
        "changelog": (root / "changelogs/10000.txt", 500),
    }
    for label, (path, limit) in files.items():
        if not path.is_file():
            errors.append(f"{locale}: missing {path.relative_to(ROOT)}")
            continue
        value = path.read_text(encoding="utf-8").strip()
        if not value:
            errors.append(f"{locale}: {label} is empty")
        if len(value) > limit:
            errors.append(
                f"{locale}: {label} has {len(value)} characters; maximum is {limit}"
            )

for required in (
    ROOT / "docs/PRIVACY.md",
    ROOT / "docs/store/DATA_SAFETY.md",
    ROOT / "docs/store/PLAY_STORE_ASSETS.md",
):
    if not required.is_file() or not required.read_text(encoding="utf-8").strip():
        errors.append(f"missing required store review artifact: {required.relative_to(ROOT)}")


def png_info(path: Path) -> tuple[int, int, int]:
    data = path.read_bytes()[:29]
    if len(data) < 29 or data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG file")
    width, height = struct.unpack(">II", data[16:24])
    color_type = data[25]
    return width, height, color_type


if channel in {"rc", "stable"}:
    en_images = ROOT / "fastlane/metadata/android/en-US/images"
    icon = en_images / "icon.png"
    feature = en_images / "featureGraphic.png"

    try:
        width, height, color_type = png_info(icon)
        if (width, height) != (512, 512):
            errors.append("Play icon must be exactly 512x512")
        if color_type not in {4, 6}:
            errors.append("Play icon PNG must contain an alpha channel")
        if icon.stat().st_size > 1024 * 1024:
            errors.append("Play icon exceeds 1024 KB")
    except (OSError, ValueError) as exc:
        errors.append(f"invalid or missing Play icon: {exc}")

    try:
        width, height, color_type = png_info(feature)
        if (width, height) != (1024, 500):
            errors.append("feature graphic must be exactly 1024x500")
        if color_type in {4, 6}:
            errors.append("feature graphic must not contain an alpha channel")
    except (OSError, ValueError) as exc:
        errors.append(f"invalid or missing feature graphic: {exc}")

    for locale in ("en-US", "es-MX"):
        directory = (
            ROOT
            / "fastlane/metadata/android"
            / locale
            / "images/phoneScreenshots"
        )
        screenshots = sorted(directory.glob("*.png")) if directory.is_dir() else []
        if len(screenshots) < 4:
            errors.append(
                f"{locale}: at least 4 real phone screenshots are required for RC/stable"
            )
            continue
        for screenshot in screenshots[:8]:
            try:
                width, height, _ = png_info(screenshot)
            except (OSError, ValueError) as exc:
                errors.append(f"{screenshot.relative_to(ROOT)}: {exc}")
                continue
            short, long = sorted((width, height))
            if short < 1080:
                errors.append(
                    f"{screenshot.relative_to(ROOT)}: minimum short dimension is 1080"
                )
            if long > 3840 or long > 2 * short:
                errors.append(
                    f"{screenshot.relative_to(ROOT)}: dimensions violate Play screenshot limits"
                )

if errors:
    print(f"Store readiness validation failed for {channel}:", file=sys.stderr)
    for error in errors:
        print(f" - {error}", file=sys.stderr)
    raise SystemExit(1)

print(f"Store readiness validation passed for {channel}.")
